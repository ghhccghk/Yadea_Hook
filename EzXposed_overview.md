# EzHookTool API Guide

EzHookTool 的 API 分成三类：

- `core`：反射、类查找、方法查找、字段读写、实例创建。
- `core.java`：给 Java 调用的入口，例如 `Classes`、`Methods`、`Fields`、`Constructors`。
- `xposed.dsl` / `xposed.java`：给 Kotlin / Java 使用的 hook 入口。

本项目不做 dex 扫描。`findClassIf` 通过 `EzReflect.classResolver` 从目标 `ClassLoader` 获取可查询类名，再按条件筛选；成员查找只在给定 `Class` 的成员和父类成员中查找。

## Kotlin

按条件查找类：

```kotlin
val clazz = findClassIf {
    findSingle()
    cacheKey("login-activity")
    packageStartsWith("com.example")
    simpleNameContains("Login")
    hasMethod {
        name("login")
        paramCount(2)
    }
    hasField {
        name("token")
    }
}
```

查找单个方法：

```kotlin
val method = clazz.findMethod {
    name("foo")
    paramCount(2)
    returnType(String::class.java)
}
```

查找多个方法：

```kotlin
val methods = clazz.findAllMethods {
    name("foo")
    paramCount(2)
    findAndSuper()
}
```

不写条件时会返回当前查找范围内的全部结果：

```kotlin
val methods = clazz.findAllMethods()
val declaredFields = clazz.findAllFields {
    findOnlyClass()
}
```

常用方法条件：

```kotlin
val method = clazz.findMethod {
    nameContains("open")
    paramCountIn(1..3)
    paramsAssignableFrom(String::class.java)
    returnTypeExtendsFrom(CharSequence::class.java)
    isPublic()
    notStatic()
}
```

查找字段：

```kotlin
val field = clazz.findField {
    name("mContext")
    type(Context::class.java)
    findOnlyClass()
}
```

查找构造器：

```kotlin
val constructor = clazz.findConstructor {
    noParams()
    isPublic()
}
```

安装 hook：

```kotlin
method.createHook {
    before { param ->
        val text = param.argAs<String>(0)
        param.args[0] = text.trim()
    }

    after { param ->
        param.result = "done"
    }
}
```

批量 hook：

```kotlin
clazz.findAllMethods {
    name("foo")
    paramCount(1)
}.createHooks {
    before {
        // ...
    }
}
```

## Java

Java 代码建议使用 `core.java` 包里的入口。链式查询方法使用 `filterBy...` 命名，能直接看出每一步是在追加查找条件。

查找类：

```java
import io.github.lingqiqi5211.ezhooktool.core.java.Classes;

Class<?> target = Classes.loadClass("com.example.Target");
```

多个候选类名按顺序兜底：

```java
Class<?> target = Classes.loadClassFirst(
        "com.example.Target",
        "com.example.a"
);
```

> Java 端目前没有「按类名 + 内含成员条件挑选类」的条件查找入口（Kotlin 的 `findClassIf { ... }`）；
> 需要类似能力时请在 Kotlin 侧暴露便利方法或自行组合 [Classes.loadClass] + 反射检查。

查找方法：

```java
import io.github.lingqiqi5211.ezhooktool.core.java.Methods;

Method method = Methods.find(target)
        .filterByName("foo")
        .filterByParamCount(2)
        .filterByReturnType(String.class)
        .findAndSuper()
        .first();
```

更宽松的参数匹配：

```java
Method method = Methods.find(target)
        .filterByNameContains("open")
        .filterByAssignableParamTypes(String.class)
        .filterByReturnTypeExtendsFrom(CharSequence.class)
        .filterPublic()
        .first();
```

查找字段：

```java
import io.github.lingqiqi5211.ezhooktool.core.java.Fields;

Field field = Fields.find(target)
        .filterByName("mContext")
        .filterByType(Context.class)
        .findOnlyClass()
        .first();
```

查找构造器：

```java
import io.github.lingqiqi5211.ezhooktool.core.java.Constructors;

Constructor<?> constructor = Constructors.find(target)
        .filterEmptyParam()
        .filterPublic()
        .first();
```

安装 before / after hook：

