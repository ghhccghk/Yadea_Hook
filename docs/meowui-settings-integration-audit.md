# MeowUI 远程设置同步接入审计报告（第一阶段 · 只读审计）

> 状态：审计完成，等待审查与批准。未修改任何业务代码、Gradle 配置、Manifest、资源或设置数据。
>
> 审计日期：2026-10-31（Asia/Taipei）
>
> 本报告只执行《实施规范》第三章规定的只读审计，不进入实现阶段。

## 0. 结论摘要

1. MeowUI 源码目录 `F:\android app code\MeowUI` 存在，且与 YadeaHook 同级，`includeBuild("../MeowUI")` 的相对路径成立。
2. MeowUI 的公开 API 均已在真实源码中证实，与计划书描述基本一致，但有两个纠正：
   - 计划中的 `MeowXposedPreferenceHost` 是 `@Composable` 函数（不是 Activity/宿主类），真实签名见 §2。
   - 设置项 `slider` 只支持 `PreferenceKey<Float>`，没有 Int 键滑条；偏移量应使用 `Float` 键。
3. MeowUI 0.2.0-rc01 **只支持源码级接入**（Maven 未发布、源码模式强制），必须走 composite build，且要求宿主对齐工具链：Gradle 9.8.1、JDK 25（字节码目标 JVM 21）、AGP 9.4.1、Kotlin 2.4.21。
4. 当前 YadeaHook 工具链为 Gradle 9.7.1、daemon JVM 21（`gradle/gradle-daemon-jvm.properties` 的 `toolchainVersion=21`，shell `java` 为 17）、Kotlin 2.4.10、Compose BOM 2026.06.01 + material3 1.4.0。与 MeowUI 存在 **Gradle / JDK / Kotlin / Compose 四个需要对齐的点**，其中 JDK 与 Compose 是主要风险。
5. 十个目标设置当前全部是硬编码常量，**没有任何旧 SharedPreferences 可迁移**；现有 `SharedPreferences`（速度警报、曲线、音频）都不在第一阶段范围内，应保持不动。
6. 计划书列出的部分默认值与原代码不符，必须按原代码修正（详见 §5.1）。
7. `overlay_background_alpha` 与 `overlay_width_ratio` 在当前代码中**不存在对应实现**，属于新语义，不是迁移，需要单独决策默认值以保持现有视觉不变。
8. 上游 MeowUI 明确声明“独立外部 Xposed 宿主仍待验证”，本工程将成为较早的外部 Xposed 宿主，风险需显式接受。

结论状态标记约定：**已证实**（有源码/配置可重复验证）、**待验证**（有依据但证据不足）、**未确认**（当前无法确定）、**不支持**（已由源码确认不支持）。

---

## 1. 项目实际环境与依赖版本

### 1.1 工作区状态

- 分支：`master`，HEAD `7f2e727016f5abf3b8abf5228f00d45946aec7ba`，跟踪 `origin/master`，与远端同步（`+0 -0`）。
- 已修改（未暂存）的受跟踪文件：
  - `AGENTS.md`
  - `app/src/main/java/com/ghhccghk/yadeahook/lyrics/LyricOverlayController.kt`
  - `app/src/main/java/com/ghhccghk/yadeahook/lyrics/LyricOverlayUi.kt`
- 未跟踪文件较多，包括 `docs/` 下大量文档、`temp_smali/`、`smali.zip`、`classes.dex`、`hs_err_pid17704.log`、`nul`、`1.log`、`app/debug/`、`app/release/`、`output/` 等。这些属于用户或其他代理的既有内容，本次审计未读取写入、未清理。

> 注意：本审计对歌词叠加的分析基于上述两个 `.kt` 文件的**工作区版本**（即当前未提交的修改版本），而非 `HEAD`。

### 1.2 YadeaHook 构建配置

来源文件：

