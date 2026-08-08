package com.ghhccghk.yadeahook.hooks

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleControlReceiver
import com.ghhccghk.yadeahook.VehicleController
import com.ghhccghk.yadeahook.VehicleServiceLoad
import io.github.lingqiqi5211.ezhooktool.core.findAllMethods
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

class VehicleControlHook : BaseHook() {
    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        val companion = VehicleServiceLoad.findCompanion(classLoader) ?: run {
            logHook("Control", "Companion 类未找到")
            return
        }

        // 初始化控制器
        VehicleController.init(companion, classLoader)

        // 动态注册广播接收器（在目标 APP 进程中）
        try {
            ContextCompat.registerReceiver(
                context,
                VehicleControlReceiver(),
                IntentFilter(VehicleControlReceiver.ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )
            logHook("Control", "控制广播接收器已注册")
        } catch (e: Throwable) {
            logHook("Control", "注册接收器失败: ${e.message}")
        }

        safeHook("Companion 方法监控") {
            val methods = companion.findAllMethods {
                notStatic()
                notAbstract()
            }

            for (method in methods) {
                val methodName = method.name
                if (methodName == "<init>") continue

                method.createHook {
                    before { param ->
                        val args = if (param.args.isNullOrEmpty()) {
                            "无参数"
                        } else {
                            param.args.joinToString { arg ->
                                if (arg == null) "null"
                                else "${arg.javaClass.simpleName}:${arg}"
                            }
                        }
                        logHook("Control", "[$methodName] $args")
                    }
                }
            }
        }
    }
}
