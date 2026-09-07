package com.ghhccghk.yadeahook.hooks

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleServiceLoad
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

class SpeedAlertHook : BaseHook() {
    private var lastAlertTime = 0L

    companion object {
        const val ACTION_SPEED_ALERT = "com.ghhccghk.yadeahook.SPEED_ALERT"
        const val EXTRA_SPEED = "speed"
        
        // 默认值
        private const val DEFAULT_SPEED_THRESHOLD = 25f
        private const val DEFAULT_COOLDOWN_MS = 10_000L
    }

    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        val vehicleService = VehicleServiceLoad.findVehicleService(classLoader) ?: run {
            logHook("SpeedAlert", "VehicleService 未找到")
            return
        }

        // Hook 电动车状态方法 (3参数)
        safeHook("速度警报-电动车状态") {
            val method = vehicleService.findMethod {
                paramCount(3)
                voidReturnType()
            }
            method.createHook {
                after { param ->
                    val ttpObj = param.args[2] ?: return@after
                    checkSpeed(ttpObj)
                }
            }
        }

        // Hook 滑板车状态方法 (2参数)
        safeHook("速度警报-滑板车状态") {
            val method = vehicleService.findMethod {
                paramCount(2)
                voidReturnType()
            }
            method.createHook {
                after { param ->
                    val entity = param.args[1] ?: return@after
                    val ttpObj = entity.getFieldValue("ttpInfo") ?: return@after
                    checkSpeed(ttpObj)
                }
            }
        }
    }


    private fun checkSpeed(ttpObj: Any) {
        safeHook("速度检查") {
            val speedValue = ttpObj.getFieldValue("currentSpeedValue")
            if (speedValue == null) {
                logHook("SpeedAlert", "速度字段为空")
                return@safeHook
            }
            val speed = when (speedValue) {
                is Number -> speedValue.toFloat()
                is String -> speedValue.toFloatOrNull() ?: return@safeHook
                else -> return@safeHook
            }
            sendAlertBroadcast(speed)

        }
    }

    private fun sendAlertBroadcast(speed: Float) {
        try {
            val intent = Intent(ACTION_SPEED_ALERT).apply {
                putExtra(EXTRA_SPEED, speed)
            }
            appContext.sendBroadcast(intent)
            logHook("SpeedAlert", "发送速度警报广播:  km/h")
        } catch (e: Throwable) {
            Log.e(TAG, "发送广播失败", e)
            logHook("SpeedAlert", "发送广播失败: ")
        }
    }
}
