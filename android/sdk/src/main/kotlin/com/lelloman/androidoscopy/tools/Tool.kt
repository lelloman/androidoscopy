package com.lelloman.androidoscopy.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Tool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject = buildJsonObject {
        put("type", "object"); put("additionalProperties", false)
    },
    val outputSchema: JsonObject? = null,
    val readOnly: Boolean = false,
    val timeout: Duration = 60.seconds,
    val handler: suspend (JsonObject) -> ToolResult,
) {
    private val mapper = ObjectMapper()
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    init {
        require(name.matches(Regex("[a-zA-Z0-9_.-]{1,128}")))
        require(description.isNotBlank())
        require(inputSchema["type"] == JsonPrimitive("object"))
        require(timeout.inWholeMilliseconds in 1..86_400_000L)
        // Schemas are local-only: never fetch remote references from an app's schema.
        require(!hasReferences(inputSchema) && !hasReferences(outputSchema)) { "Schema references are not supported" }
    }

    private val inputValidator = factory.getSchema(inputSchema.toString())
    private val outputValidator = outputSchema?.let { factory.getSchema(it.toString()) }

    private fun hasReferences(value: JsonElement?): Boolean = when (value) {
        is JsonObject -> value.keys.any { it in setOf("\$ref", "\$dynamicRef", "\$recursiveRef") } || value.values.any(::hasReferences)
        is JsonArray -> value.any(::hasReferences)
        else -> false
    }

    fun validateInput(arguments: JsonObject) {
        require(inputValidator.validate(mapper.readTree(arguments.toString())).isEmpty()) {
            "INVALID_ARGUMENTS: input does not match tool schema"
        }
    }

    fun validateOutput(result: ToolResult) {
        if (!result.isError && outputValidator != null) {
            require(result.structuredContent != null &&
                outputValidator.validate(mapper.readTree(result.structuredContent.toString())).isEmpty()) {
                "INVALID_RESULT: output does not match tool schema"
            }
        }
    }

    fun manifest() = buildJsonObject {
        put("name", name); put("description", description); put("inputSchema", inputSchema)
        outputSchema?.let { put("outputSchema", it) }
        put("annotations", buildJsonObject {
            put("readOnlyHint", readOnly); put("destructiveHint", !readOnly)
        })
        put("timeoutMs", timeout.inWholeMilliseconds)
    }
}

data class ToolResult(
    val content: JsonArray,
    val structuredContent: JsonObject? = null,
    val isError: Boolean = false,
) {
    fun toJson() = buildJsonObject {
        put("content", content); put("isError", isError)
        structuredContent?.let { put("structuredContent", it) }
    }
    companion object {
        fun text(text: String, isError: Boolean = false) = ToolResult(
            buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) },
            isError = isError,
        )
        fun json(data: JsonObject) = text(data.toString()).copy(structuredContent = data)
        fun image(base64: String, mimeType: String) = ToolResult(buildJsonArray {
            add(buildJsonObject { put("type", "image"); put("data", base64); put("mimeType", mimeType) })
        })
    }
}

internal fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { it.key.toString() to it.value.toJson() })
    is Iterable<*> -> JsonArray(map { it.toJson() })
    else -> JsonPrimitive(toString())
}

internal fun JsonElement.toValue(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> mapValues { it.value.toValue() }
    is JsonArray -> map { it.toValue() }
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
}
