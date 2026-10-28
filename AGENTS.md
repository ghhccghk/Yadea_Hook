# YadeaHook

Android Xposed module (LSPosed/libxposed API v102) targeting **Yadea SmartMoto** (`com.yadea.smartmoto`).

## Build & Run

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

## Project Structure

Single module `:app`. Source files under `app/src/main/java/com/ghhccghk/yadeahook/`:

- `MainHook.kt` — Xposed module entry. Extends `XposedModule`. Handles 360加固壳 and delegates to `VehicleServiceLoad`.
- `BaseHook.kt` — 抽象基类，提供 `safeHook()`、`logHook()`、`getFieldValue()`、`dumpFields()`、`parseTtpInfo()` 等工具方法。
- `VehicleServiceLoad.kt` — 协调器，管理所有 hook 的注册和类查找。
- `VehicleStatusStore.kt` — 全局车辆状态存储，数据变化时通过广播发送 JSON。
- `VehicleController.kt` — 车辆控制命令发送器，反射调用 companion 方法。
- `hooks/BleConnectStateHook.kt` — BLE 连接状态 hook。
- `hooks/VehicleControlHook.kt` — Companion 方法监控 hook。
- `hooks/VehicleStatusHook.kt` — 电动车状态 hook（TtpInfo 字段读取）。
- `hooks/ScooterStatusHook.kt` — 滑板车状态 hook（TtpInfo 字段读取）。
- `hooks/ScooterStatusInfoHook.kt` — 滑板车状态 hook（精确匹配 `G(ScooterStatusInfo, TtpInfo)` 方法）。
- `provider/HookLogger.kt` — 日志工具，通过广播发送 hook 事件。
- `ui/LogScreen.kt` — Compose 日志界面，显示 hook 日志和 TtpInfo 数据面板。
- `ui/LogReceiver.kt` — 日志广播接收器。
- `MainActivity.kt` — Compose UI 入口，`NavigationSuiteScaffold` 导航。
- `hooks/NaviLyricHook.kt` — 在导航 Activity 生命周期内挂载/卸载投屏歌词 ComposeView。
- `hooks/NaviScreenCaptureHook.kt` — 拦截百度/高德投屏 Bitmap，并在发送前绘制歌词叠加。
- `lyrics/LyricOverlayController.kt` — 接收 SuperLyricApi 数据、管理屏幕外 ComposeView、同步绘制到 Bitmap。
- `lyrics/LyricOverlayUi.kt` — 顶部歌词布局，优先逐字卡拉 OK，缺少逐字数据时回退普通同步行。
- `lyrics/ComposeOwners.kt` — Hook 进程内 ComposeView 所需的 Lifecycle/SavedState/ViewModelStore owners。
- `lyrics/MusicSource.kt` — 当前音乐软件名称与图标。

## Key Libraries

| Library | Purpose |
|---------|---------|
| `ezhooktool` (v1.2.2) | Xposed hooking helper — use `EzXposed` for hook registration |
| `libxposed:api` (v102.0.0) | Core Xposed module API — `compileOnly` only, not bundled |
| `SuperLyricApi` (v3.7) | 从 SuperLyric 服务接收实时歌词、逐字时间、翻译和播放进度 |
| `lyrics-ui` / `lyrics-core` (v1.0.19 / v0.4.5) | Accompanist Lyrics 的 `KaraokeLineText`、`SyncedLineText` 和歌词模型 |
| `hiddenapibypass` (v6.1) | SuperLyric/Compose 相关隐藏 API 兼容 |

## Xposed Module Conventions

- `MainHook.onModuleLoaded()` calls `EzXposed.initOnModuleLoaded()` then schedules `initHooks()` via `onTargetReady`.
- `onPackageLoaded` and `onPackageReady` guard on `param.isFirstPackage` AND `param.packageName == TargetApp`.
- Hot-reload is supported via `onHotReloading` / `onHotReloaded` — delegates to `EzXposed.handleHotReloading*`.
- Target app constant: `private const val TargetApp = "com.yadea.smartmoto"` at top of `MainHook.kt`.

## 360加固壳处理

目标 APP 使用 360加固保护。需要在 `initHooks()` 中 hook `com.stub.StubApp.attachBaseContext` 获取解密后的 Context：

```kotlin
private fun initHooks() {
    loadClassOrNull("com.stub.StubApp")?.let { stubClass ->
        stubClass.findMethod {
            name("attachBaseContext")
            paramCount(1)
        }.createHook {
            after { param ->
                val context = param.args[0] as Context
                VehicleServiceLoad.initHooks(context)
            }
        }
    } ?: run {
        VehicleServiceLoad.initHooks(context = appContext)
    }
}
```

Hook 模块的 `initHooks(context: Context)` 必须使用传入的 Context 的 ClassLoader 来查找类。

