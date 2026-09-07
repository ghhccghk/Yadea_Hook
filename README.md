# YadeaHook

这是一个为 **雅迪智行** (`com.yadea.smartmoto`) 开发的 Android Xposed 模块，支持监控车辆状态并发送控制指令。

## 功能特性

- **实时监控** — 自动读取电压、电流、电池 SOC、车速、总里程（ODO）以及锁车状态。
- **车辆控制** — 支持锁车/解锁、档位切换、巡航控制等操作。
- **BLE 连接状态** — 监控蓝牙连接过程、断开事件及通知回调。
- **广播接口 API** — 通过 JSON 广播将车辆数据实时输出，方便外部应用（如 Widget、桌面组件）对接。
- **内置仪表盘** — 基于 Compose 的 UI 界面，实时显示 Hook 日志和完整的 `TtpInfo` 数据面板。

## 环境要求

- **设备**: 已 Root 且安装了 LSPosed 的设备，或使用 LSPatch 处理后的环境。
- **系统**: Android 9.0 (API 28) 及以上。
- **目标应用**: 雅迪智行 (`com.yadea.smartmoto`)。

## 安装步骤

1. 编译或下载模块 APK。
2. 在 **LSPosed 管理器**中安装并启用模块。
3. 勾选 **雅迪智行** 作为作用域。
4. 强制停止并重新启动雅迪智行 APP。

## 广播接口说明

### 1. 接收车辆状态 (`STATUS`)
**Action**: `com.ghhccghk.yadeahook.STATUS`

```kotlin
// 注册接收器
val filter = IntentFilter("com.ghhccghk.yadeahook.STATUS")
registerReceiver(receiver, filter)

// 在 onReceive 中获取数据
val jsonData = intent.getStringExtra("data")
```

**JSON 示例数据:**
```json
{
  "title": "雅迪车辆信息",
  "time": "14:30:25",
  "soc": "85",
  "parkingStatus": "0",
  "onOffStatus": "11",
  "key1": "电压", "value1": "74.3V",
  "key5": "车速", "value5": "0.0km/h",
  "currentOdoValue": "123.4",
  "remainingMileage": "45.0"
}
```

### 2. 手动请求更新 (`UPDATE`)
**Action**: `com.ghhccghk.yadeahook.UPDATE`

```kotlin
sendBroadcast(Intent("com.ghhccghk.yadeahook.UPDATE"))
```

### 3. 发送控制命令 (`CONTROL`)
**Action**: `com.ghhccghk.yadeahook.CONTROL`

| 命令 (command) | 额外参数 (param) | 说明 |
|----------------|-----------------|------|
| `SCOOTER_LOCK` | - | 锁定滑板车 |
| `SCOOTER_UNLOCK` | - | 解锁滑板车 |
| `SCOOTER_GEAR_1` ~ `6` | - | 切换档位 (对应 0-5) |
| `disconnect` | - | 断开蓝牙连接 |
| `connect_scooter` | `mac` (String) | 连接指定 MAC 地址的车辆 |

```kotlin
sendBroadcast(Intent("com.ghhccghk.yadeahook.CONTROL").apply {
    putExtra("command", "SCOOTER_LOCK")
})
```

## 技术细节

### 360 加固处理
目标应用使用了 360 加固。本模块通过 Hook `com.stub.StubApp.attachBaseContext` 来获取解密后的 `Context` 及其 `ClassLoader`，从而确保能正常加载目标类。

### Hook 架构
- `MainHook`: 模块入口，负责加固壳处理和分发。
- `VehicleServiceLoad`: 核心协调器，管理各功能组件的初始化。
- `EzXposed`: 使用 DSL 方式简化 Xposed Hook 开发。

## 构建项目

```bash
./gradlew assembleDebug          # 构建调试版 APK
./gradlew test                   # 运行单元测试
```

- **Min SDK**: 28
- **Compile SDK**: 37 (Extension 1)
- **AGP**: 9.2.1
- **Kotlin**: 2.4.10
