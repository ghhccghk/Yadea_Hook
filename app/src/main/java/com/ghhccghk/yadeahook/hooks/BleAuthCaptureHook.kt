package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.BleAuthStore
import com.ghhccghk.yadeahook.protocol.BleFrameClassifier
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BLE 认证精确抓取 Hook —— 基于真实协议指纹识别认证序列。
 *
 * Hook 点（Android 系统蓝牙层，不依赖 App 混淆类）：
 *  1. BluetoothGatt.writeCharacteristic(BluetoothGattCharacteristic)     [API 18+]
 *  2. BluetoothGatt.writeCharacteristic(BluetoothGattCharacteristic, byte[], int) [API 33+]
 *  3. BluetoothGattCharacteristic.setValue(byte[])                        [兜底]
 *  4. BluetoothGattCallback.onCharacteristicWrite / onCharacteristicChanged / onConnectionStateChange
 *
 * 抓取内容：
 *  - 所有写入字节 + 目标特征 UUID（确认 b362 写特征）
 *  - 按协议指纹识别 INIT / KEY / QUERY / VERSION / QUERY2 / POLL_0D / POLL_7A8F / START / STOP
 *  - 车辆推送数据（INDICATE/NOTIFY 内容，认证成功的信号）
 *  - 输出完整认证序列，UI 一键复制
 */
class BleAuthCaptureHook : BaseHook() {

    private val seenInit = AtomicBoolean(false)
    private var lastInitAt = 0L
    private val sessionWindowMs = 30000L   // 30s 内视为同一会话

    // 系统类用 Class.forName 加载（framework 类，不依赖 App classloader）
    private fun sysClass(name: String): Class<*>? = try {
        Class.forName(name)
    } catch (_: Throwable) { null }

    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        BleAuthStore.init(context)

