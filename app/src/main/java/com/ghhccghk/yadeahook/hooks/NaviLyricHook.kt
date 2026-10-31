package com.ghhccghk.yadeahook.hooks

import android.app.Activity
import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.lyrics.LyricOverlayController
import io.github.libxposed.api.XposedModule
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.loadClassOrNull
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

/**
 * 在百度/高德共用的导航 Activity 上挂载 Compose 歌词条。
 */
class NaviLyricHook(
    private val module: XposedModule,
) : BaseHook() {
    companion object {
        fun shutdown() {
            LyricOverlayController.shutdown()
        }
    }

    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        LyricOverlayController.init(context, module)

        val activityClass = loadClassOrNull(
            "com.yadea.amap.view.RoutePlanningNavigationBaiduActivity",
            classLoader = classLoader
        ) ?: run {
            logHook("NaviLyric", "导航 Activity 未找到")
            return
        }

        safeHook("导航歌词 onResume") {
            activityClass.findMethod {
                name("onResume")
                paramCount(0)
            }.createHook {
                after { param ->
                    logHook(
                        "NaviLyric",
                        "onResume this=${param.thisObjectOrNull?.javaClass?.name}"
                    )
                    val activity = param.thisObjectOrNull as? Activity ?: return@after
                    LyricOverlayController.attach(activity)
                }
            }
        }

        safeHook("导航歌词 onDestroy") {
            activityClass.findMethod {
                name("onDestroy")
                paramCount(0)
            }.createHook {
                after {
                    logHook("NaviLyric", "onDestroy")
                    LyricOverlayController.detach()
                }
            }
        }
    }
}
