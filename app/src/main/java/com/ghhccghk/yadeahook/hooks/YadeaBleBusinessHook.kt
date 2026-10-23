package com.ghhccghk.yadeahook.hooks

import android.content.Context
import com.ghhccghk.yadeahook.BaseHook
import io.github.lingqiqi5211.ezhooktool.core.findAllMethods
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook
import java.lang.reflect.Method

/**
 * 雅迪 BLE 业务层只读抓包。
 *
 * 业务类名来自当前 8.8.9 temp_smali 核对结果。Hook 仅读取参数、返回值和字段，
 * 不替换返回值、不修改参数、不阻断调用。
 */
class YadeaBleBusinessHook : BaseHook() {
    private lateinit var targetClassLoader: ClassLoader

    override fun init(classLoader: ClassLoader, context: Context) {
        appContext = context
        targetClassLoader = classLoader

        hookBleBicycleConnect()
        hookBleControlManager()
        hookCommandManager()
        hookTransportEntry()
        hookHandshake()
    }

    private fun loadClass(name: String): Class<*>? {
        return try {
            targetClassLoader.loadClass(name)
        } catch (_: Throwable) {
            null
        }
    }

    private fun methodKey(method: Method): String =
        "YADEA_CAP_${describeMethod(method).replace(Regex("[^A-Za-z0-9_.$]"), "_")}"

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

    private fun installed(method: Method, feature: String) {
        captureLog(
            hookClass = HOOK,
            event = "INSTALLED",
            method = describeMethod(method),
            fields = arrayOf("feature" to feature)
        )
    }

    private fun missing(method: String, expected: String) {
        captureLog(
            hookClass = HOOK,
            event = "MISSING",
            method = method,
            fields = arrayOf("expected" to expected)
        )
    }

    // ---------------------------------------------------------------------
    // BleBicycleConnect 五步连接任务
    // ---------------------------------------------------------------------

    private fun hookBleBicycleConnect() {
        val className = "com.yadea.blecontrol.connect.BleBicycleConnect"
        val clazz = loadClass(className)
        if (clazz == null) {
            missing(className, "class")
            return
        }

        hookConnectTaskList(clazz)
        hookFifthStep(clazz)
        hookTaskExecution(clazz)
    }

