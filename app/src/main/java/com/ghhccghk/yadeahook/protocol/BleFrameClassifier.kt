package com.ghhccghk.yadeahook.protocol

/**
 * 雅迪 BLE 帧分类器 —— 基于 hook 抓包确证的真实协议指纹。
 *
 * 认证序列（App 成功流程，按序）：
 *   INIT   13B：XX XX XX | ff ff ff ff ff ff 01 00 28 01   前 3B 动态，尾 10B 固定
 *   KEY    14B：XX*9    | 41 05 01 10 55                   前 9B 动态，尾 5B 固定
 *   QUERY  3B： 5a 96 00
 *   VERSION 5B："1.4.0" → 31 2e 34 2e 30
 *   QUERY2 7B： 02 5e 04 40 00 00 03
 *   POLL_0D   37B：5944 3e1e 0100 0101 c8 ff ff 0d ...
 *   POLL_7A8F 150B：5944 7a8f ...
 *   START  解锁：5944 3e1e 0100 0101 c8 ff ff 02 ...
 *   STOP   设防：5944 3e1e 0100 0101 c8 ff ff 03 ...
 *
 * 写特征 UUID（App 实际使用）：0000b362-d6d8-c7ec-bdf0-eab1bfc6bcbc
 */
object BleFrameClassifier {

    enum class Type(val label: String) {
        INIT("INIT"),
        KEY("KEY"),
        QUERY("QUERY"),
        VERSION("VERSION"),
        QUERY2("QUERY2"),
        POLL_0D("POLL_0D"),
        POLL_7A8F("POLL_7A8F"),
        START("START"),
        STOP("STOP"),
        UNKNOWN("DATA")
    }

    // 固定尾部指纹
    private const val INIT_TAIL = "ffffffffffffff01002801"   // 10B → 20 hex
    private const val KEY_TAIL  = "4105011055"               // 5B  → 10 hex

    // 5944 控制帧前缀
    private const val P_7A8F = "59447a8f"
    private const val P_3E1E = "59443e1e01000101c8ffff"
    private const val CMD_START = P_3E1E + "02"
    private const val CMD_STOP  = P_3E1E + "03"
    private const val CMD_POLL0D = P_3E1E + "0d"

    /**
     * 将小写 hex 帧分类。返回 UNKNOWN 表示未识别（仍可能是认证/业务数据）。
     */
    fun classify(hex: String): Type {
        val h = hex.lowercase()
        return when {
            h.startsWith(P_7A8F) -> Type.POLL_7A8F
            h.startsWith(CMD_START) -> Type.START
            h.startsWith(CMD_STOP) -> Type.STOP
            h.startsWith(CMD_POLL0D) -> Type.POLL_0D
            h == "5a9600" -> Type.QUERY
            h == "312e342e30" -> Type.VERSION
            h == "025e0440000003" -> Type.QUERY2
            h.length == 26 && h.endsWith(INIT_TAIL) -> Type.INIT
            h.length == 28 && h.endsWith(KEY_TAIL) -> Type.KEY
            else -> Type.UNKNOWN
        }
    }

    /** 判断是否是"看起来像 14B Key"的候选（供旧逻辑参考，新逻辑直接用 classify） */
    fun looksLikeKey(hex: String): Boolean = classify(hex) == Type.KEY

    /** 判断是否属于认证序列中的关键帧 */
    fun isAuthFrame(type: Type): Boolean = type != Type.UNKNOWN && type != Type.START && type != Type.STOP
}
