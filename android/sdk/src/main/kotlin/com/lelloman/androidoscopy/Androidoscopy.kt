package com.lelloman.androidoscopy

import android.app.Application
import com.lelloman.androidoscopy.data.DataProvider
import com.lelloman.androidoscopy.protocol.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object Androidoscopy {

    @Volatile private var androidoscopyImpl: com.lelloman.androidoscopy.session.SessionRuntime? = null

    private val inactive = MutableStateFlow(com.lelloman.androidoscopy.session.SessionState())
    val sessionState: StateFlow<com.lelloman.androidoscopy.session.SessionState> get() = androidoscopyImpl?.state ?: inactive
    val isSessionActive get() = androidoscopyImpl?.isActive == true
    fun startSession(idleTimeout: kotlin.time.Duration? = null) = requireNotNull(androidoscopyImpl).start(idleTimeout)
    fun stopSession() { androidoscopyImpl?.stop("Stopped") }
    fun sessionActivity() { androidoscopyImpl?.activity() }
    fun approvePairing(id: String) { androidoscopyImpl?.answerPairing(id, true) }
    fun rejectPairing(id: String) { androidoscopyImpl?.answerPairing(id, false) }
    fun rememberedPeers(): List<String> = androidoscopyImpl?.rememberedPeers() ?: emptyList()
    fun forgetPeer(id: String) { androidoscopyImpl?.forgetPeer(id) }
    fun registerTool(tool: com.lelloman.androidoscopy.tools.Tool) = requireNotNull(androidoscopyImpl).registerTool(tool)
    fun unregisterTool(name: String) { androidoscopyImpl?.unregisterTool(name) }

    val connectionState get() = androidoscopyImpl?.connectionState ?: notInitializedError()

    /**
     * Observable flow of current data collected from all data providers.
     * Used by the SDK UI module to render the dashboard.
     */
    val dataFlow get() = androidoscopyImpl?.dataFlow ?: notInitializedError()

    /**
     * Observable flow of log entries.
     * Used by the SDK UI module to render the log viewer.
     */
    val logFlow get() = androidoscopyImpl?.logFlow ?: notInitializedError()

    /**
     * The dashboard schema defined in the configuration.
     * Used by the SDK UI module to render the dashboard layout.
     */
    val dashboardSchema get() = androidoscopyImpl?.dashboardSchema

    /**
     * The app name configured for this session.
     */
    val appName get() = androidoscopyImpl?.appName

    /**
     * Invokes a registered action handler.
     * Used by the SDK UI module when action buttons are clicked.
     */
    suspend fun invokeAction(action: String, args: Map<String, Any> = emptyMap()): ActionResult {
        return androidoscopyImpl?.invokeAction(action, args)
            ?: ActionResult.failure("Androidoscopy not initialized")
    }

    @Synchronized fun init(context: Application, config: AndroidoscopyConfig.() -> Unit) {
        val configBuilder = AndroidoscopyConfig()
        configBuilder.config()
        configBuilder.validate()

        if (androidoscopyImpl == null) {
            val runtime = com.lelloman.androidoscopy.session.SessionRuntime(context, configBuilder)
            androidoscopyImpl = runtime
            runtime.initialize()
        } else {
            error("Androidoscopy is already initialized")
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

}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val sessionId: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Represents a log entry for the SDK UI module.
 */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String?,
    val message: String,
    val throwable: String? = null
)
