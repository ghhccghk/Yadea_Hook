package com.ghhccghk.yadeahook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        
        // 读取设置
        val prefs = context.getSharedPreferences("speed_alert_prefs", Context.MODE_PRIVATE)
        val speedThreshold = prefs.getFloat("speed_threshold", 25f)
        
        Log.d("YadeaHook", "收到速度警报广播:  $speed km/h (阈值:  $speedThreshold km/h)")
        
        // 播放音频
        if (speed >= speedThreshold) {
            playAlertSound(context)
        }
    }

    private fun playAlertSound(context: Context) {
        try {
            val prefs = context.getSharedPreferences("speed_alert_prefs", Context.MODE_PRIVATE)
            val customPath = prefs.getString("audio_path", "")
            
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
