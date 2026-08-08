package com.ghhccghk.yadeahook

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
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
        var changed = false
        for ((k, v) in newData) {
            if (v == "null" || v.isEmpty()) continue
            if (_data[k] != v) {
                _data[k] = v
                changed = true
            }
        }
        if (changed) broadcast()
    }

    fun get(key: String): String? = _data[key]

    fun broadcast() {
        try {
            val ctx = context ?: return
            val data = buildJson()
            Log.d("YadeaHook", "Data :$data")
            val intent = Intent(ACTION).apply {
                putExtra("data", data)
            }
            ctx.sendBroadcast(intent)
        } catch (_: Throwable) { }
    }

    private fun resolve(vararg keys: String): String? {
        for (k in keys) {
            val v = _data[k]
            if (v != null && v != "null" && v != "-1" && v != "-1.0" && v != "") return v
        }
        return null
    }

    private fun buildJson(): String {
        val now = timeFormat.format(Date())
        val voltage = resolve("volt", "totalBatteryVoltage", "总电池电压")?.toDoubleOrNull()
        val current = resolve("cur", "totalBatteryElectricity", "总电池电流")?.toDoubleOrNull()
        val power = if (voltage != null && current != null) voltage * current else null
        val soc = resolve("soc1", "pBElectricitySoc")
//        val mileage = resolve("remMileage", "remainingMileage", "电池电量")
        val speed = resolve("currentSpeedValue", "车速 当前值")
        val odo = resolve("totalMileage", "currentOdoValue", "ODO 当前值")
        val lock = resolve("lockStatus", "parkingStatus", "锁车状态")
        val powerState = resolve("powerState", "onOffStatus", "开机状态")

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
            put("key4", "电池电量")
            put("value4", soc?.let { "${it} %" } ?: "--")
            put("key5", "车速")
            put("value5", speed?.let { "${it}km/h" } ?: "车辆未骑行")
            put("key6", "总里程")
            put("value6", odo?.let { "${it}km" } ?: "--")
            put("soc", soc ?: "--")
            put("parkingStatus", lock ?: "--")
            put("onOffStatus", powerState ?: "--")
            // 原始字段全量输出
            for ((k, v) in _data) {
                if (!has(k)) put(k, v)
            }
        }.toString()
    }
}
