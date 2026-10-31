package com.ghhccghk.yadeahook.settings

import io.github.lingqiqi5211.meowui.core.preference.InMemoryPreferenceStore
import io.github.lingqiqi5211.meowui.core.preference.PreferenceConnectionState
import io.github.lingqiqi5211.meowui.core.preference.PreferenceKey
import io.github.lingqiqi5211.meowui.core.preference.PreferenceStore
import io.github.lingqiqi5211.meowui.core.preference.PreferenceWriteResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class YadeaSettingsRepositoryTest {
    @Test
    fun missingValuesUseSharedDefaults() {
        val repository = YadeaSettingsRepository(InMemoryPreferenceStore())

        assertEquals(OverlaySettings(), repository.readOverlaySettings())
        assertEquals("yadeahook_settings", YadeaSettingsKeys.PREFERENCE_NAME)
        assertEquals(true, YadeaSettingsKeys.overlayEnabled.defaultValue)
        assertEquals(1.3f, YadeaSettingsKeys.overlayDensity.defaultValue)
        assertEquals(1.6f, YadeaSettingsKeys.overlayFontScale.defaultValue)
        assertEquals(30f, YadeaSettingsKeys.overlayFrameRate.defaultValue)
    }

    @Test
    fun falseAndZeroAreValidStoredValues() = runBlocking {
        val store = InMemoryPreferenceStore()
        val repository = YadeaSettingsRepository(store)
        store.write(YadeaSettingsKeys.overlayEnabled, false)
        store.write(YadeaSettingsKeys.overlayTop, 0f)

        val settings = repository.readOverlaySettings()

        assertFalse(settings.enabled)
        assertEquals(0f, settings.top)
    }

    @Test
    fun persistedOutOfRangeValuesAreNormalizedOnRead() = runBlocking {
        val store = InMemoryPreferenceStore()
        store.write(YadeaSettingsKeys.overlayDensity, 99f)
        store.write(YadeaSettingsKeys.overlayWidthRatio, Float.NaN)
        store.write(YadeaSettingsKeys.overlayFrameRate, 1f)

        val settings = YadeaSettingsRepository(store).readOverlaySettings()

        assertEquals(2f, settings.density)
        assertEquals(1f, settings.widthRatio)
        assertEquals(5f, settings.frameRate)
    }

    @Test
    fun writesAreNormalizedBeforePersistence() = runBlocking {
        val store = InMemoryPreferenceStore()
        val repository = YadeaSettingsRepository(store)

        val result = repository.write(YadeaSettingsKeys.overlayStart, -20f)
        val frameRateResult = repository.write(YadeaSettingsKeys.overlayFrameRate, 90f)

        assertEquals(PreferenceWriteResult.Success, result)
        assertEquals(0f, store.read(YadeaSettingsKeys.overlayStart))
        assertEquals(PreferenceWriteResult.Success, frameRateResult)
        assertEquals(60f, store.read(YadeaSettingsKeys.overlayFrameRate))
    }

    @Test
    fun readFailuresFallBackPerSetting() {
        val repository = YadeaSettingsRepository(FailingReadStore())

        assertEquals(OverlaySettings(), repository.readOverlaySettings())
    }

    @Test
    fun writeFailuresAreReturnedInsteadOfThrown() = runBlocking {
        val failure = IOException("remote unavailable")
        val repository = YadeaSettingsRepository(FailingWriteStore(failure))

        val result = repository.write(YadeaSettingsKeys.overlayEnabled, false)

        assertTrue(result is PreferenceWriteResult.Failure)
        assertEquals(failure, (result as PreferenceWriteResult.Failure).cause)
    }

    @Test
    fun observationStopsAllRemoteListenersWhenCancelled() = runBlocking {
        val store = TrackingStore()
        val repository = YadeaSettingsRepository(store)

        val values = repository.observeOverlaySettings().take(1).toList()

        assertEquals(listOf(OverlaySettings()), values)
        assertEquals(0, store.activeObservers.get())
    }

    @Test
    fun observedChangesAreNormalizedAndEmitted() = runBlocking {
        val store = InMemoryPreferenceStore()
        val repository = YadeaSettingsRepository(store)
        val observed = repository.observeOverlaySettings()

        store.write(YadeaSettingsKeys.overlayTop, 500f)

        assertEquals(200f, observed.first().top)
    }

    private class FailingReadStore : PreferenceStore {
        override val connectionState: StateFlow<PreferenceConnectionState> =
            InMemoryPreferenceStore().connectionState

        override fun <T : Any> read(key: PreferenceKey<T>): T =
            throw IllegalStateException("read failed")

        override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> = flow {
            throw IllegalStateException("observe failed")
        }

        override suspend fun <T : Any> write(
            key: PreferenceKey<T>,
            value: T,
        ): PreferenceWriteResult = PreferenceWriteResult.Success

        override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult =
            PreferenceWriteResult.Success

        override suspend fun clear(): PreferenceWriteResult = PreferenceWriteResult.Success
    }

    private class FailingWriteStore(
        private val failure: Throwable,
    ) : PreferenceStore {
        private val delegate = InMemoryPreferenceStore()
        override val connectionState: StateFlow<PreferenceConnectionState>
            get() = delegate.connectionState

        override fun <T : Any> read(key: PreferenceKey<T>): T = delegate.read(key)

        override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> = delegate.observe(key)

        override suspend fun <T : Any> write(
            key: PreferenceKey<T>,
            value: T,
        ): PreferenceWriteResult = throw failure

        override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult =
            throw failure

        override suspend fun clear(): PreferenceWriteResult = throw failure
    }

    private class TrackingStore : PreferenceStore {
        private val delegate = InMemoryPreferenceStore()
        val activeObservers = AtomicInteger()
        override val connectionState: StateFlow<PreferenceConnectionState>
            get() = delegate.connectionState

        override fun <T : Any> read(key: PreferenceKey<T>): T = delegate.read(key)

        override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> = flow {
            activeObservers.incrementAndGet()
            try {
                emit(delegate.read(key))
                awaitCancellation()
            } finally {
                activeObservers.decrementAndGet()
            }
        }

        override suspend fun <T : Any> write(
            key: PreferenceKey<T>,
            value: T,
        ): PreferenceWriteResult = delegate.write(key, value)

        override suspend fun delete(key: PreferenceKey<*>): PreferenceWriteResult =
            delegate.delete(key)

        override suspend fun clear(): PreferenceWriteResult = delegate.clear()
    }
}
