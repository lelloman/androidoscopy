package com.lelloman.androidoscopy.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

@Serializable
data class ServiceInfo(
    val service: String,
    val version: String,
    val websocket_port: Int,
    val http_port: Int,
    val host: String = ""
)

class DiscoveryListener(
    private val port: Int = 9998,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun discoverService(timeoutMs: Long = 10_000): ServiceInfo? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(port)
            socket.soTimeout = timeoutMs.toInt()
            socket.broadcast = true

            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            socket.receive(packet)
            parseServiceInfo(packet)
        } catch (e: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private fun parseServiceInfo(packet: DatagramPacket): ServiceInfo? {
        return try {
            val data = String(packet.data, 0, packet.length)
            val info = json.decodeFromString<ServiceInfo>(data)
            info.copy(host = packet.address.hostAddress ?: "")
        } catch (e: Exception) {
            null
        }
    }
}
