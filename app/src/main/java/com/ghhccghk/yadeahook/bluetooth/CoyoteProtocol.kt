package com.ghhccghk.yadeahook.bluetooth

/**
 * 郊狼情趣脉冲主机协议实现
 * 支持 V2 和 V3 版本
 */
object CoyoteProtocol {
    
    // UUID 定义
    object UUID {
        // V3 UUID
        const val SERVICE_V3 = "0000180C-0000-1000-8000-00805f9b34fb"
        const val WRITE_V3 = "0000150A-0000-1000-8000-00805f9b34fb"
        const val NOTIFY_V3 = "0000150B-0000-1000-8000-00805f9b34fb"
        const val BATTERY_V3 = "00001500-0000-1000-8000-00805f9b34fb"
        
        // V2 UUID (如果需要)
        const val SERVICE_V2 = "0000ffe0-0000-1000-8000-00805f9b34fb"
        const val WRITE_V2 = "0000ffe1-0000-1000-8000-00805f9b34fb"
        const val NOTIFY_V2 = "0000ffe2-0000-1000-8000-00805f9b34fb"
    }
    
    // 设备版本
    enum class Version {
        V2, V3
    }
    
    // 通道类型
    enum class Channel {
        A, B, BOTH
    }
    
    // 强度值解读方式
    object StrengthMode {
        const val NO_CHANGE = 0b00        // 不改变
        const val INCREASE = 0b01         // 相对增加
        const val DECREASE = 0b10         // 相对减少
        const val ABSOLUTE = 0b11         // 绝对设置
    }
    
    /**
     * 波形数据
     */
    data class Waveform(
        val frequencies: List<Int>,  // 4个频率值 (10-240)
        val strengths: List<Int>     // 4个强度值 (0-100)
    ) {
        init {
            require(frequencies.size == 4) { "需要4个频率值" }
            require(strengths.size == 4) { "需要4个强度值" }
            require(frequencies.all { it in 10..240 }) { "频率范围: 10-240" }
            require(strengths.all { it in 0..100 }) { "强度范围: 0-100" }
        }
    }
    
    /**
     * B0 指令数据
     */
    data class B0Command(
        val sequenceNumber: Int,           // 序列号 (0-15)
        val strengthModeA: Int,            // A通道强度解读方式
        val strengthModeB: Int,            // B通道强度解读方式
        val strengthValueA: Int,           // A通道强度设定值 (0-200)
        val strengthValueB: Int,           // B通道强度设定值 (0-200)
        val waveformA: Waveform,           // A通道波形
        val waveformB: Waveform            // B通道波形
    )
    
    /**
     * 创建默认波形
     */
    fun createDefaultWaveform(): Waveform {
        return Waveform(
            frequencies = listOf(40, 60, 80, 100),
            strengths = listOf(0, 90, 90, 90)
        )
    }
    
    /**
     * 编码 B0 指令为字节数组 (V3)
     * 总长度: 20 字节
     */
    fun encodeB0V3(command: B0Command): ByteArray {
        val data = ByteArray(20)
        
        // 字节0: 指令头 0xB0
        data[0] = 0xB0.toByte()
        
        // 字节1: 高4位=序列号, 低4位=强度值解读方式
        val modeByte = ((command.sequenceNumber and 0x0F) shl 4) or
                       (((command.strengthModeA and 0x03) shl 2) or
                        (command.strengthModeB and 0x03))
        data[1] = modeByte.toByte()
        
        // 字节2: A通道强度设定值
        data[2] = (command.strengthValueA and 0xFF).toByte()
        
        // 字节3: B通道强度设定值
        data[3] = (command.strengthValueB and 0xFF).toByte()
        
        // 字节4-7: A通道波形频率
        for (i in 0 until 4) {
            data[4 + i] = (command.waveformA.frequencies[i] and 0xFF).toByte()
        }
        
        // 字节8-11: A通道波形强度
        for (i in 0 until 4) {
            data[8 + i] = (command.waveformA.strengths[i] and 0xFF).toByte()
        }
        
        // 字节12-15: B通道波形频率
        for (i in 0 until 4) {
            data[12 + i] = (command.waveformB.frequencies[i] and 0xFF).toByte()
        }
        
        // 字节16-19: B通道波形强度
        for (i in 0 until 4) {
            data[16 + i] = (command.waveformB.strengths[i] and 0xFF).toByte()
        }
        
        return data
    }
    
