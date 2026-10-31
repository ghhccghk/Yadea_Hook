package com.ghhccghk.yadeahook.settings

data class OverlaySettings(
    val enabled: Boolean = OverlaySettingSpecs.enabled.defaultValue,
    val showSource: Boolean = OverlaySettingSpecs.showSource.defaultValue,
    val showTranslation: Boolean = OverlaySettingSpecs.showTranslation.defaultValue,
    val density: Float = OverlaySettingSpecs.density.defaultValue,
    val fontScale: Float = OverlaySettingSpecs.fontScale.defaultValue,
    val top: Float = OverlaySettingSpecs.top.defaultValue,
    val start: Float = OverlaySettingSpecs.start.defaultValue,
    val end: Float = OverlaySettingSpecs.end.defaultValue,
    val backgroundAlpha: Float = OverlaySettingSpecs.backgroundAlpha.defaultValue,
    val widthRatio: Float = OverlaySettingSpecs.widthRatio.defaultValue,
    val frameRate: Float = OverlaySettingSpecs.frameRate.defaultValue,
)

data class BooleanSettingSpec(
    val defaultValue: Boolean,
)

data class FloatSettingSpec(
    val defaultValue: Float,
    val minimum: Float,
    val maximum: Float,
) {
    init {
        require(minimum.isFinite() && maximum.isFinite()) {
            "Setting range must be finite."
        }
        require(minimum <= maximum) { "Setting maximum must not be below minimum." }
        require(defaultValue in minimum..maximum) {
            "Setting default must be within its range."
        }
    }

    fun normalize(value: Float): Float = when {
        !value.isFinite() -> defaultValue
        value < minimum -> minimum
        value > maximum -> maximum
        else -> value
    }
}

/** Shared defaults and accepted ranges for the first lyric-overlay settings batch. */
object OverlaySettingSpecs {
    val enabled = BooleanSettingSpec(true)
    val showSource = BooleanSettingSpec(true)
    val showTranslation = BooleanSettingSpec(true)

    /** Compose Density.density. It scales both dp and sp values. */
    val density = FloatSettingSpec(1.3f, minimum = 0.5f, maximum = 2f)

    /** Compose Density.fontScale. It additionally scales sp values. */
    val fontScale = FloatSettingSpec(1.6f, minimum = 0.5f, maximum = 3f)

    val top = FloatSettingSpec(42f, minimum = 0f, maximum = 200f)
    val start = FloatSettingSpec(300f, minimum = 0f, maximum = 500f)
    val end = FloatSettingSpec(30f, minimum = 0f, maximum = 200f)

    /** 0 is transparent and preserves the original overlay appearance. */
    val backgroundAlpha = FloatSettingSpec(0f, minimum = 0f, maximum = 1f)

    /** Fraction of the post-padding content width. 1 preserves fillMaxWidth behavior. */
    val widthRatio = FloatSettingSpec(1f, minimum = 0.5f, maximum = 1f)

    /** Maximum per-word lyric recomposition rate in frames per second. */
    val frameRate = FloatSettingSpec(30f, minimum = 5f, maximum = 60f)
}
