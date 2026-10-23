package com.ghhccghk.yadeahook

import android.content.Context
import android.os.Process
import android.util.Log
import com.ghhccghk.yadeahook.provider.HookLogger
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

abstract class BaseHook {
    protected val TAG = "YadeaHook"
    protected lateinit var appContext: Context

    abstract fun init(classLoader: ClassLoader, context: Context)

    protected inline fun safeHook(name: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.e(TAG, "Hook 失败: $name", e)
            logHook("Error", "Hook 失败: $name - ${e.message}")
        }
    }

    protected fun logHook(tag: String, message: String) {
        Log.d(TAG, tag + " " +message)
        HookLogger.log(appContext, tag, message)
    }

    /**
     * 只读抓包统一日志。每条日志都带调用线程、PID/TID、MAC 和运行时类，便于跨 Hook 排序。
     */
    protected fun captureLog(
        hookClass: String,
        event: String,
        method: String? = "n/a",
        mac: String? = null,
        runtimeClass: String? = null,
        vararg fields: Pair<String, Any?>
    ) {
        try {
            val seq = captureSequence.incrementAndGet()
            val details = fields.joinToString("") { (key, value) ->
                "|$key=${formatCaptureValue(value)}"
            }
            val message = buildString {
                append("YADEA_CAP")
                append("|SEQ=").append(seq)
                append("|PID=").append(Process.myPid())
                append("|TID=").append(Process.myTid())
                append("|thread=").append(Thread.currentThread().name)
                append('|').append(event)
                append("|hook=").append(hookClass)
                append("|method=").append(method ?: "n/a")
                append("|mac=").append(mac ?: "unknown")
                append("|runtime=").append(runtimeClass ?: "n/a")
                append(details)
            }
            logHook("YADEA_CAP", message)
        } catch (e: Throwable) {
            Log.e(TAG, "抓包日志失败: $hookClass#$method", e)
        }
    }

    /**
     * 每个 Hook 安装点、回调点和异常都必须经过此保护，异常不会向目标 App 传播。
     */
    protected fun captureGuard(hookClass: String, method: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            captureError(hookClass, method, e)
        }
    }

    protected fun captureError(hookClass: String, method: String, throwable: Throwable) {
        Log.e(TAG, "抓包 Hook 异常: $hookClass#$method", throwable)
        captureLog(
            hookClass = hookClass,
            event = "ERROR",
            method = method,
            fields = arrayOf(
                "exception" to throwable.javaClass.name,
                "message" to (throwable.message ?: "")
            )
        )
    }

    protected fun describeMethod(method: Executable): String {
        val params = method.parameterTypes.joinToString(",") { it.name }
        val returns = when (method) {
            is Method -> method.returnType.name
            else -> "void"
        }
        return "${method.declaringClass.name}#${method.name}($params)$returns"
    }

    protected fun hookRuntimeClass(value: Any?): String =
        value?.javaClass?.name ?: "n/a"

    protected fun extractMac(value: Any?): String? {
        if (value == null) return null
        for (methodName in listOf("getMac", "getAddress")) {
            try {
                val result = value.javaClass.getMethod(methodName).invoke(value)
                if (result is String && result.isNotBlank()) return result
            } catch (_: Throwable) {
                // 尝试下一个字段/方法
            }
        }
        for (fieldName in listOf("mac", "mMac", "device", "mDevice")) {
            val child = readFieldDeep(value, fieldName) ?: continue
            if (child is String && child.isNotBlank() && child.count { it == ':' } >= 5) {
                return child
            }
            for (methodName in listOf("getAddress", "getMac")) {
                try {
                    val result = child.javaClass.getMethod(methodName).invoke(child)
                    if (result is String && result.isNotBlank()) return result
                } catch (_: Throwable) {
                    // 继续尝试
                }
            }
        }
        return null
    }

    protected fun invokeGetter(value: Any?, methodName: String): Any? {
        if (value == null) return null
        return try {
            value.javaClass.getMethod(methodName).invoke(value)
        } catch (_: Throwable) {
            null
        }
    }

    protected fun readFieldDeep(value: Any?, fieldName: String): Any? {
        if (value == null) return null
        var clazz: Class<*>? = value.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(value)
            } catch (_: Throwable) {
                clazz = clazz.superclass
            }
        }
        return null
    }

    protected fun ByteArray.toHexUpper(): String {
        if (isEmpty()) return ""
        val chars = CharArray(size * 2)
        val digits = "0123456789ABCDEF"
        for (index in indices) {
            val value = this[index].toInt() and 0xFF
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(chars)
    }

    protected fun callStack(maxFrames: Int = 16): String =
        Throwable().stackTrace
            .drop(2)
            .take(maxFrames)
            .joinToString(" <- ") { frame ->
                "${frame.className}.${frame.methodName}(${frame.fileName ?: "unknown"}:${frame.lineNumber})"
            }

    private fun formatCaptureValue(value: Any?): String = when (value) {
        null -> "null"
        is ByteArray -> value.toHexUpper()
        is Array<*> -> value.joinToString(",") { formatCaptureValue(it) }
        is IntArray -> value.joinToString(",")
        is LongArray -> value.joinToString(",")
        is ShortArray -> value.joinToString(",")
        is BooleanArray -> value.joinToString(",")
        is CharArray -> value.concatToString()
        else -> value.toString()
    }

    protected fun Any.getFloatField(name: String): Float =
        javaClass.getField(name).getFloat(this)

    protected fun Any.getIntField(name: String): Int =
        javaClass.getField(name).getInt(this)

    protected fun Any.callGetter(name: String): Any? =
        javaClass.getMethod(name).invoke(this)

    protected fun Any.getFieldValue(name: String): Any? {
        return try {
            val field = javaClass.getDeclaredField(name)
            field.isAccessible = true
            field.get(this)
        } catch (_: Throwable) { null }
    }

    protected fun parseTtpInfo(text: String, vararg keys: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (key in keys) {
            val regex = Regex("${Regex.escape(key)}[：:]\\s*(.+)")
            regex.find(text)?.groupValues?.get(1)?.trim()?.let { result[key] = it }
        }
        return result
    }

    protected fun Any.dumpFields(): String {
        val sb = StringBuilder()
        var clazz: Class<*>? = javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                try {
                    field.isAccessible = true
                    val value = field.get(this)
                    sb.append("${field.name}=${value}, \n")
                } catch (_: Throwable) { }
            }
            clazz = clazz.superclass
        }
        return if (sb.isNotEmpty()) sb.substring(0, sb.length - 2) else "{}"
    }

    companion object {
        private val captureSequence = AtomicLong(0L)
    }
}
