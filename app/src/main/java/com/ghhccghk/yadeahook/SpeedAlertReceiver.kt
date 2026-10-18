package com.ghhccghk.yadeahook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.util.Log
import java.io.File

class SpeedAlertReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.ghhccghk.yadeahook.SPEED_ALERT"
        const val EXTRA_SPEED = "speed"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val speed = intent.getFloatExtra(EXTRA_SPEED, 0f)

        val prefs = FeatureSettings.speedAlertPrefs(context)
        if (!prefs.getBoolean(FeatureSettings.KEY_SPEED_ALERT_ENABLED, true)) return

        val speedThreshold = prefs.getFloat(FeatureSettings.KEY_SPEED_THRESHOLD, 25f)
        if (!speed.isFinite() || speed < speedThreshold) return

        val now = System.currentTimeMillis()
        val cooldownMs = prefs.getLong(FeatureSettings.KEY_COOLDOWN_MS, 10_000L)
            .coerceAtLeast(0L)
        val lastAlertAt = prefs.getLong(FeatureSettings.KEY_LAST_ALERT_AT, 0L)
        if (lastAlertAt > 0 && now - lastAlertAt < cooldownMs) return

        prefs.edit()
            .putLong(FeatureSettings.KEY_LAST_ALERT_AT, now)
            .apply()
        Log.d("YadeaHook", "收到速度警报广播: $speed km/h (阈值: $speedThreshold km/h)")
        playAlertSound(context, prefs)
    }

    private fun playAlertSound(context: Context, prefs: SharedPreferences) {
        try {
            val customPath = prefs.getString(FeatureSettings.KEY_AUDIO_PATH, "")

            if (!customPath.isNullOrEmpty() && File(customPath).exists()) {
                playCustomAudio(context, customPath)
            } else {
                playDefaultAudio(context)
            }
        } catch (e: Throwable) {
            Log.e("YadeaHook", "播放音频失败", e)
            playDefaultAudio(context)
        }
    }

    private fun playCustomAudio(context: Context, path: String) {
        try {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener { mp ->
                    mp.release()
                }
            }
            Log.d("YadeaHook", "播放自定义音频: ")
        } catch (e: Throwable) {
            Log.e("YadeaHook", "播放自定义音频失败，回退到默认", e)
            playDefaultAudio(context)
        }
    }

    private fun playDefaultAudio(context: Context) {
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.beep)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
            }
            mediaPlayer?.start()
            Log.d("YadeaHook", "播放默认音频")
        } catch (e: Throwable) {
            Log.e("YadeaHook", "播放默认音频失败", e)
        }
    }
}
