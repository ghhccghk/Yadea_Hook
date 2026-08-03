# VehicleService 完整分析报告

## 1. 类概览

| 属性 | 值 |
|------|-----|
| **完整类名** | `com.yadea.smartmoto.vehicle.service.VehicleService` |
| **源文件** | `VehicleService.kt` (Kotlin) |
| **总行数** | 2248 行 |
| **设计模式** | 单例模式 (Companion Object + Lazy) |
| **主要职责** | 蓝牙连接管理、车辆状态处理、控制命令分发 |

## 2. 类结构图

```
VehicleService
├── Companion (静态单例)
│   ├── instance: VehicleService (Lazy)
│   ├── 静态字段: p, q, r, s, t, u
│   └── 静态方法: b-y (命令发送)
├── 内部类
│   ├── b: BLE 连接回调
│   ├── c: BluetoothGattCallback
│   ├── d: NFC 部件回调
│   ├── e: 重连回调
│   └── f: 错误处理
└── 实例
    ├── 字段: a-n (各种管理器)
    └── 方法: B-S (业务逻辑)
```

## 3. 字段详解

### 3.1 静态字段 (Companion)

| 字段 | 类型 | 说明 |
|------|------|------|
| `o` | `a` | Companion 实例 |
| `p` | `int` | 连接状态标志 |
| `q` | `pe0` | BLE 配置参数 |
| `r` | `boolean` | 时间同步标志 |
| `s` | `String` | 连接追踪 ID |
| `t` | `long` | 连接时间戳 |
| `u` | `long` | 防抖时间戳 |
| `v` | `sw2<VehicleService>` | 单例实例 |

### 3.2 实例字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `a` | `qg` | 自行车命令控制器 |
| `b` | `bb5` | 滑板车命令控制器 |
| `c` | `hr` | 箱子命令控制器 |
| `d` | `ch` | 电动滑板车命令控制器 |
| `e` | `boolean` | 当前连接状态 |
| `f` | `ua2` | HUD 扫描管理器 |
| `g` | `HudProtocolManager` | HUD 协议管理器 |
| `h` | `BluetoothGattCharacteristic` | HUD 写特征 |
| `i` | `String` | 目标 MAC 地址 |
| `j` | `BleDevice` | 当前 BLE 设备 |
| `k` | `long` | 操作时间戳 |
| `l` | `c` | GATT 回调 |
| `m` | `b` | 连接状态回调 |
| `n` | `d` | NFC 回调 |

## 4. 方法详解

### 4.1 初始化方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `<init>` | `()` | 构造函数，初始化所有控制器 |
| `J` | `()V` | 注册蓝牙回调监听 |
| `R` | `()V` | 注销蓝牙回调监听 |

### 4.2 设备管理方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `B` | `()BleDevice` | 获取当前 BLE 设备 |
| `C` | `()String` | 获取目标 MAC 地址 |
| `D` | `()VehicleService` | 获取单例实例 |
| `P` | `(BleDevice)V` | 设置 BLE 设备 |
| `Q` | `(String)V` | 设置 MAC 地址 |

### 4.3 数据处理方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `E` | `(PanelInfo, FaultInfo, TtpInfo)V` | 处理电动车状态 |
| `F` | `(BoxStatusInfo)V` | 处理箱子状态 |
| `G` | `(ScooterStatusInfo, TtpInfo)V` | 处理滑板车状态 |
| `K` | `(BleNotifyEntity)V` | 上传错误信息 |
| `S` | `(BleNotifyEntity)V` | 处理 BLE 通知 |

### 4.4 控制命令方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `H` | `(CommandBean)V` | 检查网络状态 |
| `I` | `(Map)V` | 核心命令分发 |
| `L` | `(CommandBean)V` | 发送自行车命令 |
| `M` | `(CommandBean)V` | 发送滑板车命令(电量检查) |
| `N` | `(CommandBean)V` | 发送箱子命令 |
| `O` | `(CommandBean)V` | 发送滑板车命令(网络检查) |

### 4.5 Companion 方法

