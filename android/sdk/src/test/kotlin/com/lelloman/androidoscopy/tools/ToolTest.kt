package com.lelloman.androidoscopy.tools
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ToolTest {
    @Test fun nestedValuesPreserveTypes() {
        val value = mapOf("a" to listOf(true, 42L, null, mapOf("b" to 1.25)))
        assertEquals(value, value.toJson().toValue())
    }
    @Test fun validatesArguments() {
        val schema = Json.parseToJsonElement("""{"type":"object","required":["count"],"properties":{"count":{"type":"integer","minimum":1}},"additionalProperties":false}""").jsonObject
        val tool = Tool("demo", "Demo", schema) { ToolResult.text("ok") }
        tool.validateInput(buildJsonObject { put("count", 1) })
        assertThrows(IllegalArgumentException::class.java) { tool.validateInput(buildJsonObject { put("count", "1") }) }
        assertThrows(IllegalArgumentException::class.java) { tool.validateInput(buildJsonObject { put("count", 0) }) }
    }
    @Test fun rejectsReferencesBeforeCompilingSchema() {
        val schema = buildJsonObject { put("type", "object"); put("\$ref", "https://invalid.example/schema") }
        assertThrows(IllegalArgumentException::class.java) { Tool("demo", "Demo", schema) { ToolResult.text("ok") } }
    }
}
