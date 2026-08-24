package com.ghhccghk.yadeahook

import android.content.Context
import android.content.Intent

/**
 * BLE 认证序列存储 + 广播。
 * 保存最近一次会话的完整认证帧序列（INIT/KEY/QUERY/...），
 * 供 UI 展示和外部（ESP32 调试）复制使用。
 */
object BleAuthStore {
    const val ACTION = "com.ghhccghk.yadeahook.AUTH"

    data class Frame(
        val index: Int,
        val type: String,      // BleFrameClassifier.Type.label
        val uuid: String,      // 目标特征 UUID（小写），未知为 ""
        val hex: String,       // 小写 hex
        val ts: Long
    )

    @Volatile var sessionSeq: Long = 0          // 会话序号（每次发现 INIT 递增）
    @Volatile var initHex: String = ""          // 最新 INIT 帧
    @Volatile var keyHex: String = ""           // 最新 KEY 帧
    @Volatile var frames: List<Frame> = emptyList()  // 当前会话帧（含 UNKNOWN）
    @Volatile var lastPushHex: String = ""      // 车辆推送原始数据（认证成功信号）

    private var ctx: Context? = null

    fun init(context: Context) { ctx = context.applicationContext }

    /** 新会话：重置当前帧列表，返回新会话序号 */
    fun newSession(): Long {
        sessionSeq++
        initHex = ""
        keyHex = ""
        frames = emptyList()
        lastPushHex = ""
        return sessionSeq
    }

    fun addFrame(type: String, uuid: String, hex: String) {
        val f = Frame(frames.size + 1, type, uuid, hex, System.currentTimeMillis())
        frames = frames + f
        if (type == "INIT") initHex = hex
        if (type == "KEY") keyHex = hex
    }

    fun onPush(hex: String) {
        lastPushHex = hex
    }

    /** 生成一键复制的完整认证序列文本（ESP32 可直接参考） */
    fun sequenceText(): String {
        if (frames.isEmpty()) return ""
        val sb = StringBuilder()
        frames.forEachIndexed { i, f ->
            sb.append("${i + 1}. [${f.type}] ${f.hex}")
            if (f.uuid.isNotBlank()) sb.append("  <- ${f.uuid}")
            sb.append('\n')
        }
        return sb.toString()
    }

    fun broadcast() {
        try {
            val c = ctx ?: return
            c.sendBroadcast(Intent(ACTION).apply {
                putExtra("init", initHex)
                putExtra("key", keyHex)
                putExtra("seq", sessionSeq)
                putExtra("push", lastPushHex)
                putExtra("text", sequenceText())
            })
        } catch (_: Throwable) { }
    }
}
