package com.lelloman.androidoscopy

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.lelloman.androidoscopy.connection.DiscoveryListener
import com.lelloman.androidoscopy.connection.ReconnectionManager
import com.lelloman.androidoscopy.connection.WebSocketClient
import com.lelloman.androidoscopy.connection.WebSocketEvent
import com.lelloman.androidoscopy.data.DataProvider
import com.lelloman.androidoscopy.data.DataProviderManager
import com.lelloman.androidoscopy.protocol.ActionMessage
import com.lelloman.androidoscopy.protocol.ActionResultMessage
import com.lelloman.androidoscopy.protocol.ActionResultPayload
import com.lelloman.androidoscopy.protocol.DataMessage
import com.lelloman.androidoscopy.protocol.DeviceInfo
import com.lelloman.androidoscopy.protocol.LogLevel
import com.lelloman.androidoscopy.protocol.LogMessage
import com.lelloman.androidoscopy.protocol.LogPayload
import com.lelloman.androidoscopy.protocol.MessageLimits
import com.lelloman.androidoscopy.protocol.RegisterMessage
import com.lelloman.androidoscopy.protocol.RegisterPayload
import com.lelloman.androidoscopy.protocol.RegisteredMessage
import com.lelloman.androidoscopy.protocol.ServiceMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter

object Androidoscopy {

    private var androidoscopyImpl: AndroidoscopyImpl? = null

    val connectionState get() = androidoscopyImpl?.connectionState ?: notInitializedError()

    fun init(context: Application, config: AndroidoscopyConfig.() -> Unit) {
        val configBuilder = AndroidoscopyConfig()
        configBuilder.config()
        configBuilder.validate()

        if (androidoscopyImpl == null) {
            androidoscopyImpl = AndroidoscopyImpl(context, configBuilder).apply {
                connect()
            }
        } else {
            notInitializedError()
        }
    }

    fun log(level: LogLevel, tag: String?, message: String, throwable: Throwable? = null) {
        androidoscopyImpl?.log(level, tag, message, throwable) ?: notInitializedError()
    }

    fun updateData(block: MutableMap<String, Any>.() -> Unit) {
        androidoscopyImpl?.updateData(block) ?: notInitializedError()
    }

    fun registerDataProvider(provider: DataProvider) {
        androidoscopyImpl?.registerDataProvider(provider) ?: notInitializedError()
    }

    fun unregisterDataProvider(provider: DataProvider) {
        androidoscopyImpl?.unregisterDataProvider(provider) ?: notInitializedError()
    }

    private fun notInitializedError(): Nothing = error("Androidoscopy was not initialized")

    internal class AndroidoscopyImpl(
        private val context: Application,
        private val config: AndroidoscopyConfig,
    ) {
        private var webSocketClient: WebSocketClient? = null
        private var sessionId: String? = null

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val reconnectionManager = ReconnectionManager()
        private var isConnecting = false

        private val _connectionState =
            MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val connectionState: StateFlow<ConnectionState> = _connectionState

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private val currentData = mutableMapOf<String, Any>()
        private val dataProviderManager = DataProviderManager(scope) { key, data ->
            currentData[key] = data
            sendData()
        }

        fun connect() {
            if (isConnecting) return
            isConnecting = true

            scope.launch {
                val hostIp = config.hostIp ?: findServiceHost()
                if (hostIp == null) {
                    _connectionState.value = ConnectionState.Error("Could not find service host")
                    isConnecting = false
                    scheduleReconnect()
                    return@launch
                }

                val url = "ws://$hostIp:${config.port}/ws/app"
                webSocketClient = WebSocketClient(url)
                webSocketClient?.connect()

                webSocketClient?.events?.collect { event ->
                    handleWebSocketEvent(event)
                }
            }
        }

        fun disconnect() {
            dataProviderManager.stop()
            webSocketClient?.disconnect()
            webSocketClient = null
            sessionId = null
            _connectionState.value = ConnectionState.Disconnected
        }

        fun updateData(block: MutableMap<String, Any>.() -> Unit) {
            currentData.block()
            sendData()
        }

        fun registerDataProvider(provider: DataProvider) {
            dataProviderManager.register(provider)
        }

        fun unregisterDataProvider(provider: DataProvider) {
            dataProviderManager.unregister(provider)
        }

        fun log(level: LogLevel, tag: String?, message: String, throwable: Throwable? = null) {
            val sid = sessionId ?: return
            val cfg = config ?: return
            if (!cfg.enableLogging) return

            val truncatedMessage = message.take(MessageLimits.MAX_LOG_MESSAGE_SIZE)
            val truncatedThrowable =
                throwable?.stackTraceToString()?.take(MessageLimits.MAX_LOG_THROWABLE_SIZE)

            val logMessage = LogMessage(
                timestamp = currentTimestamp(),
                sessionId = sid,
                payload = LogPayload(
                    level = level,
                    tag = tag,
                    message = truncatedMessage,
                    throwable = truncatedThrowable
                )
            )

            send(json.encodeToString(logMessage))
        }

        private fun handleWebSocketEvent(event: WebSocketEvent) {
            when (event) {
                is WebSocketEvent.Connected -> {
                    _connectionState.value = ConnectionState.Connecting
                    reconnectionManager.reset()
                    sendRegister()
                }

                is WebSocketEvent.Message -> {
                    handleMessage(event.text)
                }

                is WebSocketEvent.Closed, is WebSocketEvent.Closing -> {
                    dataProviderManager.stop()
                    _connectionState.value = ConnectionState.Disconnected
                    isConnecting = false
                    scheduleReconnect()
                }

                is WebSocketEvent.Error -> {
                    dataProviderManager.stop()
                    _connectionState.value =
                        ConnectionState.Error(event.throwable.message ?: "Unknown error")
                    isConnecting = false
                    scheduleReconnect()
                }
            }
        }

        private fun handleMessage(text: String) {
            try {
                val message = json.decodeFromString<ServiceMessage>(text)
                when (message) {
                    is RegisteredMessage -> {
                        sessionId = message.payload.sessionId
                        _connectionState.value =
                            ConnectionState.Connected(message.payload.sessionId)
                        isConnecting = false
                        dataProviderManager.start()
                    }

                    is ActionMessage -> {
                        handleAction(message)
                    }

                    is com.lelloman.androidoscopy.protocol.ErrorMessage -> {
                        // Handle error from server
                    }
                }
            } catch (e: Exception) {
                // Failed to parse message
            }
        }

        private fun handleAction(message: ActionMessage) {
            val handler = config.actionHandlers[message.payload.action] ?: return

            scope.launch {
                val result = try {
                    val args = message.payload.args?.let { parseArgs(it) } ?: emptyMap()
                    handler(args)
                } catch (e: Exception) {
                    ActionResult.failure(e.message ?: "Unknown error")
                }

                val resultMessage = ActionResultMessage(
                    timestamp = currentTimestamp(),
                    sessionId = message.sessionId,
                    payload = ActionResultPayload(
                        actionId = message.payload.actionId,
                        success = result.success,
                        message = result.message,
                        data = result.data?.let { mapToJsonElement(it) }
                    )
                )

                send(json.encodeToString(resultMessage))
            }
        }

        private fun sendRegister() {
            val cfg = config ?: return
            val ctx = context ?: return

            val deviceInfo = collectDeviceInfo(ctx)
            val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)

            val registerMessage = RegisterMessage(
                timestamp = currentTimestamp(),
                payload = RegisterPayload(
                    protocolVersion = "1.0",
                    appName = cfg.appName ?: ctx.packageName,
                    packageName = ctx.packageName,
                    versionName = packageInfo.versionName ?: "unknown",
                    device = deviceInfo,
                    dashboard = cfg.dashboardSchema ?: buildJsonObject { }
                )
            )

            send(json.encodeToString(registerMessage))
        }