```java
import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.Hooks;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

Hooks.createHook(method, new IMethodHook() {
    @Override
    public void before(HookParam param) {
        Object[] args = param.getArgs();
        args[0] = ((String) args[0]).trim();
    }

    @Override
    public void after(HookParam param) {
        param.setResult("done");
    }
});
```

安装 replace hook：

```java
import io.github.lingqiqi5211.ezhooktool.xposed.java.IReplaceHook;

Hooks.createHook(method, new IReplaceHook() {
    @Override
    public Object replace(HookParam param) {
        return null;
    }
});
```

查找并 hook 单个方法：

```java
Hooks.findAndHookMethod(target, "foo", String.class, new IMethodHook() {
    @Override
    public void before(HookParam param) {
        // ...
    }
});
```

批量 hook：

```java
List<Method> methods = Methods.find(target)
        .filterByName("foo")
        .filterByParamCount(1)
        .toList();

Hooks.createHooks(methods, new IMethodHook() {
    @Override
    public void before(HookParam param) {
        // ...
    }
});
```

## 查找范围

方法和字段默认使用智能查找：先查当前类，找不到再查父类。

- `findOnlyClass()`：只查当前类。
- `findAndSuper()`：查当前类和全部父类。
- `findSuper()` 相关错误消息里的 `Search` 只描述是否继续向上查找，不代表接口继承图。

不写这两个开关时，就是默认智能查找。

旧名称 `currentClassOnly()` / `includeSuper()` 已标记弃用，最后推荐使用版本为 `1.0.4`。

查找范围写在查询块里：

```kotlin
clazz.findMethod {
    findOnlyClass()
    name("foo")
}
```

## 查询条件

方法查询常用条件：

- `name("foo")` / `nameContains("foo")` / `nameStartsWith("get")` / `nameEndsWith("Locked")`
- `paramCount(2)` / `paramCountIn(1..3)` / `noParams()` / `hasParams()`
- `params(String::class.java)`：完整参数类型，数量和顺序必须一致。
- `paramsAssignableFrom(String::class.java)`：目标方法参数能接收这些类型。
- `parameterTypesVague(String::class.java, VagueType, Boolean::class.javaObjectType)`：参数数量固定，
  某些位置用 `VagueType` 占位跳过精确匹配，其余位置仍要求完全相等。
- `genericParameterTypes(GenericTypeMatcher.typeVariableNamed("T"))`：按擦除前的
  `Method.genericParameterTypes` 逐位匹配，可命中真正声明的泛型方法参数（`TypeVariable`），
  桥接方法（bridge method）的参数已被擦除为具体类型，不会匹配。此条件禁用查询缓存。
- `genericReturnType(GenericTypeMatcher.rawType(List::class.java))`：按擦除前的
  `Method.genericReturnType` 匹配，例如区分返回 `List<T>` 与返回 `List<String>` 的桥接方法。
  此条件禁用查询缓存。
- `returnType(String::class.java)` / `returnTypeExtendsFrom(CharSequence::class.java)` / `voidReturnType()`
- `isStatic()` / `notStatic()` / `isPublic()` / `isPrivate()` / `isProtected()`
- `isFinal()` / `isAbstract()` / `isNative()` / `isSynchronized()`
- `isVarArgs()` / `isSynthetic()` / `isBridge()` / `isDefault()`
- `filter { ... }`：自定义条件。`filter` 里再调用查找器会产生警告，建议优先使用结构化条件。

字段查询常用条件：

- `name("mContext")` / `nameContains("context")`
- `type(Context::class.java)` / `typeExtendsFrom(Context::class.java)`
- `isStatic()` / `notStatic()`
- `isPublic()` / `isPrivate()` / `isProtected()`
- `isFinal()` / `isVolatile()` / `isTransient()`
- `isEnumConstant()` / `isSynthetic()`
- `filter { ... }`：自定义条件。`filter` 里再调用查找器会产生警告，建议优先使用结构化条件。

构造器查询常用条件：

- `paramCount(2)` / `paramCountIn(1..3)` / `noParams()` / `hasParams()`
- `params(String::class.java)`：完整参数类型，数量和顺序必须一致。
- `paramsAssignableFrom(String::class.java)`：目标构造器参数能接收这些类型。
- `parameterTypesVague(String::class.java, VagueType, Boolean::class.javaObjectType)`：语义与方法查询的
  同名条件一致。