- `settings.gradle.kts`
- `build.gradle.kts`（根）
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/gradle-daemon-jvm.properties`
- `app/src/main/AndroidManifest.xml`

| 项目 | 当前值 | 证据 |
| --- | --- | --- |
| Gradle Wrapper | 9.7.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle daemon JVM 标准 | 21 | `gradle/gradle-daemon-jvm.properties` `toolchainVersion=21` |
| shell `java` / `JAVA_HOME` | JDK 17.0.20.1（Microsoft） | `java -version`；`JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\` |
| AGP | 9.4.1 | `gradle/libs.versions.toml` `agp = "9.4.1"` |
| Kotlin（及 compose 插件） | 2.4.10 | `gradle/libs.versions.toml` `kotlin = "2.4.10"` |
| Compose BOM | 2026.06.01 | `gradle/libs.versions.toml` |
| material3 | 1.4.0 | `gradle/libs.versions.toml` `material3 = "1.4.0"` |
| libxposed API | 102.0.0（`compileOnly`） | `gradle/libs.versions.toml`；`app/build.gradle.kts` |
| ezHookTool | 1.2.2（版本目录）/ 实现坐标 `ezhooktool-*` | 版本目录；`app/build.gradle.kts` 中另有一处本地变量 `ezHookToolVersion = "1.1.3"`（未被依赖引用，属遗留） |
| SuperLyricApi | 3.7（JitPack） | 版本目录 |
| lyrics-ui / lyrics-core | 1.0.19 / 0.4.5 | 版本目录 |
| hiddenapibypass | 6.1 | 版本目录 |
| minSdk / targetSdk / compileSdk | 28 / 37 / `release(37){minorApiLevel=1}` | `app/build.gradle.kts` |
| Java source/target | 11 | `app/build.gradle.kts` |
| 依赖仓库 | google / mavenCentral / jitpack，`RepositoriesMode.FAIL_ON_PROJECT_REPOS` | `settings.gradle.kts` |
| configuration cache | 开启 | `gradle.properties` |

### 1.3 MeowUI 构建配置

来源文件（`F:\android app code\MeowUI`）：

- `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`
- `gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`
- `.java-version`、`.gitmodules`、`AGENTS.md`、`README.md`

| 项目 | MeowUI 要求 | 证据 |
| --- | --- | --- |
| Gradle Wrapper | 9.8.1 | `gradle/wrapper/gradle-wrapper.properties` |
| JDK | 25（字节码目标 JVM 21） | `.java-version` = `25`；`AGENTS.md`；`README.md`“工具链对齐” |
| AGP | 9.4.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.4.21 | `gradle/libs.versions.toml` |
| Compose（androidx） | 1.12.1 声明，Android 实际解析 1.13.0-alpha02 | 版本目录注释 |
| Compose Multiplatform | 1.12.2；CMP Material3 1.13.0-alpha01；Android material3 1.5.0-beta01 | 版本目录 |
| Miuix | `0.9.4`（版本目录），源码模式固定子模块提交 `8972f941` | 版本目录 + `gradle.properties` |
| libxposed api / service | 102.0.0 / 102.0.0 | 版本目录 |
| minSdk / compileSdk | 26 / 37 | 版本目录 |
| JVM 目标 | 21 | `meowui-xposed/build.gradle.kts`、`meowui-core/build.gradle.kts`、`meowui/build.gradle.kts` |
| 发布坐标 | `io.github.lingqiqi5211.meowui`，版本 `0.2.0-rc01` | `gradle.properties` |
| Miuix 接入模式 | `meowui.miuix.mode=source`（唯一支持） | `gradle.properties` + `settings.gradle.kts` 的 `require` |
| 根工程名 | `meowui-project`（规避 Windows 大小写冲突） | `settings.gradle.kts` |

### 1.4 环境事实结论

- **已证实**：MeowUI 与 YadeaHook 使用相同 AGP 9.4.1、相同 libxposed 102.0.0；minSdk 28 ≥ 26，兼容。
- **已证实**：Gradle、JDK、Kotlin、Compose/material3 四处不一致（详见 §6）。
- **待验证**：`app/build.gradle.kts` 中 `ezHookToolVersion = "1.1.3"` 的本地变量与版本目录 1.2.2 不一致，且未被引用；对本次集成无影响，但属遗留隐患。

---

## 2. MeowUI 真实模块结构及 API 证据

### 2.1 模块结构（已证实）

MeowUI 由以下 Gradle 子模块组成（`MeowUI/settings.gradle.kts`）：

- `meowui-core`：KMP（Android + `jvm("desktop")`），只含 `MeowUiStyle`、`PreferenceKey`、`PreferenceStore` 与内存实现；不依赖 Compose / libxposed。
- `meowui`：双风格 UI（Material 3 Expressive / Miuix）、组件、主题、偏好绑定；`api(project(":meowui-core"))`，不依赖 libxposed。
- `meowui-xposed`：libxposed 集成；`io.github.lingqiqi5211.meowui.xposed` 提供设置页入口，`io.github.lingqiqi5211.meowui.libxposed` 提供远程设置存储；`api(project(":meowui"))`。
- `sample`、`sample-desktop`：仅演示，不发布。
- `third_party/miuix`：Git submodule，`includeBuild` 接入。

依赖方向（`MeowUI/AGENTS.md`）：`meowui → meowui-core`；`meowui-xposed → meowui`；`sample → meowui`。

### 2.2 关键 API 逐项核查

#### `MeowXposedPreferenceHost`（已证实）

- 文件：`MeowUI/meowui-xposed/src/main/kotlin/io/github/lingqiqi5211/meowui/xposed/MeowXposed.kt`
- 声明：

```kotlin
@Composable
fun MeowXposedPreferenceHost(
    preferenceName: String,
    onWriteResult: (PreferenceWriteResult) -> Unit = {},
    content: @Composable () -> Unit,
)
```

- 行为：`remember(preferenceName)` 创建 `XposedServicePreferenceStore(preferenceName)`，`DisposableEffect` 在 `onDispose` 中 `store.close()`，再包一层 `MeowPreferenceProvider`。
- 结论：这是一个 **Composable 宿主**，不是 Activity 基类；可直接包裹设置内容。计划中把它当作“构造方式”是合理的，但实现时必须按 Composable 使用。

#### `setMeowXposedContent`（已证实）

- 文件：同上。
- 声明：

```kotlin
fun ComponentActivity.setMeowXposedContent(
    preferenceName: String,
    onWriteResult: (PreferenceWriteResult) -> Unit = {},
    content: @Composable () -> Unit,
)
```

- 行为：`setMeowContent { MeowXposedPreferenceHost(...) }`，自动管理存储生命周期。
- 约束：必须由 `ComponentActivity` 调用；`preferenceName` 非空。

#### `MeowXposedActivity`（已证实）

- 文件：同上。
- 抽象成员：`protected abstract val preferenceName: String`、`@Composable protected abstract fun Content()`；可覆写 `protected open fun onWriteResult(result: PreferenceWriteResult)`。
- 结论：适用于“没有其他 Activity 基类”的场景；本项目 `MainActivity` 已继承 `ComponentActivity` 且用 `setContent` 搭建 `NavigationSuiteScaffold`，更合适的是在现有结构内局部使用 `MeowXposedPreferenceHost`，而不是切换基类。

#### `MeowPreferencePage` / `MeowPreferenceSection`（已证实）

- 文件：`MeowUI/meowui/src/commonMain/kotlin/io/github/lingqiqi5211/meowui/component/MeowPreferencePage.kt` 与 `.../MeowPreferences.kt`、`.../MeowPreferenceSectionScope.kt`
- `MeowPreferencePage(title, subtitle, onBackClick, actionItems, bottomBar, snackbarState, effect, content)`：组合了 `MeowScaffold` + 滚动顶栏 + `MeowPreferenceScreen`。
- `MeowPreferenceSection(modifier, title, content: MeowPreferenceSectionScope.() -> Unit)`：`content` 是**普通构建器**（非 Composable），每次组合完整跑一趟。
- 行通过 scope 的 `switch` / `checkbox` / `slider` / `choice` / `textInput` / `action` / `item` 声明；每个可见行需要唯一 `id`；重复 `id` 抛 `IllegalArgumentException`。

#### `PreferenceKey` 类型参数、默认值与序列化（已证实）

- 文件：`MeowUI/meowui-core/src/commonMain/kotlin/io/github/lingqiqi5211/meowui/core/preference/PreferenceKey.kt`、`PreferenceType.kt`
- 支持类型：`Boolean`、`Int`、`Long`、`Float`、`String`、`Set<String>`。
- 构造：`PreferenceKey(name, default)` 或 `PreferenceKey.boolean/int/long/float/string/stringSet(...)`；`name` 必须非空（`require(name.isNotBlank())`）。
- 默认值通过 `type.copyValue(...)` 做防御性拷贝（`StringSet` 复制为 `linkedSetOf`），`defaultValue` 属性每次返回副本。
- `equals`/`hashCode` 基于 `name` + `type`，同名不同类型判为不相等；读取/写入遇到类型不一致抛 `PreferenceTypeMismatchException`。
- 序列化方式：不是框架级自定义序列化，而是由 `meowui-xposed` 的 `SharedPreferencesStoreBackend` 按 `get/put Boolean/Int/Long/Float/String/StringSet` 落到 libxposed 远程 `SharedPreferences`（见 §2.2 `PreferenceStore` 后端）。

#### `PreferenceStore` 读写与观察接口（已证实）

- 文件：`MeowUI/meowui-core/src/commonMain/kotlin/io/github/lingqiqi5211/meowui/core/preference/PreferenceStore.kt`

```kotlin
interface PreferenceStore {
    val connectionState: StateFlow<PreferenceConnectionState>
    fun <T : Any> read(key: PreferenceKey<T>): T
    fun <T : Any> observe(key: PreferenceKey<T>): Flow<T>
    suspend fun <T : Any> write(key: PreferenceKey<T>, value: T): PreferenceWriteResult
    suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult
    suspend fun clear(): PreferenceWriteResult
}
```

- `observe`：先发出当前值，再发后续变化；`CorePreferenceStoreAdapter` 内部用 `distinctUntilChanged()` 合并重复值。
- 内存实现 `InMemoryPreferenceStore`：`write` 前检查 `connectionState == Connected`，类型不一致返回 `Failure`，未变化返回 `Success`。

#### `XposedModule.createPreferenceStore`（已证实）

- 文件：`MeowUI/meowui-xposed/src/main/kotlin/io/github/lingqiqi5211/meowui/libxposed/LibXposedPreferenceStore.kt`

```kotlin
fun XposedModule.createPreferenceStore(
    preferenceName: String,
): XposedModulePreferenceStore = XposedModulePreferenceStore(this, preferenceName)
```

- `XposedModulePreferenceStore` 构造函数：`XposedModulePreferenceStore(module: XposedModule, preferenceName: String)`。
- 实现细节：调用 `module.getRemotePreferences(preferenceName)` **一次**，包成 `FixedRemotePreferencesConnection`，再交给 `ConnectionBackedPreferenceStore`。

#### `XposedServicePreferenceStore` / `XposedModulePreferenceStore` 生命周期与释放（已证实 / 部分待验证）

- 文件：`MeowUI/meowui-xposed/src/main/kotlin/io/github/lingqiqi5211/meowui/libxposed/LibXposedPreferenceStore.kt`
- 两个 store 都 `implements PreferenceStore by delegate, AutoCloseable`，`close()` 由 `ConnectionBackedPreferenceStore.close()` 实现：`AtomicBoolean` 幂等关闭，关闭订阅 + `adapter.close()`（注销 SharedPreferences 监听并置 `Disconnected`）。
- **已证实**：资源释放是幂等的，设置页侧由 `MeowXposedPreferenceHost` 的 `DisposableEffect` 自动关闭。
- **待验证**：Hook 进程侧没有框架提供的“模块销毁”回调；何时关闭、热重载（`onHotReloading`/`onHotReloaded`）时如何避免旧 store 泄漏，需要依据本项目 `MainHook.kt` 的实际生命周期在实现阶段确定。

#### `Connecting` / `Connected` / `Disconnected`（已证实）

- 文件：`MeowUI/meowui-core/src/commonMain/kotlin/io/github/lingqiqi5211/meowui/core/preference/PreferenceStore.kt`

```kotlin
sealed interface PreferenceConnectionState {
    data object Connecting : PreferenceConnectionState
    data object Connected : PreferenceConnectionState
    data class Disconnected(val cause: Throwable? = null) : PreferenceConnectionState
}
```

#### 设置写入失败的错误传播（已证实）

- 文件：`MeowUI/meowui-core/.../PreferenceStore.kt` 与 `MeowUI/meowui-xposed/.../CorePreferenceStoreAdapter.kt`

```kotlin
sealed interface PreferenceWriteResult {
    data object Success : PreferenceWriteResult
    data class NotConnected(val state: PreferenceConnectionState) : PreferenceWriteResult
    data class Failure(val cause: Throwable) : PreferenceWriteResult
}
```

- 后端写入使用 `editor.commit()`（同步），失败返回 `BackendWriteResult.Failure`；无可用 `SharedPreferences` 返回 `Unavailable → PreferenceWriteResult.NotConnected`。
- 框架不提供独立的“已持久化确认”机制：`Success` 表示 `commit()` 返回 `true`，并非跨进程强一致或原子双写。这符合规范“不虚构确认机制”的要求。

#### Compose UI 与远程存储的状态更新机制（已证实）

- 文件：`MeowUI/meowui/src/commonMain/kotlin/io/github/lingqiqi5211/meowui/preference/MeowPreferenceBinding.kt` 与 `.../component/MeowStoredPreferences.kt`
- 读取：`rememberMeowPreferenceValue(key)` → `store.observe(key).collectAsState(initial = read(key))`。
- 写入：`rememberMeowPreferenceWriter(key)` 在 `rememberCoroutineScope()` 中 `launch { store.write(key, value) }`，结果通过 `onWriteResult` / `LocalPreferenceWriteResultHandler` 上报。
- 连接状态：`rememberMeowPreferenceConnectionState()` → `store.connectionState.collectAsState()`。
- 写入型组件在 `Disconnected` 时 `enabled = false`；`Connecting` 期间不自动禁用。
- 滑条（`MeowSliderPreference(key: PreferenceKey<Float>, ...)`）拖动期间用本地 `draftValue` 回显，**抬手时（`onValueChangeFinished`）才提交一次**，提交结束后退出草稿；旧写入不覆盖新一轮拖动。
- 开关（`MeowSwitchPreference(key: PreferenceKey<Boolean>, ...)`）在切换时写一次。

#### Miuix Git Submodule（已证实）

- `.gitmodules`：`path = third_party/miuix`，`url = https://github.com/compose-miuix-ui/miuix.git`。
- `git submodule status`：`8972f941c194bd3c4e14c34543d5ebc7b39e40da third_party/miuix (v0.9.4-35-g8972f941)`，且 `third_party/miuix/settings.gradle.kts` 存在（子模块已初始化）。
- 构建方式：`MeowUI/settings.gradle.kts` 中 `includeBuild(miuixDir)`，并在配置期把宿主 `local.properties` 的 `sdk.dir` 同步到 `third_party/miuix/local.properties`（该写入发生在 MeowUI 仓库内部，不在本项目内）。

