package com.ghhccghk.yadeahook.settings

import io.github.lingqiqi5211.meowui.core.preference.PreferenceConnectionState
import io.github.lingqiqi5211.meowui.core.preference.PreferenceKey
import io.github.lingqiqi5211.meowui.core.preference.PreferenceStore
import io.github.lingqiqi5211.meowui.core.preference.PreferenceWriteResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * Typed access to the first lyric-overlay settings batch.
 *
 * All reads and observations are isolated from remote-storage failures. Invalid persisted
 * values are normalized on read; writes through this repository are normalized before commit.
 */
class YadeaSettingsRepository(
    private val store: PreferenceStore,
) {
    val connectionState: StateFlow<PreferenceConnectionState>
        get() = store.connectionState

    fun readOverlaySettings(): OverlaySettings = OverlaySettings(
        enabled = readBoolean(YadeaSettingsKeys.overlayEnabled),
        showSource = readBoolean(YadeaSettingsKeys.overlayShowSource),
        showTranslation = readBoolean(YadeaSettingsKeys.overlayShowTranslation),
        density = readFloat(YadeaSettingsKeys.overlayDensity, OverlaySettingSpecs.density),
        fontScale = readFloat(YadeaSettingsKeys.overlayFontScale, OverlaySettingSpecs.fontScale),
        top = readFloat(YadeaSettingsKeys.overlayTop, OverlaySettingSpecs.top),
        start = readFloat(YadeaSettingsKeys.overlayStart, OverlaySettingSpecs.start),
        end = readFloat(YadeaSettingsKeys.overlayEnd, OverlaySettingSpecs.end),
        backgroundAlpha = readFloat(
            YadeaSettingsKeys.overlayBackgroundAlpha,
            OverlaySettingSpecs.backgroundAlpha,
        ),
        widthRatio = readFloat(YadeaSettingsKeys.overlayWidthRatio, OverlaySettingSpecs.widthRatio),
        frameRate = readFloat(YadeaSettingsKeys.overlayFrameRate, OverlaySettingSpecs.frameRate),
    )

    fun observeOverlaySettings(): Flow<OverlaySettings> {
        val flags = combine(
            observeBoolean(YadeaSettingsKeys.overlayEnabled, OverlaySettingSpecs.enabled),
            observeBoolean(YadeaSettingsKeys.overlayShowSource, OverlaySettingSpecs.showSource),
            observeBoolean(
                YadeaSettingsKeys.overlayShowTranslation,
                OverlaySettingSpecs.showTranslation,
            ),
        ) { enabled, showSource, showTranslation ->
            BooleanSettings(enabled, showSource, showTranslation)
        }
        val typography = combine(
            observeFloat(YadeaSettingsKeys.overlayDensity, OverlaySettingSpecs.density),
            observeFloat(YadeaSettingsKeys.overlayFontScale, OverlaySettingSpecs.fontScale),
            observeFloat(YadeaSettingsKeys.overlayTop, OverlaySettingSpecs.top),
        ) { density, fontScale, top ->
            TypographySettings(density, fontScale, top)
        }
        val horizontalInsets = combine(
            observeFloat(YadeaSettingsKeys.overlayStart, OverlaySettingSpecs.start),
            observeFloat(YadeaSettingsKeys.overlayEnd, OverlaySettingSpecs.end),
        ) { start, end ->
            HorizontalInsets(start, end)
        }
        val appearance = combine(
            observeFloat(
                YadeaSettingsKeys.overlayBackgroundAlpha,
                OverlaySettingSpecs.backgroundAlpha,
            ),
            observeFloat(YadeaSettingsKeys.overlayWidthRatio, OverlaySettingSpecs.widthRatio),
        ) { backgroundAlpha, widthRatio ->
            AppearanceSettings(backgroundAlpha, widthRatio)
        }
        val frameRate = observeFloat(
            YadeaSettingsKeys.overlayFrameRate,
            OverlaySettingSpecs.frameRate,
        )

        return combine(flags, typography, horizontalInsets, appearance, frameRate) {
                booleanSettings,
                typographySettings,
                insets,
                appearanceSettings,
                lyricFrameRate,
            ->
            OverlaySettings(
                enabled = booleanSettings.enabled,
                showSource = booleanSettings.showSource,
                showTranslation = booleanSettings.showTranslation,
                density = typographySettings.density,
                fontScale = typographySettings.fontScale,
                top = typographySettings.top,
                start = insets.start,
                end = insets.end,
                backgroundAlpha = appearanceSettings.backgroundAlpha,
                widthRatio = appearanceSettings.widthRatio,
                frameRate = lyricFrameRate,
            )
        }
    }

    suspend fun <T : Any> write(
        key: PreferenceKey<T>,
        value: T,
    ): PreferenceWriteResult {
        return try {
            store.write(key, normalizeWriteValue(key, value))
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            PreferenceWriteResult.Failure(cause)
        }
    }

    private fun readBoolean(key: PreferenceKey<Boolean>): Boolean = try {
        store.read(key)
    } catch (cause: Throwable) {
        key.defaultValue
    }

    private fun readFloat(key: PreferenceKey<Float>, spec: FloatSettingSpec): Float = try {
        spec.normalize(store.read(key))
    } catch (cause: Throwable) {
        spec.defaultValue
    }

    private fun observeBoolean(
        key: PreferenceKey<Boolean>,
        spec: BooleanSettingSpec,
    ): Flow<Boolean> = safeObserve(key, spec.defaultValue) { it }

    private fun observeFloat(
        key: PreferenceKey<Float>,
        spec: FloatSettingSpec,
    ): Flow<Float> = safeObserve(key, spec.defaultValue, spec::normalize)

    private fun <T : Any> safeObserve(
        key: PreferenceKey<T>,
        fallback: T,
        normalize: (T) -> T,
    ): Flow<T> = flow {
        try {
            store.observe(key).collect { value ->
                emit(normalize(value))
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            emit(normalize(fallback))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> normalizeWriteValue(
        key: PreferenceKey<T>,
        value: T,
    ): T {
        val spec = floatSpecFor(key.name) ?: return value
        val rawValue = value as? Float ?: return value
        return spec.normalize(rawValue) as T
    }

    private fun floatSpecFor(keyName: String): FloatSettingSpec? = when (keyName) {
        YadeaSettingsKeys.OVERLAY_DENSITY -> OverlaySettingSpecs.density
        YadeaSettingsKeys.OVERLAY_FONT_SCALE -> OverlaySettingSpecs.fontScale
        YadeaSettingsKeys.OVERLAY_TOP -> OverlaySettingSpecs.top
        YadeaSettingsKeys.OVERLAY_START -> OverlaySettingSpecs.start
        YadeaSettingsKeys.OVERLAY_END -> OverlaySettingSpecs.end
        YadeaSettingsKeys.OVERLAY_BACKGROUND_ALPHA -> OverlaySettingSpecs.backgroundAlpha
        YadeaSettingsKeys.OVERLAY_WIDTH_RATIO -> OverlaySettingSpecs.widthRatio
        YadeaSettingsKeys.OVERLAY_FRAME_RATE -> OverlaySettingSpecs.frameRate
        else -> null
    }

    private data class BooleanSettings(
        val enabled: Boolean,
        val showSource: Boolean,
        val showTranslation: Boolean,
    )

    private data class TypographySettings(
        val density: Float,
        val fontScale: Float,
        val top: Float,
    )

    private data class HorizontalInsets(
        val start: Float,
        val end: Float,
    )

    private data class AppearanceSettings(
        val backgroundAlpha: Float,
        val widthRatio: Float,
    )
}
