package com.lelloman.androidoscopy.tools

import com.lelloman.androidoscopy.Androidoscopy
import kotlinx.serialization.json.*

/** Opt-in tools. No diagnostic data is exposed merely by linking the SDK. */
object BuiltInTools {
    fun snapshot() = Tool("androidoscopy.snapshot", "Read the latest app-provided diagnostic data", readOnly = true) {
        ToolResult.json(Androidoscopy.dataFlow.value.toJson().jsonObject)
    }

    fun logs() = Tool("androidoscopy.logs", "Read the current session's diagnostic logs", readOnly = true) {
        ToolResult.json(buildJsonObject {
            put("logs", JsonArray(Androidoscopy.logFlow.value.map { entry -> buildJsonObject {
                put("timestamp", entry.timestamp); put("level", entry.level.name); put("tag", entry.tag)
                put("message", entry.message); put("throwable", entry.throwable)
            } }))
        })
    }
}
