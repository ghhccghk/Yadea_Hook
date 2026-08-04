package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleServiceLoad
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

class ScooterStatusHook : BaseHook() {
    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        val vehicleService = VehicleServiceLoad.findVehicleService(classLoader) ?: run {
            logHook("Scooter", "VehicleService 未找到")
            return
        }

        safeHook("滑板车状态") {
            val method = vehicleService.findMethod {
                paramCount(2)
                voidReturnType()
            }
            method.createHook {
                after { param ->
                    val scooterInfo = param.args[0] ?: return@after
                    val ttpInfo = param.args[1] ?: return@after
                    safeHook("滑板车状态-字段读取") {
                        logHook("Scooter", "ScooterStatusInfo: ${scooterInfo.dumpFields()}")
                        logHook("Scooter", "TtpInfo: ${ttpInfo.dumpFields()}")
                    }
                }
            }
        }
    }
}
