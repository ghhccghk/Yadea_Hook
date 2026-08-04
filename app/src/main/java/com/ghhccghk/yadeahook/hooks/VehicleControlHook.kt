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

        safeHook("车辆控制命令") {
            val controlMethods = companion.findAllMethods {
                paramCount(1)
                voidReturnType()
            }

            for (method in controlMethods) {
                val methodName = method.name
                method.createHook {
                    before { param ->
                        val commandBean = param.args[0] ?: return@before
                        val commandType = commandBean.callGetter("getCommandType") as? String
                        logHook("Control", "[$methodName] $commandType")
                    }
                }
            }
        }
    }
}
