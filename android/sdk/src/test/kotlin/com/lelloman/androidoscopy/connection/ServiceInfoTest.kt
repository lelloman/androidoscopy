package com.lelloman.androidoscopy.connection

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses valid service info`() {
        val data = """
            {
                "service": "androidoscopy",
                "version": "1.0",
                "websocket_port": 9999,
                "http_port": 8080
            }
        """.trimIndent()

        val info = json.decodeFromString<ServiceInfo>(data)

        assertEquals("androidoscopy", info.service)
        assertEquals("1.0", info.version)
        assertEquals(9999, info.websocket_port)
        assertEquals(8080, info.http_port)
        assertEquals("", info.host) // Default value
    }

    @Test
    fun `parses service info with extra fields`() {
        val data = """
            {
                "service": "androidoscopy",
                "version": "1.0",
                "websocket_port": 9999,
                "http_port": 8080,
                "extra_field": "ignored"
            }
        """.trimIndent()

        val info = json.decodeFromString<ServiceInfo>(data)

        assertEquals("androidoscopy", info.service)
        assertEquals(9999, info.websocket_port)
    }

    @Test
    fun `host can be set via copy`() {
        val data = """
            {
                "service": "androidoscopy",
                "version": "1.0",
                "websocket_port": 9999,
                "http_port": 8080
            }
        """.trimIndent()

        val info = json.decodeFromString<ServiceInfo>(data)
        val withHost = info.copy(host = "192.168.1.100")

        assertEquals("192.168.1.100", withHost.host)
        assertEquals("androidoscopy", withHost.service)
    }

    @Test
    fun `default port values are used correctly`() {
        val data = """
            {
                "service": "androidoscopy",
                "version": "1.0",
                "websocket_port": 12345,
                "http_port": 54321
            }
        """.trimIndent()

        val info = json.decodeFromString<ServiceInfo>(data)

        assertEquals(12345, info.websocket_port)
        assertEquals(54321, info.http_port)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `throws on missing required fields`() {
        val data = """
            {
                "service": "androidoscopy"
            }
        """.trimIndent()

        json.decodeFromString<ServiceInfo>(data)
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `throws on invalid json`() {
        val data = "not valid json"
        json.decodeFromString<ServiceInfo>(data)
    }
}
