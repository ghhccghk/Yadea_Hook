package com.ghhccghk.yadeahook.hooks

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.lyrics.LyricOverlayController
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.loadClassOrNull
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 * 投屏到仪表导航画面的叠加 Hook 框架。
 *
 * 目标方法：
 *   com.yadea.amap.view.RoutePlanningNavigationBaiduActivity.captureViewToBitmap(View): Bitmap
 *
 * 该方法把投屏内容 View 画成 Bitmap，随后经 wifi.a.L(bitmap) 发往仪表 TFT。
 * 在 after 中拿到返回的 Bitmap，逐个调用已注册的 overlayRenderer 在其上叠加绘制，
 * 最后通过 param.result 写回。
 */
class NaviScreenCaptureHook : BaseHook() {

    /** 叠加绘制器列表，按注册顺序执行。 */
    private val overlayRenderers = mutableListOf<(Canvas, Bitmap) -> Unit>()

    /**
     * 已经叠加过的位图。百度 ImageView 路径会先经过 captureViewToBitmap，
     * 再经过 wifi.a.L(Bitmap)；用弱引用集合避免同一帧重复绘制。
     */
    private val overlaidBitmaps = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Bitmap, Boolean>())
    )

    /** 是否打印每一帧的尺寸日志，调试用。 */
    var logEveryFrame = false

    private var diagnosticLogCount = 0

    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context

        val activityClass = loadClassOrNull(
            "com.yadea.amap.view.RoutePlanningNavigationBaiduActivity",
            classLoader = classLoader
        ) ?: run {
            logHook("NaviCapture", "RoutePlanningNavigationBaiduActivity 未找到")
            return
        }

        safeHook("投屏导航 captureViewToBitmap") {
            val method = activityClass.findMethod {
                name("captureViewToBitmap")
                paramCount(1)
            }
            logHook(
                "NaviCapture",
                "已匹配: ${method.name}(" +
                    method.parameterTypes.joinToString(",") { it.simpleName } +
                    ") -> ${method.returnType.simpleName}"
            )

            method.createHook {
                after { param ->
                    val bitmap = param.result as? Bitmap ?: return@after
                    if (diagnosticLogCount++ < 10) {
                        logHook(
                            "NaviCapture",
                            "captureViewToBitmap -> ${bitmap.width}x${bitmap.height}"
                        )
                    }
                    param.result = applyOverlay(bitmap) ?: bitmap
                }
            }
        }

        // 百度 ImageView、百度 GLSurfaceView、高德截图/路口放大图最终都会到这里。
        // 高德路径不经过 captureViewToBitmap，所以这个公共汇合点是两套地图的统一注入点。
        safeHook("投屏导航统一位图入口") {
            val wifiHelper = loadClassOrNull(
                "com.yadea.amap.helper.wifi.a",
                classLoader = classLoader
            ) ?: run {
                logHook("NaviCapture", "wifi.a 未找到")
                return@safeHook
            }

            val method = wifiHelper.findMethod {
                name("L")
                paramCount(1)
            }
            logHook(
                "NaviCapture",
                "已匹配统一入口: ${method.name}(" +
                    method.parameterTypes.joinToString(",") { it.simpleName } +
                    ") -> ${method.returnType.simpleName}"
            )

            method.createHook {
                before { param ->
                    val bitmap = param.args.getOrNull(0) as? Bitmap ?: return@before
                    if (diagnosticLogCount++ < 10) {
                        logHook(
                            "NaviCapture",
                            "wifi.a.L <- ${bitmap.width}x${bitmap.height}"
                        )
                    }
                    val out = applyOverlay(bitmap) ?: return@before
                    if (out !== bitmap) {
                        param.args[0] = out
                    }
                }
            }
        }

        // 高德 WiFi 整图：RoutePlanningNavigationBaiduActivity$y.a(Bitmap)
        // 在 WiFi 模式下调用 wifi.a.P(bitmap)。
        safeHook("高德 WiFi 投屏入口") {
            val wifiHelper = loadClassOrNull(
                "com.yadea.amap.helper.wifi.a",
                classLoader = classLoader
            ) ?: return@safeHook

            val method = wifiHelper.findMethod {
                name("P")
                paramCount(1)
            }
            logHook("NaviCapture", "已匹配 WiFi 整图入口: ${method.name}(Bitmap)")

            method.createHook {
                before { param ->
                    val bitmap = param.args.getOrNull(0) as? Bitmap ?: return@before
                    if (diagnosticLogCount++ < 10) {
                        logHook("NaviCapture", "wifi.a.P <- ${bitmap.width}x${bitmap.height}")
                    }
                    val out = applyOverlay(bitmap) ?: return@before
                    if (out !== bitmap) {
                        param.args[0] = out
                    }
                }
            }
        }

        // 高德 TFT_IMG：图片监听器由 sendImageRequestToDevice 接收，
        // 监听器对象里通常持有原始 Bitmap 字段；按类型扫描，避免依赖构建时变化的字段名。
        safeHook("高德 TFT 图片投屏入口") {
            val controlClass = loadClassOrNull(
                "com.yadea.blecontrol.control.BleBicycleNaviImgControl",
                classLoader = classLoader
            ) ?: return@safeHook

            val method = controlClass.findMethod {
                name("sendImageRequestToDevice")
                paramCount(1)
            }
            logHook("NaviCapture", "已匹配 TFT 图片入口: sendImageRequestToDevice")

            method.createHook {
                before { param ->
                    val listener = param.args.getOrNull(0) ?: return@before
                    val bitmap = findBitmapField(listener) ?: return@before
                    if (diagnosticLogCount++ < 10) {
                        logHook(
                            "NaviCapture",
                            "TFT listener bitmap <- ${bitmap.width}x${bitmap.height} " +
                                "listener=${listener.javaClass.name}"
                        )
                    }
                    if (bitmap.isMutable) {
                        applyOverlay(bitmap)
                    } else if (diagnosticLogCount++ < 10) {
                        logHook("NaviCapture", "TFT listener bitmap 不可变，等待压缩入口替换")
                    }
                }
            }
        }

        // TFT_IMG 最终会调用 ViewModel.L(bitmap, format, quality, target, max) 压缩。
        // 在这里替换为已叠加的可变 Bitmap，保证 BLE 发出去的字节包含歌词。
        safeHook("高德 TFT 压缩入口") {
            val viewModelClass = loadClassOrNull(
                "com.yadea.amap.viewmodel.RoutePlanningNavigationViewModel",
                classLoader = classLoader
            ) ?: return@safeHook

            val method = viewModelClass.declaredMethods.firstOrNull {
                it.parameterTypes.size == 5 &&
                    it.parameterTypes[0] == Bitmap::class.java
            } ?: return@safeHook
            logHook("NaviCapture", "已匹配 TFT 压缩入口: ${method.name}(Bitmap,...)")

            method.createHook {
                before { param ->
                    val bitmap = param.args.getOrNull(0) as? Bitmap ?: return@before
                    if (diagnosticLogCount++ < 10) {
                        logHook(
                            "NaviCapture",
                            "ViewModel.${method.name} <- ${bitmap.width}x${bitmap.height} " +
                                "mutable=${bitmap.isMutable}"
                        )
                    }
                    val out = applyOverlay(bitmap) ?: return@before
                    if (out !== bitmap) {
                        param.args[0] = out
                    }
                }
            }
        }
    }

    private fun findBitmapField(value: Any): Bitmap? {
        var clazz: Class<*>? = value.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                try {
                    field.isAccessible = true
                    val child = field.get(value)
                    if (child is Bitmap) return child
                } catch (_: Throwable) {
                    // 继续扫描下一个字段
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun applyOverlay(bitmap: Bitmap): Bitmap? {
        if (overlayRenderers.isEmpty() && !LyricOverlayController.hasOverlay()) {
            return bitmap
        }

        val mutable = if (bitmap.isMutable) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: run {
                logHook("NaviCapture", "无法复制 immutable Bitmap")
                return null
            }
        }

        if (!overlaidBitmaps.add(mutable)) return mutable

        if (diagnosticLogCount++ < 10) {
            logHook(
                "NaviCapture",
                "applyOverlay ${mutable.width}x${mutable.height} " +
                    "renderers=${overlayRenderers.size} " +
                    "lyricOverlay=${LyricOverlayController.hasOverlay()} " +
                    "copied=${mutable !== bitmap}"
            )
        }

        var success = false
        safeHook("投屏导航叠加绘制") {
            try {
                if (logEveryFrame) {
                    logHook(
                        "NaviCapture",
                        "frame=${mutable.width}x${mutable.height} overlays=${overlayRenderers.size}"
                    )
                }
                val canvas = Canvas(mutable)
                for (renderer in overlayRenderers) {
                    renderer(canvas, mutable)
                }
                LyricOverlayController.draw(canvas, mutable)
                success = true
            } catch (e: Throwable) {
                overlaidBitmaps.remove(mutable)
                throw e
            }
        }
        return if (success) mutable else null
    }

    /**
     * 注册一个叠加绘制器。返回 this 便于链式调用。
     */
    fun addOverlay(renderer: (Canvas, Bitmap) -> Unit): NaviScreenCaptureHook {
        overlayRenderers += renderer
        return this
    }

    /** 清空所有叠加绘制器。 */
    fun clearOverlays() {
        overlayRenderers.clear()
    }
}

/**
 * 示例叠加器：在左上角画一行调试文字。
 * 业务上把 [NaviScreenCaptureHook.addOverlay] 的参数替换成自己的绘制即可。
 */
fun debugTextOverlay(text: String): (Canvas, Bitmap) -> Unit = { canvas, _ ->
    val paint = Paint().apply {
        color = Color.RED
        textSize = 48f
        isAntiAlias = true
    }
    canvas.drawText(text, 24f, 64f, paint)
}
