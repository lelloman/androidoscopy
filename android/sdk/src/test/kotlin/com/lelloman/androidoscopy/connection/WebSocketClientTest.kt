package com.lelloman.androidoscopy.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketClientTest {

    private lateinit var server: MockWebServer
    private var client: WebSocketClient? = null

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try {
            client?.disconnect()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            server.shutdown()
        } catch (e: Exception) {
            // Server might already be shut down
        }
    }

    @Test
    fun `client connects to WebSocket server`() = runBlocking {
        val testListener = TestWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(testListener))

        val url = "ws://${server.hostName}:${server.port}"
        client = WebSocketClient(url)

        val connectedLatch = CountDownLatch(1)
        var receivedEvent: WebSocketEvent? = null

        val job = launch(Dispatchers.IO) {
            client!!.events.collect { event ->
                receivedEvent = event
                connectedLatch.countDown()
            }
        }

        client!!.connect()
        val connected = connectedLatch.await(5, TimeUnit.SECONDS)
        job.cancel()

        assertTrue("Should connect within timeout", connected)
        assertTrue("Should receive Connected event", receivedEvent is WebSocketEvent.Connected)
    }

    @Test
    fun `client reports error on connection failure`() = runBlocking {
        // Close the server immediately
        server.shutdown()

        val url = "ws://localhost:${server.port}"
        client = WebSocketClient(url)

        val errorLatch = CountDownLatch(1)
        var receivedEvent: WebSocketEvent? = null

        val job = launch(Dispatchers.IO) {
            client!!.events.collect { event ->
                receivedEvent = event
                if (event is WebSocketEvent.Error) {
                    errorLatch.countDown()
                }
            }
        }

        client!!.connect()
        val gotError = errorLatch.await(5, TimeUnit.SECONDS)
        job.cancel()

        assertTrue("Should receive error within timeout", gotError)
        assertTrue("Should receive Error event", receivedEvent is WebSocketEvent.Error)
    }

    @Test
    fun `client sends messages`() = runBlocking {
        val testListener = TestWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(testListener))

        val url = "ws://${server.hostName}:${server.port}"
        client = WebSocketClient(url)

        val connectedLatch = CountDownLatch(1)

        val job = launch(Dispatchers.IO) {
            client!!.events.collect { event ->
                if (event is WebSocketEvent.Connected) {
                    connectedLatch.countDown()
                }
            }
        }

        client!!.connect()
        assertTrue("Should connect within timeout", connectedLatch.await(5, TimeUnit.SECONDS))

        val sent = client!!.send("test message")
        assertTrue("Send should return true", sent)

        // Give time for message to be processed
        delay(200)
        job.cancel()

        assertEquals("Server should receive message", "test message", testListener.receivedMessages.firstOrNull())
    }

    @Test
    fun `client receives messages`() = runBlocking {
        val testListener = TestWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(testListener))

        val url = "ws://${server.hostName}:${server.port}"
        client = WebSocketClient(url)

        val connectedLatch = CountDownLatch(1)
        val messageLatch = CountDownLatch(1)
        var receivedMessage: String? = null

        val job = launch(Dispatchers.IO) {
            client!!.events.collect { event ->
                when (event) {
                    is WebSocketEvent.Connected -> connectedLatch.countDown()
                    is WebSocketEvent.Message -> {
                        receivedMessage = event.text
                        messageLatch.countDown()
                    }
                    else -> {}
                }
            }
        }

        client!!.connect()
        assertTrue("Should connect within timeout", connectedLatch.await(5, TimeUnit.SECONDS))

        // Send message from server to client
        testListener.sendMessage("hello from server")

        assertTrue("Should receive message within timeout", messageLatch.await(5, TimeUnit.SECONDS))
        job.cancel()

        assertEquals("Should receive correct message", "hello from server", receivedMessage)
    }

    @Test
    fun `isConnected returns false initially`() {
        val url = "ws://localhost:9999"
        client = WebSocketClient(url)

        assertFalse(client!!.isConnected())
    }

    @Test
    fun `send returns false when not connected`() {
        val url = "ws://localhost:9999"
        client = WebSocketClient(url)

        val sent = client!!.send("test")
        assertFalse(sent)
    }

    @Test
    fun `disconnect closes connection`() = runBlocking {
        val testListener = TestWebSocketListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(testListener))

        val url = "ws://${server.hostName}:${server.port}"
        client = WebSocketClient(url)

        val connectedLatch = CountDownLatch(1)

        val job = launch(Dispatchers.IO) {
            client!!.events.collect { event ->
                if (event is WebSocketEvent.Connected) {
                    connectedLatch.countDown()
                }
            }
        }

        client!!.connect()
        assertTrue("Should connect within timeout", connectedLatch.await(5, TimeUnit.SECONDS))

        client!!.disconnect()
        job.cancel()

        assertFalse("Should not be connected after disconnect", client!!.isConnected())
    }
}

private class TestWebSocketListener : okhttp3.WebSocketListener() {
    private var serverWebSocket: okhttp3.WebSocket? = null
    val receivedMessages = mutableListOf<String>()

    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
        serverWebSocket = webSocket
    }

    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        receivedMessages.add(text)
    }

    fun sendMessage(text: String) {
        serverWebSocket?.send(text)
    }
}