#### Maven 发布产物与本地源码一致性（不支持 / 已证实）

- `README.md`：坐标“只用于源码替换，尚未上传 Maven 仓库”，“Maven 发布暂不开放”。
- `build.gradle.kts`：`:meowui` 与 `:meowui-xposed` 在 `source` 模式下 `AbstractPublishToMaven` 任务 `doFirst` 直接抛 `GradleException`，禁止发布。
- 结论：**不支持**仅加 Maven 坐标的接入方式；必须使用 composite build（`includeBuild`）由 Gradle 把 `io.github.lingqiqi5211.meowui:*` 替换为源码工程。

#### 尚未发布的 API / 版本兼容限制（已证实 / 待验证）

- **已证实**：`meowui.miuix.mode=source` 是唯一支持模式（`settings.gradle.kts` 里 `require(miuixMode == "source")`）；RC 阶段仅源码级接入。
- **已证实**：`README.md` 明确“已验证普通应用宿主编译与递归 Miuix 源码替换；Xposed 的库内测试通过，独立外部 Xposed 宿主仍待验证”。
- **待验证**：本项目正是“独立外部 Xposed 宿主”，上游未对此场景给出已验证结论。

### 2.3 组件能力清单（供设置页使用，已证实）

来源：`docs/components.md` + `MeowStoredPreferences.kt` + `MeowPreferenceSectionScope.kt`。

- 可绑定 `PreferenceKey` 的行：`switch`（Boolean）、`checkbox`（Boolean）、`slider`（**仅 Float**）、`choice`（泛型 T）、`textInput`（String）。
- 非绑定行：`action`、`item`。
- 状态展示：`MeowTip`（`Info/Success/Warning/Error`）、`MeowLoadingDialog`、`MeowSnackbarState`。
- 页面外壳：`MeowPreferencePage`、`MeowPreferenceScreen`、`MeowScaffold`。
- 主题：`MeowTheme(style = MeowUiStyle.MaterialExpressive | Miuix, ...)`；`MeowUiStyle` 枚举位于 `meowui-core`。

结论：第一批 10 项设置中，3 个 Boolean 用 `switch`，密度/字体缩放/背景透明度/宽度比例/偏移用 `slider`（Float 键）即可，无需编造 `MeowPreferenceSlider`/`MeowPreferenceSwitch`（真实名称是 `MeowSliderPreference`/`MeowSwitchPreference`，group 内用短助手 `slider`/`switch`）。

---

## 3. 当前设置读取、写入和持久化调用链

### 3.1 设置页入口

- `MainActivity`（`MainActivity.kt`）`setContent` → `YadeaHookApp()` → `NavigationSuiteScaffold`，三个目的地：`HOME`(LogScreen)、`DEVICE`(DeviceControlScreen)、`SETTINGS`(SettingsScreen)。
- `SettingsScreen`（`ui/SettingsScreen.kt`）直接使用 `FeatureSettings.speedAlertPrefs(context)` 与 `SpeedStrengthMapper(context)` 读写本地 `SharedPreferences`，用受控 Compose 状态回显，`保存所有设置` 按钮统一 `apply()`。

### 3.2 速度警报（本阶段不迁移）

- `FeatureSettings.kt`：文件 `speed_alert_prefs`，key 为 `enabled`、`speed_threshold`、`cooldown_ms`、`audio_path`、`last_alert_at`。`isSpeedAlertEnabled` 默认 `true`，阈值默认 `25f`，冷却默认 `10_000L`。
- `SpeedAlertReceiver.kt`（模块进程）：读上述 key，判定阈值与冷却，播放音频。
- `SpeedAlertHook.kt`（目标进程）：反射读 `TtpInfo.currentSpeedValue`，发隐式广播 `ACTION_SPEED_ALERT` 给模块进程。
- 结论：速度警报通过“目标进程 Hook → 广播 → 模块进程 Receiver”跨进程通信，与 MeowUI 远程设置是两套通道，本阶段不迁移。

