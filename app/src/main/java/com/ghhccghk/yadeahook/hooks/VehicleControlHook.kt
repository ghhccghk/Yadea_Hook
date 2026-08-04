package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
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
