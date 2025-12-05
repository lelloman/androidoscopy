package com.lelloman.androidoscopy

import com.lelloman.androidoscopy.dashboard.DashboardBuilder
import kotlinx.serialization.json.JsonElement

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

class AndroidoscopyConfig {
    var appName: String? = null
    var hostIp: String? = null
    var port: Int = 8080
    var autoConnect: Boolean = true
    var enableLogging: Boolean = true

    internal var dashboardSchema: JsonElement? = null
    internal val actionHandlers = mutableMapOf<String, ActionHandler>()

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
    }
}