| 方法 | 签名 | 说明 |
|------|------|------|
| `b` | `(String)V` | 发送绑定连接事件 |
| `c` | `()V` | 发送销毁事件 |
| `d` | `()V` | 发送取消扫描事件 |
| `e` | `(String, boolean)V` | 发送连接事件 |
| `f` | `(String)V` | 发送箱子连接事件 |
| `g` | `(String)V` | 发送滑板车连接事件 |
| `h` | `(String)V` | 发送电动滑板车连接事件 |
| `i` | `(CommandBean)V` | 发送自行车控制事件 |
| `j` | `(CommandBean)V` | 发送箱子控制事件 |
| `k` | `(CommandBean)V` | 发送滑板车控制事件 |
| `l` | `(CommandBean, boolean)V` | 发送滑板车控制事件(带标志) |
| `m` | `(CommandBean)V` | 发送电动滑板车控制事件 |
| `n` | `(String)String` | MAC 地址转换 |
| `o` | `()V` | 发送断开连接事件 |
| `p` | `()int` | 获取连接状态 |
| `q` | `()VehicleService` | 获取实例 |
| `s` | `()pe0` | 获取 BLE 配置 |
| `t` | `(CommandBean, boolean)CommandBean` | 命令类型映射 |
| `u` | `()boolean` | 检查连接状态 |
| `v` | `()V` | 发送蓝牙禁用事件 |
| `w` | `(int)V` | 设置连接状态 |
| `x` | `(int)V` | 处理连接状态变化 |
| `y` | `(String)V` | 发送解除配对事件 |

## 5. 控制命令类别

### 5.1 命令类别映射

| category | 说明 | 处理方法 |
|----------|------|----------|
| `vehicle_binding_connected` | 绑定连接 | `I` → 重连逻辑 |
| `vehicle_bicycle_connect` | 自行车连接 | `I` → 扫描连接 |
| `vehicle_bicycle_control` | 自行车控制 | `I` → `L` |
| `vehicle_scooter_connect` | 滑板车连接 | `I` → 扫描连接 |
| `vehicle_scooter_control` | 滑板车控制 | `I` → `O` |
| `vehicle_box_connect` | 箱子连接 | `I` → 扫描连接 |
| `vehicle_box_control` | 箱子控制 | `I` → `N` |
| `bicycle_scooter_connect` | 电动滑板车连接 | `I` → 扫描连接 |
| `bicycle_scooter_control` | 电动滑板车控制 | `I` → `M` |
| `vehicle_cancel_scan` | 取消扫描 | `I` → 停止扫描 |
| `vehicle_disconnect` | 断开连接 | `I` → 断开并清理 |
| `vehicle_destroy` | 销毁服务 | `I` → 释放资源 |
| `vehicle_unpair` | 解除配对 | `I` → 解除绑定 |
| `vehicle_ble_disable` | 禁用蓝牙 | `I` → 关闭蓝牙 |
| `vehicle_hud_ble_scan` | HUD 蓝牙扫描 | `I` → 开始扫描 |
| `vehicle_hud_write` | HUD 写入导航 | `I` → 写入数据 |
| `vehicle_hud_end` | HUD 结束导航 | `I` → 结束导航 |

### 5.2 滑板车命令映射 (Companion.t)

| 原始命令 | 映射命令 | 参数 |
|----------|----------|------|
| `SCOOTER_LOCK` | `FORTIFY` | - |
| `SCOOTER_UNLOCK` | `RELEASE_FORTIFY` | - |
| `SCOOTER_GEAR_1` | `DRIVING_MODE_SWITCH` | 0 |
| `SCOOTER_GEAR_2` | `DRIVING_MODE_SWITCH` | 1 |
| `SCOOTER_GEAR_3` | `DRIVING_MODE_SWITCH` | 2 |
| `SCOOTER_GEAR_4` | `DRIVING_MODE_SWITCH` | 3 |
| `SCOOTER_GEAR_5` | `DRIVING_MODE_SWITCH` | 4 |
| `SCOOTER_GEAR_6` | `DRIVING_MODE_SWITCH` | 5 |
| `SCOOTER_GEAR_X` | `DRIVING_MODE_SWITCH` | 3 |
| `SCOOTER_OPEN_CRUISE_CONTROL` | `CRUISE_CONTROL_OPEN` | - |
| `SCOOTER_CLOSE_CRUISE_CONTROL` | `CRUISE_CONTROL_CLOSE` | - |
| `SCOOTER_ENERGY_RECOVERY_WEAK` | `BRAKE_RECOVER_LEVEL` | 1 |
| `SCOOTER_ENERGY_RECOVERY_MEDIUM` | `BRAKE_RECOVER_LEVEL` | 2 |
| `SCOOTER_ENERGY_RECOVERY_STRONG` | `BRAKE_RECOVER_LEVEL` | 3 |

## 6. 内部类详解

### 6.1 类 b (BLE 连接回调)

