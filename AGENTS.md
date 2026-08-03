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

Single module `:app`. Two entry points:

- `MainHook.kt` — Xposed module entry. Extends `XposedModule`. Add hooks inside `initHooks()`.
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
