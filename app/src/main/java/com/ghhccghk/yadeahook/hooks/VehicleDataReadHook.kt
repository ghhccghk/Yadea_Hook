package com.ghhccghk.yadeahook.hooks

import android.content.Context
import android.util.Log
import com.ghhccghk.yadeahook.BaseHook
import com.ghhccghk.yadeahook.VehicleStatusStore
import io.github.lingqiqi5211.ezhooktool.core.findAllMethods
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

class VehicleDataReadHook : BaseHook() {
    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context

        safeHook("VehicleDataManager 数据读取") {
            val managerClass = classLoader.loadClass("com.yadea.smartmoto.common.manager.VehicleDataManager")

            // hook V1(String, VehicleStatusBean) — 车辆状态更新
            val methods = managerClass.findAllMethods {
                paramCount(2)
                voidReturnType()
            }

            for (method in methods) {
                val paramTypes = method.parameterTypes
                if (paramTypes[0] == String::class.java && !paramTypes[1].isPrimitive) {
                    val methodName = method.name
                    method.createHook {
                        after { param ->
                            safeHook("VehicleDataRead-$methodName") {
                                val statusBean = param.args[1] ?: return@safeHook
                                val data = mutableMapOf<String, String>()
                                readAllFields(statusBean, data)
                                if (data.isNotEmpty()) {
                                    VehicleStatusStore.update(data)
                                    logHook("VehicleData", "[VehicleStatusBean] ${dumpAllFields(statusBean)}")
                                }
                            }
                        }
                    }
                    logHook("VehicleData", "已 hook: $methodName(${paramTypes.joinToString { it.simpleName }})")
                }
            }

            // hook BleNotifyEntity 更新方法 — TtpInfo 数据
            val notifyMethods = managerClass.findAllMethods {
                paramCount(1)
                voidReturnType()
            }

            for (method in notifyMethods) {
                val paramType = method.parameterTypes[0]
                if (paramType.name.contains("BleNotifyEntity")) {
                    val methodName = method.name
                    method.createHook {
                        after { param ->
                            safeHook("VehicleDataRead-$methodName") {
                                val entity = param.args[0] ?: return@safeHook
                                val ttpInfo = entity.getFieldValue("ttpInfo") ?: return@safeHook
                                val data = mutableMapOf<String, String>()
                                readAllFields(ttpInfo, data)
                                if (data.isNotEmpty()) {
                                    VehicleStatusStore.update(data)
                                }
                            }
                        }
                    }
                    Log.d("VehicleDataRead", "已 hook BleNotifyEntity: $methodName")
//                    logHook("VehicleData", "已 hook BleNotifyEntity: $methodName")
                }
            }
        }
    }

    private fun readAllFields(obj: Any, data: MutableMap<String, String>) {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            // 停止遍历：遇到 Java/Android 框架类
            val pkg = clazz.`package`?.name ?: ""
            if (pkg.startsWith("java.") || pkg.startsWith("android.") || pkg.startsWith("kotlin.")) break
            for (field in clazz.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                try {
                    field.isAccessible = true
                    val value = field.get(obj)
                    data[field.name] = value?.toString() ?: "null"
                } catch (_: Throwable) { }
            }
            clazz = clazz.superclass
        }
    }

    private fun dumpAllFields(obj: Any): String {
        val sb = StringBuilder()
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val pkg = clazz.`package`?.name ?: ""
            if (pkg.startsWith("java.") || pkg.startsWith("android.") || pkg.startsWith("kotlin.")) break
            for (field in clazz.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                try {
                    field.isAccessible = true
                    val value = field.get(obj)
                    sb.append("${field.name}=${value} \n")
                } catch (_: Throwable) { }
            }
            clazz = clazz.superclass
        }
        return if (sb.isNotEmpty()) sb.substring(0, sb.length - 2) else "{}"
    }
}