- `genericParameterTypes(GenericTypeMatcher.typeVariableNamed("T"))`：语义与方法查询的同名条件一致，
  按擦除前的 `Constructor.genericParameterTypes` 匹配。此条件禁用查询缓存。
- `isPublic()` / `isPrivate()` / `isProtected()`
- `isVarArgs()` / `isSynthetic()`
- `filter { ... }`：自定义条件。`filter` 里再调用查找器会产生警告，建议优先使用结构化条件。

Java 入口使用同义的 `filterBy...`、`filterPublic()`、`filterStatic()` 等方法。

## 查询缓存

缓存保存的是最终查找结果，不保存查找过程。

会缓存的结果：

- `loadClass` / `loadClassOrNull`：按 `ClassLoader + 类名` 缓存成功加载的类。
- `loadClassFirst` / `loadClassFirstOrNull`：按 `ClassLoader + 候选类名列表` 缓存第一个成功结果。
- `findClassIf` / `findClassIfOrNull` / `findAllClassesIf`：按结构化条件或 `cacheKey(...)` 缓存查询结果。
- `findMethod` / `findField` / `findConstructor`：按结构化查询条件缓存第一个匹配结果。
- `findAllMethods` / `findAllFields` / `findAllConstructors`：按结构化查询条件缓存完整列表。
- best-match 查找：按名称和参数类型缓存匹配结果。

不会缓存的内容：

- 找不到的类或成员。
- 没有 `cacheKey(...)` 的自定义 `filter` 查询。

`findAll` 的行为：

1. 带查询条件的 `findAll` 只扫描一次。
2. 查询可缓存，或手动指定 `cacheKey(...)` 时，会缓存完整列表。
3. 列表里的每个结果会按精确签名写入单个查找缓存。

缓存只保存在当前运行期内。高频命中的缓存会刷新访问记录；低频缓存会在内部清理条件达成后自动释放。

关闭缓存会释放已有缓存，也可以手动清除：

```kotlin
EzReflect.cacheEnabled = false
EzReflect.clearCache()
```

`init()`、`reset()` 和 `clearCache()` 都会清空缓存。

## 调用和字段读写

Kotlin：

```kotlin
val value = obj.callMethod("getValue")
obj.putField("enabled", true)
```

Java：

```java
Object value = Methods.callMethod(obj, "getValue");
Fields.setBooleanField(obj, "enabled", true);
```

## libxposed 102 intercept

`intercept` 只用于需要直接操作 `XposedInterface.Chain` 的场景。

```java
Hooks.intercept(method, chain -> {
    Object[] args = chain.getArgs().toArray();
    return chain.proceed(args);
});
```

## libxposed 102 hook ID 与替换

在默认 `EzXposed.onTargetReady` 同步初始化中，未调用 `HookFactory.id(...)` 或 `reloadKey(...)` 的
DSL / Java helper hook 会按 `executable + priority + exceptionMode` 自动聚合成一个物理 hook，并使用稳定
内部 hook ID。整个回调正常完成后才提交这些物理 hook；同一目标上新增、删除或重排逻辑 hook 都不会改变
该组 ID，新 generation 会由 framework 逐条原子替换旧组，原物理 handle 随即失效。新增或删除
executable、priority/exceptionMode 组及显式 hook ID 也受支持：新增项先安装，相同 identity 逐条替换，
最后才撤销新代码不再声明的旧项。新 generation 即使没有注册任何 hook，只要 `onTargetReady` 回调确实执行，
也会正确撤销全部旧 hook。

事务外才临时注册的 helper hook 会收到兜底内部 ID，但它不属于默认自动热重载的可靠边界；应改为同步注册，
或显式声明 `reloadKey(...)`。

`HookFactory.id(...)` 用于指定自定义 hook ID；`reloadKey(...)` 表示该 ID 是跨版本稳定契约。
`id(null)` 则明确关闭自动 ID，适用于完全自定义旧 handle 处置的进阶流程。底层仍完全使用 libxposed
原生的 hook ID / 原子替换机制；它不能放进默认 `HookReloadBatch`，应在事务外自行定义收尾边界。

```kotlin
val handle = method.createHook {
    reloadKey("license-check")
    before {
        // ...
    }
}
```

