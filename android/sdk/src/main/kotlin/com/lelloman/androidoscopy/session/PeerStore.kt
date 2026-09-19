package com.lelloman.androidoscopy.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.*

/** Private credentials are encrypted with a non-exportable per-install Android Keystore key. */
internal class PeerStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "androidoscopy").apply { mkdirs() }
    val instanceId: String = File(directory, "instance").let {
        if (it.exists()) it.readText() else UUID.randomUUID().toString().also(it::writeText)
    }
    private val file = File(directory, "peers")
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey("androidoscopy.peers", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).run {
            init(KeyGenParameterSpec.Builder("androidoscopy.peers", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    @Synchronized private fun read(): JsonObject = runCatching {
        val data = Base64.decode(file.readText(), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        Json.parseToJsonElement(String(cipher.doFinal(data.copyOfRange(12, data.size)))).jsonObject
    }.getOrDefault(JsonObject(emptyMap()))
    @Synchronized private fun write(value: JsonObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val temp = File(directory, "peers.tmp")
        temp.writeText(Base64.encodeToString(cipher.iv + cipher.doFinal(value.toString().toByteArray()), Base64.NO_WRAP))
        check(temp.renameTo(file))
    }
    fun get(id: String): ByteArray? = read()[id]?.jsonPrimitive?.content?.let(PairingCrypto::unhex)
    fun list(): List<String> = read().keys.sorted()
    @Synchronized fun remember(id: String, secret: ByteArray) = write(JsonObject(read() + (id to JsonPrimitive(PairingCrypto.hex(secret)))))
    @Synchronized fun forget(id: String) = write(JsonObject(read() - id))
}
