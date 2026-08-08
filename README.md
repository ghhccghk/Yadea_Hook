# YadeaHook

Android Xposed 模块，用于监控和控制雅迪智行（`com.yadea.smartmoto`）电动车辆。

## 功能特性

- **车辆数据监控** — 实时读取电压、电流、电量、车速、里程、锁车状态等
- **车辆控制** — 锁车/解锁、档位切换、启动/停止
- **BLE 连接状态** — 监控蓝牙连接/断开事件
- **广播数据输出** — 数据变化时通过广播发送 JSON，供外部 APP/Widget 接收
- **日志面板** — APP 内实时显示 hook 日志和 TtpInfo 全量数据

## 环境要求

- Android 9+ (API 28)
- LSPosed / LSPatch
- 目标 APP：雅迪智行 `com.yadea.smartmoto`

## 安装

1. 编译或下载 APK
2. 通过 LSPosed 安装并勾选 `com.yadea.smartmoto` 作用域
3. 重启目标 APP 或重启手机

## 使用

### 查看日志
打开 YadeaHook APP，Home 页面显示：
- **车辆控制按钮** — 锁车、解锁、档位切换、断开等
- **TtpInfo 数据面板** — 可展开查看所有字段，支持复制
- **Hook 日志** — 实时显示 hook 事件和车辆数据

### 广播接口

#### 接收车辆数据 (`STATUS`)
```kotlin
// 注册接收器
val filter = IntentFilter("com.ghhccghk.yadeahook.STATUS")
registerReceiver(receiver, filter)

// onReceive 中获取 JSON
val json = intent.getStringExtra("data")
```

JSON 格式：
```json
{
  "title": "雅迪车辆信息",
  "time": "14:30:25",
  "key1": "电压", "value1": "74.3V",
  "key2": "电流", "value2": "5153.4A",
  "key3": "功率", "value3": "382690.6W",
  "key4": "剩余里程", "value4": "--",
  "key5": "车速", "value5": "0.0km/h",
  "key6": "总里程", "value6": "37.2km",
  "soc": "54",
  "parkingStatus": "0",
  "onOffStatus": "11"
}
```

#### 请求数据更新 (`UPDATE`)
```kotlin
sendBroadcast(Intent("com.ghhccghk.yadeahook.UPDATE"))
```

#### 发送控制命令 (`CONTROL`)
```kotlin
sendBroadcast(Intent("com.ghhccghk.yadeahook.CONTROL").apply {
    putExtra("command", "SCOOTER_LOCK")   // 锁车
    // putExtra("command", "SCOOTER_UNLOCK") // 解锁
    // putExtra("command", "SCOOTER_GEAR_1") // 1档
    // putExtra("command", "disconnect")      // 断开
    // putExtra("command", "cancel_scan")     // 取消扫描
})
```

## 构建

```bash
./gradlew assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

## 技术栈

- Kotlin + Jetpack Compose (Material3)
- EzXposed v1.1.3 (Xposed hook DSL)
- libxposed API v102
- AGP 9.2.1, Kotlin 2.4.10, Gradle 9.4.1

## 项目结构

```
app/src/main/java/com/ghhccghk/yadeahook/
├── MainHook.kt                 # Xposed 模块入口
├── BaseHook.kt                 # Hook 抽象基类
├── VehicleServiceLoad.kt       # Hook 协调器
├── VehicleStatusStore.kt       # 车辆状态存储 + 广播
├── VehicleController.kt        # 车辆控制命令发送
├── StatusUpdateReceiver.kt     # UPDATE 广播接收器
├── hooks/
│   ├── BleConnectStateHook.kt  # BLE 连接状态
│   ├── VehicleControlHook.kt   # 控制命令监控
│   ├── VehicleDataReadHook.kt  # VehicleDataManager 数据读取
│   ├── VehicleStatusHook.kt    # 电动车状态
│   ├── ScooterStatusHook.kt    # 滑板车状态
│   └── ScooterStatusInfoHook.kt # TtpInfo 全量数据
├── provider/
│   └── HookLogger.kt           # 日志工具
└── ui/
    ├── LogScreen.kt            # 日志 + 控制界面
    └── LogReceiver.kt          # 日志广播接收器
```