拿到旧 handle 后，用 `replaceWith` / `replaceIntercept` 用 lambda 直接替换：

```kotlin
val newHandle = handle.replaceWith { /* HookParam */ true }
```

也可以传 libxposed 原生 `Hooker`，直接走接口成员：

```kotlin
val newHandle = handle.replaceHook(myHooker)
```

替换会保留原 hook 的 `executable`、`priority`、`exceptionMode` 和 hook ID；调用成功后原 handle 不再可用。
替换后的 hook 也会沿用 `EzXposed.safeMode` 的保护。

Java 调用方：

```java
HookHandle newHandle = Hooks.replaceHook(oldHandle, methodHook);  // IMethodHook
HookHandle newHandle = Hooks.replaceHook(oldHandle, replaceHook); // IReplaceHook
HookHandle newHandle = oldHandle.replaceHook(hooker);             // 原生 Hooker

// 新建、可参与 HotReloadSession 的 Java hook：
HookHandle handle = Hooks.createHook(method, "license-check", methodHook);
```

`HookHandle.id` 是 `getId()` 的 Kotlin 直通属性。默认聚合模式返回的是逻辑 handle，其物理 ID 为工具内部
实现细节，因此会是 `null`；显式 `id(...)` / `reloadKey(...)` 的 handle 则返回对应 ID：

```kotlin
val current: String? = handle.id
```

## libxposed 102 entry detach

`EzXposed.detachCurrentEntry()` 停止 framework 向当前 module entry 分发后续生命周期回调；
已注册的 hook 与其它 `XposedInterface` API 不受影响。

适合的场景：

- 多 entry 模块里，当前 entry 检测到自己不在目标 app 中，并确认不需要热重载，立即停止接收回调。

```kotlin
override fun onPackageReady(param: PackageReadyParam) {
    if (param.packageName != TargetApp) {
        EzXposed.detachCurrentEntry()
        return
    }
    EzXposed.initOnPackageReady(param)
    // ...
}
```

`detach()` 幂等，多次调用等价于一次。该入口需要 `EzXposed.initOnModuleLoaded` 传入的是
`XposedInterfaceWrapper`（即 `XposedModule` 或其子类）；否则会抛 `IllegalStateException`。
调用后也不会再收到 `onHotReloading`，所以目标 entry 只要需要热重载就不能 detach。

## libxposed 102 热重载

API 102 的热重载由 framework 在 `onHotReloading` 时发起。要让模块 APK 更新后自动触发，
在 `META-INF/xposed/module.prop` 中声明：

```properties
minApiVersion=102
targetApiVersion=102
autoHotReload=true
```

启用 `autoHotReload` 的模块应只保留一个 Java entry。framework 不会因为热重载重新分发
`onModuleLoaded`、`onPackageLoaded`、`onPackageReady` 或 `onSystemServerStarting`；新代码只会收到
`onHotReloaded`。因此必须把当前目标进程的 classloader 等信息保存并恢复，并在该回调中重新注册规则。

### 热重载是可选特性

hook ID（`HookBuilder.setId` / `HookHandle.getId`）、`HookHandle.replaceHook`、
`XposedInterfaceWrapper.detach` 和这一整套热重载回调都是 API 102 才新增的，API 101 没有。
`hook-xposed-102` 按 102 编译，但把这些当作**可选特性**：运行时根据 framework 通过
`XposedInterface.getApiVersion()` 报告的 API 版本按需启用，因此同一份产物也能跑在只实现 API 101
的 framework 上。能力判断不反射 Xposed API，framework 启用 `PROP_RT_API_PROTECTION` 时也不会误判。

特性通过 `XposedFeature` 查询，每一项都带最低版本：

```kotlin
XposedFeature.HOOK_ID.isSupported       // HookBuilder.setId / HookHandle.getId
XposedFeature.REPLACE_HOOK.isSupported  // HookHandle.replaceHook
XposedFeature.HOT_RELOAD.isSupported    // onHotReloading / onHotReloaded
XposedFeature.DETACH_ENTRY.isSupported  // XposedInterfaceWrapper.detach

XposedFeature.HOOK_ID.minApiVersion     // 102
EzXposed.frameworkApiVersion            // framework 侧 API 版本，未初始化时为 0
```

