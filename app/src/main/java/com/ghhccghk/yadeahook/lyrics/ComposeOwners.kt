package com.ghhccghk.yadeahook.lyrics

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * 模块侧 ComposeView 使用的生命周期宿主。
 *
 * 不能直接使用目标 App 的 ComponentActivity：目标 App 和模块各自加载的 androidx
 * 类可能属于不同 ClassLoader，接口身份不相等。
 */
internal class ModuleComposeLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = registry

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

internal class ModuleComposeSavedStateOwner(
    private val lifecycleOwner: LifecycleOwner
) : SavedStateRegistryOwner {
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleOwner.lifecycle

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    init {
        controller.performRestore(null)
    }
}

internal class ModuleComposeViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