## 投屏导航歌词叠加

投屏歌词链路：

```
MainHook → VehicleServiceLoad.initHooks(context)
  → NaviLyricHook: 导航 Activity onResume/onDestroy
  → LyricOverlayController: 注册 ISuperLyricReceiver，挂载屏幕外 ComposeView
  → NaviScreenCaptureHook: 拦截投屏 Bitmap
  → LyricOverlayController.draw(canvas, bitmap): 在发送给仪表前叠加
```

### 歌词布局规则

- `SuperLyricLine.words` 存在且时间有效时，转换成 `KaraokeLine.MainKaraokeLine`，使用 `KaraokeLineText` 逐字高亮。
- 没有逐字数据、words 为空或时间无效时，使用 `SyncedLineText`。
- 不使用仓库内的简化手绘歌词行；布局与逐字渲染以依赖中的 Accompanist Lyrics 组件为准。
- SuperLyric 只在切行时下发增量包，`LyricOverlayUi.kt` 以当前 `SuperLyricData.position` 为锚点，结合 `SystemClock.elapsedRealtime()` 每 33ms 插值行内进度。
- 翻译和来源行仍在同一顶部歌词条内；来源显示为图标在左、应用名在右。

### Bitmap 约束

- Compose 画布在首次投屏时调整为与目标 Bitmap 完全相同的宽高，坐标语义为 `1.dp = 1.px`、`1.sp = 1.px × fontScale`。
- 目标 Bitmap 可能不可变；`NaviScreenCaptureHook` 会复制为可变的 `ARGB_8888` Bitmap，再把叠加结果回写到 Hook 参数。
- 使用弱引用集合避免同一帧经过多个出口时重复叠加。
- 当前覆盖出口包括百度 `captureViewToBitmap(View)`、WiFi/TFT 发送链路、图片压缩入口和百度放大图回调。
- `lyrics-ui` 声明 minSdk 29，模块自身 minSdk 28；Android 10 以下跳过 Compose 歌词条。

## Hook 架构

```
MainHook → VehicleServiceLoad.initHooks(context)
  → 进程检查: 只在 com.yadea.smartmoto 主进程初始化
  → BleConnectStateHook.init(classLoader, context)
  → VehicleControlHook.init(classLoader, context)
  → VehicleStatusHook.init(classLoader, context)
  → ScooterStatusHook.init(classLoader, context)
  → ScooterStatusInfoHook.init(classLoader, context)
```

每个 hook 继承 `BaseHook`，实现 `init(classLoader, context)`。
类查找使用 `loadClassOrNull()` 按全限定名直接加载（360加固壳下 `findClassIf` 的 `simpleName` 搜索不可用）。

## VehicleService 类结构

目标类: `com.yadea.smartmoto.vehicle.service.VehicleService` (Kotlin)

| 内部类 | 用途 |
|--------|------|
| `a` (Companion) | 静态方法、命令发送、单例管理。实例通过 `VehicleService.o` 静态字段获取 |
| `b` | BLE 连接回调 (onConnectStateChange, onNotifyDataCallBack) |
| `c` | BluetoothGattCallback |
| `d` | NFC 部件回调 |

### Companion (VehicleService$a) 关键方法

| 方法 | 参数 | 用途 |
|------|------|------|
| `i(CommandBean)` | CommandBean | 自行车/网络控制（直接发送，不做命令映射） |
| `k(CommandBean)` | CommandBean | 滑板车/BLE 控制（调用 `t()` 做命令映射后发送） |
| `t(CommandBean, boolean)` | CommandBean, boolean | 命令类型映射：SCOOTER_LOCK→FORTIFY, SCOOTER_UNLOCK→RELEASE_FORTIFY 等 |
| `g(String)` | mac | 滑板车连接 |
| `e(String, boolean)` | mac, flag | 自行车连接 |
| `o()` | 无 | 断开连接 |
| `d()` | 无 | 取消扫描 |

### VehicleService 实例方法

| 方法 | 签名 | 用途 |
|------|------|------|
| `E` | `(PanelInfo, FaultInfo, TtpInfo)V` | 处理电动车状态 |
| `G` | `(ScooterStatusInfo, TtpInfo)V` | 处理滑板车状态 |

注意：EzXposed 的 `param.args` 包含 `this` 引用作为 `args[0]`，所以 `args[1]` 才是第一个参数。

### TtpInfo (com.yadea.blecontrol.entity.TtpInfo)

包含所有车辆数据的实体类。字段直接是数据值（如 `busbarVoltage`、`currentOdoValue`），不是字符串。
通过 `BleNotifyEntity.ttpInfo` 获取 TtpInfo 对象，再通过 `getFieldValue()` 读取字段。