`isSupported` 只表示 framework 是否提供能力，不会主动调用对应功能；应在 `initOnModuleLoaded` 之后查询。
初始化前所有特性都返回 `false`，该结果不会缓存。热重载仍由 `hotReloadEnabled` 手动开关，其它特性只在
调用对应 API 时使用。

依赖这些特性、且**无法降级**的公开 API 都标注了 `@RequiresXposedApi(102)`：版本不足时调用会抛出
`IllegalStateException`，报错信息里带上要求的版本、特性名和当前 framework 版本。能优雅降级的
API 不标注。完整对照：

| | API 101 framework | API 102 framework |
| --- | --- | --- |
| hook / 反射 / 资源等全部其它能力 | 可用 | 可用 |
| 自动分配 hook ID、调用 `setId` | 不执行 | 执行 |
| `HookHandle.id`、`groupById()` | 恒为 `null` | 返回底层 ID |
| `HookFactory.id(...)` / `reloadKey(...)` | 安装时抛异常 | 可用 |
| `replaceWith` / `replaceIntercept` / `replaceAll` / `Hooks.replaceHook` | 抛异常 | 可用 |
| `HotReloadSession` / `HookReloadBatch` | 构造即抛异常 | 可用 |
| `handleHotReloaded` 系列 | 抛异常 | 可用 |
| `handleHotReloading` | 记一条警告并返回 `false` | 正常保存 snapshot |
| `detachCurrentEntry()` | 抛异常 | 可用 |

### 主动关掉热重载

即使 framework 支持，模块也可以用一个开关关掉整套机制——不再分配内部 hook ID、不再调用 `setId`、
`onTargetReady` 的初始化不再进入默认聚合事务，`handleHotReloading` 直接返回 `false` 让 framework 放弃
热重载（模块自己的显式声明，不打日志）。开关必须在 `initOnModuleLoaded` 之前设置，因为默认聚合事务
在那里创建：

```kotlin
class MainHook : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        EzXposed.hotReloadEnabled = false
        EzXposed.initOnModuleLoaded(this, param)
        EzXposed.onTargetReady { installHooks() }
    }
}

EzXposed.hotReloadActive  // hotReloadEnabled && XposedFeature.HOT_RELOAD.isSupported
```

关掉后显式 `id(...)` / `reloadKey(...)` 仍然透传给 framework，`HookReloadBatch` 也照常工作；
但 `HotReloadSession.prepare` 内部走 `handleHotReloading`，同样会返回 `false`——要用 session 就不要关
这个开关。

要让模块真的能被 101 framework 加载，`module.prop` 需要放开下限，并且不要声明 `autoHotReload`：

```properties
minApiVersion=101
targetApiVersion=102
```

模块自身仍然按 102 编译。`onHotReloading` / `onHotReloaded` 这两个覆写方法的参数类型在 101 上不存在，
但 framework 永远不会调用它们，方法体也就不会被执行到，因此保留覆写是安全的。

### 默认自动模式

新模块不需要逐条写 `reloadKey` 或包一层批次。把全部**同步** hook 初始化放进
`EzXposed.onTargetReady { ... }`，并使用下列生命周期骨架：

```kotlin
class MainHook : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        EzXposed.initOnModuleLoaded(this, param)
        EzXposed.onTargetReady { installHooks() }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.isFirstPackage && param.packageName == TargetApp) {
            EzXposed.initOnPackageLoaded(param)
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.isFirstPackage && param.packageName == TargetApp) {
            EzXposed.initOnPackageReady(param)
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean =
        EzXposed.handleHotReloading(param)

    override fun onHotReloaded(param: HotReloadedParam) {
        // API 102 不会重放 onModuleLoaded；此重载会初始化新 generation、注册回调并恢复 snapshot。
        EzXposed.handleHotReloadedWithTargetReady(this, param, { installHooks() })
    }
}
```

如果 Hook 必须在 `AppComponentFactory` 创建前生效，可手动把
`initOnPackageLoaded(param)` 换成 `initOnPackageLoadedAsTargetReady(param)`。
这会在 `onPackageLoaded` 立即执行 `onTargetReady`；后续
`initOnPackageReady(param)` 会被安全忽略，初次加载和热重载都继续使用
`defaultClassLoader`，不会重复安装同一批 Hook。默认行为仍保持在
`onPackageReady` 安装。

