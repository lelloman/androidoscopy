package com.lelloman.androidoscopy.session

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.conscrypt.Conscrypt
import org.junit.Assert.*
import org.junit.Test
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/** Exercises the real Android Keystore key through the scoped Conscrypt provider. */
class LanSocketTest {
    @Test fun keystoreTls13ExporterAndFramingInteroperate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lan = LanSocket(context, "instrumentation")
        val executor = Executors.newSingleThreadExecutor()
        try {
            val listener = lan.open()
            val server = executor.submit<ByteArray> {
                (listener.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = 5000
                    try { socket.startHandshake() } catch (e: Exception) {
                        android.util.Log.e("AndroidoscopyTest", "Server handshake failed", e)
                        throw e
                    }
                    val framed = FramedSocket(socket)
                    val input = framed.read()
                    framed.write(input)
                    framed.exporter()
                }
            }
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, auth: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, auth: String) {}
            }
            val tls = SSLContext.getInstance("TLS", Conscrypt.newProvider()).apply { init(null, arrayOf(trust), null) }
            (tls.socketFactory.createSocket(listener.inetAddress, listener.localPort) as SSLSocket).use { socket ->
                socket.soTimeout = 5000
                socket.enabledProtocols = arrayOf("TLSv1.3")
                socket.startHandshake()
                assertEquals("TLSv1.3", socket.session.protocol)
                val framed = FramedSocket(socket)
                val value = Json.parseToJsonElement("""{"typed":[true,42,null,{"n":1.25}]}""").jsonObject
                framed.write(value)
                assertEquals(value, framed.read())
                assertArrayEquals(server.get(10, TimeUnit.SECONDS), framed.exporter())
            }
        } finally { lan.close(); executor.shutdownNow() }
    }
}
