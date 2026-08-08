package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleServiceLoad
import com.ghhccghk.yadeahook.VehicleStatusStore
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
            logHook("Vehicle", "匹配方法: ${method.name} params=${method.parameterTypes.map { it.simpleName }}")
            method.createHook {
                after { param ->
                    val ttpObj = param.args[2] ?: return@after
                    safeHook("电动车状态-字段读取") {
                        val data = mutableMapOf<String, String>()
                        ttpObj.getFieldValue("maximumSpeedCurrent")?.let { data["最高车速当前值"] = "$it" }
                        ttpObj.getFieldValue("busbarVoltage")?.let { data["总电池电压"] = "$it" }
                        ttpObj.getFieldValue("busbarCurrent")?.let { data["总电池电流"] = "$it" }
                        ttpObj.getFieldValue("remainingMileage")?.let { data["剩余里程"] = "$it" }
                        ttpObj.getFieldValue("currentSpeedValue")?.let { data["车速 当前值"] = "$it" }
                        ttpObj.getFieldValue("currentOdoValue")?.let { data["ODO 当前值"] = "$it" }
                        ttpObj.getFieldValue("currentTripValue")?.let { data["TRIP 当前值"] = "$it" }
                        if (data.isNotEmpty()) {
//                            VehicleStatusStore.update(data)
                            logHook("Vehicle", data.entries.joinToString(", ") { "${it.key}: ${it.value}" })
                        }
                    }
                }
            }
        }
    }
}
