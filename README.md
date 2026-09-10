# YadeaHook

这是一个为 **雅迪智行** (`com.yadea.smartmoto`) 开发的 Android Xposed 模块，支持监控车辆状态、发送控制指令、BLE 设备连接以及超速警报等功能。

## 功能特性

### 🚗 车辆状态监控
- **实时数据** — 自动读取电压、电流、电池 SOC、车速、总里程（ODO）、锁车状态等 TtpInfo 字段。
- **数据广播** — 通过 JSON 广播实时输出车辆数据，方便外部应用（如 Widget、桌面组件）对接。
- **多车辆支持** — 支持电动车（PanelInfo/FaultInfo/TtpInfo）和滑板车（ScooterStatusInfo/TtpInfo）两种状态格式。

### 🎮 车辆控制
- **锁车/解锁** — 远程锁定和解锁滑板车。
- **档位切换** — 支持 SCOOTER_GEAR_1 ~ SCOOTER_GEAR_6 六个档位。
- **巡航控制** — 开启/关闭巡航模式。
- **能量回收** — 三档能量回收等级调节。
- **命令映射** — 自动映射命令类型（如 SCOOTER_LOCK → FORTIFY）。

### 📡 BLE 连接管理
- **连接状态监控** — 实时监控蓝牙连接过程、断开事件及通知回调。
- **设备扫描** — 扫描附近的 BLE 设备。
- **多协议支持** — 支持滑板车、自行车、网络三种连接方式。
- **认证捕获** — 捕获 BLE 认证序列（INIT/KEY/QUERY 等帧）用于调试。

### 🚨 超速警报
- **速度监控** — 实时监控车速，超过阈值时触发警报。
- **自定义音频** — 支持自定义警报音频文件。
- **可配置阈值** — 通过 UI 设置速度阈值（默认 25 km/h）。
- **冷却时间** — 防止频繁触发，支持配置冷却时间。

### 🔐 凭证提取
- **BLE Key 捕获** — 捕获雅迪 App 连接车辆时使用的蓝牙认证密钥。
- **Device Token 抓取** — 从 OkHttp 响应中提取设备认证令牌。
- **协议分析** — 通过 BleFrameClassifier 精确识别认证帧类型。

### 📱 设备控制（ DG-LAB 协议）
- **BLE 设备连接** — 连接支持 DG-LAB 协议的外部 BLE 设备。
- **强度控制** — 手动或自动调节设备强度（0-200）。
- **速度-强度映射** — 根据车速自动调整设备强度，支持自定义曲线。
- **电池电量读取** — 读取连接设备的电池电量。
- **V2/V3 协议** — 支持 DG-LAB 协议 V2 和 V3 版本。

### 🖥️ 内置仪表盘
- **Material3 UI** — 基于 Compose 的现代化界面。
- **三页面导航** — Home（日志和数据）、Device（设备控制）、Settings（设置）。
- **实时日志** — 显示 Hook 日志和完整的 TtpInfo 数据面板。
- **曲线编辑器** — 可视化编辑速度-强度映射曲线。

## 环境要求

- **设备**: 已 Root 且安装了 LSPosed 的设备，或使用 LSPatch 处理后的环境。
- **系统**: Android 9.0 (API 28) 及以上。
- **目标应用**: 雅迪智行 (`com.yadea.smartmoto`)。
- **蓝牙权限**: 需要 BLUETOOTH_SCAN、BLUETOOTH_CONNECT、ACCESS_FINE_LOCATION 权限（用于设备控制功能）。

## 安装步骤

1. 编译或下载模块 APK。
2. 在 **LSPosed 管理器**中安装并启用模块。
3. 勾选 **雅迪智行** 作为作用域。
4. 强制停止并重新启动雅迪智行 APP。
5. 授予蓝牙权限（首次使用设备控制功能时）。

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
  "tag": "更新时间：14:30:25",
  "time": "14:30:25",
  "key1": "电压",
  "value1": "74.3V",
  "key2": "电流",
  "value2": "12.5A",
  "key3": "功率",
  "value3": "928.8W",
  "key4": "电池电量",
  "value4": "85 %",
  "key5": "车速",
  "value5": "25.0km/h",
  "key6": "总里程",
  "value6": "1234.5km",
  "soc": "85",
  "parkingStatus": "0",
  "onOffStatus": "11"
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
| `SCOOTER_OPEN_CRUISE_CONTROL` | - | 开启巡航控制 |
| `SCOOTER_CLOSE_CRUISE_CONTROL` | - | 关闭巡航控制 |
| `SCOOTER_ENERGY_RECOVERY_1` ~ `3` | - | 能量回收等级 (1-3) |
| `disconnect` | - | 断开蓝牙连接 |
| `connect_scooter` | `mac` (String) | 连接指定 MAC 地址的滑板车 |
| `connect_bicycle` | `mac` (String) | 连接指定 MAC 地址的自行车 |

```kotlin
sendBroadcast(Intent("com.ghhccghk.yadeahook.CONTROL").apply {
    putExtra("command", "SCOOTER_LOCK")
})
```

### 4. 接收速度警报 (`SPEED_ALERT`)
**Action**: `com.ghhccghk.yadeahook.SPEED_ALERT`

