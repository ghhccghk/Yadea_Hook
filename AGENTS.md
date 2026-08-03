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

- `MainHook.kt` — Xposed module entry. Extends `XposedModule`. Handles 360加固壳 and delegates to hook modules.
- `VehicleServiceHook.kt` — VehicleService hooks (蓝牙连接、车辆控制、状态监控).
- `MainActivity.kt` — Compose settings UI with `NavigationSuiteScaffold`.

## Key Libraries

| Library | Purpose |
|---------|---------|
| `ezhooktool` (v1.1.3) | Xposed hooking helper — use `EzXposed` for hook registration |
| `libxposed:api` (v102.0.0) | Core Xposed module API — `compileOnly` only, not bundled |

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
                VehicleServiceHook.initHooks(context)
            }
        }
    } ?: run {
        // 没有加固壳，直接初始化
        VehicleServiceHook.initHooks(null)
    }
}
```

Hook 模块的 `initHooks(context: Context?)` 必须使用传入的 Context 的 ClassLoader 来查找类。

## EzXposed API 使用规范

使用 EzXposed DSL API（`io.github.lingqiqi5211.ezhooktool.xposed.dsl`）：

```kotlin
// 查找类
val clazz = findClassIf {
    cacheKey("unique-key")
    classLoader(classLoader)  // 加固壳场景必须传入
    packageStartsWith("com.example")
    simpleName("ClassName")
    hasMethod { name("methodName") }
}

// 查找方法
val method = clazz.findMethod {
    name("methodName")
    paramCount(2)
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

## VehicleService 类结构

目标类: `com.yadea.smartmoto.vehicle.service.VehicleService` (Kotlin)

| 内部类 | 用途 |
|--------|------|
| `a` (Companion) | 静态方法、命令发送、单例管理 |
| `b` | BLE 连接回调 (onConnectStateChange, onNotifyDataCallBack) |
| `c` | BluetoothGattCallback |
| `d` | NFC 部件回调 |

关键方法：
- `E(PanelInfo, FaultInfo, TtpInfo)` — 处理电动车状态
- `G(ScooterStatusInfo, TtpInfo)` — 处理滑板车状态
- `I(Map)` — 核心命令分发

## Build Config Notes

- **AGP 9.2.1**, Kotlin 2.2.10, Gradle 9.4.1
- `compileSdk` uses the `release(36)` syntax with `minorApiLevel = 1` — non-standard, don't flatten to a plain int.
- Configuration cache enabled (`org.gradle.configuration-cache=true`).
- `local.properties` is gitignored and auto-generated — contains `sdk.dir` path. Don't commit it.
- Version catalog at `gradle/libs.versions.toml` — all dependency versions centralized there.
- R8 keep rules in `app/src/main/keepRules/rules.keep` (currently empty/boilerplate).

## UI

Compose with Material3 adaptive navigation suite. Theme in `ui/theme/`. Navigation destinations defined as `AppDestinations` enum in `MainActivity.kt`.

## Testing

Only placeholder tests exist (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`). No custom test fixtures or snapshot workflows.