### 3.3 车速—强度曲线（本阶段不迁移）

- `SpeedStrengthMapper.kt`：文件 `speed_strength_curve`，key 为 `curve_points`（Gson JSON）、`enabled`、`channel`、`max_strength`；`calculateStrength` 线性插值。曲线点是有序 `List<CurvePoint>`。
- `ui/CurveEditor.kt`：编辑曲线点；`ui/DeviceControlScreen.kt` 也使用 `SpeedStrengthMapper`。
- 结论：本阶段不动，保持 Gson + `SharedPreferences`。

### 3.4 歌词叠加（本阶段迁移目标）的当前参数来源

- `LyricOverlayController.kt`（工作区版本）：
  - `private const val OVERLAY_DENSITY = 1.3f`
  - `private const val OVERLAY_FONT_SCALE = 1.6f`
  - `attach(activity)` 时 `CompositionLocalProvider(LocalDensity provides Density(OVERLAY_DENSITY, OVERLAY_FONT_SCALE))`。
  - 生命周期：`NaviLyricHook` 在 `RoutePlanningNavigationBaiduActivity.onResume` → `attach()`；`onDestroy` → `detach()`。`detach()` 移除视图并 `destroy()` lifecycle owner。
- `LyricOverlayUi.kt`（工作区版本）：
  - 内容容器 `Box(fillMaxWidth, TopCenter)` + `Column(fillMaxWidth).padding(start = 300.dp, top = 42.dp, end = 30.dp, bottom = 5.dp)`。
  - 来源行：图标 `20.dp` 圆形裁剪，应用名 `widthIn(max = 180.dp)`，字号 `12.sp/14.sp`。
  - 主歌词：`LyricTextStyle = TextStyle(15.sp, 20.sp, Bold)`；翻译 `TranslationTextStyle = 11.sp/13.sp`；无歌词占位 `12.sp/15.sp`。
  - `showTranslation = true`（KaraokeLineText 与 SyncedLineText 均固定为 true）；来源行在 `source != null` 时始终显示。
- 结论：**歌词叠加目前没有任何 SharedPreferences**，全部是代码内常量；`overlay_enabled`、`overlay_show_source`、`overlay_show_translation` 目前也没有开关，行为恒为“显示”。

### 3.5 其他模块是否直接读写相同设置

- `rg` 全库仅 `LyricOverlayController` / `LyricOverlayUi` 使用上述常量；没有任何 `overlay_*` SharedPreferences key。
- `FeatureSettings`、`SpeedStrengthMapper`、`SpeedAlertReceiver` 使用的是另一组文件与 key，互不重叠。
- 结论：第一批 10 项设置与现有持久化无冲突，也没有需要“迁移”的旧值（旧值即当前常量）。

---

## 4. 当前歌词叠加布局参数的实际计算方式

### 4.1 坐标系与缩放

`LyricOverlayController.draw()` 在首次拿到目标 Bitmap 后，把 ComposeView 的 `layoutParams.width/height` 设为 `target.width/height` 并 `measure/layout` 到目标尺寸，然后 `view.draw(canvas)`。Compose 的坐标换算由 `LocalDensity provides Density(density = 1.3f, fontScale = 1.6f)` 决定：

- `dp` → px：`value * density = value * 1.3`。
- `sp` → px：`value * density * fontScale = value * 1.3 * 1.6`（Compose `TextUnit.toPx()` 对 `Sp` 同时乘 density 与 fontScale）。

因此当前实际像素值（示例）：

| 代码常量 | 单位 | 实际像素 |
| --- | --- | --- |
| `start = 300.dp` | dp | 390 px |
| `top = 42.dp` | dp | 54.6 px |
| `end = 30.dp` | dp | 39 px |
| `bottom = 5.dp` | dp | 6.5 px |
| 来源图标 `20.dp` | dp | 26 px |
| 主歌词 `15.sp` | sp | 31.2 px |
| 翻译 `11.sp` | sp | 22.88 px |
| 来源/占位 `12.sp` | sp | 24.96 px |

### 4.2 关键发现

- **存在“密度重复作用”**：`density` 同时作用于所有 `dp`（偏移、图标）与所有 `sp`（字号），`fontScale` 再额外作用于 `sp`。这是当前真实行为，不是 bug 解读；迁移时若把 `overlay_density` 做成独立可调，它会影响偏移与字号两类数值，必须保留这一语义或显式改语义并经批准。
- **AGENTS.md 与代码不一致**：项目 `AGENTS.md` 写“坐标语义为 1.dp = 1.px、1.sp = 1.px × fontScale”，但代码实际传入 `density = 1.3f`，使 `1.dp = 1.3px`、`1.sp = 1.3 × 1.6 px`。以代码为准；迁移默认值必须复刻代码行为。
- **没有背景层**：`LyricOverlay` 的根 `Box` 与 `Column` 都没有 `background(...)`，`draw()` 也没有先铺底色。`overlay_background_alpha` 当前无对应实现。
- **没有宽度比例**：内容 `fillMaxWidth()`，靠固定的 `start=300.dp`、`end=30.dp` 收缩，不存在 `width_ratio`。
- **底部 5.dp 不在 10 个 key 内**：若只迁移 top/start/end，`bottom = 5.dp` 仍为硬编码常量。

---

## 5. 第一批 10 项设置：类型、默认值与语义建议

> 以下默认值按真实代码核定，**不采用计划书中的 1f / 1.2f / 48 / 112 / 24 / 0.42f / 0.94f**。

| Key | 建议类型 | 当前代码依据 | 建议默认值 | 合法范围建议 | 单位/语义 |
| --- | --- | --- | --- | --- | --- |
| `overlay_enabled` | Boolean | 无开关，恒显示 | `true` | true/false | 总开关；false 时不再叠加 |
| `overlay_show_source` | Boolean | 来源行在 `source != null` 时恒显示 | `true` | true/false | 是否显示音乐来源 |
| `overlay_show_translation` | Boolean | `showTranslation = true` 恒为真 | `true` | true/false | 是否显示翻译 |
| `overlay_density` | Float | `OVERLAY_DENSITY = 1.3f` | `1.3f` | 建议 `0.5f..2.0f` | `Density.density`；影响 dp 与 sp |
| `overlay_font_scale` | Float | `OVERLAY_FONT_SCALE = 1.6f` | `1.6f` | 建议 `0.5f..3.0f` | `Density.fontScale`；额外影响 sp |
| `overlay_top` | Float | `top = 42.dp` | `42f` | 建议 `0f..200f` | dp；实际像素 × density |
| `overlay_start` | Float | `start = 300.dp` | `300f` | 建议 `0f..500f` | dp；左侧偏移 |
| `overlay_end` | Float | `end = 30.dp` | `30f` | 建议 `0f..200f` | dp；右侧偏移 |
| `overlay_background_alpha` | Float | **无背景实现** | `0f`（建议） | `0f..1f` | 新语义；0=全透明=现状，1=不透明 |
| `overlay_width_ratio` | Float | **无宽度比例实现** | `1f`（建议） | 建议 `0.5f..1f` | 新语义；1=当前 `fillMaxWidth()` 语义 |

### 5.1 必须向用户确认的语义决策

