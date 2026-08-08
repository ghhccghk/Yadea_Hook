package com.ghhccghk.yadeahook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StatusUpdateReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.ghhccghk.yadeahook.UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        VehicleStatusStore.broadcast()
    }
}
