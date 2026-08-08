package com.ghhccghk.yadeahook

import android.content.Context
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.ghhccghk.yadeahook.hooks.BleConnectStateHook
import com.ghhccghk.yadeahook.hooks.ScooterStatusHook
import com.ghhccghk.yadeahook.hooks.VehicleControlHook
import com.ghhccghk.yadeahook.hooks.VehicleDataReadHook
import com.ghhccghk.yadeahook.hooks.VehicleServiceOnNotifyDataCallBackHook
import com.ghhccghk.yadeahook.hooks.VehicleStatusHook
import io.github.lingqiqi5211.ezhooktool.core.loadClassOrNull

object VehicleServiceLoad {
    private const val TAG = "YadeaHook"
    private const val PKG = "com.yadea.smartmoto.vehicle.service"
    internal var classLoader: ClassLoader? = null
        private set

    fun initHooks(context: Context) {
        // 只在主进程初始化，跳过 :pushservice、:remote 等子进程
        val processName = android.app.Application.getProcessName()
        if (processName != "com.yadea.smartmoto") {
            Log.d(TAG, "跳过子进程: $processName")
            return
        }

        val cl = context.classLoader
        classLoader = cl
        VehicleStatusStore.init(context)
        Log.d(TAG, "开始初始化 VehicleService hooks, classLoader=$cl")

        val hooks = listOf(
            BleConnectStateHook(),
            VehicleControlHook(),
            VehicleStatusHook(),
            ScooterStatusHook(),
            VehicleDataReadHook(),
            VehicleServiceOnNotifyDataCallBackHook()
        )

        hooks.forEach { hook ->
            try {
                hook.init(cl, context)
                Log.d(TAG, "Hook 初始化成功: ${hook::class.simpleName}")
            } catch (e: Throwable) {
                Log.e(TAG, "Hook 初始化失败: ${hook::class.simpleName}", e)
            }
        }

        // 注册状态更新广播接收器
        try {
            ContextCompat.registerReceiver(
                context,
                StatusUpdateReceiver(),
                IntentFilter(StatusUpdateReceiver.ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.d(TAG, "StatusUpdateReceiver 已注册")
        } catch (e: Throwable) {
            Log.e(TAG, "StatusUpdateReceiver 注册失败", e)
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
