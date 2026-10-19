package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.LoginCaptureStore
import io.github.lingqiqi5211.ezhooktool.core.loadClassOrNull
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import java.lang.reflect.Method

/**
 * 登录链路精确抓取 Hook。
 *
 * 目标类名来自当前 APK 的 smali 混淆结果；方法查找同时校验
 * 参数类型、返回类型，并在签名变化时退回“方法名 + 参数个数”匹配。
 */
class LoginCaptureHook : BaseHook() {

    companion object {
        private const val USER_INFO = "com.yadea.smartmoto.base.base.UserInfoBean"
        private const val API_EXCEPTION = "com.yadea.smartmoto.network.exception.ApiException"
        private const val CONTEXT = "android.content.Context"
        private const val STRING = "java.lang.String"
        private const val VOID = "void"
        private const val INT = "int"
        private const val BOOLEAN = "boolean"
        private const val MAP = "java.util.Map"
        private const val TREE_MAP = "java.util.TreeMap"
        private const val CHAIN = "okhttp3.Interceptor\$Chain"
        private const val RESPONSE = "okhttp3.Response"
    }

    private var lastRegistrationId = ""
    private var lastOppoPushId = ""
    private var lastUsrToken = ""

    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        LoginCaptureStore.init(context)

        hookOneTapLogin(classLoader)
        hookOneTapCallbacks(classLoader, "p46\$a")
        hookOneTapCallbacks(classLoader, "p46\$b")
        hookBusinessJson(classLoader)
        hookGatewaySignature(classLoader)
        hookRequestHeaders(classLoader)
        hookRequestBuilder(classLoader)
        hookPushIds(classLoader)
        hookLoginTokenStorage(classLoader)
        hookLoginState(classLoader)