默认事务会先完整收集 `onTargetReady` 内的逻辑 hook，再按
`executable + priority + exceptionMode` 提交稳定物理 ID。回调抛异常时不会发布这些默认物理 hook，旧 generation
保持有效。提交时先安装新增 identity，再让 framework 逐条原子替换相同 ID 的旧组；全部成功后才撤销
新代码不再声明的旧 hook。这允许开关导致的目标增删，也允许全部开关关闭。需要统计结果时，使用
`EzXposed.restoreHotReloadedAutomatically(this, param)`，它返回 `AutomaticHotReloadResult`。

libxposed 没有跨多个 hook 的整批事务。规则收集期间的异常可以保证零发布；新增 hook 安装失败时会尝试撤销
本次已经新增的项。若撤销本身失败，或底层在第 N 条旧 hook 替换时失败，进程可能包含两代实现，工具会抛出
明确错误并要求重启目标进程。这里的“原子”始终只表示单个 handle 或相同 executable + ID 的单条替换。

首次从无 hook ID 的旧版本迁移时，默认模式会拒绝热重载并要求完整重启一次目标进程；这样不会在无法核对
旧 handle 的情况下伪报成功。

### 多 hook 与功能开关

模块更新时重新读取持久设置，并用一个开关包住同一功能的全部**同步** hook 初始化即可。开关从开到关时，
新 generation 不再声明这些 hook，默认流程会在其它新 hook 安装成功后统一撤销对应旧 handle；所有开关都关闭
也属于正常结果。

```kotlin
private fun installHooks() {
    val switches = readHookSwitches()

    if (switches.loginReporter) {
        // 一个开关管理两个目标；不需要为默认模式手写 reloadKey。
        loginMethod.createBeforeHook { /* ... */ }
        reportMethod.createBeforeHook { /* ... */ }
    }

    if (switches.premium) {
        premiumMethod.createReplaceHook { true }
    }
}
```

同一 executable 上的多个无 key helper hook 会聚合到同一个物理 hook；分别开关、增删或重排逻辑 callback
不会互相覆盖。若显式使用 `reloadKey`，同一 executable 内每条逻辑 hook 必须使用不同 key。

如果开关要在模块不更新时立即生效，推荐固定安装 hook，只让 callback 读取可更新状态。`before` / `after`
关闭时直接返回；replace 类功能用 `intercept` 在关闭时放行原调用：

```kotlin
method.createInterceptHook { chain ->
    if (isFeatureEnabled()) patchedResult() else chain.proceed()
}
```

`isFeatureEnabled()` 应读取当前 remote preferences 或由 listener 更新的线程安全状态，不能复用安装 hook 时取得的
固定 snapshot。

不要把旧 generation 的 `HookHandle`、模块 data class 或 lambda 放进 saved state。开关应来自 remote preferences、
文件、系统服务等跨 generation 来源，新 generation 自己重新读取。若为了即时开关注册了 listener、receiver 或线程，
还必须按后文的 `HotReloadSession.scope` 用法在旧 generation 停止它们。

### 多作用域与多进程

scope list 可以包含多个 app；同一 app 的主进程和 remote process 也会各自创建 module entry。每个目标进程都有
独立 snapshot、hook batch 和热重载结果，不存在一次回调同时切换所有作用域。初始化函数按恢复后的
`isSystemServer`、`packageName`、`processName` 分派规则：

```kotlin
private val TargetApps = setOf("com.example.alpha", "com.example.beta")

override fun onPackageLoaded(param: PackageLoadedParam) {
    if (!param.isFirstPackage || param.packageName !in TargetApps) return
    EzXposed.initOnPackageLoaded(param)
}

override fun onPackageReady(param: PackageReadyParam) {
    if (!param.isFirstPackage || param.packageName !in TargetApps) return
    EzXposed.initOnPackageReady(param)
}

override fun onSystemServerStarting(param: SystemServerStartingParam) {
    EzXposed.initOnSystemServerStarting(param)
}

private fun installHooks() {
    when {
        EzXposed.isSystemServer -> installSystemServerHooks()
        EzXposed.packageName == "com.example.alpha" &&
            EzXposed.processName.endsWith(":remote") -> installAlphaRemoteHooks()
        EzXposed.packageName == "com.example.alpha" -> installAlphaHooks()
        EzXposed.packageName == "com.example.beta" -> installBetaHooks()
    }
}
```