    private fun hookConnectTaskList(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "z", 2) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice"
        }
        if (methods.isEmpty()) {
            missing(
                "${clazz.name}.z",
                "(BleDevice,Function)List<Task>"
            )
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CONNECT|TASK_LIST_CREATE",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "deviceType" to hookRuntimeClass(param.args.getOrNull(0)),
                                    "callbackType" to hookRuntimeClass(param.args.getOrNull(1))
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val tasks = param.result as? List<*>
                            captureLog(
                                hookClass = HOOK,
                                event = "CONNECT|TASK_LIST",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "count" to (tasks?.size ?: 0),
                                    "tasks" to tasks.orEmpty().mapIndexed { index, task ->
                                        "$index:${taskName(task)}:${hookRuntimeClass(task)}"
                                    }.joinToString(",")
                                )
                            )
                        }
                    }
                }
                installed(method, "five-connect-tasks")
            }
        }
    }

    private fun hookFifthStep(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "v", 1) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice"
        }
        if (methods.isEmpty()) {
            missing("${clazz.name}.v", "(BleDevice)Task")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CONNECT|STEP5_QUERY_SCHEDULE",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "step" to 5,
                                    "taskName" to "QUERY_VEHICLE_STATUS",
                                    "deviceType" to hookRuntimeClass(param.args.getOrNull(0))
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CONNECT|STEP5_QUERY_CREATED",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.result),
                                fields = arrayOf(
                                    "taskName" to taskName(param.result),
                                    "taskType" to hookRuntimeClass(param.result)
                                )
                            )
                        }
                    }
                }
                installed(method, "step5-status-query")
            }
        }
    }

    private fun hookTaskExecution(clazz: Class<*>) {
        val startMethods = matchingMethods(clazz, "E", 7) {
            List::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (startMethods.isEmpty()) {
            missing("${clazz.name}.E", "task sequence starter")
        }
        for (method in startMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val tasks = param.args.getOrNull(0) as? List<*>
                            val index = param.args.getOrNull(5) as? Int
                            val task = index?.let { tasks?.getOrNull(it) }
                            captureLog(
                                hookClass = HOOK,
                                event = "CONNECT|TASK_START",
                                method = describeMethod(method),
                                runtimeClass = "static",
                                fields = arrayOf(
                                    "index" to index,
                                    "oneBased" to index?.plus(1),
                                    "taskName" to taskName(task),
                                    "taskType" to hookRuntimeClass(task),
                                    "maxRetries" to param.args.getOrNull(4),
                                    "retryCount" to param.args.getOrNull(6)
                                )
                            )
                        }
                    }
                }
                installed(method, "connect-task-start")
            }
        }

        val successClass = loadClass("${clazz.name}\$executeTasksSequentially\$executeTask\$1")
        val successMethods = matchingMethods(successClass, "invoke", 0) {
            it.returnType == Void.TYPE
        }
        if (successMethods.isEmpty()) {
            missing("${successClass?.name ?: "TaskSuccess"}.invoke", "()V")
        }
        for (method in successMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val thisObject = param.thisObjectOrNull
                            val task = readFieldDeep(thisObject, "\$task")
                            captureLog(
                                hookClass = HOOK,
                                event = "CONNECT|TASK_COMPLETE",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(thisObject),
                                fields = arrayOf(
                                    "index" to readFieldDeep(thisObject, "\$index"),
                                    "taskName" to taskName(task),
                                    "taskType" to hookRuntimeClass(task)
                                )
                            )
                        }
                    }
                }
                installed(method, "connect-task-complete")
            }
        }

        val failureClass = loadClass("${clazz.name}\$executeTasksSequentially\$executeTask\$2")
        val failureMethods = matchingMethods(failureClass, "c", 1)
        for (method in failureMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val thisObject = param.thisObjectOrNull
                            val task = readFieldDeep(thisObject, "\$task")
                            val error = param.args.getOrNull(0)
                            captureLog(
                                hookClass = HOOK,
                                event = "CONNECT|TASK_FAILED",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(thisObject),
                                fields = arrayOf(
                                    "index" to readFieldDeep(thisObject, "\$index"),
                                    "retryCount" to readFieldDeep(thisObject, "\$retryCount"),
                                    "maxRetries" to readFieldDeep(thisObject, "\$maxRetries"),
                                    "taskName" to taskName(task),
                                    "errorType" to hookRuntimeClass(error),
                                    "error" to invokeGetter(error, "b")
                                )
                            )
                        }
                    }
                }
                installed(method, "connect-task-failure")
            }
        }
    }

    private fun taskName(task: Any?): String? {
        return (invokeGetter(task, "f") as? String)
            ?: (invokeGetter(task, "a") as? String)
    }

    // ---------------------------------------------------------------------
    // BleControlManager 通知与 increaseByteNum
    // ---------------------------------------------------------------------

    private fun hookBleControlManager() {
        val clazz = loadClass("com.yadea.blecontrol.control.BleControlManager")
        if (clazz == null) {
            missing("com.yadea.blecontrol.control.BleControlManager", "class")
            return
        }

        hookControlManagerSchedule(clazz, "d", "bicycle-status-notification")
        hookControlManagerSchedule(clazz, "s", "scooter-status-notification")
        hookControlManagerIncrease(clazz)
        hookScooterNotificationHelper()
    }

    private fun hookControlManagerSchedule(clazz: Class<*>, methodName: String, feature: String) {
        val methods = matchingMethods(clazz, methodName, 2) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice"
        }
        if (methods.isEmpty()) {
            missing("${clazz.name}.$methodName", "(BleDevice,Callback)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "NOTIFY|REGISTER_SCHEDULE",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "feature" to feature,
                                    "callbackType" to hookRuntimeClass(param.args.getOrNull(1)),
                                    "deviceType" to hookRuntimeClass(param.args.getOrNull(0)),
                                    "stack" to callStack(10)
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "NOTIFY|REGISTER_SCHEDULE_RETURN",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf("feature" to feature)
                            )
                        }
                    }
                }
                installed(method, feature)
            }
        }
    }

    private fun hookControlManagerIncrease(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "e", 1) {
            it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (methods.isEmpty()) {
            missing("${clazz.name}.e", "(int)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CMD|INCREASE_REQUEST",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "requested" to param.args.getOrNull(0),
                                    "state" to commandManagerState()
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CMD|INCREASE_AFTER",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "requested" to param.args.getOrNull(0),
                                    "state" to commandManagerState()
                                )
                            )
                        }
                    }
                }
                installed(method, "increase-control-byte")
            }
        }
    }

    private fun hookScooterNotificationHelper() {
        val clazz = loadClass("com.yadea.blecontrol.control.c")
        if (clazz == null) {
            missing("com.yadea.blecontrol.control.c", "class")
            return
        }
        val enableMethods = matchingMethods(clazz, "e", 3) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice"
        }
        if (enableMethods.isEmpty()) {
            missing("${clazz.name}.e", "(BleDevice,Callback,TaskCallback)V")
        }
        for (method in enableMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "NOTIFY|SCOOTER_REGISTER",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
                                    "uuid" to "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
                                    "callbackType" to hookRuntimeClass(param.args.getOrNull(1)),
                                    "order" to "BleControlManager.s -> c.e -> tl.Q"
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "NOTIFY|SCOOTER_REGISTER_RETURN",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull)
                            )
                        }
                    }
                }
                installed(method, "scooter-notification-register")
            }
        }

        val directMethods = matchingMethods(clazz, "d", 1) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice"
        }
        for (method in directMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "NOTIFY|SCOOTER_NOTIFY_DIRECT",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
                                    "uuid" to "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
                                )
                            )
                        }
                    }
                }
                installed(method, "scooter-notification-enable")
            }
        }
    }

    // ---------------------------------------------------------------------
    // BleBicycleCommandManager 命令与字段快照
    // ---------------------------------------------------------------------

    private fun hookCommandManager() {
        val clazz = loadClass("com.yadea.blecontrol.data.BleBicycleCommandManager")
        if (clazz == null) {
            missing("com.yadea.blecontrol.data.BleBicycleCommandManager", "class")
            return
        }

        hookGenerateBleCommand(clazz)
        hookIncreaseGetter(clazz)
        hookCommandIncreaseControl(clazz)
        hookSetMethod(clazz, "setAppType", 1);
        hookSetMethod(clazz, "setAppVersion", 2);
        hookSetMethod(clazz, "setBikeBondType", 1);
    }

    private fun hookGenerateBleCommand(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "generateBleCommand", 3) {
            it.parameterTypes.all { type -> type == Int::class.javaPrimitiveType } &&
                it.returnType == ByteArray::class.java
        }
        if (methods.isEmpty()) {
            missing("${clazz.name}.generateBleCommand", "(int,int,int)byte[]")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val manager = param.thisObjectOrNull
                            captureLog(
                                hookClass = HOOK,
                                event = "CMD|GENERATE_BEFORE",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(manager),
                                fields = arrayOf(
                                    "command" to param.args.getOrNull(0),
                                    "param" to param.args.getOrNull(1),
                                    "protocol" to param.args.getOrNull(2),
                                    "increaseByteNum" to increaseByteNum(manager),
                                    "state" to managerState(manager),
                                    "stack" to callStack(16)
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val manager = param.thisObjectOrNull
                            val command = param.args.getOrNull(0)
                            val data = (param.result as? ByteArray)?.clone()
                            captureLog(
                                hookClass = HOOK,
                                event = if (command == 0x34) {
                                    "CMD|STATUS_QUERY_0x34"
                                } else {
                                    "CMD|GENERATE_AFTER"
                                },
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(manager),
                                fields = arrayOf(
                                    "command" to command,
                                    "param" to param.args.getOrNull(1),
                                    "protocol" to param.args.getOrNull(2),
                                    "increaseByteNum" to increaseByteNum(manager),
                                    "len" to (data?.size ?: 0),
                                    "data" to data,
                                    "state" to managerState(manager)
                                )
                            )
                        }
                    }
                }
                installed(method, "command-generate")
            }
        }
    }

    private fun hookIncreaseGetter(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "getIncreaseByteHexStr", 0) {
            it.returnType == String::class.java
        }
        if (methods.isEmpty()) {
            missing("${clazz.name}.getIncreaseByteHexStr", "()String")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CMD|INCREASE_GET_BEFORE",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "increaseByteNum" to increaseByteNum(param.thisObjectOrNull),
                                    "state" to managerState(param.thisObjectOrNull)
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CMD|INCREASE_GET_AFTER",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "increaseByteNum" to increaseByteNum(param.thisObjectOrNull),
                                    "result" to param.result,
                                    "state" to managerState(param.thisObjectOrNull)
                                )
                            )
                        }
                    }
                }
                installed(method, "increase-byte-getter")
            }
        }
    }

    private fun hookCommandIncreaseControl(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "increaseControlByte", 1) {
            it.parameterTypes[0] == Int::class.javaPrimitiveType
        }
        if (methods.isEmpty()) {
            missing("${clazz.name}.increaseControlByte", "(int)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CMD|INCREASE_SET_BEFORE",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "requested" to param.args.getOrNull(0),
                                    "increaseByteNum" to increaseByteNum(param.thisObjectOrNull),
                                    "state" to managerState(param.thisObjectOrNull)
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CMD|INCREASE_SET_AFTER",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "requested" to param.args.getOrNull(0),
                                    "increaseByteNum" to increaseByteNum(param.thisObjectOrNull),
                                    "state" to managerState(param.thisObjectOrNull)
                                )
                            )
                        }
                    }
                }
                installed(method, "increase-byte-setter")
            }
        }
    }

    private fun hookSetMethod(clazz: Class<*>, methodName: String, paramCount: Int) {
        val methods = matchingMethods(clazz, methodName, paramCount)
        if (methods.isEmpty()) {
            missing("${clazz.name}.$methodName", "paramCount=$paramCount")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "CMD|FIELD_SNAPSHOT",
                                method = describeMethod(method),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "args" to param.args.contentToString(),
                                    "state" to managerState(param.thisObjectOrNull)
                                )
                            )
                        }
                    }
                }
                installed(method, "command-field-snapshot")
            }
        }
    }

    private fun commandManagerState(): String {
        val clazz = loadClass("com.yadea.blecontrol.data.BleBicycleCommandManager")
        val instance = try {
            clazz?.getMethod("getInstance")?.invoke(null)
        } catch (_: Throwable) {
            null
        }
        return managerState(instance)
    }

    private fun increaseByteNum(manager: Any?): Any? = readFieldDeep(manager, "increaseByteNum")

    private fun managerState(manager: Any?): String {
        if (manager == null) return "null"
        val fields = listOf(
            "appType",
            "bikeBondType",
            "appBigVersion",
            "appSmartVersion",
            "bleCommBigVersion",
            "bleCommSmartVersion",
            "increaseByteNum"
        )
        return fields.joinToString(",") { name ->
            "$name=${readFieldDeep(manager, name)}"
        }
    }

    // ---------------------------------------------------------------------
    // tl 业务集中写入口 / 通知注册入口
    // ---------------------------------------------------------------------

    private fun hookTransportEntry() {
        val clazz = loadClass("tl")
        if (clazz == null) {
            missing("tl", "class")
            return
        }

        val writeMethods = matchingMethods(clazz, "s0", 6) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice" &&
                it.parameterTypes[3] == ByteArray::class.java &&
                it.parameterTypes[4] == Boolean::class.javaPrimitiveType
        }
        if (writeMethods.isEmpty()) {
            missing("tl.s0", "(BleDevice,String,String,byte[],boolean,Callback)V")
        }
        for (method in writeMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val data = (param.args.getOrNull(3) as? ByteArray)?.clone()
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|BUSINESS_WRITE",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to param.args.getOrNull(1),
                                    "uuid" to param.args.getOrNull(2),
                                    "split" to param.args.getOrNull(4),
                                    "len" to (data?.size ?: 0),
                                    "data" to data,
                                    "callbackType" to hookRuntimeClass(param.args.getOrNull(5)),
                                    "stack" to callStack(16)
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|BUSINESS_WRITE_RETURN",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to param.args.getOrNull(1),
                                    "uuid" to param.args.getOrNull(2),
                                    "throwable" to param.throwable?.javaClass?.name
                                )
                            )
                        }
                    }
                }
                installed(method, "business-write-entry")
            }
        }

        hookTransportNotifyEnable(clazz)
        hookTransportRead(clazz)
    }

    private fun hookTransportNotifyEnable(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "q0", 3) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice" &&
                it.returnType == Boolean::class.javaPrimitiveType
        }
        if (methods.isEmpty()) {
            missing("tl.q0", "(BleDevice,String,String)boolean")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "NOTIFY|ENABLE_BEFORE",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to param.args.getOrNull(1),
                                    "uuid" to param.args.getOrNull(2),
                                    "order" to "tl.q0"
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "NOTIFY|ENABLE_AFTER",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to param.args.getOrNull(1),
                                    "uuid" to param.args.getOrNull(2),
                                    "result" to param.result
                                )
                            )
                        }
                    }
                }
                installed(method, "notification-enable")
            }
        }
    }

    private fun hookTransportRead(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "Q", 4) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice"
        }
        if (methods.isEmpty()) {
            missing("tl.Q", "(BleDevice,String,String,Callback)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|BUSINESS_READ",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "service" to param.args.getOrNull(1),
                                    "uuid" to param.args.getOrNull(2),
                                    "callbackType" to hookRuntimeClass(param.args.getOrNull(3)),
                                    "stack" to callStack(12)
                                )
                            )
                        }
                    }
                }
                installed(method, "business-read-entry")
            }
        }
    }

    // ---------------------------------------------------------------------
    // wi 握手与通知解析器
    // ---------------------------------------------------------------------

    private fun hookHandshake() {
        val wiClass = loadClass("wi")
        if (wiClass == null) {
            missing("wi", "class")
        } else {
            hookWiMethod(wiClass, "S", 3, "HANDSHAKE|S")
            hookWiMethod(wiClass, "M", 5, "HANDSHAKE|M")
            hookWiOldHandshake(wiClass)
        }

        val gClass = loadClass("wi\$g")
        val oldWriteCallbackMethods = matchingMethods(gClass, "f", 3)
        if (oldWriteCallbackMethods.isEmpty()) {
            missing("wi\$g.f", "(int,int,byte[])V")
        }
        for (method in oldWriteCallbackMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val thisObject = param.thisObjectOrNull
                            val handshakeData = readFieldDeep(thisObject, "c") as? ByteArray
                            captureLog(
                                hookClass = HOOK,
                                event = "HANDSHAKE|OLD_WRITE_SUCCESS",
                                method = describeMethod(method),
                                mac = extractMac(readFieldDeep(thisObject, "e")),
                                runtimeClass = hookRuntimeClass(thisObject),
                                fields = arrayOf(
                                    "arg0" to param.args.getOrNull(0),
                                    "arg1" to param.args.getOrNull(1),
                                    "callbackData" to (param.args.getOrNull(2) as? ByteArray)?.clone(),
                                    "handshakeData" to handshakeData?.clone(),
                                    "len" to (handshakeData?.size ?: 0)
                                )
                            )
                        }
                    }
                }
                installed(method, "old-handshake-write-success")
            }
        }

        val dClass = loadClass("wi\$d")
        val parserMethods = matchingMethods(dClass, "e", 1) {
            it.parameterTypes[0] == ByteArray::class.java
        }
        if (parserMethods.isEmpty()) {
            missing("wi\$d.e", "(byte[])V")
        }
        for (method in parserMethods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val data = (param.args.getOrNull(0) as? ByteArray)?.clone()
                            val thisObject = param.thisObjectOrNull
                            captureLog(
                                hookClass = HOOK,
                                event = "BLE|WI_D_PARSER_INPUT",
                                method = describeMethod(method),
                                mac = extractMac(readFieldDeep(thisObject, "e")),
                                runtimeClass = hookRuntimeClass(thisObject),
                                fields = arrayOf(
                                    "len" to (data?.size ?: 0),
                                    "data" to data,
                                    "callbackType" to hookRuntimeClass(readFieldDeep(thisObject, "c")),
                                    "deviceType" to hookRuntimeClass(readFieldDeep(thisObject, "e"))
                                )
                            )
                        }
                    }
                }
                installed(method, "notification-parser-input")
            }
        }
    }

    private fun hookWiMethod(clazz: Class<*>, methodName: String, paramCount: Int, event: String) {
        val methods = matchingMethods(clazz, methodName, paramCount) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice"
        }
        if (methods.isEmpty()) {
            missing("wi.$methodName", "paramCount=$paramCount, first=BleDevice")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val detailFields = mutableListOf<Pair<String, Any?>>()
                            if (methodName == "M") {
                                detailFields += "command" to param.args.getOrNull(1)
                                detailFields += "param" to param.args.getOrNull(2)
                            } else {
                                detailFields += "arg1Type" to hookRuntimeClass(param.args.getOrNull(1))
                                detailFields += "arg2Type" to hookRuntimeClass(param.args.getOrNull(2))
                            }
                            detailFields += "args" to param.args.drop(3).joinToString(",") {
                                hookRuntimeClass(it)
                            }
                            detailFields += "stack" to callStack(14)
                            captureLog(
                                hookClass = HOOK,
                                event = event,
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = detailFields.toTypedArray()
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "$event|RETURN",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "return" to param.result,
                                    "throwable" to param.throwable?.javaClass?.name
                                )
                            )
                        }
                    }
                }
                installed(method, event)
            }
        }
    }

    private fun hookWiOldHandshake(clazz: Class<*>) {
        val methods = matchingMethods(clazz, "Z0", 5) {
            it.parameterTypes[0].name == "com.yadea.blecontrol.data.BleDevice" &&
                it.parameterTypes[1] == ByteArray::class.java &&
                it.parameterTypes[2] == Boolean::class.javaPrimitiveType
        }
        if (methods.isEmpty()) {
            missing("wi.Z0", "(BleDevice,byte[],boolean,Callback,Callback)V")
        }
        for (method in methods) {
            captureGuard(HOOK, describeMethod(method)) {
                method.createHook {
                    reloadKey(methodKey(method))
                    before { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            val input = (param.args.getOrNull(1) as? ByteArray)?.clone()
                            captureLog(
                                hookClass = HOOK,
                                event = "HANDSHAKE|OLD_INPUT",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "len" to (input?.size ?: 0),
                                    "random16" to input,
                                    "waitForAuth" to param.args.getOrNull(2),
                                    "arg3Type" to hookRuntimeClass(param.args.getOrNull(3)),
                                    "arg4Type" to hookRuntimeClass(param.args.getOrNull(4)),
                                    "stack" to callStack(14)
                                )
                            )
                        }
                    }
                    after { param ->
                        captureGuard(HOOK, describeMethod(method)) {
                            captureLog(
                                hookClass = HOOK,
                                event = "HANDSHAKE|OLD_RETURN",
                                method = describeMethod(method),
                                mac = extractMac(param.args.getOrNull(0)),
                                runtimeClass = hookRuntimeClass(param.thisObjectOrNull),
                                fields = arrayOf(
                                    "waitForAuth" to param.args.getOrNull(2),
                                    "throwable" to param.throwable?.javaClass?.name
                                )
                            )
                        }
                    }
                }
                installed(method, "old-handshake")
            }
        }
    }

    companion object {
        private const val HOOK = "YadeaBleBusinessHook"
    }
}
