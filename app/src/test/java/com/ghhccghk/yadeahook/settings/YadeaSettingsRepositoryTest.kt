package com.ghhccghk.yadeahook.settings

import io.github.lingqiqi5211.meowui.core.preference.InMemoryPreferenceStore
import io.github.lingqiqi5211.meowui.core.preference.PreferenceConnectionState
import io.github.lingqiqi5211.meowui.core.preference.PreferenceKey
import io.github.lingqiqi5211.meowui.core.preference.PreferenceStore
import io.github.lingqiqi5211.meowui.core.preference.PreferenceType
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
    }

    @Test
    fun sharedKeysMatchTheirNamesTypesAndDefaults() {
        assertKey(
            YadeaSettingsKeys.overlayEnabled,
            YadeaSettingsKeys.OVERLAY_ENABLED,
            PreferenceType.Boolean,
            OverlaySettingSpecs.enabled.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayShowSource,
            YadeaSettingsKeys.OVERLAY_SHOW_SOURCE,
            PreferenceType.Boolean,
            OverlaySettingSpecs.showSource.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayShowTranslation,
            YadeaSettingsKeys.OVERLAY_SHOW_TRANSLATION,
            PreferenceType.Boolean,
            OverlaySettingSpecs.showTranslation.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayDensity,
            YadeaSettingsKeys.OVERLAY_DENSITY,
            PreferenceType.Float,
            OverlaySettingSpecs.density.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayFontScale,
            YadeaSettingsKeys.OVERLAY_FONT_SCALE,
            PreferenceType.Float,
            OverlaySettingSpecs.fontScale.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayTop,
            YadeaSettingsKeys.OVERLAY_TOP,
            PreferenceType.Float,
            OverlaySettingSpecs.top.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayStart,
            YadeaSettingsKeys.OVERLAY_START,
            PreferenceType.Float,
            OverlaySettingSpecs.start.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayEnd,
            YadeaSettingsKeys.OVERLAY_END,
            PreferenceType.Float,
            OverlaySettingSpecs.end.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayBackgroundAlpha,
            YadeaSettingsKeys.OVERLAY_BACKGROUND_ALPHA,
            PreferenceType.Float,
            OverlaySettingSpecs.backgroundAlpha.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayWidthRatio,
            YadeaSettingsKeys.OVERLAY_WIDTH_RATIO,
            PreferenceType.Float,
            OverlaySettingSpecs.widthRatio.defaultValue,
        )
        assertKey(
            YadeaSettingsKeys.overlayFrameRate,
            YadeaSettingsKeys.OVERLAY_FRAME_RATE,
            PreferenceType.Float,
            OverlaySettingSpecs.frameRate.defaultValue,
        )
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
    fun notConnectedWriteResultIsPreserved() = runBlocking {
        val disconnected = PreferenceConnectionState.Disconnected(IOException("service died"))
        val repository = YadeaSettingsRepository(
            InMemoryPreferenceStore(initialConnectionState = disconnected),
        )

        val result = repository.write(YadeaSettingsKeys.overlayEnabled, false)

        assertEquals(PreferenceWriteResult.NotConnected(disconnected), result)
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

    @Test
    fun observerPreservesFalseAndZeroValues() = runBlocking {
        val store = InMemoryPreferenceStore()
        val repository = YadeaSettingsRepository(store)

        store.write(YadeaSettingsKeys.overlayEnabled, false)
        store.write(YadeaSettingsKeys.overlayBackgroundAlpha, 0f)

        val settings = repository.observeOverlaySettings().first()

        assertFalse(settings.enabled)
        assertEquals(0f, settings.backgroundAlpha)
    }

    private fun <T : Any> assertKey(
        key: PreferenceKey<T>,
        expectedName: String,
        expectedType: PreferenceType<T>,
        expectedDefault: T,
    ) {
        assertEquals(expectedName, key.name)
        assertEquals(expectedType, key.type)
        assertEquals(expectedDefault, key.defaultValue)
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
