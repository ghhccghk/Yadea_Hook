package com.ghhccghk.yadeahook

import android.util.Log

object VehicleController {
    private const val TAG = "YadeaHook"
    private var companionClass: Class<*>? = null
    private var companionInstance: Any? = null
    private var commandBeanClass: Class<*>? = null

    fun init(companion: Class<*>, cl: ClassLoader) {
        companionClass = companion
        commandBeanClass = try {
            cl.loadClass("com.yadea.smartmoto.vehicle.bean.CommandBean")
        } catch (_: Throwable) { null }

        try {
            val vehicleServiceClass = cl.loadClass("com.yadea.smartmoto.vehicle.service.VehicleService")
            val field = vehicleServiceClass.getDeclaredField("o")
            field.isAccessible = true
            companionInstance = field.get(null)
        } catch (e: Throwable) {
            Log.w(TAG, "获取 companion 实例失败: ${e.message}")
        }

        Log.d(TAG, "VehicleController 初始化: companion=${companion.name}, instance=${companionInstance?.javaClass?.name}, CommandBean=${commandBeanClass?.name}")
    }

    fun execute(commandType: String, param: Int = 0) {
        try {
            val cls = companionClass ?: run { Log.w(TAG, "companion 未初始化"); return }
            val cmdClass = commandBeanClass ?: run { Log.w(TAG, "CommandBean 未找到"); return }
            val companion = companionInstance ?: run { Log.w(TAG, "companion 实例为 null"); return }

            // 创建 CommandBean(String commandType)
            val cmd = cmdClass.getConstructor(String::class.java).newInstance(commandType)

            // 调用 t(CommandBean, false) 做命令映射
            // t() 会将 SCOOTER_LOCK → FORTIFY, SCOOTER_UNLOCK → RELEASE_FORTIFY 等
            val mappedCmd = try {
                cls.getMethod("t", cmdClass, Boolean::class.javaPrimitiveType)
                    .invoke(companion, cmd, true)
            } catch (_: Throwable) { cmd }

            // 通过 i() 发送命令
            cls.getMethod("i", cmdClass).invoke(companion, mappedCmd)
            Log.d(TAG, "控制命令已发送: $commandType → ${mappedCmd.javaClass.getMethod("getCommandType").invoke(mappedCmd)} via i()")
        } catch (e: Throwable) {
            Log.e(TAG, "控制命令失败: $commandType", e)
        }
    }

    fun connect(type: String = "scooter", mac: String = "") {
        try {
            val cls = companionClass ?: return
            val companion = companionInstance ?: return
            when (type) {
                "scooter" -> cls.getMethod("g", String::class.java).invoke(companion, mac)
                "bicycle" -> cls.getMethod("e", String::class.java, Boolean::class.javaPrimitiveType).invoke(companion, mac, false)
                "box" -> cls.getMethod("f", String::class.java).invoke(companion, mac)
            }
            Log.d(TAG, "连接命令: $type mac=$mac")
        } catch (e: Throwable) {
            Log.e(TAG, "连接命令失败", e)
        }
    }

    fun disconnect() {
        try {
            val cls = companionClass ?: return
            val companion = companionInstance ?: return
            cls.getMethod("o").invoke(companion)
            Log.d(TAG, "断开连接")
        } catch (e: Throwable) {
            Log.e(TAG, "断开连接失败", e)
        }
    }

    fun cancelScan() {
        try {
            val cls = companionClass ?: return
            val companion = companionInstance ?: return
            cls.getMethod("d").invoke(companion)
            Log.d(TAG, "取消扫描")
        } catch (e: Throwable) {
            Log.e(TAG, "取消扫描失败", e)
        }
    }
}