1. **偏移量类型用 Float**：MeowUI 滑条只支持 `PreferenceKey<Float>`，偏移按 `dp` 存为 Float；若坚持 Int，只能改用 `textInput`，交互变差。
2. **密度重复缩放**：`overlay_density` 会同时改变偏移与字号。若不希望这样，需要拆分“画布密度”与“布局缩放”，但这会改变现有布局语义，属于新设计而非迁移，需单独批准。
3. **背景透明度**：当前无背景。若计划中的 `0.42f` 被当作默认，会引入一个此前不存在的半透明背景条，改变视觉。审计建议默认 `0f`（透明），把背景做成可选增强，保证默认不改变现状。
4. **宽度比例**：当前无此概念。需要定义计算基准（例如“内容可用宽度 = 位图宽度 × ratio，再受 start/end 约束”还是“替代 start/end”）。审计建议默认 `1f` 且仅作为可选的额外约束，避免改变现状。
5. **底部偏移**：`bottom = 5.dp` 不在 10 项内，应保持硬编码，或另行增加 key（超出本阶段范围）。

### 5.2 缺失值与非法值处理（基于 MeowUI 后端实现，已证实）

- 缺失值：`SharedPreferencesStoreBackend` 的 `get*` 在 key 不存在或 `currentPreferences == null` 时返回 key 的默认值；类型不符（`ClassCastException`）也回退默认值。
- `Boolean false` / 数值 `0`：SharedPreferences 的 `getBoolean`/`getInt`/`getFloat` 能区分“存在且为 false/0”与“缺失”，不存在把 0/false 误判为缺失的问题。
- 非法范围：框架不提供范围校验；需要仓库层自行 `coerceIn` 并决定是否写回。
- 非法类型：同名 key 在远端以不同类型存在时，`read`/`observe` 会走 `ClassCastException → default` 的回退（后端实现），不会抛到目标进程。

### 5.3 设置键唯一性

- 同一 key 在设置端与 Hook 端必须同名、同类型、同默认值。建议用单一 `object YadeaSettingsKeys`（纯 Kotlin，不含 Context/UI/Hook 逻辑），两端共享同一引用；`preferenceName` 必须非空且完全一致。
- MeowUI 支持共享对象：`PreferenceKey` 是普通类，直接共享 `object` 即可，不需要特殊机制。

---

## 6. 远程设置通信的可行性

### 6.1 通信机制（已证实）

- 设置端：`XposedServicePreferenceStore(preferenceName)` 通过 `ProcessXposedServiceRegistry` 订阅 `XposedServiceHelper`，拿到 `XposedService.getRemotePreferences(preferenceName)`。
- Hook 端：`XposedModulePreferenceStore(module, preferenceName)` 调用 `module.getRemotePreferences(preferenceName)` 拿 `SharedPreferences` 快照。
- 两边落在 libxposed 的同一远程偏好文件上（由框架在模块数据目录维护），从而跨进程同步。
- 写入用 `editor.commit()`（同步提交）；变化通过 `OnSharedPreferenceChangeListener` 驱动 `observe` 的 Flow 更新。
- `ProcessXposedServiceRegistry` 全进程只注册一次 `XposedServiceHelper.registerListener`（libxposed 的 listener 是单槽，重复注册会互相顶掉），这是 MeowUI 的全局单点，业务代码不应再自行注册。

### 6.2 关键可行性与约束

- **已证实**：设置页写入 → `commit()` 返回结果 → Hook 端 `observe` 收到变化，链路成立；写入失败通过 `PreferenceWriteResult` 暴露，不吞错。
- **已证实**：Hook 端 `read` 在远端不可用时回退默认值，不抛异常到目标进程（`getRemotePreferences` 抛错被 `runCatching` 捕获为 `preferences = null`）。
- **待验证 / 需实现时注意**：Hook 端 store 在构造时只 `getRemotePreferences` 一次，使用 `FixedRemotePreferencesConnection`（一次性快照）。若构造时服务不可用，store 会保持 `Disconnected` 且**不会自动重连**；因此 store 必须在服务可用的生命周期点创建，且不得假设“服务恢复后自动重连”。
- **待验证**：目标进程重启后监听器不会自动恢复；需在模块初始化流程中重新创建 store（`MainHook` → `VehicleServiceLoad.initHooks` 每次进入时创建，并在热重载时关闭旧实例）。
- **已证实**：写入结果语义边界——`Success` 只代表 `commit()` 成功，不是跨进程原子性保证，也非“已持久化确认”；不应对用户宣称超出该范围的保证。

### 6.3 可行性结论

远程设置通信**可行**，但依赖 libxposed 服务在目标进程与模块进程均可用；需在实现阶段处理“一次性快照、无自动重连、进程重启需重建、热重载需关闭旧实例”四个生命周期点。

---

## 7. 构建兼容性和版本冲突风险

### 7.1 工具链差异矩阵

| 维度 | YadeaHook 当前 | MeowUI 要求 | 差异结论 |
| --- | --- | --- | --- |
| Gradle Wrapper | 9.7.1 | 9.8.1 | **待验证**：composite build 运行在宿主 Gradle 上，需验证 9.7.1 能否评估 MeowUI 构建；上游要求宿主对齐 9.8.1 |
| daemon JVM | 21（`gradle-daemon-jvm.properties`） | 25（`.java-version`） | **待验证/风险高**：需安装 JDK 25 并把 daemon JVM 调为 25；字节码目标 JVM 21 |
| AGP | 9.4.1 | 9.4.1 | 已证实一致 |
| Kotlin | 2.4.10 | 2.4.21 | **待验证**：同为 2.4.x，patch 差异，元数据大概率兼容，但建议对齐 |
| libxposed | 102.0.0 | 102.0.0 | 已证实一致 |
| minSdk | 28 | 26 | 已证实兼容（28 ≥ 26） |
| Compose / material3 | BOM 2026.06.01；material3 1.4.0 | androidx Compose 1.12.1（实际解析 1.13.0-alpha02）；Android material3 1.5.0-beta01 | **待验证/风险高**：见 7.2 |
| Java 字节码目标 | app `source/target 11` | JVM 21 | **待验证**：D8 需能处理 Java 21 class 文件（AGP 9.4.1 一般支持，但需构建确认） |

### 7.2 Compose / Material3 冲突（重点）

- MeowUI 的 `meowui` 模块 `api` 暴露 CMP 依赖；在 Android 上解析为 `androidx.compose.material3:material3:1.5.0-beta01`（其 `androidMain` 显式声明），Compose runtime/ui/foundation 实际解析为 1.13.0-alpha02。
- YadeaHook 当前用 `composeBom 2026.06.01` + `material3 1.4.0`，且 `MainActivity` 使用 `material3-adaptive-navigation-suite` 的 `NavigationSuiteScaffold`。
- Gradle 冲突解析会取较高版本 `material3 1.5.0-beta01`，可能造成宿主现有 Material3/Compose 代码的 source/binary 兼容问题，需编译验证；不排除需要同步升级宿主 Compose BOM / material3 并回归 `MainActivity`。
- **风险等级：高，但可验证**。必须在实现前单独跑一次“仅加依赖 + 对齐 Compose”的构建基线，确认不破坏现有 UI 编译。

### 7.3 依赖接入方式

