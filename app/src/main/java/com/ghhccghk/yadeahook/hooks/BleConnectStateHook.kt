package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleServiceLoad
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

class BleConnectStateHook : BaseHook() {
    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        val callbackClass = VehicleServiceLoad.findBleCallback(classLoader) ?: run {
            logHook("BLE", "BLE 回调类未找到")
            return
        }

        safeHook("BLE连接状态") {
            val method = callbackClass.findMethod {
                name("onConnectStateChange")
                paramCount(3)
            }
            method.createHook {
                after { param ->
                    val state = param.argAs<Int>(1)
                    val subState = param.argAs<Int>(2)
                    val stateText = when (state) {
                        0 -> "断开"
                        1 -> "连接中"
                        2 -> "已连接"
                        else -> "未知($state)"
                    }
                    logHook("BLE", "蓝牙状态: $stateText, 子状态: $subState")
                }
            }
        }
    }
}
