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
                    val ttpInfo = param.args[1] ?: return@after
                    safeHook("滑板车状态-字段读取") {
                        val gear = ttpInfo.getIntField("drivingMemory")
                        val cruise = ttpInfo.getIntField("cruiseControl")
                        val energyRecovery = ttpInfo.getIntField("brakeModePowerRecoverGearStatus")
                        val lockState = ttpInfo.getIntField("parkingStatus")

                        val gearText = if (gear >= 0) "${gear + 1}档" else "未知"
                        val cruiseText = when (cruise) {
                            0 -> "关闭"
                            1 -> "开启"
                            else -> "未知"
                        }
                        val lockText = when (lockState) {
                            0 -> "未锁"
                            1 -> "已锁"
                            else -> "未知"
                        }
                        logHook("Scooter", "档位: $gearText, 巡航: $cruiseText, 锁车: $lockText, 能量回收: $energyRecovery")
                    }
                }
            }
        }
    }
}