- **已证实**：Maven 未发布，唯一可行路径是 `includeBuild("../MeowUI")`。
- **已证实**：MeowUI 自身 `includeBuild(third_party/miuix)`，构成递归 composite build；上游已声明“已验证普通应用宿主 + 递归 Miuix 源码替换”，但**未验证外部 Xposed 宿主**。
- **待验证**：`includeBuild` 与 Maven 坐标不能也不必同时配置；规范要求二选一，审计建议只用 composite build，不声明 Maven 仓库里的同坐标。
- **已证实**：MeowUI 配置期会把 `MeowUI/local.properties` 的 `sdk.dir` 写入 `third_party/miuix/local.properties`（副作用在 MeowUI 仓库内）；宿主构建需提供 Android SDK 定位（`ANDROID_HOME` 或 `local.properties`）。
- **已证实**：MeowUI `README` 要求“宿主需位于有提交的 Git 仓库”，YadeaHook 是有提交的 Git 仓库，满足。

### 7.4 打包与 R8 影响

- `meowui-xposed` 依赖：`compileOnly(libs.libxposed.api)` + `implementation(libs.libxposed.service)`。`implementation` 会以运行时传递依赖进入 APK，即把 `libxposed-service`（102）打进 dex。设置页需要它，但需确认 R8 不误删相关类，并确认 `libxposed-api` 仍保持 `compileOnly`（框架提供）。
- `meowui` 会传递带入大量依赖（Miuix、CMP Material3、material-color-utilities、navigationevent-compose、datastore-preferences-core 等），显著改变依赖图与 APK 体积，需要 R8 规则检查。
- `meowui` 自带 `consumerKeepRules`（枚举名持久化）会随 AAR 合并；YadeaHook 现有 `app/src/main/keepRules/rules.keep` 需检查是否需要补充 MeowUI/Miuix/libxposed-service 的 keep。
- release 构建 `isMinifyEnabled = true`，新增库的反射与枚举需重点验证（SuperLyricApi 已有 keep 规则，MeowUI 相关需新增评估）。

### 7.5 Manifest 影响

- Miuix Blur 依赖声明 minSdk 33；MeowUI 库 Manifest 已携带 `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"`，但 `getting-started.md` 提示“低于 33 的宿主若清单合并报 minSdk 冲突，可在主 Manifest 再写同一覆盖”。YadeaHook minSdk 28，**待验证**清单合并是否需要在 `app/src/main/AndroidManifest.xml` 增加该覆盖。

### 7.6 工具链升级清单（仅当获准进入实现阶段）

以下升级是“接入 MeowUI 源码构建”的前提，必须单独建立构建基线验证，不能与业务迁移混在一起：

1. Gradle Wrapper 9.7.1 → 9.8.1。
2. 安装 JDK 25，并将 daemon JVM 从 21 调整为 25（`gradle/gradle-daemon-jvm.properties` 或 `org.gradle.java.home`）。
3. Kotlin（及 `org.jetbrains.kotlin.plugin.compose`）2.4.10 → 2.4.21。
4. Compose BOM / material3 / 相关 Compose 依赖与 MeowUI 解析结果对齐，并回归 `MainActivity`。

替代方案：若不愿意升级宿主工具链，可考虑“把 MeowUI 需要的源码/API 复制进本仓库”或“等待 MeowUI 发布 Maven 产物”，但这会带来维护成本或阻塞，需用户决策。审计不推荐复制源码。

---

## 8. 旧设置迁移方案

### 8.1 本阶段实际情况

- 第一批 10 项 overlay 设置**没有旧 SharedPreferences**，当前值全部是代码常量。因此本阶段不存在“从旧配置迁移数据”的动作，只需要把当前常量固化为 key 默认值（§5）。
- 现有的 `speed_alert_prefs` 与 `speed_strength_curve` 属于**本阶段不迁移**的设置，应保持原样；不要把它们纳入新的 remote preferenceName，也不要改动其读写逻辑。

### 8.2 读取优先级（作为仓库层通用规则）

规范要求的优先级在本阶段退化为：

1. 远程设置存在有效值 → 用远程值。
2. （不适用：overlay 无旧配置）读旧 SharedPreferences。
3. 用共享 key 的默认值。
4. 远程服务暂时不可用时，区分“通信失败（回退默认，不写本地）”与“确实不存在远程值（回退默认）”。

若未来迁移速度警报/曲线/音频，再套用完整的四层优先级，并对 `false`/`0` 做存在性判断（后端已正确区分，见 §5.2）。

### 8.3 迁移幂等性与记录

- 本阶段无需“迁移完成”状态机；未来迁移时再实现：记录“是否完成、来源、结果、失败原因、重试条件”，且只在目标存储写入符合预期后标记成功。
- 不删除旧 SharedPreferences；不宣称远程与 SharedPreferences 双写具备原子性（实现不提供该保证）。

---

## 9. 推荐架构和数据流

### 9.1 职责划分（候选命名，优先复用现有类）

```text
设置进程（com.ghhccghk.yadeahook）
  MainActivity
    └─ SettingsScreen（只替换“投屏歌词”区域为 MeowUI 分组）
         └─ MeowXposedPreferenceHost(preferenceName = "yadeahook_settings", onWriteResult)
              └─ MeowPreferencePage/MeowPreferenceSection
                   switch/slider(key = YadeaSettingsKeys.*)

目标进程（com.yadea.smartmoto）
  MainHook → VehicleServiceLoad.initHooks
    └─ 创建一次 XposedModulePreferenceStore（模块级持有，热重载时关闭旧实例）
         └─ YadeaSettingsRepository(store)   // 默认值、范围校验、错误隔离
              └─ LyricOverlayController       // observe 布局设置，热更新叠加层
              └─ LyricOverlayUi               // 接受参数，不再读全局常量
```

### 9.2 建议的类

- `YadeaSettingsKeys`：集中定义 10 个 `PreferenceKey` 与 `preferenceName`；纯 Kotlin，不含 Context/UI/Hook 逻辑。
- `YadeaSettingsRepository`：封装 `PreferenceStore` 的 `read`/`write`/`observe`，负责默认值、`coerceIn` 范围校验、非法值回退、错误隔离；隔离 MeowUI 底层 API。
- `SettingsScreen`：只替换投屏歌词区域；保留速度警报、曲线编辑、音频选择；不直接承担跨进程协议；不把临时 UI 状态当“已保存”。
- `LyricOverlayController`：从 repository 读取有效布局参数；收到更新时刷新现有叠加层（或评估重建叠加层的副作用后选择最低风险方案）；服务不可用时不崩溃。

### 9.3 热更新路径

- `LyricOverlayController` 持有 store 的 `observe` 订阅（与目标进程生命周期一致的 scope），收到变化后更新 Compose 状态；因 `LyricOverlay` 的布局参数是 Compose 状态，更新会触发重绘，再由 `NaviScreenCaptureHook` 的每帧 `draw()` 输出到 Bitmap。
- 若现有实现只能重建 ComposeView 才能改密度（`LocalDensity` 在 `setContent` 内固定），实现阶段需评估：密度/字体缩放变化是否通过重组即可生效，还是需要 `attach()` 重建；优先复用现有 `attach/detach` 生命周期。

---

## 10. 分阶段实施任务及文件变更清单（候选，待批准后细化）

### 阶段 A：构建基线对齐（独立验证）

- 变更：`gradle/wrapper/gradle-wrapper.properties`、`gradle/gradle-daemon-jvm.properties`、`gradle/libs.versions.toml`、`settings.gradle.kts`（加 `includeBuild("../MeowUI")`）、`app/build.gradle.kts`（加 `implementation("io.github.lingqiqi5211.meowui:meowui-xposed:0.2.0-rc01")`）。
- 可能变更：Compose BOM/material3 对齐；`AndroidManifest.xml` 加 blur `overrideLibrary`（如清单合并报错）。
- 验证：`./gradlew assembleDebug` 先通过，再进入业务阶段。失败项逐一归因，不靠随意升级/删除测试掩盖。

### 阶段 B：共享设置层

