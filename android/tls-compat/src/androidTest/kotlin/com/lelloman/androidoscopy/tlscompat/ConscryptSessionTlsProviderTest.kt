package com.lelloman.androidoscopy.tlscompat

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.net.InetAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

class ConscryptSessionTlsProviderTest {
    @Test fun keystoreTls13AndExporterInteroperate() {
        val backend = ConscryptSessionTlsProvider()
        val alias = "androidoscopy.tls.compat.test"
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                    .setCertificateSubject(X500Principal("CN=androidoscopy-test"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(0))
                    .setCertificateNotAfter(Date(4_102_444_800_000L)).build())
                generateKeyPair()
            }
            val manager = object : X509ExtendedKeyManager() {
                override fun getClientAliases(type: String?, issuers: Array<out Principal>?): Array<String>? = null
                override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
                override fun getServerAliases(type: String?, issuers: Array<out Principal>?) = if (type == "EC") arrayOf(alias) else null
                override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?) = if (type == "EC") alias else null
                override fun chooseEngineServerAlias(type: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = if (type == "EC") alias else null
                override fun getCertificateChain(name: String?): Array<X509Certificate> = keys.getCertificateChain(alias).map { it as X509Certificate }.toTypedArray()
                override fun getPrivateKey(name: String?): PrivateKey = keys.getKey(alias, null) as PrivateKey
            }
            val serverContext = backend.createContext().apply { init(arrayOf(manager), null, null) }
            val label = "EXPORTER-Androidoscopy-v2"
            (serverContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket).use { listener ->
                listener.enabledProtocols = arrayOf("TLSv1.3")
                listener.soTimeout = 10_000
                val server = executor.submit<ByteArray> {
                    (listener.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 5_000
                        socket.startHandshake()
                        val exported = backend.exportKeyingMaterial(socket, label, null, 32)
                        socket.outputStream.write(exported)
                        socket.outputStream.flush()
                        exported
                    }
                }
                val trust = object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    override fun checkClientTrusted(chain: Array<X509Certificate>, auth: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, auth: String) {}
                }
                val clientContext = backend.createContext().apply { init(null, arrayOf(trust), null) }
                (clientContext.socketFactory.createSocket(listener.inetAddress, listener.localPort) as SSLSocket).use { socket ->
                    socket.soTimeout = 5_000
                    socket.enabledProtocols = arrayOf("TLSv1.3")
                    socket.startHandshake()
                    assertEquals("TLSv1.3", socket.session.protocol)
                    val exported = backend.exportKeyingMaterial(socket, label, null, 32)
                    val received = ByteArray(32)
                    java.io.DataInputStream(socket.inputStream).readFully(received)
                    assertArrayEquals(exported, received)
                    assertArrayEquals(exported, server.get(10, TimeUnit.SECONDS))
                }
            }
        } finally {
            executor.shutdownNow()
            keys.deleteEntry(alias)
        }
    }
}
