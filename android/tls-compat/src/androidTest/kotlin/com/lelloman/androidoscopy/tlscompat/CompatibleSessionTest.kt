package com.lelloman.androidoscopy.tlscompat

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.protocol.LogLevel
import com.lelloman.androidoscopy.session.SessionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class CompatibleSessionTest {
    @Test fun configuredCompatibilityProviderServesRealSdkSession() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val backend = ConscryptSessionTlsProvider()
        Androidoscopy.init(app) {
            appName = "Compatibility test"
            sessionMode = SessionMode.MANUAL
            legacyTlsProvider = backend
        }
        try {
            Androidoscopy.startSession()
            Androidoscopy.log(LogLevel.INFO, "TLS", "Compatibility session started")
            assertTrue(Androidoscopy.logFlow.value.single().timestamp.matches(
                Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z")))
            val state = withTimeout(15_000) { Androidoscopy.sessionState.first { it.address != null } }
            val address = requireNotNull(state.address)
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, auth: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, auth: String) {}
            }
            val context = backend.createContext().apply { init(null, arrayOf(trust), null) }
            (context.socketFactory.createSocket(address.substringBefore(':'), address.substringAfter(':').toInt()) as SSLSocket).use { socket ->
                socket.soTimeout = 5_000
                socket.enabledProtocols = arrayOf("TLSv1.3")
                socket.startHandshake()
                assertEquals("TLSv1.3", socket.session.protocol)
                assertEquals(32, backend.exportKeyingMaterial(socket, "EXPORTER-Androidoscopy-v2", null, 32).size)
                val input = DataInputStream(socket.inputStream)
                val length = input.readInt()
                assertTrue(length in 1..1_048_576)
                val bytes = ByteArray(length).also(input::readFully)
                val hello = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                // The runtime exports its key material before sending HELLO.
                assertEquals("HELLO", hello.getValue("type").jsonPrimitive.content)
                assertEquals(state.sessionId, hello.getValue("session").jsonPrimitive.content)
            }
        } finally {
            Androidoscopy.stopSession()
        }
        assertFalse(Androidoscopy.isSessionActive)
    }
}
