package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleServiceLoad
import io.github.lingqiqi5211.ezhooktool.core.findAllMethods
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.loadClassOrNull
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

/**
 * 凭证提取 Hook：BLE Key + Device Token
 */
class CredentialExtractHook : BaseHook() {

    private var capturedBleKey: String? = null
    private var capturedDeviceToken: String? = null
    private val capturedTokens = mutableSetOf<String>()

    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        hookSharedPreferences(classLoader)
        hookBleWrite(classLoader)
        hookOkHttp(classLoader)
        hookVehicleServiceSend(classLoader)
        scanForCredentialClasses(classLoader)
    }

    private fun hookSharedPreferences(classLoader: ClassLoader) {
        safeHook("SharedPreferences 读取") {
            val spImplClass = loadClassOrNull("android.app.SharedPreferencesImpl", classLoader)
            if (spImplClass == null) {
                logHook("Cred", "SharedPreferencesImpl 未找到，跳过SP Hook")
                return@safeHook
            }

            val getStringMethod = spImplClass.findMethod { name("getString"); paramCount(2) }
            getStringMethod.createHook {
                before { param ->
                    val key = param.args[0] as? String ?: return@before
                    val credentialKeys = listOf(
                        "token", "access_token", "refresh_token",
                        "device_token", "deviceId", "device_id",
                        "ble_key", "bleKey", "ble_key_pair", "blekey",
                        "auth_token", "session_token", "sessionId",
                        "yadea_token", "ydsm_token", "yd_token",
                        "authkey", "secret", "sign_key", "akey"
                    )
                    if (credentialKeys.any { key.contains(it, ignoreCase = true) }) {
                        val result = param.getResult() as? String ?: return@before
                        logHook("Cred-SP", "key=$key value=$result")
                        identifyCredential(key, result)
                    }
                }
            }

            try {
                val getLongMethod = spImplClass.findMethod { name("getLong"); paramCount(2) }
                getLongMethod.createHook {
                    before { param ->
                        val key = param.args[0] as? String ?: return@before
                        if (key.contains("token", ignoreCase = true) || key.contains("device", ignoreCase = true)) {
                            val result = param.getResult() as? Long
                            if (result != null && result > 0) {
                                logHook("Cred-SP", "key=$key long=$result")
                            }
                        }
                    }
                }
            } catch (_: Throwable) { }

            logHook("Cred", "SP Hook 已安装")
        }
    }

    private fun hookBleWrite(classLoader: ClassLoader) {
        safeHook("BLE写入操作") {
            val bleCandidates = listOf(
                "com.yadea.smartmoto.ble.BluetoothLeService",
                "com.yadea.smartmoto.ble.BleService",
                "com.yadea.smartmoto.ble.BleManager",
                "com.yadea.smartmoto.bluetooth.BluetoothLeService",
                "com.yadea.smartmoto.bluetooth.BleService",
                "com.yadea.smartmoto.bluetooth.BleManager",
                "com.yadea.smartmoto.service.BleService",
            )

            var bleClass: Class<*>? = null
            for (name in bleCandidates) {
                loadClassOrNull(name, classLoader)?.let {
                    bleClass = it
                    logHook("Cred", "找到BLE服务类: $name")
                    return@let
                }
            }

            try {
                val gattCharClass = classLoader.loadClass("android.bluetooth.BluetoothGattCharacteristic")
                val setValueMethod = gattCharClass.getDeclaredMethod("setValue", ByteArray::class.java)
                setValueMethod.createHook {
                    before { param ->
                        val bytes = param.args[0] as? ByteArray ?: return@before
                        val hex = bytesToHex(bytes)
                        logHook("BLE-GattWrite", "hex=$hex")
                        parseBleKeyFromHex(hex)
                    }
                }
                logHook("Cred", "BluetoothGattCharacteristic Hook 已安装")
            } catch (e: Throwable) {
                logHook("Cred", "GattCharacteristic Hook 失败: ${e.message}")
            }

            if (bleClass != null) {
                val writeMethods = bleClass!!.findAllMethods {
                    notStatic(); voidReturnType()
                    paramTypes { anyMatch { it == ByteArray::class.java } }
                }
                for (method in writeMethods) {
                    if (method.name == "<init>") continue
                    method.createHook {
                        before { param ->
                            val hexArgs = param.args.filterIsInstance<ByteArray>()
                                .joinToString(" | ") { "byte[${it.size}](${bytesToHex(it).take(80)})" }
                            logHook("BLE-Write", "[${method.name}] $hexArgs")
                            param.args.filterIsInstance<ByteArray>().forEach { parseBleKeyFromHex(bytesToHex(it)) }
                        }
                    }
                }
                logHook("Cred", "BLE服务类方法 Hook 已安装 (${writeMethods.size}个)")
            }
        }
    }

    private fun hookOkHttp(classLoader: ClassLoader) {
        safeHook("OkHttp拦截层") {
            hookAllInterceptorImpl(classLoader)
            hookHttpURLConnection(classLoader)
        }
    }

    private fun hookAllInterceptorImpl(classLoader: ClassLoader) {
        val interceptorClassNames = listOf(
            "com.yadea.smartmoto.common.interceptor.AuthInterceptor",
            "com.yadea.smartmoto.common.interceptor.TokenInterceptor",
            "com.yadea.smartmoto.common.interceptor.HttpInterceptor",
            "com.yadea.smartmoto.network.interceptor.AuthInterceptor",
            "com.yadea.smartmoto.network.interceptor.TokenInterceptor",
            "com.yadea.smartmoto.http.interceptor.AuthInterceptor",
            "com.yadea.smartmoto.http.interceptor.TokenInterceptor",
        )

        for (className in interceptorClassNames) {
            try {
                val cls = loadClassOrNull(className, classLoader) ?: continue
                logHook("Cred", "找到Interceptor: $className")
                val chainClass = classLoader.loadClass("okhttp3.Interceptor\$Chain")
                val method = cls.getDeclaredMethod("intercept", chainClass)

                method.createHook {
                    before { param ->
                        val chain = param.args[0]
                        try {
                            val request = chain.javaClass.getMethod("request").invoke(chain)
                            val url = request.javaClass.getMethod("url").invoke(request) as? java.net.URL
                            val urlStr = url?.toString() ?: ""
                            val headers = request.javaClass.getMethod("headers").invoke(request)
                            val names = headers.javaClass.getMethod("names").invoke(headers) as? Collection<*>

                            names?.forEach { name ->
                                val nameStr = name?.toString() ?: return@forEach
                                if (nameStr.contains("token", ignoreCase = true) ||
                                    nameStr.contains("auth", ignoreCase = true) ||
                                    nameStr == "Authorization" ||
                                    nameStr == "X-Device-Token" ||
                                    nameStr == "device-token") {
                                    val value = headers.javaClass.getMethod("get", String::class.java)
                                        .invoke(headers, nameStr) as? String
                                    if (!value.isNullOrBlank()) {
                                        logHook("HTTP-Req", "[$className] $nameStr: $value")
                                        if (capturedDeviceToken == null && value.length > 10) {
                                            capturedDeviceToken = value
                                            logHook("Cred!", "Device Token 捕获: $value")
                                        }
                                    }
                                }
                            }

                            val suspicious = listOf("token", "login", "auth", "bind", "device", "vehicle", "scooter", "yadea", "ydsm")
                            if (suspicious.any { urlStr.contains(it, ignoreCase = true) }) {
                                logHook("HTTP-API", "URL: $urlStr")
                            }
                        } catch (_: Throwable) { }
                    }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun hookHttpURLConnection(classLoader: ClassLoader) {
        try {
            val addRequestProperty = classLoader.loadClass("com.android.okhttp.internal.http.HttpURLConnectionImpl")
                .getMethod("addRequestProperty", String::class.java, String::class.java)
            addRequestProperty.createHook {
                before { param ->
                    val key = param.args[0] as? String ?: return@before
                    val value = param.args[1] as? String ?: return@before
                    if (key.contains("token", ignoreCase = true) ||
                        key.contains("auth", ignoreCase = true) ||
                        key == "Authorization" || key == "device-token") {
                        logHook("HTTP-ReqProp", "$key: $value")
                        if (capturedDeviceToken == null && value.length > 10) {
                            capturedDeviceToken = value
                            logHook("Cred!", "Device Token (URLConn): $value")
                        }
                    }
                }
            }
            logHook("Cred", "HttpURLConnection Hook 已安装")
        } catch (_: Throwable) { }
    }

    private fun hookVehicleServiceSend(classLoader: ClassLoader) {
        safeHook("VehicleService BLE发送") {
            val vehicleServiceClass = VehicleServiceLoad.findVehicleService(classLoader)
                ?: run {
                    logHook("Cred", "VehicleService 未找到")
                    return@safeHook
                }

            logHook("Cred", "VehicleService: ${vehicleServiceClass.name}")

            val methods = vehicleServiceClass.findAllMethods {
                notStatic(); voidReturnType(); notAbstract()
            }
            logHook("Cred", "VehicleService 共 ${methods.size} 个方法，开始Hook...")

            for (method in methods) {
                if (method.name == "<init>") continue
                method.createHook {
                    before { param ->
                        val argDescs = param.args.mapIndexed { i, arg ->
                            when {
                                arg == null -> "arg[$i]=null"
                                arg is ByteArray -> "arg[$i]=byte[${arg.size}](${bytesToHex(arg).take(80)})"
                                arg is String -> if (arg.length < 80) "arg[$i]=\"$arg\"" else "arg[$i]=\"${arg.take(40)}...\""
                                arg is Int -> "arg[$i]=int:$arg"
                                arg is Long -> "arg[$i]=long:$arg"
                                arg is Boolean -> "arg[$i]=bool:$arg"
                                else -> "arg[$i]=Obj:${arg.javaClass.simpleName}"
                            }
                        }.joinToString(", ")

                        logHook("VehSend", "[${method.name}] $argDescs")

                        param.args.filterIsInstance<String>().forEach { str ->
                            if (str.length in 16..512 && !str.contains(" ") && !str.contains("http")) {
                                identifyCredential("VehSend-str", str)
                            }
                        }
                        param.args.filterIsInstance<ByteArray>().forEach { parseBleKeyFromHex(bytesToHex(it)) }
                    }
                    after { param ->
                        val result = param.getResult()
                        if (result != null && result !is Unit) {
                            val retStr = when (result) {
                                is ByteArray -> "byte[${result.size}](${bytesToHex(result).take(80)})"
                                is String -> if (result.length < 200) result else result.take(100)
                                else -> "Obj:${result.javaClass.simpleName}"
                            }
                            logHook("VehSend-Ret", "[${method.name}] ret=$retStr")
                            if (result is String) identifyCredential("VehSend-ret", result)
                        }
                    }
                }
            }
            logHook("Cred", "VehicleService BLE发送 Hook 完成")
        }
    }

    private fun scanForCredentialClasses(classLoader: ClassLoader) {
        safeHook("凭证类扫描") {
            val suspiciousClassNames = listOf(
                "com.yadea.smartmoto.ble.BleKeyManager",
                "com.yadea.smartmoto.ble.BleAuth",
                "com.yadea.smartmoto.ble.BleToken",
                "com.yadea.smartmoto.ble.DeviceKey",
                "com.yadea.smartmoto.ble.BleKey",
                "com.yadea.smartmoto.ble.BleKeyStore",
                "com.yadea.smartmoto.common.manager.BleKeyManager",
                "com.yadea.smartmoto.common.manager.DeviceKeyManager",
                "com.yadea.smartmoto.common.manager.TokenManager",
                "com.yadea.smartmoto.common.manager.AuthManager",
                "com.yadea.smartmoto.common.manager.DeviceTokenManager",
                "com.yadea.smartmoto.common.manager.SessionManager",
                "com.yadea.smartmoto.model.BleKey",
                "com.yadea.smartmoto.model.DeviceToken",
                "com.yadea.smartmoto.model.Token",
                "com.yadea.smartmoto.utils.BleUtils",
                "com.yadea.smartmoto.utils.SecurityUtils",
                "com.yadea.smartmoto.utils.TokenUtils",
                "com.yadea.smartmoto.utils.DeviceUtils",
                "com.yadea.smartmoto.common.util.SecurityUtil",
                "com.yadea.smartmoto.common.util.TokenUtil",
                "com.yadea.smartmoto.common.util.BleKeyUtil",
                "com.yadea.smartmoto.common.util.EncryptUtil",
            )

            for (className in suspiciousClassNames) {
                try {
                    val cls = loadClassOrNull(className, classLoader) ?: continue
                    logHook("Cred", "找到可疑类: $className")

                    val methods = cls.findAllMethods { notStatic() }
                    for (method in methods) {
                        if (method.name == "<init>") continue
                        method.createHook {
                            before { param ->
                                val args = param.args.mapIndexed { i, a ->
                                    when {
                                        a == null -> "null"
                                        a is ByteArray -> "byte[${a.size}](${bytesToHex(a).take(40)})"
                                        a is String -> "\"$a\""
                                        else -> a.toString()
                                    }
                                }.joinToString(", ")
                                logHook("CredClass", "[$className.${method.name}] args=($args)")
                            }
                            after { param ->
                                val ret = param.getResult()
                                if (ret != null && ret !is Unit) {
                                    val retStr = when (ret) {
                                        is ByteArray -> "byte[${ret.size}](${bytesToHex(ret).take(60)})"
                                        is String -> if (ret.length < 200) ret else ret.take(100)
                                        else -> ret.toString()
                                    }
                                    logHook("CredClass", "[$className.${method.name}] ret=$retStr")
                                    identifyCredential("$className.${method.name}", retStr)
                                }
                            }
                        }
                    }
                } catch (_: Throwable) { }
            }
        }
    }

    private fun identifyCredential(source: String, value: String) {
        if (value.isBlank() || value == "null" || value.length < 8) return
        if (capturedTokens.contains(value)) return

        val skipPatterns = listOf("http://", "https://", "null", "0", "false", "true", "{", "}", "[", "]", ",")
        if (skipPatterns.any { value.startsWith(it) && value.length < 20 }) return

        capturedTokens.add(value)

        val isBleKey = (value.length == 32 && value.matches(Regex("[a-fA-F0-9]+"))) ||
                (value.length == 24 && value.matches(Regex("[A-Za-z0-9+/]+=?"))) ||
                value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))

        val isToken = value.length in 20..256 && value.matches(Regex("[A-Za-z0-9_-]+"))

        if (isBleKey && capturedBleKey == null) {
            capturedBleKey = value
            logHook("Cred!!!", "===== BLE Key 捕获 ($source) =====")
            logHook("Cred!!!", "值: $value")
            val encoding = when {
                value.matches(Regex("[a-fA-F0-9]+")) -> "Hex (32位=16字节)"
                value.matches(Regex("[A-Za-z0-9+/]+=?")) -> "Base64"
                value.contains("-") -> "UUID格式"
                else -> "未知"
            }
            logHook("Cred!!!", "编码: $encoding")
            logHook("Cred!!!", "试试用这个值连接设备!")
        }

        if (isToken && capturedDeviceToken == null) {
            capturedDeviceToken = value
            logHook("Cred!!!", "===== Device Token 捕获 ($source) =====")
            logHook("Cred!!!", "值: $value")
            logHook("Cred!!!", "长度: ${value.length}")
            logHook("Cred!!!", "可用于API调用!")
        }
    }

    private fun parseBleKeyFromHex(hex: String) {
        if (hex.isBlank() || hex.length < 16) return
        val cleaned = hex.replace(" ", "").replace("-", "")
        if (cleaned.length == 32 && cleaned.matches(Regex("[a-fA-F0-9]+"))) {
            if (capturedBleKey == null) {
                capturedBleKey = cleaned
                logHook("Cred!", "BLE Key 候选 (from hex): $cleaned")
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