实现接口: `wj`

**方法:**
- `onConnectStateChange(BleDevice, int, int)` - 连接状态变化
- `onNotifyDataCallBack(BleNotifyEntity)` - 数据通知
- `onExternalNotifyDataCallBack(BleNotifyEntity)` - 外部数据通知
- `onPartsConnectStateChange(BleDevice, int, int)` - 部件连接状态

**状态码:**
- `0` = 断开
- `1` = 连接中
- `2` = 已连接

### 6.2 类 c (GATT 回调)

继承: `BluetoothGattCallback`

### 6.3 类 d (NFC 回调)

实现接口: `zj`

**方法:**
- `a(BleNotifyEntity)` - 处理 NFC 数据

### 6.4 类 e (重连回调)

实现接口: `bt1<rc6>`

**方法:**
- `invoke()` - 触发重连

### 6.5 类 f (错误处理)

继承: `gn5<Object>`

**方法:**
- `onSuccess(Object)` - 成功回调
- `onError(ApiException)` - 错误回调

## 7. 蓝牙 UUID

| UUID | 用途 |
|------|------|
| `0000b360-d6d8-c7ec-bdf0-eab1bfc6bcbc` | BLE 服务 UUID |
| `0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc` | BLE 写特征 UUID |
| `0000180a-0000-1000-8000-00805f9b34fb` | 设备信息服务 |
| `00002a28-0000-1000-8000-00805f9b34fb` | 固件版本特征 |
| `0000ffe3-0000-1000-8000-00805f9b34fb` | HUD 通知特征 |

## 8. 数据模型

### 8.1 VehicleStatusBean (电动车状态)

关键字段:
- `powerState` - 电源状态 (0=关, 1=开)
- `lockStatus` - 锁状态 (0=解锁, 1=锁定)
- `rideStatus` - 骑行状态
- `soc1`, `soc2`, `soc3` - 电量百分比
- `remMileage` - 续航里程
- `totalMileage` - 总里程
- `voltage` - 电压
- `chgStatus` - 充电状态
- `chargeTime` - 充电剩余时间

### 8.2 VehicleScooterStatusBean (滑板车状态)

关键字段:
- `gear` - 档位 (1-6)
- `battery` - 电量
- `lockCarState` - 锁车状态
- `cruiseControl` - 巡航状态
- `energyRecoveryState` - 能量回收状态
- `activateX` - X 模式状态
- `liftSpeedLimitMode` - 解除限速模式

### 8.3 VehicleBoxStatusBean (箱子状态)

关键字段:
- `speed` - 速度
- `batter` - 电量
- `power` - 功率
- `nowKm` - 当前里程
- `odo` - 总里程
- `lightColor` - 灯光颜色
- `lightType` - 灯光类型
- `find` - 查找状态
- `limit` - 限速状态

## 9. Hook 策略建议

### 9.1 数据监控

```kotlin
// 监控所有 BLE 通知数据
hookAfter("VehicleService\$b", "onNotifyDataCallBack") { param ->
    val data = param.args[0] as BleNotifyEntity
    // 处理 data.ttpInfo, data.scooterInfo, data.panelInfo 等
}
```

### 9.2 命令拦截

```kotlin
// 拦截控制命令
hookBefore("VehicleService\$a", "k") { param ->
    val commandBean = param.args[0]
    // 检查或修改命令
}
```

### 9.3 状态追踪

```kotlin
// 追踪连接状态
hookAfter("VehicleService\$b", "onConnectStateChange") { param ->
    val state = param.args[1] as Int
    val device = param.args[0]
    // 记录连接事件
}
```

### 9.4 数据修改

```kotlin
// 修改车辆状态数据
hookBefore("VehicleService", "E") { param ->
    val ttpInfo = param.args[2]
    // 修改 ttpInfo 字段值
}
```

## 10. 注意事项

1. **混淆**: 大部分类名和方法名已被混淆，需要根据上下文推断用途
2. **线程安全**: 使用 `LiveEventBus` 进行线程间通信，hook 时注意线程
3. **生命周期**: `VehicleService` 是单例，hook 一次即可持续生效
4. **权限**: 蓝牙操作需要相关权限，hook 时注意权限检查
5. **版本兼容**: 不同版本的 APP 可能有不同的混淆映射

## 11. 反编译输出位置

```
output/
└── sources/
    └── com/
        └── yadea/
            └── smartmoto/
                └── vehicle/
                    └── service/
                        └── VehicleService.java
```