默认自动模式以“一个进程中的首个目标 package”为恢复边界，因此 app 场景建议只处理 `isFirstPackage`。
如果同一进程之后通过 shared UID 或 `createPackageContext(..., CONTEXT_INCLUDE_CODE)` 加载第二个 package，并且也要
对它安装 hook，这属于多个 target-ready 时点，不能塞进已经提交的默认 batch；应使用显式 `reloadKey` 和自定义
旧 handle 管理。需要热重载的目标 entry 不要调用 `detachCurrentEntry()`。

### 自定义 identity 与收尾

少量需要在同一 executable 内跨大规模重排保持精确 identity 的 hook，可显式声明 `reloadKey`：

```kotlin
method.createHook {
    reloadKey("license-check")
    before { /* ... */ }
}
```

`HotReloadSession` 仍适用于要求每条 hook 都显式 `reloadKey` 的严格流程；默认模式内部已经使用
`HookReloadBatch` 聚合同类 hook。只有需要自定义 namespace、收尾时机或手动事务边界时，才需要直接使用
`HookReloadBatch`。两种方式都会在新 hook 成功后才处理遗留旧 handle。

完全自定义旧 handle 迁移时，传入 `onOldHooks`；工具不会再默认全量 unhook：

```kotlin
EzXposed.handleHotReloaded(
    this,
    param,
    onOldHooks = java.util.function.Consumer { oldHandles ->
        // 自行 replaceHook / unhook / 保留。
    },
)
```

`HookFactory.id(null)` 会明确关闭自动 ID；此类 hook 不能进入默认自动 batch，必须走上述自定义流程。Java 可使用
`Hooks.createHook(method, "license-check", callback)`、`Hooks.findAndHookMethodWithKey(...)` 或
`Hooks.findAndHookConstructorWithKey(...)` 声明稳定 `reloadKey`。

### 外部回调与 `HotReloadSession.scope`

需要注销 listener、receiver、binder callback 或线程时，使用一个 `HotReloadSession` 同时管理全部 hook 和外部
清理。每个 generation 都重新创建 session；session 会接管 `oldHookHandles` 的全部内容，所以不能与 raw
libxposed hook、另一个 session 或其它旧 handle 管理方式混用。session 中每条 helper hook 都必须有唯一
`reloadKey`。

```kotlin
class MainHook : XposedModule() {
    private val reloadSession = HotReloadSession()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        EzXposed.initOnModuleLoaded(this, param)
        registerTargetReady()
    }

    private fun registerTargetReady() {
        reloadSession.onTargetReady {
            watchedMethod.createHook {
                reloadKey("watched-method")
                before { /* ... */ }
            }

            val registration = registerHostListener()
            reloadSession.scope.onReloading {
                registration.unregister()
                stopModuleThreads()
            }

            // 这里只能保存 system / app classloader 对象或 String、primitive 等中立值。
            currentHostToken()?.let { reloadSession.scope.putState("host-token", it) }
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean =
        reloadSession.prepare(param)

    override fun onHotReloaded(param: HotReloadedParam) {
        // framework 不会重放 onModuleLoaded。
        EzXposed.initOnModuleLoaded(this, param)
        registerTargetReady()
        reloadSession.restore(this, param) { extra ->
            val token = reloadSession.scope.state("host-token")
            // 在 target-ready callback 前恢复其它中立状态。
        }
    }
}
```

`prepare` 只有在 snapshot 可恢复时才执行清理，并按登记的相反顺序调用 cleanup。cleanup 完成后旧 generation 的
外部注册已经停止；如果新 generation 随后恢复失败，不能继续假设旧运行状态完整，应重启目标进程。saved state
不得包含模块类、模块 lambda、模块 `ClassLoader` 或间接持有它们的容器。

## 错误契约

- descriptor 格式错误抛 `IllegalArgumentException`。
- descriptor 合法但目标成员不存在，严格入口抛 `MemberNotFoundException`，`OrNull` 入口返回 `null`。
- `MemberNotFoundException` 的 `Search` 只描述当前类或当前类 + 父类，不包含接口继承图。

## safeMode 阶段语义

