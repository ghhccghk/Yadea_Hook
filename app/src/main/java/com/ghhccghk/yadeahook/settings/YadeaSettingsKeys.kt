package com.ghhccghk.yadeahook.settings

import io.github.lingqiqi5211.meowui.core.preference.PreferenceKey

/** Shared keys used by both the settings process and the hooked target process. */
object YadeaSettingsKeys {
    const val PREFERENCE_NAME = "yadeahook_settings"

    const val OVERLAY_ENABLED = "overlay_enabled"
    const val OVERLAY_SHOW_SOURCE = "overlay_show_source"
    const val OVERLAY_SHOW_TRANSLATION = "overlay_show_translation"
    const val OVERLAY_DENSITY = "overlay_density"
    const val OVERLAY_FONT_SCALE = "overlay_font_scale"
    const val OVERLAY_TOP = "overlay_top"
    const val OVERLAY_START = "overlay_start"
    const val OVERLAY_END = "overlay_end"
    const val OVERLAY_BACKGROUND_ALPHA = "overlay_background_alpha"
    const val OVERLAY_WIDTH_RATIO = "overlay_width_ratio"

    val overlayEnabled = PreferenceKey.boolean(
        OVERLAY_ENABLED,
        OverlaySettingSpecs.enabled.defaultValue,
    )
    val overlayShowSource = PreferenceKey.boolean(
        OVERLAY_SHOW_SOURCE,
        OverlaySettingSpecs.showSource.defaultValue,
    )
    val overlayShowTranslation = PreferenceKey.boolean(
        OVERLAY_SHOW_TRANSLATION,
        OverlaySettingSpecs.showTranslation.defaultValue,
    )
    val overlayDensity = PreferenceKey.float(
        OVERLAY_DENSITY,
        OverlaySettingSpecs.density.defaultValue,
    )
    val overlayFontScale = PreferenceKey.float(
        OVERLAY_FONT_SCALE,
        OverlaySettingSpecs.fontScale.defaultValue,
    )
    val overlayTop = PreferenceKey.float(
        OVERLAY_TOP,
        OverlaySettingSpecs.top.defaultValue,
    )
    val overlayStart = PreferenceKey.float(
        OVERLAY_START,
        OverlaySettingSpecs.start.defaultValue,
    )
    val overlayEnd = PreferenceKey.float(
        OVERLAY_END,
        OverlaySettingSpecs.end.defaultValue,
    )
    val overlayBackgroundAlpha = PreferenceKey.float(
        OVERLAY_BACKGROUND_ALPHA,
        OverlaySettingSpecs.backgroundAlpha.defaultValue,
    )
    val overlayWidthRatio = PreferenceKey.float(
        OVERLAY_WIDTH_RATIO,
        OverlaySettingSpecs.widthRatio.defaultValue,
    )
}
