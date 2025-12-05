package com.lelloman.androidoscopy.connection

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val url: String,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    private var webSocket: WebSocket? = null
    private val messageChannel = Channel<WebSocketEvent>(Channel.BUFFERED)

    val events: Flow<WebSocketEvent> = messageChannel.receiveAsFlow()

    fun connect() {
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                messageChannel.trySend(WebSocketEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                messageChannel.trySend(WebSocketEvent.Message(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                messageChannel.trySend(WebSocketEvent.Closing(code, reason))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                messageChannel.trySend(WebSocketEvent.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                messageChannel.trySend(WebSocketEvent.Error(t))
            }
        })
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
    }

    fun isConnected(): Boolean = webSocket != null
}

sealed class WebSocketEvent {
    data object Connected : WebSocketEvent()
    data class Message(val text: String) : WebSocketEvent()
    data class Closing(val code: Int, val reason: String) : WebSocketEvent()
    data class Closed(val code: Int, val reason: String) : WebSocketEvent()
    data class Error(val throwable: Throwable) : WebSocketEvent()
}
