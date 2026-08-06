package com.ghhccghk.yadeahook

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("StaticFieldLeak")
object VehicleStatusStore {
    const val ACTION = "com.ghhccghk.yadeahook.STATUS"

    private val _data = mutableMapOf<String, String>()
    val data: Map<String, String> get() = _data
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun update(newData: Map<String, String>) {
        if (newData.all { _data[it.key] == it.value }) return
        _data.putAll(newData)
        broadcast()
    }

    fun get(key: String): String? = _data[key]

    private fun broadcast() {
        try {
            val ctx = context ?: return
            val intent = Intent(ACTION).apply {
                putExtra("data", buildJson())
            }
            ctx.sendBroadcast(intent)
        } catch (_: Throwable) { }
    }

    private fun buildJson(): String {
        val now = timeFormat.format(Date())
        val voltage = _data["总电池电压"]?.toDoubleOrNull()
        val current = _data["总电池电流"]?.toDoubleOrNull()
        val power = if (voltage != null && current != null) voltage * current else null

        return JSONObject().apply {
            put("title", "雅迪车辆信息")
            put("tag", "更新时间：$now")
            put("time", now)
            put("key1", "电压")
            put("value1", voltage?.let { "${it}V" } ?: "--")
            put("key2", "电流")
            put("value2", current?.let { "${it}A" } ?: "--")
            put("key3", "功率")
            put("value3", power?.let { "${String.format(Locale.US, "%.1f", it)}W" } ?: "--")
            put("key4", "剩余里程")
            put("value4", _data["剩余里程"]?.let { "${it}km" } ?: "--")
            put("key5", "车速")
            put("value5", _data["车速 当前值"]?.let { "${it}km/h" } ?: "--")
            put("key6", "总里程")
            put("value6", _data["ODO 当前值"]?.let { "${it}km" } ?: "--")
        }.toString()
    }
}
