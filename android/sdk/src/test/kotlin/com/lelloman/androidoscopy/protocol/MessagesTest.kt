package com.lelloman.androidoscopy.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `RegisterMessage serializes correctly`() {
        val message = RegisterMessage(
            timestamp = "2024-01-01T00:00:00Z",
            payload = RegisterPayload(
                protocolVersion = "1.0",
                appName = "TestApp",
                packageName = "com.test.app",
                versionName = "1.0.0",
                device = DeviceInfo(
                    deviceId = "device123",
                    manufacturer = "Google",
                    model = "Pixel 6",
                    androidVersion = "14",
                    apiLevel = 34,
                    isEmulator = false
                ),
                dashboard = buildJsonObject {}
            )
        )

        val encoded = json.encodeToString(message)
        assertTrue(encoded.contains("\"type\":\"REGISTER\""))
        assertTrue(encoded.contains("\"app_name\":\"TestApp\""))
        assertTrue(encoded.contains("\"device_id\":\"device123\""))
    }

    @Test
    fun `DataMessage serializes correctly`() {
        val message = DataMessage(
            timestamp = "2024-01-01T00:00:00Z",
            sessionId = "session123",
            payload = buildJsonObject {
                put("count", JsonPrimitive(42))
                put("name", JsonPrimitive("test"))
            }
        )

        val encoded = json.encodeToString(message)
        assertTrue(encoded.contains("\"type\":\"DATA\""))
        assertTrue(encoded.contains("\"session_id\":\"session123\""))
        assertTrue(encoded.contains("\"count\":42"))
    }

    @Test
    fun `LogMessage serializes correctly`() {
        val message = LogMessage(
            timestamp = "2024-01-01T00:00:00Z",
            sessionId = "session123",
            payload = LogPayload(
                level = LogLevel.ERROR,
                tag = "TestTag",
                message = "Error occurred",
                throwable = "java.lang.RuntimeException: Test"
            )
        )

        val encoded = json.encodeToString(message)
        assertTrue(encoded.contains("\"type\":\"LOG\""))
        assertTrue(encoded.contains("\"level\":\"ERROR\""))
        assertTrue(encoded.contains("\"tag\":\"TestTag\""))
    }

    @Test
    fun `ActionResultMessage serializes correctly`() {
        val message = ActionResultMessage(
            timestamp = "2024-01-01T00:00:00Z",
            sessionId = "session123",
            payload = ActionResultPayload(
                actionId = "action123",
                success = true,
                message = "Action completed",
                data = buildJsonObject {
                    put("result", JsonPrimitive("success"))
                }
            )
        )

        val encoded = json.encodeToString(message)
        assertTrue(encoded.contains("\"type\":\"ACTION_RESULT\""))
        assertTrue(encoded.contains("\"action_id\":\"action123\""))
        assertTrue(encoded.contains("\"success\":true"))
    }

    @Test
    fun `RegisteredMessage deserializes correctly`() {
        val data = """
            {
                "type": "REGISTERED",
                "timestamp": "2024-01-01T00:00:00Z",
                "payload": {
                    "session_id": "session123"
                }
            }
        """.trimIndent()

        val message = json.decodeFromString<ServiceMessage>(data)
        assertTrue(message is RegisteredMessage)
        assertEquals("session123", (message as RegisteredMessage).payload.sessionId)
    }

    @Test
    fun `ActionMessage deserializes correctly`() {
        val data = """
            {
                "type": "ACTION",
                "timestamp": "2024-01-01T00:00:00Z",
                "session_id": "session123",
                "payload": {
                    "action_id": "action123",
                    "action": "refresh",
                    "args": {"force": true}
                }
            }
        """.trimIndent()

        val message = json.decodeFromString<ServiceMessage>(data)
        assertTrue(message is ActionMessage)
        val actionMessage = message as ActionMessage
        assertEquals("action123", actionMessage.payload.actionId)
        assertEquals("refresh", actionMessage.payload.action)
        assertNotNull(actionMessage.payload.args)
    }

    @Test
    fun `ErrorMessage deserializes correctly`() {
        val data = """
            {
                "type": "ERROR",
                "timestamp": "2024-01-01T00:00:00Z",
                "payload": {
                    "code": "INVALID_SESSION",
                    "message": "Session not found"
                }
            }
        """.trimIndent()

        val message = json.decodeFromString<ServiceMessage>(data)
        assertTrue(message is ErrorMessage)
        assertEquals("INVALID_SESSION", (message as ErrorMessage).payload.code)
    }

    @Test
    fun `LogLevel enum values are correct`() {
        assertEquals("VERBOSE", LogLevel.VERBOSE.name)
        assertEquals("DEBUG", LogLevel.DEBUG.name)
        assertEquals("INFO", LogLevel.INFO.name)
        assertEquals("WARN", LogLevel.WARN.name)
        assertEquals("ERROR", LogLevel.ERROR.name)
    }

    @Test
    fun `DeviceInfo serializes all fields`() {
        val deviceInfo = DeviceInfo(
            deviceId = "id123",
            manufacturer = "Samsung",
            model = "Galaxy S21",
            androidVersion = "13",
            apiLevel = 33,
            isEmulator = true
        )

        val encoded = json.encodeToString(deviceInfo)
        assertTrue(encoded.contains("\"device_id\":\"id123\""))
        assertTrue(encoded.contains("\"manufacturer\":\"Samsung\""))
        assertTrue(encoded.contains("\"model\":\"Galaxy S21\""))
        assertTrue(encoded.contains("\"android_version\":\"13\""))
        assertTrue(encoded.contains("\"api_level\":33"))
        assertTrue(encoded.contains("\"is_emulator\":true"))
    }

    @Test
    fun `MessageLimits has correct values`() {
        assertEquals(1024 * 1024, MessageLimits.MAX_MESSAGE_SIZE) // 1 MB
        assertEquals(64 * 1024, MessageLimits.MAX_LOG_MESSAGE_SIZE) // 64 KB
        assertEquals(256 * 1024, MessageLimits.MAX_LOG_THROWABLE_SIZE) // 256 KB
    }

    @Test
    fun `LogPayload with null throwable serializes correctly`() {
        val payload = LogPayload(
            level = LogLevel.INFO,
            tag = "Test",
            message = "Hello",
            throwable = null
        )

        val encoded = json.encodeToString(payload)
        assertTrue(encoded.contains("\"level\":\"INFO\""))
        assertTrue(encoded.contains("\"throwable\":null"))
    }
}
