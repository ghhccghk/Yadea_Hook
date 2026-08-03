package com.ghhccghk.yadeahook

import io.github.lingqiqi5211.ezhooktool.core.findClassIf
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.loadClassFirst
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook


/**
 * VehicleService Hook 管理器
 * 负责 hook 雅迪智行 APP 的车辆服务相关功能
 */
object VehicleServiceHook {

    private const val TAG = "YadeaHook"

    // 缓存的类引用
    private var vehicleServiceClass: Class<*>? = null
    private var companionClass: Class<*>? = null
    private var bleCallbackClass: Class<*>? = null

    /**
     * 初始化所有 VehicleService 相关的 hooks
     */
    fun initHooks() {
        // 查找目标类
        findClasses()

        // 安装 hooks
        hookBleConnectState()
        hookVehicleControl()
        hookVehicleStatus()
        hookScooterStatus()
    }

    /**
     * 查找并缓存目标类
     */
    private fun findClasses() {
        // 查找 VehicleService 主类
        vehicleServiceClass = findClassIf {
            cacheKey("vehicle-service")
            packageStartsWith("com.yadea.smartmoto")
            simpleNameContains("VehicleService")
            hasMethod {
                nameContains("onControlCommand")
            }
        }

        // 查找 Companion 类 (静态方法持有者)
        companionClass = findClassIf {
            cacheKey("vehicle-service-companion")
            packageStartsWith("com.yadea.smartmoto")
            simpleName("a")
            loadClassFirst("VehicleService")
        }

        // 查找 BLE 连接回调类
        bleCallbackClass = findClassIf {
            cacheKey("vehicle-ble-callback")
            packageStartsWith("com.yadea.smartmoto")
            simpleName("b")
            loadClassFirst("VehicleService")
            hasMethod {
                name("onConnectStateChange")
                paramCount(3)
            }
        }
    }

    /**
     * Hook 蓝牙连接状态变化
     * 监控连接/断开事件
     */
    private fun hookBleConnectState() {
        val callbackClass = bleCallbackClass ?: return

        // Hook onConnectStateChange 方法
        // 参数: BleDevice, int (状态码), int (子状态)
        val method = callbackClass.findMethod {
            name("onConnectStateChange")
            paramCount(3)
        }

        method.createHook {
            after { param ->
                val state = param.argAs<Int>(1)
                val subState = param.argAs<Int>(2)
                val stateText = when (state) {
                    0 -> "断开"
                    1 -> "连接中"
                    2 -> "已连接"
                    else -> "未知($state)"
                }
                android.util.Log.d(TAG, "蓝牙状态变化: $stateText, 子状态: $subState")
            }
        }
    }

    /**
     * Hook 车辆控制命令
     * 拦截所有发送的控制命令
     */
    private fun hookVehicleControl() {
        val companion = companionClass ?: return

        // Hook 自行车控制命令 (方法 i)
        runCatching {
            val bicycleMethod = companion.findMethod {
                name("i")
                paramCount(1)
            }
            bicycleMethod.createHook {
                before { param ->
                    val commandBean = param.args[0]
                    val getCommandType = commandBean?.javaClass?.getMethod("getCommandType")
                    val commandType = getCommandType?.invoke(commandBean) as? String
                    android.util.Log.d(TAG, "自行车控制命令: $commandType")
                }
            }
        }

        // Hook 滑板车控制命令 (方法 k)
        runCatching {
            val scooterMethod = companion.findMethod {
                name("k")
                paramCount(1)
            }
            scooterMethod.createHook {
                before { param ->
                    val commandBean = param.args[0]
                    val getCommandType = commandBean?.javaClass?.getMethod("getCommandType")
                    val commandType = getCommandType?.invoke(commandBean) as? String
                    android.util.Log.d(TAG, "滑板车控制命令: $commandType")
                }
            }
        }
    }

    /**
     * Hook 车辆状态数据更新
     * 监控电动车电量、里程、锁状态等
     */
    private fun hookVehicleStatus() {
        val vehicleService = vehicleServiceClass ?: return

        // Hook E 方法 (PanelInfo, FaultInfo, TtpInfo 处理)
        runCatching {
            val statusMethod = vehicleService.findMethod {
                name("E")
                paramCount(3)
            }
            statusMethod.createHook {
                after { param ->
                    val ttpInfo = param.args[2] ?: return@after
                    runCatching {
                        // 反射获取 TtpInfo 字段
                        val soc = ttpInfo.javaClass.getField("pBElectricitySoc").getFloat(ttpInfo)
                        val mileage = ttpInfo.javaClass.getField("remainingMileage").getFloat(ttpInfo)
                        val odo = ttpInfo.javaClass.getField("currentOdoValue").getFloat(ttpInfo)

                        android.util.Log.d(
                            TAG,
                            "车辆状态 - 电量: ${soc}%, 续航: ${mileage}km, 总里程: ${odo}km"
                        )
                    }
                }
            }
        }
    }

    /**
     * Hook 滑板车状态数据
     * 监控滑板车档位、巡航、能量回收等
     */
    private fun hookScooterStatus() {
        val vehicleService = vehicleServiceClass ?: return

        // Hook G 方法 (ScooterStatusInfo, TtpInfo 处理)
        runCatching {
            val scooterMethod = vehicleService.findMethod {
                name("G")
                paramCount(2)
            }
            scooterMethod.createHook {
                after { param ->
                    val ttpInfo = param.args[1] ?: return@after
                    runCatching {
                        val gear = ttpInfo.javaClass.getField("drivingMemory").getInt(ttpInfo)
                        val cruise = ttpInfo.javaClass.getField("cruiseControl").getInt(ttpInfo)
                        val energyRecovery = ttpInfo.javaClass.getField("brakeModePowerRecoverGearStatus").getInt(ttpInfo)
                        val lockState = ttpInfo.javaClass.getField("parkingStatus").getInt(ttpInfo)

                        val gearText = if (gear >= 0) "${gear + 1}档" else "未知"
                        val cruiseText = when (cruise) {
                            0 -> "关闭"
                            1 -> "开启"
                            else -> "未知"
                        }
                        val lockText = when (lockState) {
                            0 -> "未锁"
                            1 -> "已锁"
                            else -> "未知"
                        }

                        android.util.Log.d(
                            TAG,
                            "滑板车状态 - 档位: $gearText, 巡航: $cruiseText, 锁车: $lockText, 能量回收: $energyRecovery"
                        )
                    }
                }
            }
        }
    }
}