        hookGattWrite()
        hookSetValue()
        hookGattCallback()
    }

    // ============================================================
    //  1. BluetoothGatt.writeCharacteristic（两个重载）
    // ============================================================
    private fun hookGattWrite() {
        safeHook("GATT writeCharacteristic") {
            val gattClass = sysClass("android.bluetooth.BluetoothGatt")
                ?: run { logHook("BLE-Auth", "BluetoothGatt 类未找到"); return@safeHook }
            val charClass = sysClass("android.bluetooth.BluetoothGattCharacteristic")
                ?: run { logHook("BLE-Auth", "BluetoothGattCharacteristic 类未找到"); return@safeHook }

            // 老 API: writeCharacteristic(BluetoothGattCharacteristic)
            try {
                val m = gattClass.getMethod("writeCharacteristic", charClass)
                m.createHook {
                    before { param ->
                        val char = param.args[0] ?: return@before
                        val uuid = charUuid(char)
                        val value = charValue(char)
                        if (value != null) onWrite(uuid, bytesToHex(value))
                    }
                }
                logHook("BLE-Auth", "Hook writeCharacteristic(1) 已安装")
            } catch (e: Throwable) {
                logHook("BLE-Auth", "writeCharacteristic(1) hook 失败: ${e.message}")
            }

            // 新 API (API 33+): writeCharacteristic(BluetoothGattCharacteristic, byte[], int)
            try {
                val m = gattClass.getMethod("writeCharacteristic", charClass, ByteArray::class.java, Int::class.javaPrimitiveType)
                m.createHook {
                    before { param ->
                        val char = param.args[0] ?: return@before
                        val value = param.args[1] as? ByteArray ?: return@before
                        val uuid = charUuid(char)
                        onWrite(uuid, bytesToHex(value))
                    }
                }
                logHook("BLE-Auth", "Hook writeCharacteristic(3) 已安装")
            } catch (e: Throwable) {
                logHook("BLE-Auth", "writeCharacteristic(3) hook 失败: ${e.message}")
            }
        }
    }

    // ============================================================
    //  2. BluetoothGattCharacteristic.setValue(byte[]) 兜底
    // ============================================================
    private fun hookSetValue() {
        safeHook("GATT setValue") {
            val charClass = sysClass("android.bluetooth.BluetoothGattCharacteristic")
                ?: run { logHook("BLE-Auth", "BluetoothGattCharacteristic 类未找到"); return@safeHook }

            try {
                val m = charClass.getMethod("setValue", ByteArray::class.java)
                m.createHook {
                    before { param ->
                        val bytes = param.args[0] as? ByteArray ?: return@before
                        val uuid = try {
                            val thisObj = getThisObject(param)
                            thisObjectUuid(thisObj)
                        } catch (_: Throwable) { "" }
                        onWrite(uuid, bytesToHex(bytes))
                    }
                }
                logHook("BLE-Auth", "Hook setValue 已安装")
            } catch (e: Throwable) {
                logHook("BLE-Auth", "setValue hook 失败: ${e.message}")
            }
        }
    }

    // ============================================================
    //  3. GATT 回调：写确认 / 车辆推送 / 连接状态
    // ============================================================
    private fun hookGattCallback() {
        safeHook("GATT 回调") {
            val cbClass = sysClass("android.bluetooth.BluetoothGattCallback")
                ?: run { logHook("BLE-Auth", "BluetoothGattCallback 类未找到"); return@safeHook }
            val gattClass = sysClass("android.bluetooth.BluetoothGatt")
                ?: return@safeHook
            val charClass = sysClass("android.bluetooth.BluetoothGattCharacteristic")
                ?: return@safeHook

            // onCharacteristicWrite(BluetoothGatt, BluetoothGattCharacteristic, int)
            try {
                val m = cbClass.getMethod("onCharacteristicWrite", gattClass, charClass, Int::class.javaPrimitiveType)
                m.createHook {
                    after { param ->
                        val status = (param.args[2] as? Int) ?: return@after
                        val uuid = charUuid(param.args[1])
                        if (status == 0) {
                            logHook("BLE-Auth", "WRITE OK -> $uuid")
                        } else {
                            logHook("BLE-Auth", "WRITE FAIL status=$status -> $uuid")
                        }
                    }
                }
            } catch (_: Throwable) { }

            // onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic) [老]
            try {
                val m = cbClass.getMethod("onCharacteristicChanged", gattClass, charClass)
                m.createHook {
                    after { param ->
                        val char = param.args[1]
                        val value = charValue(char)
                        if (value != null) onPush(uuid = charUuid(char), hex = bytesToHex(value))
                    }
                }
            } catch (_: Throwable) { }

            // onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic, byte[]) [API 33+]
            try {
                val m = cbClass.getMethod("onCharacteristicChanged", gattClass, charClass, ByteArray::class.java)
                m.createHook {
                    after { param ->
                        val char = param.args[1]
                        val value = param.args[2] as? ByteArray
                        if (value != null) onPush(uuid = charUuid(char), hex = bytesToHex(value))
                    }
                }
            } catch (_: Throwable) { }

            // onConnectionStateChange(BluetoothGatt, int, int)
            try {
                val m = cbClass.getMethod("onConnectionStateChange", gattClass, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                m.createHook {
                    after { param ->
                        val status = (param.args[1] as? Int) ?: return@after
                        val newState = (param.args[2] as? Int) ?: return@after
                        val stateText = when (newState) {
                            0 -> "断开"
                            1 -> "连接中"
                            2 -> "已连接"
                            else -> "未知($newState)"
                        }
                        logHook("BLE-Auth", "连接状态: $stateText (status=$status)")
                        if (newState == 0) {
                            seenInit.set(false)
                        }
                    }
                }
            } catch (_: Throwable) { }

            logHook("BLE-Auth", "GATT 回调 Hook 已安装")
        }
    }

    // ============================================================
    //  核心：写入帧处理 + 协议识别
    // ============================================================
    private fun onWrite(uuid: String, hex: String) {
        if (hex.isEmpty()) return
        val type = BleFrameClassifier.classify(hex)

        // 检测新会话：出现 INIT 帧，或离上次 INIT 超过窗口
        if (type == BleFrameClassifier.Type.INIT) {
            val now = System.currentTimeMillis()
            if (!seenInit.get() || now - lastInitAt > sessionWindowMs) {
                BleAuthStore.newSession()
                seenInit.set(true)
            }
            lastInitAt = now
        }

        BleAuthStore.addFrame(type.label, uuid, hex)
        BleAuthStore.broadcast()

        when (type) {
            BleFrameClassifier.Type.INIT -> logHook("AUTH-INIT", "INIT 帧: $hex  uuid=$uuid")
            BleFrameClassifier.Type.KEY -> logHook("AUTH-KEY", "★ KEY 帧: $hex  uuid=$uuid  （14B 尾固定 4105011055）")
            BleFrameClassifier.Type.QUERY -> logHook("AUTH", "QUERY: $hex")
            BleFrameClassifier.Type.VERSION -> logHook("AUTH", "VERSION: $hex")
            BleFrameClassifier.Type.QUERY2 -> logHook("AUTH", "QUERY2: $hex")
            BleFrameClassifier.Type.POLL_0D -> logHook("AUTH", "POLL_0D 查询帧: ${hex.take(40)}...(${hex.length / 2}B)")
            BleFrameClassifier.Type.POLL_7A8F -> logHook("AUTH", "POLL_7A8F 状态轮询帧: ${hex.take(40)}...(${hex.length / 2}B)")
            BleFrameClassifier.Type.START -> logHook("AUTH", "START 解锁命令: ${hex.take(40)}...(${hex.length / 2}B)")
            BleFrameClassifier.Type.STOP -> logHook("AUTH", "STOP 设防命令: ${hex.take(40)}...(${hex.length / 2}B)")
            BleFrameClassifier.Type.UNKNOWN -> logHook("BLE-Write", "hex=${hex.take(60)}... uuid=$uuid")
        }

        if (type == BleFrameClassifier.Type.KEY) {
            logHook("AUTH", ">>> 请在 YadeaHook UI 复制 KEY 和 INIT（当前会话 Key）")
        }
    }

    private fun onPush(uuid: String, hex: String) {
        BleAuthStore.onPush(hex)
        BleAuthStore.broadcast()
        logHook("VEH-PUSH", "★ 车辆推送数据: ${hex.take(80)}...(${hex.length / 2}B)  uuid=$uuid")
        logHook("VEH-PUSH", ">>> 收到车辆推送 = 认证已通过！可解锁/锁车")
    }

    // ============================================================
    //  辅助（全部反射，兼容 EzXposed HookParam）
    // ============================================================
    private fun charUuid(char: Any?): String {
        if (char == null) return ""
        return try {
            (char.javaClass.getMethod("getUuid").invoke(char)?.toString() ?: "").lowercase()
        } catch (_: Throwable) { "" }
    }

    private fun charValue(char: Any?): ByteArray? {
        if (char == null) return null
        return try {
            char.javaClass.getMethod("getValue").invoke(char) as? ByteArray
        } catch (_: Throwable) { null }
    }

    private fun thisObjectUuid(thisObj: Any?): String {
        if (thisObj == null) return ""
        return try {
            (thisObj.javaClass.getMethod("getUuid").invoke(thisObj)?.toString() ?: "").lowercase()
        } catch (_: Throwable) { "" }
    }

    /** 反射获取 HookParam.thisObject，兼容不同 EzXposed 版本 */
    private fun getThisObject(param: Any): Any? {
        return try {
            val m = param.javaClass.getMethod("getThisObject")
            m.invoke(param)
        } catch (_: Throwable) {
            try {
                val f = param.javaClass.getField("thisObject")
                f.get(param)
            } catch (_: Throwable) { null }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