- 新增 `YadeaSettingsKeys.kt`（或复用现有职责一致的类）。
- 新增 `YadeaSettingsRepository.kt`（默认值、范围校验、非法值处理、错误隔离）。
- 单测：用 `InMemoryPreferenceStore` 覆盖默认值/有效值/非法值/观察器注册释放等。

### 阶段 C：Hook 进程接入

- 修改 `MainHook.kt` 或 `VehicleServiceLoad.kt`：模块级创建一次 `XposedModulePreferenceStore`，热重载时关闭旧实例。
- 修改 `LyricOverlayController.kt` / `LyricOverlayUi.kt`：参数化密度、字体缩放、开关、偏移、来源、翻译；按 `overlay_background_alpha`/`overlay_width_ratio` 的最终语义决定是否新增视觉元素。
- 处理一次性快照、无自动重连、进程重启重建等生命周期点；异常不传播到目标进程。

### 阶段 D：设置页替换（仅歌词区域）

- 修改 `SettingsScreen.kt`（或新设歌词设置分组）：用 `MeowXposedPreferenceHost` 包裹，`switch`/`slider` 绑定 `YadeaSettingsKeys`，`MeowTip` 展示连接状态与写入失败。
- 保留速度警报、曲线编辑、音频选择原样。

### 阶段 E：跨进程与真机验收

- 按 §11 执行；未执行项明确标注。

---

## 11. 自动化测试、跨进程测试和真机验收计划

### 11.1 构建测试

- `./gradlew assembleDebug`、`./gradlew test`。
- 记录命令、退出状态、关键输出、失败原因、未执行任务。

### 11.2 设置仓库测试（用测试替身，不冒充真实通信）

- 默认值读取；有效值读取；非法值处理（越界 clamp、类型错误回退）；旧值迁移（本阶段 overlay 无旧值，验证“无旧值→默认值”路径）；重复启动幂等；远程读取失败回退；远程写入失败暴露；远程值与旧值冲突；`Boolean false` 与数值 `0` 的正确区分；观察器注册与释放。

### 11.3 跨进程测试（必须真机/真环境，不能以单测代替）

1. 启动设置界面 → 2. 修改一个设置 → 3. 确认写入成功 → 4. Hook 进程读该设置 → 5. 读到预期值 → 6. 修改同一设置观察 Hook 端更新 → 7. 重启设置进程确认保存 → 8. 重启目标进程确认仍可用 → 9. 模拟服务不可用确认安全降级 → 10. 恢复服务确认读取/观察恢复。

### 11.4 功能回归

- 高德/百度投屏歌词叠加；Bitmap 不可变复制；来源图标与应用名；翻译开关；长歌词与安全区；布局参数热更新；速度警报；曲线编辑；音频选择；目标应用异常退出/重启恢复。

### 11.5 验收证据等级

最终分别记录：源码验证 / 构建验证 / 自动化测试 / 跨进程验证 / 真机验证，各自标“已执行”或“未执行”。

---

## 12. 未解决的问题及其影响

1. **工具链升级决策未定**：Gradle 9.8.1、JDK 25、Kotlin 2.4.21、Compose/material3 对齐是否接受。影响：不升级则无法稳定构建 MeowUI 源码。
2. **Compose/Material3 冲突未验证**：可能要求升级宿主现有 Compose 依赖并回归 `MainActivity`。影响：现有 UI 可能编译失败，需单独构建基线验证。
3. **`overlay_background_alpha` 与 `overlay_width_ratio` 是新语义**：默认值与计算基准需用户确认（审计建议 0f / 1f，保持现状）。
4. **偏移与密度双重缩放**：`overlay_density` 会同时影响偏移和字号，是否拆分需决策。
5. **Hook 端 store 一次性快照、无自动重连**：创建时机与异常回退需实现阶段验证；真机 libxposed 行为尚未实测。
6. **上游未验证外部 Xposed 宿主**：本工程是早期接入者，编译期风险集中在递归 composite build 与 Compose 冲突。
7. **底部偏移与子布局常量未纳入 key**：`bottom=5.dp`、图标 20dp、来源名 maxWidth 180dp 等仍硬编码。
8. **release R8/打包**：`libxposed-service` 传递依赖与 MeowUI/Miuix 反射需补 keep 规则并验证 shrink。
9. **Blur overrideLibrary**：清单合并是否需显式加 `top.yukonga.miuix.kmp.blur` 覆盖待验证。

---

## 13. 每项结论的证据来源

| 结论 | 证据 |
| --- | --- |
| Git 状态/分支/未跟踪文件 | `git status --porcelain=v2 --branch`、`git log --oneline -10` |
| 项目依赖与版本 | `YadeaHook/settings.gradle.kts`、`build.gradle.kts`、`app/build.gradle.kts`、`gradle/libs.versions.toml`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties`、`gradle/gradle-daemon-jvm.properties`、`app/src/main/AndroidManifest.xml` |
| shell JDK 17 | `java -version`、`JAVA_HOME` |
| MeowUI 工具链 | `MeowUI/gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`、`.java-version`、`gradle.properties`、`AGENTS.md`、`README.md` |
| MeowUI 模块结构 | `MeowUI/settings.gradle.kts`、`AGENTS.md` |
| MeowUI 源码接入限制 | `MeowUI/README.md`、`build.gradle.kts`（发布禁止）、`gradle.properties`（`meowui.miuix.mode=source`） |
| PreferenceKey / PreferenceStore / 连接与写入结果 | `meowui-core/src/commonMain/.../PreferenceKey.kt`、`PreferenceStore.kt`、`PreferenceType.kt`、`InMemoryPreferenceStore.kt` |
| Xposed 入口与存储 | `meowui-xposed/src/main/.../xposed/MeowXposed.kt`、`.../libxposed/LibXposedPreferenceStore.kt`、`.../libxposed/RemotePreferencesConnection.kt`、`.../libxposed/CorePreferenceStoreAdapter.kt`、`.../libxposed/SharedPreferencesStoreBackend.kt`、`.../libxposed/MeowXposedService.kt` |
| 设置组件与绑定 | `meowui/src/commonMain/.../component/MeowPreferencePage.kt`、`MeowPreferenceSectionScope.kt`、`MeowPreferences.kt`、`MeowStoredPreferences.kt`；`meowui/src/commonMain/.../preference/MeowPreferenceBinding.kt`；`meowui-core/.../MeowUiStyle.kt` |
| Miuix submodule | `MeowUI/.gitmodules`、`git submodule status`、`third_party/miuix/settings.gradle.kts` 存在 |
| 设置页调用链 | `MainActivity.kt`、`ui/SettingsScreen.kt`、`FeatureSettings.kt`、`bluetooth/SpeedStrengthMapper.kt`、`SpeedAlertReceiver.kt`、`hooks/SpeedAlertHook.kt` |
| 歌词叠加参数 | `lyrics/LyricOverlayController.kt`（工作区）、`lyrics/LyricOverlayUi.kt`（工作区） |
| 叠加生命周期/捕获出口 | `hooks/NaviLyricHook.kt`、`hooks/NaviScreenCaptureHook.kt` |
| Hook 进程初始化 | `MainHook.kt`、`VehicleServiceLoad.kt`、`resources/META-INF/xposed/module.prop`、`scope.list`、`java_init.list` |
| 无 overlay 旧持久化 | `rg -n "SharedPreferences|overlay_" app/src/main/java` 与 `rg -n "FeatureSettings|SpeedStrengthMapper|LyricOverlayController"` 结果 |

---

## 14. 本阶段未执行项

- 未执行任何构建、测试、跨进程或真机验证（只读审计阶段）。
- 未修改任何业务代码、Gradle 配置、Manifest、资源或设置数据。
- 未安装或升级依赖、未运行 `gradlew`。

**本阶段到此为止，等待审查与明确批准后再进入实现阶段。**

---

## 15. 补充评估：降级到 MeowUI 0.1.5（推荐，优于 0.2.0-rc01）

### 15.1 存在性与发布状态（已证实）

- `git tag -l` 显示存在 `v0.1.5`（另有 `v0.1.5-rc01`、`v0.1.5-rc02`）。
- Maven Central 已发布二进制构件：`io.github.lingqiqi5211.meowui:meowui-xposed:0.1.5` 与 `io.github.lingqiqi5211.meowui:meowui:0.1.5`，均含 AAR、POM、sources、javadoc 与签名。
- 证据：`https://repo1.maven.org/maven2/io/github/lingqiqi5211/meowui/meowui-xposed/0.1.5/` 目录列表；两份 POM 的实际依赖。

