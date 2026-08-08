package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleServiceLoad
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

class VehicleServiceOnNotifyDataCallBackHook : BaseHook() {
    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        val callbackClass = VehicleServiceLoad.findBleCallback(classLoader) ?: run {
            logHook("Control", "回调类未找到")
            return
        }

        safeHook("BLE连接状态") {
            val method = callbackClass.findMethod {
                name("onNotifyDataCallBack")
            }
            logHook("onNotifyDataCallBack", "匹配方法: ${method.name} params=${method.parameterTypes.map { it.simpleName }}")
            method.createHook {
                before { param ->
                    val bleNotifyEntity = param.args[0] ?: return@before
                    logHook("BLE", "收到通知数据: ${bleNotifyEntity.dumpFields()}")

                }
            }
        }
    }
}