        private fun sendData() {
            val sid = sessionId ?: return

            val dataMessage = DataMessage(
                timestamp = currentTimestamp(),
                sessionId = sid,
                payload = mapToJsonElement(currentData)
            )

            send(json.encodeToString(dataMessage))
        }

        private fun send(message: String): Boolean {
            return webSocketClient?.send(message) ?: false
        }

        private fun scheduleReconnect() {
            scope.launch {
                val delay = reconnectionManager.nextDelay()
                delay(delay)
                connect()
            }
        }

        private suspend fun findServiceHost(): String? {
            // Try known emulator host IPs first
            if (isEmulator()) {
                val emulatorHosts = listOf(
                    "10.0.2.2",    // Android Emulator (AVD), BlueStacks
                    "10.0.3.2",    // Genymotion
                    "192.168.56.1" // Android-x86
                )
                for (host in emulatorHosts) {
                    if (canConnect(host)) return host
                }
            }

            // Try UDP discovery
            val discoveryListener = DiscoveryListener()
            val serviceInfo = discoveryListener.discoverService(timeoutMs = 5_000)
            if (serviceInfo != null && serviceInfo.host.isNotEmpty()) {
                return serviceInfo.host
            }

            return null
        }

        private suspend fun canConnect(host: String): Boolean {
            // Simple TCP connection check
            return try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, config?.port ?: 9999), 1000)
                socket.close()
                true
            } catch (e: Exception) {
                false
            }
        }

        private fun collectDeviceInfo(context: Context): DeviceInfo {
            val deviceId =
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            return DeviceInfo(
                deviceId = deviceId ?: "unknown",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                apiLevel = Build.VERSION.SDK_INT,
                isEmulator = isEmulator()
            )
        }

        private fun isEmulator(): Boolean {
            return Build.FINGERPRINT.contains("generic") ||
                    Build.FINGERPRINT.contains("emulator") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    Build.BRAND.startsWith("generic") ||
                    Build.DEVICE.startsWith("generic") ||
                    Build.PRODUCT == "google_sdk" ||
                    Build.PRODUCT == "sdk" ||
                    Build.PRODUCT == "sdk_x86" ||
                    Build.PRODUCT == "sdk_gphone64_arm64" ||
                    Build.HARDWARE.contains("goldfish") ||
                    Build.HARDWARE.contains("ranchu")
        }

        private fun currentTimestamp(): String {
            return DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        }

        private fun parseArgs(json: JsonElement): Map<String, Any> {
            if (json !is JsonObject) return emptyMap()
            return json.mapValues { (_, value) ->
                when (value) {
                    is JsonPrimitive -> when {
                        value.isString -> value.content
                        else -> value.content
                    }

                    else -> value.toString()
                }
            }
        }

        private fun mapToJsonElement(map: Map<String, Any>): JsonElement {
            return buildJsonObject {
                map.forEach { (key, value) ->
                    put(key, valueToJsonElement(value))
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun valueToJsonElement(value: Any): JsonElement {
            return when (value) {
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Map<*, *> -> mapToJsonElement(value as Map<String, Any>)
                is List<*> -> kotlinx.serialization.json.JsonArray(value.map {
                    valueToJsonElement(
                        it ?: ""
                    )
                })

                else -> JsonPrimitive(value.toString())
            }
        }
    }
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val sessionId: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
