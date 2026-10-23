package com.ghhccghk.yadeahook.hooks

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.BleAuthStore
import com.ghhccghk.yadeahook.protocol.BleFrameClassifier
import io.github.lingqiqi5211.ezhooktool.core.findAllMethods
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android BLE 只读抓包 Hook。
 *
 * 原有认证帧识别、写入分类和 BleAuthStore 行为保持不变；本类同时补齐 GATT 操作、
 * 业务 callback 覆写以及 framework 内部 onNotify 分发入口。所有回调都只读参数。
 */
class BleAuthCaptureHook : BaseHook() {
    private val seenInit = AtomicBoolean(false)
    private var lastInitAt = 0L
    private val sessionWindowMs = 30_000L
    private val characteristicChangedDepth = ThreadLocal.withInitial { 0 }

    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        BleAuthStore.init(context)

        val gattClass = loadClass(classLoader, "android.bluetooth.BluetoothGatt")
        val deviceClass = loadClass(classLoader, "android.bluetooth.BluetoothDevice")
        val callbackClass = loadClass(classLoader, "android.bluetooth.BluetoothGattCallback")

        hookConnectGatt(deviceClass, callbackClass, gattClass)
        hookGattOperations(gattClass)
        hookBluetoothGattCallbackBase(callbackClass, gattClass)
        hookInternalNotifyDispatch(gattClass)
    }

    private fun loadClass(classLoader: ClassLoader?, name: String): Class<*>? {
        if (classLoader != null) {
            try {
                return classLoader.loadClass(name)
            } catch (_: Throwable) {
                // 回退到 Class.forName，framework/boot class 可能来自 boot classloader。
            }
        }
        return try {
            Class.forName(name)
        } catch (_: Throwable) {
            null
        }
    }

    private fun methodKey(method: Method): String =
        "YADEA_CAP_${describeMethod(method).replace(Regex("[^A-Za-z0-9_.$]"), "_")}"

    private fun logMissing(hookClass: String, method: String, expected: String) {
        captureLog(
            hookClass = hookClass,
            event = "MISSING",
            method = method,
            fields = arrayOf("expected" to expected)
        )
    }

    private fun logInstalled(hookClass: String, method: Method, scope: String) {
        captureLog(
            hookClass = hookClass,
            event = "INSTALLED",
            method = describeMethod(method),
            fields = arrayOf("scope" to scope)
        )
    }

    private fun matchingMethods(
        owner: Class<*>?,
        methodName: String,
        paramCount: Int,
        predicate: (Method) -> Boolean = { true }
    ): List<Method> {
        if (owner == null) return emptyList()
        return try {
            owner.findAllMethods {
                name(methodName)
                paramCount(paramCount)
                findOnlyClass()
            }.filter(predicate)
        } catch (e: Throwable) {
            captureError(HOOK, "$methodName/$paramCount", e)
            emptyList()
        }
    }

    // ---------------------------------------------------------------------
    // BluetoothDevice.connectGatt 全部公开重载
    // ---------------------------------------------------------------------

    private fun hookConnectGatt(
        deviceClass: Class<*>?,
        callbackClass: Class<*>?,
        gattClass: Class<*>?
    ) {
        val hookName = "$HOOK#connectGatt"
        if (deviceClass == null || callbackClass == null) {
            logMissing(HOOK, hookName, "BluetoothDevice/BluetoothGattCallback")
            return
        }

        val methods = matchingMethods(deviceClass, "connectGatt", 3) { true } +
            matchingMethods(deviceClass, "connectGatt", 4) { true } +
            matchingMethods(deviceClass, "connectGatt", 5) { true } +
            matchingMethods(deviceClass, "connectGatt", 6) { true }

        val validMethods = methods.distinct().filter { method ->
            val params = method.parameterTypes
            params.size >= 3 &&
                params[0] == Context::class.java &&
                params[1] == Boolean::class.javaPrimitiveType &&
                callbackClass.isAssignableFrom(params[2])
        }

        if (validMethods.isEmpty()) {
            logMissing(HOOK, hookName, "Context,boolean,BluetoothGattCallback,...")
            return
        }

        for (method in validMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val device = param.thisObjectOrNull
                            val callback = param.args.getOrNull(2)
                            val autoConnect = param.args.getOrNull(1) as? Boolean
                            val transport = param.args.getOrNull(3) as? Int
                            val phy = param.args.getOrNull(4) as? Int
                            val opportunistic = param.args.getOrNull(5) as? Boolean
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|CONNECT_GATT_REQUEST",
                                method = describeMethod(method),
                                mac = extractMac(device),
                                runtimeClass = hookRuntimeClass(callback),
                                fields = arrayOf(
                                    "autoConnect" to autoConnect,
                                    "transport" to transport,
                                    "phy" to phy,
                                    "opportunistic" to opportunistic,
                                    "callbackType" to hookRuntimeClass(callback),
                                    "deviceType" to hookRuntimeClass(device)
                                )
                            )
                            if (callback != null) {
                                installCallbackOverrides(callback.javaClass, gattClass)
                            }
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val gatt = param.result
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|CONNECT_GATT_RETURN",
                                method = describeMethod(method),
                                mac = extractMac(gatt),
                                runtimeClass = hookRuntimeClass(gatt),
                                fields = arrayOf(
                                    "gattType" to hookRuntimeClass(gatt),
                                    "callbackType" to hookRuntimeClass(param.args.getOrNull(2))
                                )
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, "android-framework")
            }
        }
    }

    private fun installCallbackOverrides(callbackClass: Class<*>, gattClass: Class<*>?) {
        if (gattClass == null) {
            logMissing(HOOK, "callback-overrides", "BluetoothGatt")
            return
        }
        var current: Class<*>? = callbackClass
        var levels = 0
        while (
            current != null &&
            current != BluetoothGattCallback::class.java &&
            levels < 16
        ) {
            hookCallbackMethods(current, gattClass, onlyDeclaredOverrides = true)
            current = current.superclass
            levels++
        }
    }

    // ---------------------------------------------------------------------
    // BluetoothGatt 操作入口
    // ---------------------------------------------------------------------

    private fun hookGattOperations(gattClass: Class<*>?) {
        if (gattClass == null) {
            logMissing(HOOK, "gatt-operations", "android.bluetooth.BluetoothGatt")
            return
        }
        val charClass = loadClass(gattClass.classLoader, "android.bluetooth.BluetoothGattCharacteristic")
        val descriptorClass = loadClass(gattClass.classLoader, "android.bluetooth.BluetoothGattDescriptor")

        hookDiscoverServices(gattClass)
        hookSetCharacteristicNotification(gattClass, charClass)
        hookWriteDescriptor(gattClass, descriptorClass)
        hookReadDescriptor(gattClass, descriptorClass)
        hookReadCharacteristic(gattClass, charClass)
        hookWriteCharacteristic(gattClass, charClass)
        hookCharacteristicSetValue(charClass)
    }

    private fun hookDiscoverServices(gattClass: Class<*>) {
        val methods = matchingMethods(gattClass, "discoverServices", 0)
        if (methods.isEmpty()) logMissing(HOOK, "BluetoothGatt.discoverServices", "()Z")
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|DISCOVER_SERVICES",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull)
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|DISCOVER_SERVICES_RESULT",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf("result" to param.result)
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, "gatt-operation")
            }
        }
    }

    private fun hookSetCharacteristicNotification(gattClass: Class<*>, charClass: Class<*>?) {
        if (charClass == null) {
            logMissing(HOOK, "setCharacteristicNotification", "BluetoothGattCharacteristic")
            return
        }
        val methods = matchingMethods(gattClass, "setCharacteristicNotification", 2) {
            it.parameterTypes[0] == charClass &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        }
        if (methods.isEmpty()) {
            logMissing(HOOK, "setCharacteristicNotification", "(BluetoothGattCharacteristic,boolean)Z")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val characteristic = param.args.getOrNull(0)
                            val enabled = param.args.getOrNull(1) as? Boolean
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|SET_NOTIFICATION",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to serviceUuid(characteristic),
                                    "uuid" to objectUuid(characteristic),
                                    "descriptor" to CCCD_UUID,
                                    "data" to "N/A(setCharacteristicNotification)",
                                    "enable" to enabled,
                                    "properties" to characteristicProperties(characteristic)
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|SET_NOTIFICATION_RESULT",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "uuid" to objectUuid(param.args.getOrNull(0)),
                                    "enable" to param.args.getOrNull(1),
                                    "result" to param.result
                                )
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, "gatt-operation")
            }
        }
    }

    private fun hookWriteDescriptor(gattClass: Class<*>, descriptorClass: Class<*>?) {
        if (descriptorClass == null) {
            logMissing(HOOK, "writeDescriptor", "BluetoothGattDescriptor")
            return
        }
        val methods = (matchingMethods(gattClass, "writeDescriptor", 1) {
            it.parameterTypes[0] == descriptorClass
        } + matchingMethods(gattClass, "writeDescriptor", 2) {
            it.parameterTypes[0] == descriptorClass &&
                it.parameterTypes[1] == ByteArray::class.java
        }).distinct()

        if (methods.isEmpty()) {
            logMissing(HOOK, "writeDescriptor", "(BluetoothGattDescriptor[,byte[]])Z")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val descriptor = param.args.getOrNull(0)
                            val data = when (method.parameterTypes.size) {
                                2 -> (param.args.getOrNull(1) as? ByteArray)?.clone()
                                else -> descriptorValue(descriptor)
                            }
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|TX_DESCRIPTOR",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to descriptorServiceUuid(descriptor),
                                    "uuid" to objectUuid(descriptor),
                                    "characteristic" to descriptorCharacteristicUuid(descriptor),
                                    "isCccd" to isCccd(descriptor),
                                    "writeType" to descriptorWriteType(descriptor),
                                    "len" to (data?.size ?: 0),
                                    "data" to data
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|TX_DESCRIPTOR_RESULT",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "uuid" to objectUuid(param.args.getOrNull(0)),
                                    "result" to param.result
                                )
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, "gatt-operation")
            }
        }
    }

    private fun hookReadDescriptor(gattClass: Class<*>, descriptorClass: Class<*>?) {
        if (descriptorClass == null) {
            logMissing(HOOK, "readDescriptor", "BluetoothGattDescriptor")
            return
        }
        val methods = matchingMethods(gattClass, "readDescriptor", 1) {
            it.parameterTypes[0] == descriptorClass
        }
        if (methods.isEmpty()) logMissing(HOOK, "readDescriptor", "(BluetoothGattDescriptor)Z")
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|READ_DESCRIPTOR",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to descriptorServiceUuid(param.args.getOrNull(0)),
                                    "uuid" to objectUuid(param.args.getOrNull(0)),
                                    "characteristic" to descriptorCharacteristicUuid(param.args.getOrNull(0))
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|READ_DESCRIPTOR_RESULT",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf("result" to param.result)
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, "gatt-operation")
            }
        }
    }

    private fun hookReadCharacteristic(gattClass: Class<*>, charClass: Class<*>?) {
        if (charClass == null) {
            logMissing(HOOK, "readCharacteristic", "BluetoothGattCharacteristic")
            return
        }
        val methods = matchingMethods(gattClass, "readCharacteristic", 1) {
            it.parameterTypes[0] == charClass
        }
        if (methods.isEmpty()) logMissing(HOOK, "readCharacteristic", "(BluetoothGattCharacteristic)Z")
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val characteristic = param.args.getOrNull(0)
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|READ_CHARACTERISTIC",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to serviceUuid(characteristic),
                                    "uuid" to objectUuid(characteristic),
                                    "properties" to characteristicProperties(characteristic)
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|READ_CHARACTERISTIC_RESULT",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                fields = arrayOf("result" to param.result)
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, "gatt-operation")
            }
        }
    }

    private fun hookWriteCharacteristic(gattClass: Class<*>, charClass: Class<*>?) {
        if (charClass == null) {
            logMissing(HOOK, "writeCharacteristic", "BluetoothGattCharacteristic")
            return
        }
        val methods = (matchingMethods(gattClass, "writeCharacteristic", 1) {
            it.parameterTypes[0] == charClass
        } + matchingMethods(gattClass, "writeCharacteristic", 3) {
            it.parameterTypes[0] == charClass &&
                it.parameterTypes[1] == ByteArray::class.java &&
                it.parameterTypes[2] == Int::class.javaPrimitiveType
        }).distinct()

        if (methods.isEmpty()) {
            logMissing(HOOK, "writeCharacteristic", "(BluetoothGattCharacteristic[,byte[],int])Z")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val characteristic = param.args.getOrNull(0)
                            val data = when (method.parameterTypes.size) {
                                3 -> (param.args.getOrNull(1) as? ByteArray)?.clone()
                                else -> characteristicValue(characteristic)
                            }
                            val writeType = if (method.parameterTypes.size == 3) {
                                param.args.getOrNull(2) as? Int
                            } else {
                                characteristicWriteType(characteristic)
                            }
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|TX",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to serviceUuid(characteristic),
                                    "uuid" to objectUuid(characteristic),
                                    "props" to characteristicProperties(characteristic),
                                    "writeType" to writeType,
                                    "len" to (data?.size ?: 0),
                                    "data" to data
                                )
                            )
                            if (data != null) {
                                onWrite(objectUuid(characteristic) ?: "unknown", data.toHexUpper())
                            }
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|TX_RESULT",
                                method = describeMethod(method),
                                mac = extractMac(param.thisObjectOrNull),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "uuid" to objectUuid(param.args.getOrNull(0)),
                                    "result" to param.result
                                )
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, "gatt-operation")
            }
        }
    }

    private fun hookCharacteristicSetValue(charClass: Class<*>?) {
        if (charClass == null) {
            logMissing(HOOK, "BluetoothGattCharacteristic.setValue", "BluetoothGattCharacteristic")
            return
        }
        val methods = matchingMethods(charClass, "setValue", 1) {
            it.parameterTypes[0] == ByteArray::class.java
        }
        if (methods.isEmpty()) {
            logMissing(HOOK, "BluetoothGattCharacteristic.setValue", "(byte[])V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val data = (param.args.getOrNull(0) as? ByteArray)?.clone()
                            val characteristic = param.thisObjectOrNull
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|SET_VALUE_FALLBACK",
                                method = describeMethod(method),
                                mac = null,
                                runtimeClass = hookRuntimeClass(characteristic),
                                fields = arrayOf(
                                    "service" to serviceUuid(characteristic),
                                    "uuid" to objectUuid(characteristic),
                                    "len" to (data?.size ?: 0),
                                    "data" to data
                                )
                            )
                            if (data != null) {
                                onWrite(objectUuid(characteristic) ?: "unknown", data.toHexUpper())
                            }
                        }
                    }
                }
                logInstalled(HOOK, method, "fallback")
            }
        }
    }

    // ---------------------------------------------------------------------
    // BluetoothGattCallback 抽象基类 + 实际 App callback 覆写
    // ---------------------------------------------------------------------

    private fun hookBluetoothGattCallbackBase(callbackClass: Class<*>?, gattClass: Class<*>?) {
        if (callbackClass == null || gattClass == null) {
            logMissing(HOOK, "BluetoothGattCallback", "BluetoothGattCallback/BluetoothGatt")
            return
        }
        hookCallbackMethods(callbackClass, gattClass, onlyDeclaredOverrides = false)
    }

    private fun hookCallbackMethods(
        callbackClass: Class<*>,
        gattClass: Class<*>,
        onlyDeclaredOverrides: Boolean
    ) {
        hookConnectionStateChange(callbackClass, gattClass, onlyDeclaredOverrides)
        hookServicesDiscovered(callbackClass, gattClass, onlyDeclaredOverrides)
        hookCharacteristicWrite(callbackClass, gattClass, onlyDeclaredOverrides)
        hookDescriptorWrite(callbackClass, gattClass, onlyDeclaredOverrides)
        hookCharacteristicRead(callbackClass, gattClass, onlyDeclaredOverrides)
        hookDescriptorRead(callbackClass, gattClass, onlyDeclaredOverrides)
        hookCharacteristicChanged(callbackClass, gattClass, onlyDeclaredOverrides)
    }

    private fun callbackMethods(
        owner: Class<*>,
        methodName: String,
        paramCount: Int,
        onlyDeclaredOverrides: Boolean,
        predicate: (Method) -> Boolean = { true }
    ): List<Method> {
        val methods = matchingMethods(owner, methodName, paramCount, predicate)
        return methods.filter { method ->
            !Modifier.isAbstract(method.modifiers) &&
                (!onlyDeclaredOverrides || method.declaringClass == owner)
        }
    }

    private fun isDeclaredOverride(param: HookParam, method: Method): Boolean {
        val runtimeClass = param.thisObjectOrNull?.javaClass ?: return false
        if (runtimeClass == method.declaringClass) return false
        return try {
            var current: Class<*>? = runtimeClass
            while (current != null && current != method.declaringClass) {
                if (current.declaredMethods.any {
                        it.name == method.name &&
                            it.parameterTypes.contentEquals(method.parameterTypes)
                    }
                ) {
                    return true
                }
                current = current.superclass
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun hookConnectionStateChange(
        owner: Class<*>,
        gattClass: Class<*>,
        onlyDeclaredOverrides: Boolean
    ) {
        val methods = callbackMethods(owner, "onConnectionStateChange", 3, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass &&
                it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                it.parameterTypes[2] == Int::class.javaPrimitiveType
        }
        if (methods.isEmpty() && !onlyDeclaredOverrides) {
            logMissing(HOOK, "onConnectionStateChange", "(BluetoothGatt,int,int)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        if (isDeclaredOverride(param, method)) return@before
                        captureGuard(HOOK, describeMethod(method)) {
                            val gatt = param.args.getOrNull(0)
                            val status = param.args.getOrNull(1)
                            val newState = param.args.getOrNull(2)
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|CONNECTION_STATE_CHANGE",
                                method = describeMethod(method),
                                mac = extractMac(gatt),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "status" to status,
                                    "newState" to newState,
                                    "gattType" to hookRuntimeClass(gatt)
                                )
                            )
                            if (newState == 0) {
                                seenInit.set(false)
                            }
                        }
                    }
                }
                logInstalled(HOOK, method, if (onlyDeclaredOverrides) "app-callback-override" else "framework-base")
            }
        }
    }

    private fun hookServicesDiscovered(
        owner: Class<*>,
        gattClass: Class<*>,
        onlyDeclaredOverrides: Boolean
    ) {
        val methods = callbackMethods(owner, "onServicesDiscovered", 2, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass && it.parameterTypes[1] == Int::class.javaPrimitiveType
        }
        if (methods.isEmpty() && !onlyDeclaredOverrides) {
            logMissing(HOOK, "onServicesDiscovered", "(BluetoothGatt,int)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        if (isDeclaredOverride(param, method)) return@before
                        captureGuard(HOOK, describeMethod(method)) {
                            val gatt = param.args.getOrNull(0)
                            val status = param.args.getOrNull(1) as? Int
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|SERVICES_DISCOVERED",
                                method = describeMethod(method),
                                mac = extractMac(gatt),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "status" to status,
                                    "callbackType" to hookRuntimeClass(param.thisObjectOrNull)
                                )
                            )
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                enumerateGattDatabase(gatt)
                            }
                        }
                    }
                }
                logInstalled(HOOK, method, if (onlyDeclaredOverrides) "app-callback-override" else "framework-base")
            }
        }
    }

    private fun hookCharacteristicWrite(
        owner: Class<*>,
        gattClass: Class<*>,
        onlyDeclaredOverrides: Boolean
    ) {
        val methods = callbackMethods(owner, "onCharacteristicWrite", 3, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass &&
                it.parameterTypes[2] == Int::class.javaPrimitiveType
        }
        if (methods.isEmpty() && !onlyDeclaredOverrides) {
            logMissing(HOOK, "onCharacteristicWrite", "(BluetoothGatt,BluetoothGattCharacteristic,int)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    after { param ->
                        if (isDeclaredOverride(param, method)) return@after
                        captureGuard(HOOK, describeMethod(method)) {
                            val gatt = param.args.getOrNull(0)
                            val characteristic = param.args.getOrNull(1)
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|WRITE_STATUS",
                                method = describeMethod(method),
                                mac = extractMac(gatt),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "uuid" to objectUuid(characteristic),
                                    "status" to param.args.getOrNull(2),
                                    "data" to characteristicValue(characteristic)
                                )
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, if (onlyDeclaredOverrides) "app-callback-override" else "framework-base")
            }
        }
    }

    private fun hookDescriptorWrite(
        owner: Class<*>,
        gattClass: Class<*>,
        onlyDeclaredOverrides: Boolean
    ) {
        val methods = callbackMethods(owner, "onDescriptorWrite", 3, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass &&
                it.parameterTypes[2] == Int::class.javaPrimitiveType
        }
        if (methods.isEmpty() && !onlyDeclaredOverrides) {
            logMissing(HOOK, "onDescriptorWrite", "(BluetoothGatt,BluetoothGattDescriptor,int)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    after { param ->
                        if (isDeclaredOverride(param, method)) return@after
                        captureGuard(HOOK, describeMethod(method)) {
                            val gatt = param.args.getOrNull(0)
                            val descriptor = param.args.getOrNull(1)
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|DESCRIPTOR_WRITE_STATUS",
                                method = describeMethod(method),
                                mac = extractMac(gatt),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to descriptorServiceUuid(descriptor),
                                    "uuid" to objectUuid(descriptor),
                                    "characteristic" to descriptorCharacteristicUuid(descriptor),
                                    "isCccd" to isCccd(descriptor),
                                    "status" to param.args.getOrNull(2),
                                    "data" to descriptorValue(descriptor)
                                )
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, if (onlyDeclaredOverrides) "app-callback-override" else "framework-base")
            }
        }
    }

    private fun hookCharacteristicRead(
        owner: Class<*>,
        gattClass: Class<*>,
        onlyDeclaredOverrides: Boolean
    ) {
        val oldMethods = callbackMethods(owner, "onCharacteristicRead", 3, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass &&
                it.parameterTypes[2] == Int::class.javaPrimitiveType
        }
        val newMethods = callbackMethods(owner, "onCharacteristicRead", 4, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass &&
                it.parameterTypes[2] == ByteArray::class.java &&
                it.parameterTypes[3] == Int::class.javaPrimitiveType
        }
        if (oldMethods.isEmpty() && newMethods.isEmpty() && !onlyDeclaredOverrides) {
            logMissing(HOOK, "onCharacteristicRead", "old/new overloads")
        }
        for (method in (oldMethods + newMethods).distinct()) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    after { param ->
                        if (isDeclaredOverride(param, method)) return@after
                        captureGuard(HOOK, describeMethod(method)) {
                            val gatt = param.args.getOrNull(0)
                            val characteristic = param.args.getOrNull(1)
                            val value = if (method.parameterTypes.size == 4) {
                                (param.args.getOrNull(2) as? ByteArray)?.clone()
                            } else {
                                characteristicValue(characteristic)
                            }
                            val status = param.args.lastOrNull()
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|READ_STATUS",
                                method = describeMethod(method),
                                mac = extractMac(gatt),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "uuid" to objectUuid(characteristic),
                                    "status" to status,
                                    "len" to (value?.size ?: 0),
                                    "data" to value
                                )
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, if (onlyDeclaredOverrides) "app-callback-override" else "framework-base")
            }
        }
    }

    private fun hookDescriptorRead(
        owner: Class<*>,
        gattClass: Class<*>,
        onlyDeclaredOverrides: Boolean
    ) {
        val oldMethods = callbackMethods(owner, "onDescriptorRead", 3, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass &&
                it.parameterTypes[2] == Int::class.javaPrimitiveType
        }
        val newMethods = callbackMethods(owner, "onDescriptorRead", 4, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass &&
                it.parameterTypes[2] == ByteArray::class.java &&
                it.parameterTypes[3] == Int::class.javaPrimitiveType
        }
        if (oldMethods.isEmpty() && newMethods.isEmpty() && !onlyDeclaredOverrides) {
            logMissing(HOOK, "onDescriptorRead", "old/new overloads")
        }
        for (method in (oldMethods + newMethods).distinct()) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    after { param ->
                        if (isDeclaredOverride(param, method)) return@after
                        captureGuard(HOOK, describeMethod(method)) {
                            val gatt = param.args.getOrNull(0)
                            val descriptor = param.args.getOrNull(1)
                            val value = if (method.parameterTypes.size == 4) {
                                (param.args.getOrNull(2) as? ByteArray)?.clone()
                            } else {
                                descriptorValue(descriptor)
                            }
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|DESCRIPTOR_READ_STATUS",
                                method = describeMethod(method),
                                mac = extractMac(gatt),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "uuid" to objectUuid(descriptor),
                                    "characteristic" to descriptorCharacteristicUuid(descriptor),
                                    "status" to param.args.lastOrNull(),
                                    "data" to value
                                )
                            )
                        }
                    }
                }
                logInstalled(HOOK, method, if (onlyDeclaredOverrides) "app-callback-override" else "framework-base")
            }
        }
    }

    private fun hookCharacteristicChanged(
        owner: Class<*>,
        gattClass: Class<*>,
        onlyDeclaredOverrides: Boolean
    ) {
        val oldMethods = callbackMethods(owner, "onCharacteristicChanged", 2, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass
        }
        val newMethods = callbackMethods(owner, "onCharacteristicChanged", 3, onlyDeclaredOverrides) {
            it.parameterTypes[0] == gattClass &&
                it.parameterTypes[2] == ByteArray::class.java
        }
        if (oldMethods.isEmpty() && newMethods.isEmpty() && !onlyDeclaredOverrides) {
            logMissing(HOOK, "onCharacteristicChanged", "old/new overloads")
        }

        for (method in oldMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        val depth = characteristicChangedDepth.get() ?: 0
                        if (depth > 0 || isDeclaredOverride(param, method)) {
                            return@before
                        }
                        captureGuard(HOOK, describeMethod(method)) {
                            val characteristic = param.args.getOrNull(1)
                            onPush(param, characteristicValue(characteristic), characteristic, method)
                        }
                    }
                }
                logInstalled(HOOK, method, if (onlyDeclaredOverrides) "app-callback-override" else "framework-base-old")
            }
        }

        for (method in newMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        if (isDeclaredOverride(param, method)) return@before
                        val depth = characteristicChangedDepth.get() ?: 0
                        characteristicChangedDepth.set(depth + 1)
                        captureGuard(HOOK, describeMethod(method)) {
                            val characteristic = param.args.getOrNull(1)
                            val data = (param.args.getOrNull(2) as? ByteArray)?.clone()
                            onPush(param, data, characteristic, method)
                        }
                    }
                    after {
                        val depth = characteristicChangedDepth.get() ?: 0
                        characteristicChangedDepth.set((depth - 1).coerceAtLeast(0))
                    }
                }
                logInstalled(HOOK, method, if (onlyDeclaredOverrides) "app-callback-override" else "framework-base-new")
            }
        }
    }

    private fun onPush(
        param: HookParam,
        data: ByteArray?,
        characteristic: Any?,
        method: Method
    ) {
        val gatt = param.args.getOrNull(0)
        val hex = data?.toHexUpper().orEmpty()
        captureLog(
            hookClass = HOOK,
            event = "BLE|RX_NOTIFY",
            method = describeMethod(method),
            mac = extractMac(gatt),
            runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
            fields = arrayOf(
                "service" to serviceUuid(characteristic),
                "uuid" to objectUuid(characteristic),
                "props" to characteristicProperties(characteristic),
                "len" to (data?.size ?: 0),
                "data" to hex
            )
        )
        if (hex.isNotEmpty()) onPush(uuid = objectUuid(characteristic) ?: "unknown", hex = hex)
    }

    // ---------------------------------------------------------------------
    // framework 内部通知分发入口：Android 14+ 的 BluetoothGatt$1.onNotify
    // ---------------------------------------------------------------------

    private fun hookInternalNotifyDispatch(gattClass: Class<*>?) {
        if (gattClass == null) {
            logMissing(HOOK, "internal-notify-dispatch", "BluetoothGatt")
            return
        }

        val candidates = linkedSetOf<Class<*>>()
        loadClass(gattClass.classLoader, "android.bluetooth.BluetoothGatt\$1")?.let(candidates::add)
        try {
            gattClass.declaredClasses
                .filter { it.name.startsWith("${gattClass.name}\$") }
                .forEach(candidates::add)
        } catch (e: Throwable) {
            captureError(HOOK, "BluetoothGatt.declaredClasses", e)
        }

        var installed = 0
        for (candidate in candidates) {
            val methods = matchingMethods(candidate, "onNotify", 3) {
                it.parameterTypes[0] == String::class.java &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[2] == ByteArray::class.java
            }
            for (method in methods) {
                captureGuard(HOOK, describeMethod(method)) {
                    method.createHook {
                        reloadKey(methodKey(method))
                        before { param ->
                            captureGuard(HOOK, describeMethod(method)) {
                                val address = param.args.getOrNull(0) as? String
                                val handle = param.args.getOrNull(1) as? Int
                                val data = (param.args.getOrNull(2) as? ByteArray)?.clone()
                                captureLog(
                                    hookClass = HOOK,
                                    event = "BLE|INTERNAL_NOTIFY",
                                    method = describeMethod(method),
                                    mac = address,
                                    runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                    fields = arrayOf(
                                        "handle" to handle,
                                        "len" to (data?.size ?: 0),
                                        "data" to data
                                    )
                                )
                            }
                        }
                    }
                    installed++
                    logInstalled(HOOK, method, "rom-internal-dispatch")
                }
            }
        }

        if (installed == 0) {
            logMissing(
                HOOK,
                "internal-notify-dispatch",
                "BluetoothGatt\\$1.onNotify(String,int,byte[]) or declaredClasses fallback"
            )
        }
    }

    // ---------------------------------------------------------------------
    // GATT 数据库枚举
    // ---------------------------------------------------------------------

    private fun enumerateGattDatabase(gatt: Any?) {
        if (gatt !is BluetoothGatt) return
        try {
            val services = gatt.services.orEmpty()
            captureLog(
                hookClass = HOOK,
                event = "BLE|ATT_DB",
                method = "onServicesDiscovered",
                mac = extractMac(gatt),
                runtimeClass = hookRuntimeClass(gatt),
                fields = arrayOf(
                    "services" to services.size,
                    "deviceType" to hookRuntimeClass(gatt.device)
                )
            )
            for (service in services) {
                val characteristics = service.characteristics.orEmpty()
                captureLog(
                    hookClass = HOOK,
                    event = "BLE|SERVICE",
                    method = "onServicesDiscovered",
                    mac = extractMac(gatt),
                    runtimeClass = hookRuntimeClass(service),
                    fields = arrayOf(
                        "uuid" to service.uuid?.toString(),
                        "type" to service.type,
                        "characteristics" to characteristics.size,
                        "includedServices" to service.includedServices.orEmpty().size
                    )
                )
                for (characteristic in characteristics) {
                    val descriptors = characteristic.descriptors.orEmpty()
                    captureLog(
                        hookClass = HOOK,
                        event = "BLE|CHARACTERISTIC",
                        method = "onServicesDiscovered",
                        mac = extractMac(gatt),
                        runtimeClass = hookRuntimeClass(characteristic),
                        fields = arrayOf(
                            "service" to service.uuid?.toString(),
                            "uuid" to characteristic.uuid?.toString(),
                            "properties" to characteristicProperties(characteristic),
                            "writeType" to characteristicWriteType(characteristic),
                            "descriptors" to descriptors.size
                        )
                    )
                    for (descriptor in descriptors) {
                        captureLog(
                            hookClass = HOOK,
                            event = "BLE|DESCRIPTOR",
                            method = "onServicesDiscovered",
                            mac = extractMac(gatt),
                            runtimeClass = hookRuntimeClass(descriptor),
                            fields = arrayOf(
                                "service" to service.uuid?.toString(),
                                "characteristic" to characteristic.uuid?.toString(),
                                "uuid" to descriptor.uuid?.toString(),
                                "isCccd" to isCccd(descriptor),
                                "value" to descriptorValue(descriptor)
                            )
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            captureError(HOOK, "enumerateGattDatabase", e)
        }
    }

    // ---------------------------------------------------------------------
    // 原有认证帧识别
    // ---------------------------------------------------------------------

    @Synchronized
    private fun onWrite(uuid: String, hex: String) {
        if (hex.isEmpty()) return
        val type = BleFrameClassifier.classify(hex)

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
            BleFrameClassifier.Type.INIT -> logHook("AUTH-INIT", "INIT 帧: $hex uuid=$uuid")
            BleFrameClassifier.Type.KEY -> logHook("AUTH-KEY", "KEY 帧: $hex uuid=$uuid")
            BleFrameClassifier.Type.QUERY -> logHook("AUTH", "QUERY: $hex")
            BleFrameClassifier.Type.VERSION -> logHook("AUTH", "VERSION: $hex")
            BleFrameClassifier.Type.QUERY2 -> logHook("AUTH", "QUERY2: $hex")
            BleFrameClassifier.Type.POLL_0D -> logHook("AUTH", "POLL_0D: $hex")
            BleFrameClassifier.Type.POLL_7A8F -> logHook("AUTH", "POLL_7A8F: $hex")
            BleFrameClassifier.Type.START -> logHook("AUTH", "START: $hex")
            BleFrameClassifier.Type.STOP -> logHook("AUTH", "STOP: $hex")
            BleFrameClassifier.Type.UNKNOWN -> logHook("BLE-Write", "hex=$hex uuid=$uuid")
        }
    }

    private fun onPush(uuid: String, hex: String) {
        BleAuthStore.onPush(hex)
        BleAuthStore.broadcast()
        logHook("VEH-PUSH", "车辆推送 uuid=$uuid len=${hex.length / 2} data=$hex")
    }

    // ---------------------------------------------------------------------
    // 反射格式化
    // ---------------------------------------------------------------------

    private fun objectUuid(value: Any?): String? {
        if (value == null) return null
        return try {
            value.javaClass.getMethod("getUuid").invoke(value)?.toString()
        } catch (_: Throwable) {
            null
        }
    }

    private fun serviceUuid(characteristic: Any?): String? {
        if (characteristic == null) return null
        return try {
            val service = characteristic.javaClass.getMethod("getService").invoke(characteristic)
            objectUuid(service)
        } catch (_: Throwable) {
            null
        }
    }

    private fun descriptorServiceUuid(descriptor: Any?): String? {
        if (descriptor == null) return null
        return try {
            val characteristic = descriptor.javaClass.getMethod("getCharacteristic").invoke(descriptor)
            serviceUuid(characteristic)
        } catch (_: Throwable) {
            null
        }
    }

    private fun descriptorCharacteristicUuid(descriptor: Any?): String? {
        if (descriptor == null) return null
        return try {
            val characteristic = descriptor.javaClass.getMethod("getCharacteristic").invoke(descriptor)
            objectUuid(characteristic)
        } catch (_: Throwable) {
            null
        }
    }

    private fun characteristicValue(characteristic: Any?): ByteArray? {
        if (characteristic == null) return null
        return try {
            (characteristic.javaClass.getMethod("getValue").invoke(characteristic) as? ByteArray)?.clone()
        } catch (_: Throwable) {
            null
        }
    }

    private fun descriptorValue(descriptor: Any?): ByteArray? {
        if (descriptor == null) return null
        return try {
            (descriptor.javaClass.getMethod("getValue").invoke(descriptor) as? ByteArray)?.clone()
        } catch (_: Throwable) {
            null
        }
    }

    private fun characteristicWriteType(characteristic: Any?): Int? {
        if (characteristic == null) return null
        return try {
            characteristic.javaClass.getMethod("getWriteType").invoke(characteristic) as? Int
        } catch (_: Throwable) {
            null
        }
    }

    private fun descriptorWriteType(descriptor: Any?): String? {
        if (descriptor == null) return null
        return "N/A(descriptor-api)"
    }

    private fun characteristicProperties(characteristic: Any?): String? {
        if (characteristic == null) return null
        val value = try {
            characteristic.javaClass.getMethod("getProperties").invoke(characteristic) as? Int
        } catch (_: Throwable) {
            null
        } ?: return null
        val names = buildList {
            if (value and 0x01 != 0) add("BROADCAST")
            if (value and 0x02 != 0) add("READ")
            if (value and 0x04 != 0) add("WRITE_NO_RESPONSE")
            if (value and 0x08 != 0) add("WRITE")
            if (value and 0x10 != 0) add("NOTIFY")
            if (value and 0x20 != 0) add("INDICATE")
            if (value and 0x40 != 0) add("SIGNED_WRITE")
            if (value and 0x80 != 0) add("EXTENDED_PROPS")
        }
        return "0x${value.toString(16).uppercase()}(${names.joinToString(",")})"
    }

    private fun isCccd(descriptor: Any?): Boolean =
        objectUuid(descriptor)?.equals(CCCD_UUID, ignoreCase = true) == true

    companion object {
        private const val HOOK = "BleAuthCaptureHook"
        private const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }
}
