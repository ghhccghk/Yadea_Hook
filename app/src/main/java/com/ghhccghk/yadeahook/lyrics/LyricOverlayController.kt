package com.ghhccghk.yadeahook.lyrics

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hchen.superlyricapi.ISuperLyricReceiver
import com.hchen.superlyricapi.SuperLyricCache
import com.hchen.superlyricapi.SuperLyricData
import com.hchen.superlyricapi.SuperLyricHelper
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * 负责三件事：
 * 1. 注册 SuperLyric 接收器，保存当前歌词行；
 * 2. 在目标导航 Activity 中挂一个屏幕外的 ComposeView；
 * 3. 把 ComposeView 画到投屏 Bitmap 的顶部。
 */
object LyricOverlayController {
    private const val TAG = "YadeaHook-Lyric"
    private const val OVERLAY_DENSITY = 1.3f
    private const val OVERLAY_FONT_SCALE = 1.6f

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lyricState = mutableStateOf<SuperLyricData?>(null)
    private val lyricRefreshToken = mutableStateOf(0L)
    private val musicSourceState = mutableStateOf<MusicSource?>(null)
    private val darkThemeState = mutableStateOf(false)
    private val musicSourceCache = mutableMapOf<String, MusicSource>()

    private var appContext: Context? = null
    private var receiver: ISuperLyricReceiver? = null
    private var activityRef: WeakReference<Activity>? = null
    private var composeView: ComposeView? = null
    private var composeLifecycleOwner: ModuleComposeLifecycleOwner? = null
    private var firstDrawLogged = false
    private var canvasWidth = 0
    private var canvasHeight = 0

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureHiddenApiBypass()
        ensureReceiver()
    }

    fun hasOverlay(): Boolean = composeView != null

    fun attach(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.w(TAG, "Android 10 以下跳过 Compose 歌词条")
            return
        }

        ensureReceiver()
        Log.d(TAG, "attach: ${activity.javaClass.name}")

        syncOverlayTheme(activity)
        if (activityRef?.get() === activity && composeView != null) {
            return
        }

        detach()
        activityRef = WeakReference(activity)

        val view = try {
            val lifecycleOwner = ModuleComposeLifecycleOwner()
            val savedStateOwner = ModuleComposeSavedStateOwner(lifecycleOwner)
            val viewModelStoreOwner = ModuleComposeViewModelStoreOwner()
            ComposeView(activity).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(savedStateOwner)
                setViewTreeViewModelStoreOwner(viewModelStoreOwner)
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = OVERLAY_DENSITY,
                            fontScale = OVERLAY_FONT_SCALE
                        )
                    ) {
                        LyricOverlay(
                            data = lyricState.value,
                            source = musicSourceState.value,
                            refreshToken = lyricRefreshToken.value,
                            isDarkTheme = darkThemeState.value
                        )
                    }
                }

                // 保持在窗口内参与组合和测量，但移到屏幕外；绘制时直接 view.draw(canvas)。
                translationY = -10000f
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }.also {
                composeLifecycleOwner = lifecycleOwner
            }
        } catch (e: Throwable) {
            Log.e(TAG, "创建 ComposeView 失败", e)
            return
        }

        // SavedStateRegistry 必须先在 INITIALIZED 阶段完成 restore，ComposeView 再进入 RESUMED。
        composeLifecycleOwner?.resume()

        try {
            activity.addContentView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP
                )
            )
            composeView = view
            Log.d(TAG, "ComposeView attached")
            view.post {
                Log.d(
                    TAG,
                    "ComposeView measured: ${view.width}x${view.height}, " +
                        "lyric=${lyricState.value?.lyric?.text}"
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "挂载 ComposeView 失败", e)
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    fun detach() {
        composeView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        composeView = null
        activityRef = null
        canvasWidth = 0
        canvasHeight = 0
        composeLifecycleOwner?.destroy()
        composeLifecycleOwner = null
    }

    /**
     * 把歌词条画到仪表位图顶部。按目标位图宽度缩放 ComposeView。
     */
    fun draw(canvas: Canvas, target: Bitmap) {
        val view = composeView ?: return
        if (view.width <= 0 || view.height <= 0) return

        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "跳过非主线程歌词绘制")
            return
        }

        // 原 App 的夜间模式由 AppCompat 手动切换，以导航 Activity 的实际配置为准。
        syncOverlayTheme(activityRef?.get())

        if (!firstDrawLogged) {
            firstDrawLogged = true
            Log.d(
                TAG,
                "first draw: compose=${view.width}x${view.height}, " +
                    "target=${target.width}x${target.height}, " +
                    "lyric=${lyricState.value?.lyric?.text}"
            )
        }

        // Compose 画布与投屏 Bitmap 1:1：首次拿到目标尺寸后，把 ComposeView
        // 的布局尺寸设成 Bitmap 的宽高，不再按 Activity 宽度做整体缩放。
        if (canvasWidth != target.width || canvasHeight != target.height ||
            view.width != target.width || view.height != target.height
        ) {
            val params = (view.layoutParams as? FrameLayout.LayoutParams)
                ?.apply {
                    width = target.width
                    height = target.height
                    gravity = Gravity.TOP
                }
                ?: FrameLayout.LayoutParams(
                    target.width,
                    target.height,
                    Gravity.TOP
                )
            view.layoutParams = params
            view.measure(
                View.MeasureSpec.makeMeasureSpec(
                    target.width,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    target.height,
                    View.MeasureSpec.EXACTLY
                )
            )
            view.layout(0, 0, target.width, target.height)
            canvasWidth = target.width
            canvasHeight = target.height
            Log.d(
                TAG,
                "Compose canvas resized to bitmap: ${canvasWidth}x${canvasHeight}"
            )
        }

        view.draw(canvas)
    }

    private fun syncOverlayTheme(activity: Activity?) {
        val isDark = activity?.resources?.configuration?.let { configuration ->
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        } ?: false
        if (darkThemeState.value != isDark) {
            darkThemeState.value = isDark
        }
    }

    private fun ensureReceiver() {
        if (receiver != null) return

        val available = try {
            SuperLyricHelper.isAvailable()
        } catch (e: Throwable) {
            Log.w(TAG, "SuperLyric 服务不可用: ${e.message}")
            false
        }
        if (!available) return

        val newReceiver = object : ISuperLyricReceiver.Stub() {
            override fun onLyric(publisher: String?, rawData: SuperLyricData?) {
                val data = try {
                    SuperLyricCache.resolve(rawData)
                } catch (e: Throwable) {
                    Log.w(TAG, "解析 SuperLyric 数据失败", e)
                    rawData
                }
                if (data != null) {
                    val source = publisher
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::resolveMusicSource)
                    mainHandler.post {
                        lyricState.value = data
                        lyricRefreshToken.value += 1L
                        if (source != null) {
                            musicSourceState.value = source
                        }
                    }
                }
            }

            override fun onStop(publisher: String?, data: SuperLyricData?) {
                mainHandler.post { lyricState.value = null }
            }
        }

        try {
            SuperLyricHelper.registerReceiver(newReceiver)
            receiver = newReceiver
            Log.d(TAG, "SuperLyric 接收器已注册")
        } catch (e: Throwable) {
            Log.e(TAG, "注册 SuperLyric 接收器失败", e)
            return
        }

        // 冷启动时主动补一次当前歌曲数据。
        Thread({
            try {
                val latest = SuperLyricHelper.getLatestLyric() ?: return@Thread
                val resolved = SuperLyricCache.resolve(latest) ?: latest
                mainHandler.post {
                    lyricState.value = resolved
                    lyricRefreshToken.value += 1L
                }
            } catch (e: Throwable) {
                Log.w(TAG, "拉取当前歌词失败: ${e.message}")
            }
        }, "YadeaHook-Lyric-Init").start()
    }

    private fun ensureHiddenApiBypass() {
        try {
            HiddenApiBypass.addHiddenApiExemptions("")
        } catch (e: Throwable) {
            Log.w(TAG, "HiddenApiBypass 初始化失败: ${e.message}")
        }
    }

    private fun resolveMusicSource(packageName: String): MusicSource? {
        synchronized(musicSourceCache) {
            musicSourceCache[packageName]?.let { return it }
        }

        val context = appContext ?: return MusicSource(packageName, packageName, null)
        val packageManager = context.packageManager

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo)
                .toString()
                .ifBlank { packageName }
            val iconSize = (20 * context.resources.displayMetrics.density)
                .roundToInt()
                .coerceAtLeast(1)
            val icon = packageManager.getApplicationIcon(appInfo)
                .toBitmap(iconSize, iconSize)
                .asImageBitmap()
            MusicSource(packageName, appName, icon).also { source ->
                Log.d(TAG, "music source: $packageName -> $appName, icon=${icon != null}")
                synchronized(musicSourceCache) {
                    musicSourceCache[packageName] = source
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "音乐应用不存在: $packageName")
            MusicSource(packageName, packageName, null)
        } catch (e: Throwable) {
            Log.w(TAG, "读取音乐应用信息失败: $packageName", e)
            MusicSource(packageName, packageName, null)
        }
    }
}
