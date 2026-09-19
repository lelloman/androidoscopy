package com.lelloman.androidoscopy.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.json.*
import org.conscrypt.Conscrypt
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.Inet4Address
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.security.auth.x500.X500Principal

internal const val MAX_FRAME = 1024 * 1024

internal class FramedSocket(val socket: SSLSocket) {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    fun read(): JsonObject {
        val length = input.readInt()
        require(length in 1..MAX_FRAME) { "Invalid frame size" }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return Json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
    }
    @Synchronized fun write(value: JsonObject) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_FRAME) { "RESULT_TOO_LARGE" }
        output.writeInt(bytes.size); output.write(bytes); output.flush()
    }
    fun exporter(): ByteArray = requireNotNull(Conscrypt.exportKeyingMaterial(socket, PairingCrypto.EXPORTER, null, 32))
}

internal class LanSocket(private val context: Context, private val instanceId: String) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var registration: NsdManager.RegistrationListener? = null
    @Volatile private var server: SSLServerSocket? = null
    val address: String? get() = server?.let { "${it.inetAddress.hostAddress}:${it.localPort}" }

    fun localAddress(): InetAddress? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.allNetworks.asSequence().filter { network ->
            connectivity.getNetworkCapabilities(network)?.let {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        }.flatMap { connectivity.getLinkProperties(it)?.linkAddresses.orEmpty().asSequence() }
            .map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }

    fun open(): SSLServerSocket {
        val address = localAddress() ?: error("Connect to Wi-Fi or Ethernet to start diagnostics")
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias("androidoscopy.tls.v2")) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").run {
                initialize(KeyGenParameterSpec.Builder("androidoscopy.tls.v2", KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    // Conscrypt's TLS engine supplies an already-hashed ECDSA digest.
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                    .setCertificateSubject(X500Principal("CN=androidoscopy"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(0))
                    .setCertificateNotAfter(Date(4_102_444_800_000L)).build())
                generateKeyPair()
            }
        }
        // Never let TLS choose unrelated keys belonging to the host application.
        val alias = "androidoscopy.tls.v2"
        val manager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(type: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
            override fun getServerAliases(type: String?, issuers: Array<out Principal>?): Array<String>? = if (type == "EC") arrayOf(alias) else null
            override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?): String? = if (type == "EC") alias else null
            override fun chooseEngineServerAlias(type: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = if (type == "EC") alias else null
            override fun getCertificateChain(name: String?): Array<X509Certificate>? = if (name == alias)
                keyStore.getCertificateChain(alias).map { it as X509Certificate }.toTypedArray() else null
            override fun getPrivateKey(name: String?): PrivateKey? = if (name == alias) keyStore.getKey(alias, null) as PrivateKey else null
        }
        val tls = SSLContext.getInstance("TLS", Conscrypt.newProvider()).apply { init(arrayOf(manager), null, null) }
        return (tls.serverSocketFactory.createServerSocket(0, 2, address) as SSLServerSocket).also {
            it.enabledProtocols = arrayOf("TLSv1.3")
            server = it
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {}
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
            }
            registration = listener
            nsd.registerService(NsdServiceInfo().apply {
                serviceName = "Androidoscopy-${instanceId.take(8)}"
                serviceType = "_androidoscopy._tcp."
                port = it.localPort
                setAttribute("id", instanceId); setAttribute("version", "2")
            }, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun close() {
        runCatching { server?.close() }; server = null
        registration?.let { runCatching { nsd.unregisterService(it) } }; registration = null
    }
}
