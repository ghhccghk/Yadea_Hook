package com.ghhccghk.yadeahook.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import com.ghhccghk.yadeahook.provider.HookLogger

data class LogEntry(val timestamp: Long, val tag: String, val message: String)

class LogReceiver : BroadcastReceiver() {
    companion object {
        val logs = mutableStateListOf<LogEntry>()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val tag = intent.getStringExtra("tag") ?: return
        val msg = intent.getStringExtra("msg") ?: return
        val ts = intent.getLongExtra("ts", System.currentTimeMillis())
        logs.add(LogEntry(ts, tag, msg))
        if (logs.size > 500) {
            logs.removeRange(0, logs.size - 500)
        }
    }
}