`EzXposed.safeMode = true` 时，hook callback 阶段性失败会按以下规则回退，避免目标 app 因模块异常崩溃：

**before / replace 阶段：**

callback 失败时回退原调用，恢复 framework 传入的初始 `thisObject` / `args` / `result` / `throwable`，让原方法执行一次并返回真实结果。

```kotlin
method.createHook {
    before { param ->
        param.result = fetchCache()  // 若 fetchCache() 抛异常，before callback 整体失败
    }
}
// safeMode: before 失败 → 恢复初始状态 → 执行原方法 → 返回原方法结果
```

**after 阶段：**

callback 失败时保留下游已执行的原方法结果或异常，**不会重复执行原方法**。

```kotlin
method.createHook {
    after { param ->
        logResult(param.result)  // 若 logResult 抛异常，after callback 失败
    }
}
// safeMode: after 失败 → 保留原方法已返回的 result/throwable → 直接传播给调用方
```

**intercept 阶段：**

未调用 `proceed()` 时失败行为同 replace；已调用 `proceed()` 时保留下游结果，不重复执行。

```kotlin
method.createHook {
    intercept { chain ->
        val start = System.nanoTime()
        val result = chain.proceed()  // 下游已执行
        logTiming(System.nanoTime() - start)  // 若 logTiming 抛异常
        result
    }
}
// safeMode: intercept 已 proceed 后失败 → 保留 proceed 返回的结果 → 传播给调用方
```

**原方法自身异常：**

原方法抛的异常直接传播给调用方，不视为 callback 失败，safeMode 不拦截。

```kotlin
method.createHook {
    before { param ->
        // 修改参数，让原方法内部抛 IllegalArgumentException
    }
}
// 原方法抛的 IllegalArgumentException 会原样抛给调用方，safeMode 不干预
```

### 外部回调与跨代宿主状态

hook 之外的 listener、receiver、binder callback、线程或资源 observer 不是 `HookHandle`，必须由模块自己
取消注册。需要统一保存状态和清理回调时可使用 `HotReloadSession.scope`；它只接受 system、system_server
或目标 app classloader 创建的对象。模块自身的 data class、lambda、匿名对象，或包含这些对象的容器不能跨代
保存，framework 仍会做最终校验。

### 不适合承诺“无缝”的情况

默认模式能逐条原子替换的是**已经存在、在 `onTargetReady` 同步回调内声明的代码 hook**，不是任意运行时副作用：

- 首次调用、异步任务或回调内部才创建的延迟 hook，无法在 `onHotReloaded` 时组成可靠的同步序列。应改为
  在 `onTargetReady` 同步安装，或使用显式 `reloadKey` 加自定义清理边界。
- 目标已经 ready 后才新增的 `onTargetReady` 回调会立即执行，但不属于已提交的默认聚合事务；这类规则应
  使用显式 `reloadKey` 或自定义旧 handle 收尾。
- `Resources`、`AssetManager`、`ResourcesLoader`、已 inflate 的 View、静态缓存和 SystemUI 资源缓存
  可能仍指向旧 APK 或已计算结果。热重载不会自动重跑资源覆盖、重新 inflate UI 或清空宿主缓存；这类
  改动需要模块自行撤销并重新应用，若宿主没有可逆 API，就应回退到重启目标进程。
- 已启动的线程和向 framework/系统服务注册的外部回调必须在热重载前停止或注销，否则旧 module classloader
  仍可能被引用。

### 兼容入口

`EzXposed.handleHotReloading` / `handleHotReloaded` 仍是默认低成本入口；
`handleHotReloadedWithTargetReady` 可在 API 102 不重放生命周期时一并注册新 generation 的同步规则。
`EzXposed.safeMode` 只负责回退 hook callback 的阶段性失败：before / replace / intercept 失败会回退原调用；after 失败只保留下游原结果，不会重复执行原方法。
与旧版本不同，默认流程不再先 unhook 全部旧 handle。手动处理旧 handle 时，辅助函数仍可使用：

- `oldHandles.groupById()` → `Map<String?, List<HookHandle>>`
- `oldHandles.replaceAll(hooker)` → `List<HookHandle>`，按原顺序返回新 handle
- `oldHandles.unhookAll()`：尝试全部 unhook，最后统一报告所有失败
