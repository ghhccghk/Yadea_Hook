package com.ghhccghk.yadeahook.provider

import android.content.Context
import android.content.Intent

object HookLogger {
    const val ACTION = "com.ghhccghk.yadeahook.LOG"
    private const val THROTTLE_MS = 500L
    private var lastMsg = ""
    private var lastTime = 0L

    fun log(context: Context, tag: String, message: String) {
        try {
            val now = System.currentTimeMillis()
            val key = "$tag|$message"
            if (key == lastMsg && now - lastTime < THROTTLE_MS) return
            lastMsg = key
            lastTime = now
            context.sendBroadcast(Intent(ACTION).apply {
                putExtra("tag", tag)
                putExtra("msg", message)
                putExtra("ts", now)
                setPackage("com.ghhccghk.yadeahook")
            })
        } catch (_: Throwable) { }
    }
}