```kotlin
val speed = intent.getFloatExtra("speed", 0f)
```

### 5. 接收 BLE 认证数据 (`AUTH`)
**Action**: `com.ghhccghk.yadeahook.AUTH`

```kotlin
val initHex = intent.getStringExtra("init")
val keyHex = intent.getStringExtra("key")
val seq = intent.getLongExtra("seq", 0)
val push = intent.getStringExtra("push")
val text = intent.getStringExtra("text")
```

## 技术细节

### 360 加固处理
目标应用使用了 360 加固。本模块通过 Hook `com.stub.StubApp.attachBaseContext` 来获取解密后的 `Context` 及其 `ClassLoader`，从而确保能正常加载目标类。

### Hook 架构
```
MainHook → VehicleServiceLoad.initHooks(context)
  → 进程检查: 只在 com.yadea.smartmoto 主进程初始化
  → BleConnectStateHook.init(classLoader, context)
  → VehicleControlHook.init(classLoader, context)
  → VehicleStatusHook.init(classLoader, context)
  → ScooterStatusHook.init(classLoader, context)
  → VehicleDataReadHook.init(classLoader, context)
  → VehicleServiceOnNotifyDataCallBackHook.init(classLoader, context)
  → BleAuthCaptureHook.init(classLoader, context)
  → CredentialExtractHook.init(classLoader, context)
  → SpeedAlertHook.init(classLoader, context)
```

### BLE 协议分析
- **BleFrameClassifier** — 基于协议指纹精确识别 BLE 帧类型（INIT、KEY、QUERY、START、STOP 等）。
- **CoyoteProtocol** — 支持 Coyote 设备协议 V2/V3，包括 B0 命令编码和 B1 响应解析。
- **SpeedStrengthMapper** — 车速-强度映射器，支持用户自定义曲线和线性插值。

### 数据流
1. **车辆数据** — Hook VehicleService 方法 → 提取 TtpInfo 字段 → VehicleStatusStore 广播。
2. **BLE 认证** — Hook BLE 写操作 → BleFrameClassifier 分类 → BleAuthStore 存储/广播。
3. **速度警报** — Hook 状态方法 → 提取车速 → SpeedAlertReceiver 播放音频。
4. **设备控制** — UI 操作 → BleManager 发送 B0 命令 → CoyoteProtocol 编码。


## 版本信息

- **当前版本**: 0.2.0
- **版本代码**: 2

## 构建项目

```bash
./gradlew assembleDebug          # 构建调试版 APK
./gradlew assembleRelease        # 构建发布版 APK
./gradlew test                   # 运行单元测试
./gradlew connectedAndroidTest   # 运行仪器化测试
```

- **Min SDK**: 28
- **Compile SDK**: 37 (Extension 1)
- **AGP**: 9.3.2
- **Kotlin**: 2.4.10
- **EzHookTool**: 1.2.2
- **libxposed API**: 102.0.0

## 项目结构

```
app/src/main/java/com/ghhccghk/yadeahook/
├── MainHook.kt                 # Xposed 模块入口
├── BaseHook.kt                 # 抽象基类，提供工具方法
├── VehicleServiceLoad.kt       # 核心协调器，管理 Hook 初始化
├── VehicleStatusStore.kt       # 全局车辆状态存储
├── VehicleController.kt        # 车辆控制命令发送器
├── BleAuthStore.kt             # BLE 认证序列存储
├── SpeedAlertReceiver.kt       # 速度警报广播接收器
├── StatusUpdateReceiver.kt     # 状态更新广播接收器
├── VehicleControlReceiver.kt   # 车辆控制广播接收器
├── MainActivity.kt             # Compose UI 入口
├── bluetooth/
│   ├── BleManager.kt           # BLE 连接管理器
│   ├── CoyoteProtocol.kt       # Coyote 协议实现
│   └── SpeedStrengthMapper.kt  # 车速-强度映射器
├── hooks/
│   ├── BleConnectStateHook.kt  # BLE 连接状态 Hook
│   ├── VehicleControlHook.kt   # Companion 方法监控 Hook
│   ├── VehicleStatusHook.kt    # 电动车状态 Hook
│   ├── ScooterStatusHook.kt    # 滑板车状态 Hook
│   ├── VehicleDataReadHook.kt  # 车辆数据读取 Hook
│   ├── BleAuthCaptureHook.kt   # BLE 认证捕获 Hook
│   ├── CredentialExtractHook.kt# 凭证提取 Hook
│   └── SpeedAlertHook.kt       # 速度警报 Hook
├── protocol/
│   └── BleFrameClassifier.kt   # BLE 帧分类器
├── provider/
│   └── HookLogger.kt           # 日志工具
└── ui/
    ├── LogScreen.kt            # 日志和数据面板界面
    ├── DeviceControlScreen.kt  # 设备控制界面
    ├── SettingsScreen.kt       # 设置界面
    ├── CurveEditor.kt          # 曲线编辑器
    ├── LogReceiver.kt          # 日志广播接收器
    └── theme/                  # 主题文件
```

## 许可证

本项目仅供学习和研究使用。请遵守当地法律法规，不得用于非法用途,注意DG-LAB 使用安全!!!!

