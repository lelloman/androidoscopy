package com.lelloman.androidoscopy

import com.lelloman.androidoscopy.anr.AnrWatchdog
import com.lelloman.androidoscopy.dashboard.DashboardBuilder
import kotlinx.serialization.json.JsonElement
import com.lelloman.androidoscopy.session.SessionMode
import com.lelloman.androidoscopy.tools.Tool
import com.lelloman.androidoscopy.data.DataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

typealias ActionHandler = suspend (args: Map<String, Any>) -> ActionResult

data class ActionResult(
    val success: Boolean,
    val message: String? = null,
    val data: Map<String, Any>? = null
) {
    companion object {
        fun success(message: String? = null, data: Map<String, Any>? = null) =
            ActionResult(true, message, data)

        fun failure(message: String) =
            ActionResult(false, message)
    }
}

data class AnrConfig(
    val enabled: Boolean = true,
    val thresholdMs: Long = AnrWatchdog.DEFAULT_THRESHOLD_MS,
    val maxHistory: Int = 10
)

class AndroidoscopyConfig {
    var sessionMode: SessionMode = SessionMode.AUTO
    var releaseIdleTimeout: Duration = 15.minutes
    internal val tools = linkedMapOf<String, Tool>()
    internal val providerFactories = mutableListOf<() -> DataProvider>()

    fun tool(tool: Tool) {
        require(tools.putIfAbsent(tool.name, tool) == null) { "Duplicate tool: ${tool.name}" }
    }

    /** Factories are evaluated only while a diagnostic session is active. */
    fun dataProvider(factory: () -> DataProvider) { providerFactories += factory }
    var appName: String? = null
    @Deprecated("Protocol v2 listens on the phone; select the device from the desktop")
    var hostIp: String? = null
    @Deprecated("Protocol v2 uses an OS-assigned LAN port")
    var port: Int = 8889
    var enableLogging: Boolean = true

    internal var dashboardSchema: JsonElement? = null
    internal val actionHandlers = mutableMapOf<String, ActionHandler>()
    internal var anrConfig: AnrConfig? = null

    /**
     * Enable ANR (Application Not Responding) detection.
     *
     * @param thresholdMs Time in milliseconds to wait for main thread response. Default is 4000ms.
     * @param maxHistory Maximum number of ANR events to keep in history. Default is 10.
     */
    fun enableAnrDetection(
        thresholdMs: Long = AnrWatchdog.DEFAULT_THRESHOLD_MS,
        maxHistory: Int = 10
    ) {
        anrConfig = AnrConfig(
            enabled = true,
            thresholdMs = thresholdMs,
            maxHistory = maxHistory
        )
    }

    fun dashboard(block: DashboardBuilder.() -> Unit) {
        val builder = DashboardBuilder()
        builder.block()
        dashboardSchema = builder.build()
    }

    fun onAction(action: String, handler: ActionHandler) {
        actionHandlers[action] = handler
    }

    internal fun validate() {
        requireNotNull(appName) { "appName must be set in AndroidoscopyConfig" }
        require(releaseIdleTimeout.inWholeMilliseconds in 1..86_400_000L)
    }
}
