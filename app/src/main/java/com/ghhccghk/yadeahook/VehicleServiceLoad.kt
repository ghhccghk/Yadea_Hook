package com.ghhccghk.yadeahook

import android.content.Context
import android.util.Log
import com.ghhccghk.yadeahook.hooks.BleConnectStateHook
import com.ghhccghk.yadeahook.hooks.ScooterStatusHook
import com.ghhccghk.yadeahook.hooks.VehicleControlHook
import com.ghhccghk.yadeahook.hooks.VehicleStatusHook
import io.github.lingqiqi5211.ezhooktool.core.loadClassOrNull

object VehicleServiceLoad {
    private const val TAG = "YadeaHook"
    private const val PKG = "com.yadea.smartmoto.vehicle.service"

    fun initHooks(context: Context) {
        val classLoader = context.classLoader
        VehicleStatusStore.init(context)
        Log.d(TAG, "开始初始化 VehicleService hooks, classLoader=$classLoader")

        val hooks = listOf(
            BleConnectStateHook(),
            VehicleControlHook(),
            VehicleStatusHook(),
            ScooterStatusHook()
        )

        hooks.forEach { hook ->
            try {
                hook.init(classLoader, context)
                Log.d(TAG, "Hook 初始化成功: ${hook::class.simpleName}")
            } catch (e: Throwable) {
                Log.e(TAG, "Hook 初始化失败: ${hook::class.simpleName}", e)
            }
        }
    }

    private fun safeLoadClass(name: String, classLoader: ClassLoader): Class<*>? {
        return try {
            loadClassOrNull(name, classLoader = classLoader)
        } catch (e: Throwable) {
            Log.w(TAG, "类加载失败: $name - ${e.message}")
            null
        }
    }

    internal fun findVehicleService(classLoader: ClassLoader): Class<*>? {
        return safeLoadClass("$PKG.VehicleService", classLoader)
    }

    internal fun findCompanion(classLoader: ClassLoader): Class<*>? {
        return safeLoadClass("$PKG.VehicleService\$a", classLoader)
            ?: safeLoadClass("$PKG.a", classLoader)
    }

    internal fun findBleCallback(classLoader: ClassLoader): Class<*>? {
        return safeLoadClass("$PKG.VehicleService\$b", classLoader)
            ?: safeLoadClass("$PKG.b", classLoader)
    }
}