### 15.2 工具链（已证实）

| 维度 | 0.1.5 | 与 YadeaHook 对比 |
| --- | --- | --- |
| Gradle Wrapper | 9.6.1 | 仅源码接入时需要；二进制消费不运行其构建 |
| AGP | 9.3.1 | 仅源码接入时需要；二进制消费无关 |
| Kotlin | 2.4.10 | 与 YadeaHook 完全一致 |
| JDK | `.java-version`=25，但 README 称宿主需 JDK 21+ | 二进制消费无需 JDK 25 |
| 字节码目标 | JVM 21 | D8 需能处理 Java 21 class 文件（AGP 9.4.1 一般支持，需构建确认） |
| compileSdk / minSdk | 37 / 26 | minSdk 28 ≥ 26 兼容 |
| libxposed | api/service 102.0.0 | 一致 |
| Miuix | 0.9.4-rc01（Maven Central 二进制） | 无需子模块源码编译 |

结论：采用 0.1.5 二进制接入后，**§7.6 的 Gradle / JDK / Kotlin 三项升级不再需要**；`settings.gradle.kts` 无需 `includeBuild("../MeowUI")`，只需在版本目录加 `meowui-xposed = { module = "io.github.lingqiqi5211.meowui:meowui-xposed", version = "0.1.5" }` 并在 `app/build.gradle.kts` 引入。

### 15.3 API 一致性（已证实）

以下 API 在 `v0.1.5` 中均存在，签名与 0.2.0-rc01 基本一致：

- `MeowXposedPreferenceHost`、`setMeowXposedContent`、`MeowXposedActivity`
- `XposedServicePreferenceStore`、`XposedModulePreferenceStore`、`XposedModule.createPreferenceStore`
- `PreferenceKey`（Boolean/Int/Long/Float/String/Set<String>）、`PreferenceStore`、`PreferenceConnectionState`、`PreferenceWriteResult`
- 键绑定组件：`MeowSwitchPreference`、`MeowCheckboxPreference`、`MeowSliderPreference`（Float）、`MeowPopupPreference`、`MeowTextInputPreference`

`PreferenceKey` 包名同为 `io.github.lingqiqi5211.meowui.core.preference`（只是没有独立的 `meowui-core` KMP 模块，类型直接位于 `meowui` 内）。

### 15.4 与 0.2.0-rc01 的差异（已证实，均不阻塞本任务）

- 分组 DSL 使用 `MeowSwitchPreference(...)` / `MeowSliderPreference(...)` / `MeowPopupPreference(...)` 等命名，而不是 0.2.0-rc01 的短助手 `switch(...)` / `slider(...)` / `choice(...)`。
- 0.1.5 的 `MeowSliderPreference` 无 `showDefaultValue` / `snapToDefault` / `showSteps` / `defaultText` 参数（这些是 0.2.0-rc01 新增），对十项 Float 设置无影响。
- 0.1.5 的滑条写入行为与 0.2.0-rc01 相同：拖动期间用本地 draft 回显，抬手时（`onValueChangeFinished`）提交一次。

### 15.5 依赖影响（POM 已证实）

- `meowui-xposed:0.1.5` 传递：`meowui:0.1.5`、`activity-compose:1.13.0`、`libxposed-service:102.0.0`（runtime）。
- `meowui:0.1.5` 传递：`compose runtime/ui/ui-graphics/foundation:1.12.0`（compile）、`material3:1.5.0-alpha26`（runtime）、`material-icons-extended:1.7.8`、`material-kolor:5.0.0`、`miuix-*:0.9.4-rc01`（5 个，runtime）、`coroutines 1.11.0`、`core-ktx 1.19.0`、`lifecycle-runtime-compose 2.11.0` 等。

### 15.6 剩余风险（与版本无关的部分不变）

- 仍会把宿主 Compose 解析到 1.12.0、material3 到 1.5.0-alpha26（高于当前 BOM / material3 1.4.0），需编译验证 `MainActivity` 等现有 Material3 用法。
- Blur 的 `tools:overrideLibrary="top.yukonga.miuix.kmp.blur"` 清单合并待验证。
- `libxposed-service` 102 以 runtime 依赖打入 APK，需 R8 验证。
- 十个 key 的默认值修正、`overlay_background_alpha` / `overlay_width_ratio` 新语义、密度双重缩放等结论不变。

### 15.7 结论

**推荐改为 MeowUI 0.1.5 二进制接入**：不升级 Gradle/JDK/Kotlin、不做 composite build、不编译 Miuix 子模块，实施范围缩小为“加一个 Maven 依赖 + 对齐 Compose/material3 + 按 §5 实现十项设置”。0.2.0-rc01 的源码接入只在确需其新特性（KMP、新 Section 构建器、Miuix 2026 配色等）时才有必要。

---

## 16. 最终实施决定与构建证据

### 16.1 最终决定（已批准）

- 采用 **MeowUI 0.1.5 Maven Central 二进制依赖**：`io.github.lingqiqi5211.meowui:meowui-xposed:0.1.5`。
- 不使用 0.2.0-rc01 源码接入、不使用 Composite Build、不编译 Miuix Git Submodule。
- 不为本任务升级 Gradle、JDK、Kotlin 或 Compose；工作区已有的工具链变更由用户独立管理。
- 第一阶段只迁移投屏歌词的十项设置；速度警报、强度曲线和音频选择继续使用原有本地存储。

### 16.2 依赖提交

提交 `451587f`（`build: add MeowUI 0.1.5 dependency`）实际只包含：

- `gradle/libs.versions.toml`：新增 `meowui = "0.1.5"` 与 `meowui-xposed` 库别名。
- `app/build.gradle.kts`：新增 `implementation(libs.meowui.xposed)`。

### 16.3 构建验证（已执行）

在包含用户既有工具链升级的工作区执行：

```text
.\gradlew.bat compileDebugKotlin
BUILD SUCCESSFUL

.\gradlew.bat assembleDebug
BUILD SUCCESSFUL
```

另在基于干净 `HEAD` 的临时副本中加入同一依赖后执行 `compileDebugKotlin`，同样成功。构建只出现项目原有的弃用/警告，未发现 MeowUI 0.1.5 引入的编译错误。

### 16.4 当前证据等级

- **源码验证**：0.1.5 的宿主、组件和远程存储 API 已逐项核对。
- **构建验证**：`compileDebugKotlin` 与 `assembleDebug` 已成功。
- **自动化测试**：待共享设置层实现后执行。
- **跨进程验证**：未执行。
- **真机验证**：未执行。

因此当前只能确认依赖可编译和 APK 可组装，不能宣称远程设置同步或投屏热更新已经工作。
