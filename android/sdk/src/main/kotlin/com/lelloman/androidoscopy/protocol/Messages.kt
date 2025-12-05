package com.lelloman.androidoscopy.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// === App → Service Messages ===

@Serializable
sealed class AppMessage {
    abstract val type: String
    abstract val timestamp: String
}

@Serializable
@SerialName("REGISTER")
data class RegisterMessage(
    override val timestamp: String,
    val payload: RegisterPayload
) : AppMessage() {
    override val type: String = "REGISTER"
}

@Serializable
@SerialName("DATA")
data class DataMessage(
    override val timestamp: String,
    @SerialName("session_id")
    val sessionId: String,
    val payload: JsonElement
) : AppMessage() {
    override val type: String = "DATA"
}

@Serializable
@SerialName("LOG")
data class LogMessage(
    override val timestamp: String,
    @SerialName("session_id")
    val sessionId: String,
    val payload: LogPayload
) : AppMessage() {
    override val type: String = "LOG"
}

@Serializable
@SerialName("ACTION_RESULT")
data class ActionResultMessage(
    override val timestamp: String,
    @SerialName("session_id")
    val sessionId: String,
    val payload: ActionResultPayload
) : AppMessage() {
    override val type: String = "ACTION_RESULT"
}

// === Payloads ===

@Serializable
data class RegisterPayload(
    @SerialName("protocol_version")
    val protocolVersion: String,
    @SerialName("app_name")
    val appName: String,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("version_name")
    val versionName: String,
    val device: DeviceInfo,
    val dashboard: JsonElement
)

@Serializable
data class DeviceInfo(
    @SerialName("device_id")
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    @SerialName("android_version")
    val androidVersion: String,
    @SerialName("api_level")
    val apiLevel: Int,
    @SerialName("is_emulator")
    val isEmulator: Boolean
)

@Serializable
data class LogPayload(
    val level: LogLevel,
    val tag: String? = null,
    val message: String,
    val throwable: String? = null
)

@Serializable
enum class LogLevel {
    @SerialName("VERBOSE")
    VERBOSE,
    @SerialName("DEBUG")
    DEBUG,
    @SerialName("INFO")
    INFO,
    @SerialName("WARN")
    WARN,
    @SerialName("ERROR")
    ERROR
}

@Serializable
data class ActionResultPayload(
    @SerialName("action_id")
    val actionId: String,
    val success: Boolean,
    val message: String? = null,
    val data: JsonElement? = null
)

// === Service → App Messages ===

@Serializable
sealed class ServiceMessage {
    abstract val type: String
    abstract val timestamp: String
}

@Serializable
@SerialName("REGISTERED")
data class RegisteredMessage(
    override val timestamp: String,
    val payload: RegisteredPayload
) : ServiceMessage() {
    override val type: String = "REGISTERED"
}

@Serializable
@SerialName("ACTION")
data class ActionMessage(
    override val timestamp: String,
    @SerialName("session_id")
    val sessionId: String,
    val payload: ActionPayload
) : ServiceMessage() {
    override val type: String = "ACTION"
}

@Serializable
@SerialName("ERROR")
data class ErrorMessage(
    override val timestamp: String,
    val payload: ErrorPayload
) : ServiceMessage() {
    override val type: String = "ERROR"
}

@Serializable
data class RegisteredPayload(
    @SerialName("session_id")
    val sessionId: String
)

@Serializable
data class ActionPayload(
    @SerialName("action_id")
    val actionId: String,
    val action: String,
    val args: JsonElement? = null
)

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String
)

// === Size Limits ===

object MessageLimits {
    const val MAX_MESSAGE_SIZE = 1024 * 1024 // 1 MB
    const val MAX_LOG_MESSAGE_SIZE = 64 * 1024 // 64 KB
    const val MAX_LOG_THROWABLE_SIZE = 256 * 1024 // 256 KB
}
