package com.ghhccghk.yadeahook

import android.content.Context
import android.content.Intent

/**
 * 登录链路抓取结果。
 *
 * Hook 运行在目标 App 进程，UI 运行在模块进程，因此通过广播同步快照。
 */
object LoginCaptureStore {
    const val ACTION = "com.ghhccghk.yadeahook.LOGIN_CAPTURE"

    private const val EXTRA_LOGIN_TOKEN = "loginToken"
    private const val EXTRA_GYUID = "gyuid"
    private const val EXTRA_USR_TOKEN = "usrToken"
    private const val EXTRA_BUSINESS_JSON = "businessJson"
    private const val EXTRA_GATEWAY_FIELDS = "gatewayFields"
    private const val EXTRA_REQUEST_HEADERS = "requestHeaders"
    private const val EXTRA_REGISTRATION_ID = "registrationId"
    private const val EXTRA_OPPO_PUSH_ID = "oppoPushId"
    private const val EXTRA_USER_INFO = "userInfo"
    private const val EXTRA_LAST_ERROR = "lastError"

    @Volatile var loginToken: String = ""
        private set
    @Volatile var gyuid: String = ""
        private set
    @Volatile var usrToken: String = ""
        private set
    @Volatile var businessJson: String = ""
        private set
    @Volatile var gatewayFields: String = ""
        private set
    @Volatile var requestHeaders: String = ""
        private set
    @Volatile var registrationId: String = ""
        private set
    @Volatile var oppoPushId: String = ""
        private set
    @Volatile var userInfo: String = ""
        private set
    @Volatile var lastError: String = ""
        private set

    data class Snapshot(
        val loginToken: String,
        val gyuid: String,
        val usrToken: String,
        val businessJson: String,
        val gatewayFields: String,
        val requestHeaders: String,
        val registrationId: String,
        val oppoPushId: String,
        val userInfo: String,
        val lastError: String
    ) {
        fun bearerToken(): String = when {
            usrToken.isBlank() -> ""
            usrToken.startsWith("Bearer", ignoreCase = true) -> usrToken
            else -> "Bearer $usrToken"
        }

        fun asText(): String = buildString {
            appendLine("loginToken=$loginToken")
            appendLine("gyuid=$gyuid")
            appendLine("usr_token=$usrToken")
            appendLine("authorization=${bearerToken()}")
            appendLine("registrationId=$registrationId")
            appendLine("oppoPushId=$oppoPushId")
            appendLine("userInfo=$userInfo")
            appendLine("gateway=$gatewayFields")
            appendLine("lastError=$lastError")
            appendLine("requestHeaders:")
            appendLine(requestHeaders)
            appendLine("businessJson:")
            append(businessJson)
        }
    }

    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun snapshot(): Snapshot = Snapshot(
        loginToken = loginToken,
        gyuid = gyuid,
        usrToken = usrToken,
        businessJson = businessJson,
        gatewayFields = gatewayFields,
        requestHeaders = requestHeaders,
        registrationId = registrationId,
        oppoPushId = oppoPushId,
        userInfo = userInfo,
        lastError = lastError
    )

    fun update(block: () -> Unit) {
        block()
        broadcast()
    }

    fun setLoginToken(value: String?) {
        if (value.isNullOrBlank() || value == loginToken) return
        loginToken = value
        broadcast()
    }

    fun setGyuid(value: String?) {
        if (value.isNullOrBlank() || value == gyuid) return
        gyuid = value
        broadcast()
    }

    fun setUsrToken(value: String?) {
        if (value.isNullOrBlank() || value == usrToken) return
        usrToken = value
        broadcast()
    }

    fun setBusinessJson(value: String?) {
        if (value.isNullOrBlank() || value == businessJson) return
        businessJson = value
        broadcast()
    }

    fun setGatewayFields(value: String?) {
        if (value.isNullOrBlank() || value == gatewayFields) return
        gatewayFields = value
        broadcast()
    }

    fun setRequestHeaders(value: String?) {
        if (value.isNullOrBlank() || value == requestHeaders) return
        requestHeaders = value
        broadcast()
    }

    fun setRegistrationId(value: String?) {
        if (value.isNullOrBlank() || value == registrationId) return
        registrationId = value
        broadcast()
    }

    fun setOppoPushId(value: String?) {
        if (value.isNullOrBlank() || value == oppoPushId) return
        oppoPushId = value
        broadcast()
    }

    fun setUserInfo(value: String?) {
        if (value.isNullOrBlank() || value == userInfo) return
        userInfo = value
        broadcast()
    }

    fun setLastError(value: String?) {
        if (value.isNullOrBlank() || value == lastError) return
        lastError = value
        broadcast()
    }

    fun clearLastError() {
        if (lastError.isEmpty()) return
        lastError = ""
        broadcast()
    }

    fun applyFromIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_LOGIN_TOKEN)?.let { loginToken = it }
        intent.getStringExtra(EXTRA_GYUID)?.let { gyuid = it }
        intent.getStringExtra(EXTRA_USR_TOKEN)?.let { usrToken = it }
        intent.getStringExtra(EXTRA_BUSINESS_JSON)?.let { businessJson = it }
        intent.getStringExtra(EXTRA_GATEWAY_FIELDS)?.let { gatewayFields = it }
        intent.getStringExtra(EXTRA_REQUEST_HEADERS)?.let { requestHeaders = it }
        intent.getStringExtra(EXTRA_REGISTRATION_ID)?.let { registrationId = it }
        intent.getStringExtra(EXTRA_OPPO_PUSH_ID)?.let { oppoPushId = it }
        intent.getStringExtra(EXTRA_USER_INFO)?.let { userInfo = it }
        intent.getStringExtra(EXTRA_LAST_ERROR)?.let { lastError = it }
    }

    fun broadcast() {
        try {
            val ctx = context ?: return
            ctx.sendBroadcast(Intent(ACTION).apply {
                setPackage("com.ghhccghk.yadeahook")
                putExtra(EXTRA_LOGIN_TOKEN, loginToken)
                putExtra(EXTRA_GYUID, gyuid)
                putExtra(EXTRA_USR_TOKEN, usrToken)
                putExtra(EXTRA_BUSINESS_JSON, businessJson)
                putExtra(EXTRA_GATEWAY_FIELDS, gatewayFields)
                putExtra(EXTRA_REQUEST_HEADERS, requestHeaders)
                putExtra(EXTRA_REGISTRATION_ID, registrationId)
                putExtra(EXTRA_OPPO_PUSH_ID, oppoPushId)
                putExtra(EXTRA_USER_INFO, userInfo)
                putExtra(EXTRA_LAST_ERROR, lastError)
            })
        } catch (_: Throwable) { }
    }
}
