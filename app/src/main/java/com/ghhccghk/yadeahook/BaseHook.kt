package com.ghhccghk.yadeahook

import android.content.Context
import android.util.Log
import com.ghhccghk.yadeahook.provider.HookLogger

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
        Log.d(TAG, message)
        HookLogger.log(appContext, tag, message)
    }

    protected fun Any.getFloatField(name: String): Float =
        javaClass.getField(name).getFloat(this)

    protected fun Any.getIntField(name: String): Int =
        javaClass.getField(name).getInt(this)

    protected fun Any.callGetter(name: String): Any? =
        javaClass.getMethod(name).invoke(this)

    protected fun Any.dumpFields(): String {
        val sb = StringBuilder()
        var clazz: Class<*>? = javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                try {
                    field.isAccessible = true
                    val value = field.get(this)
                    sb.append("${field.name}=${value}, ")
                } catch (_: Throwable) { }
            }
            clazz = clazz.superclass
        }
        return if (sb.isNotEmpty()) sb.substring(0, sb.length - 2) else "{}"
    }
}