**关键字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `parkingStatus` | int | 锁车状态：0=已锁, 1=未锁 |
| `onOffStatus` | int | 开机状态（11=开机） |
| `totalBatteryVoltage` | float | 总电池电压 |
| `totalBatteryElectricity` | float | 总电池电流 |
| `pBElectricitySoc` | float | 电池 SOC 百分比 |
| `currentOdoValue` | float | ODO 当前值 |
| `currentSpeedValue` | float | 车速当前值 |
| `remainingMileage` | float | 剩余里程 |
| `maximumSpeedCurrent` | int | 最高车速当前值 |
| `drivingMemory` | int | 档位记忆 |
| `cruiseControl` | int | 巡航状态 |

### CommandBean (com.yadea.smartmoto.vehicle.bean.CommandBean)

控制命令载体。构造函数：`CommandBean(String commandType)`。
发送前需调用 `t()` 做命令映射（如 `SCOOTER_LOCK` → `FORTIFY`）。

**滑板车命令映射 (Companion.t)**

| 原始命令 | 映射命令 | 参数 |
|----------|----------|------|
| `SCOOTER_LOCK` | `FORTIFY` | - |
| `SCOOTER_UNLOCK` | `RELEASE_FORTIFY` | - |
| `SCOOTER_GEAR_1` ~ `SCOOTER_GEAR_6` | `DRIVING_MODE_SWITCH` | 0~5 |
| `SCOOTER_OPEN_CRUISE_CONTROL` | `CRUISE_CONTROL_OPEN` | - |
| `SCOOTER_CLOSE_CRUISE_CONTROL` | `CRUISE_CONTROL_CLOSE` | - |
| `SCOOTER_ENERGY_RECOVERY_*` | `BRAKE_RECOVER_LEVEL` | 1~3 |

**控制方式：** `k()` 内部调用 `t()` 做命令映射，直接传入原始命令即可。
`i()` 不做映射，用于自行车/网络控制。

## VehicleStatusStore 广播

数据变化时发送广播，JSON 格式：
- Action: `com.ghhccghk.yadeahook.STATUS`
- Extra key: `data` → JSON 字符串
- JSON 字段：title, tag, time, key1/value1 ~ key6/value6, parkingStatus, onOffStatus

外部 app 通过 `registerReceiver` 监听即可获取车辆数据。

## EzXposed API 使用规范

使用 EzXposed DSL API（`io.github.lingqiqi5211.ezhooktool.xposed.dsl`）：

```kotlin
// 查找方法
val method = clazz.findMethod {
    name("methodName")
    paramCount(2)
    voidReturnType()
}

// 批量查找方法
val methods = clazz.findAllMethods {
    paramCount(1)
    voidReturnType()
}

// 创建 hook
method.createHook {
    before { param -> /* ... */ }
    after { param -> /* ... */ }
}
```

**不要使用**：
- `.isNotNull {}` — 不是 EzXposed API，使用 `?.let {}` 或 `?: run {}`
- `methodFinder()` — 旧 Xposed API，使用 `findMethod {}` DSL
- `XposedHelpers.findAndHookMethod()` — 旧 API，使用 EzXposed DSL
- `findClassIf` + `simpleName` — 360加固壳下不可用，使用 `loadClassOrNull()` 直接加载

## Build Config Notes

- **AGP 9.4.1**, Kotlin 2.4.10, Gradle 9.7.1；daemon JVM 为 Java 21
- `compileSdk` uses the `release(37)` syntax with `minorApiLevel = 1` — non-standard, don't flatten to a plain int.
- Configuration cache enabled (`org.gradle.configuration-cache=true`).
- `local.properties` is gitignored and auto-generated — contains `sdk.dir` path. Don't commit it.
- Version catalog at `gradle/libs.versions.toml` — all dependency versions centralized there.
- R8 keep rules in `app/src/main/keepRules/rules.keep`；保留 SuperLyricApi 并忽略 `android.os.ServiceManager`。
- JitPack 已在 `settings.gradle.kts` 配置，供 SuperLyricApi 使用。

## UI

Compose with Material3 adaptive navigation suite. Theme in `ui/theme/`. Navigation destinations defined as `AppDestinations` enum in `MainActivity.kt`.

Home tab shows:
- 车辆控制按钮（锁车、解锁、档位切换等）
- TtpInfo 全量数据面板（可展开、可复制）
- Hook 日志列表（实时更新）

投屏歌词不属于模块主界面：它只在目标 App 的导航 Activity 存活时挂载屏幕外 ComposeView，并绘制到投屏 Bitmap。

## Testing

Only placeholder tests exist (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`). No custom test fixtures or snapshot workflows.

修改投屏歌词后至少运行：

```bash
./gradlew compileDebugKotlin
./gradlew assembleDebug
```

真机验证需要重新投屏，并检查 logcat 中的 `YadeaHook-Lyric` 和 `NaviCapture` 日志。
