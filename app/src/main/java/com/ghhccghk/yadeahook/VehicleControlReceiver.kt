package com.ghhccghk.yadeahook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class VehicleControlReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.ghhccghk.yadeahook.CONTROL"
        const val TAG = "YadeaHook"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG,"command : ${intent.getStringExtra("command")},param: ${intent.getIntExtra("param", 0)}")
        val command = intent.getStringExtra("command") ?: return
        val param = intent.getIntExtra("param", 0)

        when (command) {
            "disconnect" -> VehicleController.disconnect()
            "cancel_scan" -> VehicleController.cancelScan()
            "connect_scooter" -> VehicleController.connect("scooter", intent.getStringExtra("mac") ?: "")
            "connect_bicycle" -> VehicleController.connect("bicycle", intent.getStringExtra("mac") ?: "")
            else -> VehicleController.execute(command, param)
        }
    }
}
