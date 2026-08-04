package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleServiceLoad
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

class VehicleStatusHook : BaseHook() {
    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        val vehicleService = VehicleServiceLoad.findVehicleService(classLoader) ?: run {
            logHook("Status", "VehicleService 未找到")
            return
        }

        safeHook("电动车状态") {
            val method = vehicleService.findMethod {
                paramCount(3)
                voidReturnType()
            }
            method.createHook {
                after { param ->
                    val panelInfo = param.args[0] ?: return@after
                    val faultInfo = param.args[1] ?: return@after
                    val ttpInfo = param.args[2] ?: return@after
                    safeHook("电动车状态-字段读取") {
                        logHook("Vehicle", "PanelInfo: ${panelInfo.dumpFields()}")
                        logHook("Vehicle", "FaultInfo: ${faultInfo.dumpFields()}")
                        logHook("Vehicle", "TtpInfo: ${ttpInfo.dumpFields()}")
                    }
                }
            }
        }
    }
}