        logHook("Login", "登录抓取 Hook 初始化完成")
    }

    // ============================================================
    //  p46.a / p46.b：一键认证 loginToken + gyuid
    // ============================================================
    private fun hookOneTapLogin(classLoader: ClassLoader) {
        val clazz = loadLoginClass(classLoader, "p46")
        hookMethod(
            clazz,
            "p46",
            "a",
            listOf(CONTEXT, STRING, STRING),
            VOID,
            "p46.a",
            onBefore = { param -> captureOneTapArgs("p46.a", param.args) }
        )
        hookMethod(
            clazz,
            "p46",
            "b",
            listOf(CONTEXT, STRING, STRING, INT, "y04"),
            VOID,
            "p46.b",
            onBefore = { param -> captureOneTapArgs("p46.b", param.args) }
        )
    }

    private fun captureOneTapArgs(source: String, args: Array<Any?>) {
        val loginToken = args.getOrNull(1) as? String
        val gyuid = args.getOrNull(2) as? String
        val from = (args.getOrNull(3) as? Number)?.toInt()

        LoginCaptureStore.setLoginToken(loginToken)
        LoginCaptureStore.setGyuid(gyuid)

        logHook("Login-Token", "[$source] loginToken=${loginToken.orEmpty()}")
        logHook("Login-Token", "[$source] gyuid=${gyuid.orEmpty()}")
        if (from != null) {
            logHook("Login-Token", "[$source] from=$from")
        }
    }

    // ============================================================
    //  p46$a / p46$b：登录成功 / 失败
    // ============================================================
    private fun hookOneTapCallbacks(classLoader: ClassLoader, className: String) {
        val clazz = loadLoginClass(classLoader, className) ?: return

        // 实际业务方法为 b(UserInfoBean)，bridge 方法 onSuccess(Object) 可能也保留。
        // 优先 hook 具体方法，避免一次成功回调被记录两遍。
        val successMethod = findMethodBySignature(clazz, "b", listOf(USER_INFO), VOID)
            ?: findMethodBySignature(clazz, "onSuccess", listOf("java.lang.Object"), VOID)
            ?: findLooseMethod(clazz, "b", 1, VOID)
            ?: findLooseMethod(clazz, "onSuccess", 1, VOID)

        successMethod?.let { method ->
            hookResolvedMethod(
                method,
                "$className.onSuccess",
                onAfter = { param ->
                    captureUserInfo("$className.onSuccess", param.args.getOrNull(0))
                }
            )
        } ?: logHook("Login", "$className 未匹配到 onSuccess")

        val errorMethod = findMethodBySignature(clazz, "onError", listOf(API_EXCEPTION), VOID)
            ?: findLooseMethod(clazz, "onError", 1, VOID)
        errorMethod?.let { method ->
            hookResolvedMethod(
                method,
                "$className.onError",
                onBefore = { param ->
                    captureLoginError("$className.onError", param.args.getOrNull(0))
                }
            )
        } ?: logHook("Login", "$className 未匹配到 onError")
    }

    private fun captureUserInfo(source: String, bean: Any?) {
        if (bean == null) return

        val fieldNames = listOf(
            "id", "userId", "mobile", "userName", "nickName", "email",
            "token", "tokenExpire", "authKey"
        )
        val summary = fieldNames.mapNotNull { field ->
            val value = bean.getFieldValue(field) ?: return@mapNotNull null
            "$field=$value"
        }.joinToString(", ")

        val token = bean.getFieldValue("token") as? String
        logHook("Login-Success", "[$source] $summary")
        LoginCaptureStore.clearLastError()
        LoginCaptureStore.setUserInfo("[$source] $summary")
        LoginCaptureStore.setUsrToken(token)
    }

    private fun captureLoginError(source: String, error: Any?) {
        if (error == null) {
            logHook("Login-Error", "[$source] error=null")
            return
        }
        val code = readIntMethod(error, "a", "getCode", "code")
        val message = readStringMethod(error, "getMessage", "message")
        val text = "[$source] code=$code message=$message"
        logHook("Login-Error", text)
        LoginCaptureStore.setLastError(text)
    }

    // ============================================================
    //  f84.b：签名前完整业务 JSON
    // ============================================================
    private fun hookBusinessJson(classLoader: ClassLoader) {
        val clazz = loadLoginClass(classLoader, "f84")
        hookMethod(
            clazz,
            "f84",
            "b",
            listOf(MAP),
            STRING,
            "f84.b",
            onAfter = { param ->
                val json = param.result as? String
                if (!json.isNullOrBlank()) {
                    logHook("Login-JSON", json)
                    LoginCaptureStore.setBusinessJson(json)
                }
            }
        )
    }

    // ============================================================
    //  tb0.d：签名后的网关字段
    // ============================================================
    private fun hookGatewaySignature(classLoader: ClassLoader) {
        val clazz = loadLoginClass(classLoader, "tb0")
        hookMethod(
            clazz,
            "tb0",
            "d",
            listOf(TREE_MAP),
            TREE_MAP,
            "tb0.d",
            onBefore = { param ->
                logHook("Login-Gateway", "before=${formatMap(param.args.getOrNull(0))}")
            },
            onAfter = { param ->
                val result = param.result ?: param.args.getOrNull(0)
                val text = formatMap(result)
                logHook("Login-Gateway", "after=$text")
                LoginCaptureStore.setGatewayFields(text)
            }
        )
    }

    // ============================================================
    //  sb0.intercept：实际请求头
    // ============================================================
    private fun hookRequestHeaders(classLoader: ClassLoader) {
        val clazz = loadLoginClass(classLoader, "sb0")
        hookMethod(
            clazz,
            "sb0",
            "intercept",
            listOf(CHAIN),
            RESPONSE,
            "sb0.intercept",
            onBefore = { param -> captureRequestHeaders(param.args.getOrNull(0)) },
            onAfter = { param ->
                val code = readIntMethod(param.result, "code", "getCode")
                if (code != null) logHook("HTTP-Resp", "code=$code")
            }
        )
    }

    /**
     * sb0 在 intercept 内部构建新的 Request，before 阶段看不到它随后添加的
     * Authorization/req-id 等 header。再 hook Request.Builder.build()，
     * 读取真正交给 OkHttp 的最终请求头。
     */
    private fun hookRequestBuilder(classLoader: ClassLoader) {
        val clazz = loadLoginClass(classLoader, "okhttp3.Request\$Builder")
        hookMethod(
            clazz,
            "okhttp3.Request\$Builder",
            "build",
            emptyList(),
            "okhttp3.Request",
            "okhttp3.Request.Builder.build",
            onAfter = { param -> captureFinalRequest(param.result) }
        )
    }

    private fun captureRequestHeaders(chain: Any?) {
        if (chain == null) return
        try {
            val request = invokeNoArg(chain, "request") ?: return
            val method = invokeNoArg(request, "method") as? String
            val url = invokeNoArg(request, "url")?.toString()
            if (url == null || (!url.contains("/api/") && !url.contains("yadeaiot", ignoreCase = true))) {
                return
            }
            val headers = invokeNoArg(request, "headers")
            val names = invokeNoArg(headers, "names") as? Collection<*>
            val lines = mutableListOf<String>()

            names?.forEach { rawName ->
                val name = rawName?.toString() ?: return@forEach
                val value = invokeOneArg(headers, "get", name) as? String ?: return@forEach
                lines += "$name: $value"
            }

            val headerText = lines.joinToString("\n")
            val text = "${method.orEmpty()} ${url.orEmpty()}\n$headerText"
            logHook("HTTP-Headers-pre", text)
            LoginCaptureStore.setRequestHeaders(text)

            lines.firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.replace(Regex("^Bearer\\s*", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { LoginCaptureStore.setUsrToken(it) }
        } catch (e: Throwable) {
            logHook("HTTP-Headers", "读取失败: ${e.message}")
        }
    }

    private fun captureFinalRequest(request: Any?) {
        if (request == null) return
        try {
            val url = invokeNoArg(request, "url")?.toString() ?: return
            if (!url.contains("/api/") && !url.contains("yadeaiot", ignoreCase = true)) return

            val method = invokeNoArg(request, "method") as? String
            val headers = invokeNoArg(request, "headers")
            val names = invokeNoArg(headers, "names") as? Collection<*>
            val lines = mutableListOf<String>()
            names?.forEach { rawName ->
                val name = rawName?.toString() ?: return@forEach
                val value = invokeOneArg(headers, "get", name) as? String ?: return@forEach
                lines += "$name: $value"
            }

            val headerText = lines.joinToString("\n")
            val text = "${method.orEmpty()} $url\n$headerText"
            logHook("HTTP-Headers", text)
            LoginCaptureStore.setRequestHeaders(text)

            lines.firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.replace(Regex("^Bearer\\s*", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { LoginCaptureStore.setUsrToken(it) }
        } catch (e: Throwable) {
            logHook("HTTP-Headers", "最终请求读取失败: ${e.message}")
        }
    }

    // ============================================================
    //  vy1.c / va0.i：registrationId / oppoPushId
    // ============================================================
    private fun hookPushIds(classLoader: ClassLoader) {
        val vy1 = loadLoginClass(classLoader, "vy1")
        hookMethod(
            vy1,
            "vy1",
            "c",
            emptyList(),
            STRING,
            "vy1.c",
            onAfter = { param ->
                val value = param.result as? String
                if (!value.isNullOrBlank() && value != lastRegistrationId) {
                    lastRegistrationId = value
                    LoginCaptureStore.setRegistrationId(value)
                    logHook("Login-Push", "registrationId=$value")
                }
            }
        )

        val va0 = loadLoginClass(classLoader, "va0")
        hookMethod(
            va0,
            "va0",
            "i",
            emptyList(),
            STRING,
            "va0.i",
            onAfter = { param ->
                val value = param.result as? String
                if (!value.isNullOrBlank() && value != lastOppoPushId) {
                    lastOppoPushId = value
                    LoginCaptureStore.setOppoPushId(value)
                    logHook("Login-Push", "oppoPushId=$value")
                }
            }
        )
    }

    // ============================================================
    //  zm3.z0 / zm3.h2：usr_token 与写盘
    // ============================================================
    private fun hookLoginTokenStorage(classLoader: ClassLoader) {
        val zm3 = loadLoginClass(classLoader, "zm3")
        hookMethod(
            zm3,
            "zm3",
            "z0",
            emptyList(),
            STRING,
            "zm3.z0",
            onAfter = { param ->
                val value = param.result as? String
                if (!value.isNullOrBlank() && value != lastUsrToken) {
                    lastUsrToken = value
                    LoginCaptureStore.setUsrToken(value)
                    logHook("Login-Token", "usr_token=$value")
                    logHook("Login-Token", "Authorization=Bearer $value")
                }
            }
        )

        hookMethod(
            zm3,
            "zm3",
            "h2",
            listOf(STRING),
            VOID,
            "zm3.h2",
            onBefore = { param ->
                val value = param.args.getOrNull(0) as? String
                if (!value.isNullOrBlank()) {
                    lastUsrToken = value
                    LoginCaptureStore.setUsrToken(value)
                    logHook("Login-Token", "usr_token 写盘=$value")
                }
            }
        )
    }

    // ============================================================
    //  o13.a：登录态写盘
    // ============================================================
    private fun hookLoginState(classLoader: ClassLoader) {
        val clazz = loadLoginClass(classLoader, "o13")
        hookMethod(
            clazz,
            "o13",
            "a",
            listOf(USER_INFO),
            BOOLEAN,
            "o13.a",
            onAfter = { param ->
                val ok = param.result as? Boolean
                logHook("Login-State", "o13.a result=$ok")
                captureUserInfo("o13.a", param.args.getOrNull(0))
            }
        )
    }

    // ============================================================
    //  方法匹配
    // ============================================================
    private fun loadLoginClass(classLoader: ClassLoader, name: String): Class<*>? {
        return try {
            loadClassOrNull(name, classLoader).also {
                if (it == null) logHook("Login", "类不存在: $name")
            }
        } catch (e: Throwable) {
            logHook("Login", "类加载失败: $name - ${e.message}")
            null
        }
    }

    private fun findMethodBySignature(
        clazz: Class<*>,
        name: String,
        parameterTypes: List<String>,
        returnType: String
    ): Method? = clazz.declaredMethods.firstOrNull { method ->
        method.name == name &&
            method.parameterTypes.map { it.name } == parameterTypes &&
            method.returnType.name == returnType
    }

    private fun findLooseMethod(
        clazz: Class<*>,
        name: String,
        parameterCount: Int,
        returnType: String
    ): Method? = clazz.declaredMethods.firstOrNull { method ->
        method.name == name &&
            method.parameterCount == parameterCount &&
            method.returnType.name == returnType
    }

    private fun hookMethod(
        clazz: Class<*>?,
        className: String,
        methodName: String,
        parameterTypes: List<String>,
        returnType: String,
        tag: String,
        onBefore: ((HookParam) -> Unit)? = null,
        onAfter: ((HookParam) -> Unit)? = null
    ) {
        if (clazz == null) return
        val method = findMethodBySignature(clazz, methodName, parameterTypes, returnType)
            ?: findLooseMethod(clazz, methodName, parameterTypes.size, returnType)
        if (method == null) {
            logHook("Login", "未匹配方法: $className.$methodName(${parameterTypes.joinToString()})")
            return
        }
        hookResolvedMethod(method, tag, onBefore, onAfter)
    }

    private fun hookResolvedMethod(
        method: Method,
        tag: String,
        onBefore: ((HookParam) -> Unit)? = null,
        onAfter: ((HookParam) -> Unit)? = null
    ) {
        safeHook(tag) {
            method.isAccessible = true
            method.createHook {
                if (onBefore != null) before { param -> onBefore(param) }
                if (onAfter != null) after { param -> onAfter(param) }
            }
            logHook("Login", "已 hook $tag -> ${describe(method)}")
        }
    }

    private fun describe(method: Method): String =
        "${method.declaringClass.name}.${method.name}(" +
            method.parameterTypes.joinToString { it.name } +
            "):${method.returnType.name}"

    // ============================================================
    //  反射辅助（不依赖 OkHttp / Gson 的编译期类型）
    // ============================================================
    private fun invokeNoArg(target: Any?, name: String): Any? {
        if (target == null) return null
        return try {
            target.javaClass.getMethod(name).invoke(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeOneArg(target: Any?, name: String, arg: Any): Any? {
        if (target == null) return null
        return try {
            target.javaClass.getMethod(name, arg.javaClass).invoke(target, arg)
        } catch (_: Throwable) {
            null
        }
    }

    private fun readIntMethod(target: Any?, vararg names: String): Any? {
        if (target == null) return null
        for (name in names) {
            val value = invokeNoArg(target, name) ?: continue
            if (value is Number) return value.toInt()
        }
        return null
    }

    private fun readStringMethod(target: Any?, vararg names: String): String? {
        if (target == null) return null
        for (name in names) {
            val value = invokeNoArg(target, name) as? String ?: continue
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun formatMap(value: Any?): String {
        if (value == null) return "null"
        return try {
            val map = value as? Map<*, *>
            if (map == null) {
                value.toString()
            } else {
                map.entries.joinToString("&") { (k, v) -> "$k=$v" }
            }
        } catch (_: Throwable) {
            value.toString()
        }
    }
}