    /**
     * 创建设置绝对强度的 B0 指令
     */
    fun createSetStrengthCommand(
        strengthA: Int,
        strengthB: Int,
        sequenceNumber: Int = 1,
        waveformA: Waveform = createDefaultWaveform(),
        waveformB: Waveform = createDefaultWaveform()
    ): B0Command {
        return B0Command(
            sequenceNumber = sequenceNumber,
            strengthModeA = StrengthMode.ABSOLUTE,
            strengthModeB = StrengthMode.ABSOLUTE,
            strengthValueA = strengthA.coerceIn(0, 200),
            strengthValueB = strengthB.coerceIn(0, 200),
            waveformA = waveformA,
            waveformB = waveformB
        )
    }
    
    /**
     * 创建相对增加强度的 B0 指令
     */
    fun createIncreaseStrengthCommand(
        increaseA: Int,
        increaseB: Int,
        sequenceNumber: Int = 1,
        waveformA: Waveform = createDefaultWaveform(),
        waveformB: Waveform = createDefaultWaveform()
    ): B0Command {
        return B0Command(
            sequenceNumber = sequenceNumber,
            strengthModeA = if (increaseA > 0) StrengthMode.INCREASE else StrengthMode.NO_CHANGE,
            strengthModeB = if (increaseB > 0) StrengthMode.INCREASE else StrengthMode.NO_CHANGE,
            strengthValueA = increaseA.coerceIn(0, 200),
            strengthValueB = increaseB.coerceIn(0, 200),
            waveformA = waveformA,
            waveformB = waveformB
        )
    }
    
    /**
     * 创建相对减少强度的 B0 指令
     */
    fun createDecreaseStrengthCommand(
        decreaseA: Int,
        decreaseB: Int,
        sequenceNumber: Int = 1,
        waveformA: Waveform = createDefaultWaveform(),
        waveformB: Waveform = createDefaultWaveform()
    ): B0Command {
        return B0Command(
            sequenceNumber = sequenceNumber,
            strengthModeA = if (decreaseA > 0) StrengthMode.DECREASE else StrengthMode.NO_CHANGE,
            strengthModeB = if (decreaseB > 0) StrengthMode.DECREASE else StrengthMode.NO_CHANGE,
            strengthValueA = decreaseA.coerceIn(0, 200),
            strengthValueB = decreaseB.coerceIn(0, 200),
            waveformA = waveformA,
            waveformB = waveformB
        )
    }
    
    /**
     * 创建清零强度的 B0 指令
     */
    fun createZeroStrengthCommand(
        waveformA: Waveform = createDefaultWaveform(),
        waveformB: Waveform = createDefaultWaveform()
    ): B0Command {
        return B0Command(
            sequenceNumber = 1,
            strengthModeA = StrengthMode.ABSOLUTE,
            strengthModeB = StrengthMode.ABSOLUTE,
            strengthValueA = 0,
            strengthValueB = 0,
            waveformA = waveformA,
            waveformB = waveformB
        )
    }
    
    /**
     * 解析 B1 回应消息
     * @return Pair<序列号, 当前强度>
     */
    fun parseB1Response(data: ByteArray): Pair<Int, Int>? {
        if (data.size < 3 || data[0] != 0xB1.toByte()) {
            return null
        }
        val sequenceNumber = (data[1].toInt() and 0xFF)
        val strength = (data[2].toInt() and 0xFF)
        return Pair(sequenceNumber, strength)
    }
    
    /**
     * 解析电池电量
     */
    fun parseBatteryLevel(data: ByteArray): Int? {
        if (data.isEmpty()) return null
        return data[0].toInt() and 0xFF
    }
    
    /**
     * 获取 V3 服务和特性 UUID
     */
    fun getV3Uuids(): Triple<String, String, String> {
        return Triple(UUID.SERVICE_V3, UUID.WRITE_V3, UUID.NOTIFY_V3)
    }
    
    /**
     * 获取 V2 服务和特性 UUID
     */
    fun getV2Uuids(): Triple<String, String, String> {
        return Triple(UUID.SERVICE_V2, UUID.WRITE_V2, UUID.NOTIFY_V2)
    }
}
