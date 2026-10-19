package com.ghhccghk.yadeahook

import android.content.Context
import android.content.SharedPreferences

/**
 * 模块功能开关和速度警报配置的统一入口。
 */
object FeatureSettings {
    const val SPEED_ALERT_PREFS = "speed_alert_prefs"
    const val KEY_SPEED_ALERT_ENABLED = "enabled"
    const val KEY_SPEED_THRESHOLD = "speed_threshold"
    const val KEY_COOLDOWN_MS = "cooldown_ms"
    const val KEY_AUDIO_PATH = "audio_path"
    const val KEY_LAST_ALERT_AT = "last_alert_at"

    fun speedAlertPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SPEED_ALERT_PREFS, Context.MODE_PRIVATE)

    fun isSpeedAlertEnabled(context: Context): Boolean =
        speedAlertPrefs(context).getBoolean(KEY_SPEED_ALERT_ENABLED, true)

    fun setSpeedAlertEnabled(context: Context, enabled: Boolean) {
        speedAlertPrefs(context)
            .edit()
            .putBoolean(KEY_SPEED_ALERT_ENABLED, enabled)
            .apply()
    }
}
