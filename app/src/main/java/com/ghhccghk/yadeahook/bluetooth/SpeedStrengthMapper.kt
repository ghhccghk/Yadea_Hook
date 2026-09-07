package com.ghhccghk.yadeahook.bluetooth

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 车速-强度映射器
 * 支持用户自定义曲线
 */
class SpeedStrengthMapper(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "speed_strength_curve"
        private const val KEY_CURVE_POINTS = "curve_points"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_CHANNEL = "channel"
        private const val KEY_MAX_STRENGTH = "max_strength"
        
        // 默认曲线点: [车速, 强度]
        private val DEFAULT_CURVE = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(10f, 0f),
            CurvePoint(15f, 20f),
            CurvePoint(25f, 50f),
            CurvePoint(35f, 80f),
            CurvePoint(60f, 100f)
        )
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 曲线点
     */
    data class CurvePoint(
        val speed: Float,    // 车速 km/h
        val strength: Float  // 强度百分比 (0-100)
    )
    
    /**
     * 映射配置
     */
    data class MappingConfig(
        val enabled: Boolean = true,
        val channel: CoyoteProtocol.Channel = CoyoteProtocol.Channel.BOTH,
        val maxStrength: Int = 100,  // 最大强度 (0-200)
        val curvePoints: List<CurvePoint> = DEFAULT_CURVE
    )
    
    /**
     * 获取当前配置
     */
    fun getConfig(): MappingConfig {
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val channelName = prefs.getString(KEY_CHANNEL, CoyoteProtocol.Channel.BOTH.name)
        val channel = try {
            CoyoteProtocol.Channel.valueOf(channelName ?: CoyoteProtocol.Channel.BOTH.name)
        } catch (e: Exception) {
            CoyoteProtocol.Channel.BOTH
        }
        val maxStrength = prefs.getInt(KEY_MAX_STRENGTH, 100)
        val curvePoints = loadCurvePoints()
        
        return MappingConfig(
            enabled = enabled,
            channel = channel,
            maxStrength = maxStrength,
            curvePoints = curvePoints
        )
    }
    
    /**
     * 保存配置
     */
    fun saveConfig(config: MappingConfig) {
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, config.enabled)
            putString(KEY_CHANNEL, config.channel.name)
            putInt(KEY_MAX_STRENGTH, config.maxStrength)
            putString(KEY_CURVE_POINTS, gson.toJson(config.curvePoints))
            apply()
        }
    }
    
    /**
     * 加载曲线点
     */
    private fun loadCurvePoints(): List<CurvePoint> {
        val json = prefs.getString(KEY_CURVE_POINTS, null)
        if (json == null) {
            return DEFAULT_CURVE
        }
        
        return try {
            val type = object : TypeToken<List<CurvePoint>>() {}.type
            gson.fromJson(json, type) ?: DEFAULT_CURVE
        } catch (e: Exception) {
            DEFAULT_CURVE
        }
    }
    
    /**
     * 保存曲线点
     */
    fun saveCurvePoints(points: List<CurvePoint>) {
        val config = getConfig().copy(curvePoints = points)
        saveConfig(config)
    }
    
    /**
     * 根据车速计算强度
     * 使用线性插值
     */
    fun calculateStrength(speed: Float): Int {
        val config = getConfig()
        
        if (!config.enabled) return 0
        if (config.curvePoints.isEmpty()) return 0
        
        val points = config.curvePoints.sortedBy { it.speed }
        
        // 低于第一个点
        if (speed <= points.first().speed) {
            return (points.first().strength * config.maxStrength / 100).toInt()
        }
        
        // 高于最后一个点
        if (speed >= points.last().speed) {
            return (points.last().strength * config.maxStrength / 100).toInt()
        }
        
        // 线性插值
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            
            if (speed in p1.speed..p2.speed) {
                val ratio = (speed - p1.speed) / (p2.speed - p1.speed)
                val strength = p1.strength + ratio * (p2.strength - p1.strength)
                return (strength * config.maxStrength / 100).toInt().coerceIn(0, config.maxStrength)
            }
        }
        
        return 0
    }
    
    /**
     * 获取映射通道
     */
    fun getChannel(): CoyoteProtocol.Channel {
        return getConfig().channel
    }
    
    /**
     * 是否启用
     */
    fun isEnabled(): Boolean {
        return getConfig().enabled
    }
    
    /**
     * 启用/禁用映射
     */
    fun setEnabled(enabled: Boolean) {
        val config = getConfig().copy(enabled = enabled)
        saveConfig(config)
    }
    
    /**
     * 设置通道
     */
    fun setChannel(channel: CoyoteProtocol.Channel) {
        val config = getConfig().copy(channel = channel)
        saveConfig(config)
    }
    
    /**
     * 设置最大强度
     */
    fun setMaxStrength(maxStrength: Int) {
        val config = getConfig().copy(maxStrength = maxStrength.coerceIn(0, 200))
        saveConfig(config)
    }
    
    /**
     * 重置为默认曲线
     */
    fun resetToDefault() {
        saveCurvePoints(DEFAULT_CURVE)
    }
    
    /**
     * 获取曲线数据用于图表显示
     * @return Pair<车速列表, 强度列表>
     */
    fun getCurveData(): Pair<List<Float>, List<Float>> {
        val points = getConfig().curvePoints.sortedBy { it.speed }
        val speeds = points.map { it.speed }
        val strengths = points.map { it.strength }
        return Pair(speeds, strengths)
    }
    
    /**
     * 生成平滑曲线点（用于图表显示）
     */
    fun generateSmoothCurve(numPoints: Int = 100): Pair<List<Float>, List<Float>> {
        val config = getConfig()
        val points = config.curvePoints.sortedBy { it.speed }
        
        if (points.size < 2) {
            return Pair(listOf(0f), listOf(0f))
        }
        
        val minSpeed = points.first().speed
        val maxSpeed = points.last().speed
        val step = (maxSpeed - minSpeed) / (numPoints - 1)
        
        val speeds = mutableListOf<Float>()
        val strengths = mutableListOf<Float>()
        
        for (i in 0 until numPoints) {
            val speed = minSpeed + step * i
            speeds.add(speed)
            strengths.add(calculateStrength(speed).toFloat() * 100 / config.maxStrength)
        }
        
        return Pair(speeds, strengths)
    }
}