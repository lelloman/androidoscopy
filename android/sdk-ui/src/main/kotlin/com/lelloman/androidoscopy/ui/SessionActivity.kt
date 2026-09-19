package com.lelloman.androidoscopy.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.Androidoscopy

/** Optional reusable session controls. Opening this screen never activates a release session. */
class SessionActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val localNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        val palette = SessionPalette.fromArgbArray(intent.getIntArrayExtra(EXTRA_PALETTE))
        setContent {
            val colors = palette?.toColorScheme()
                ?: if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                val state by Androidoscopy.sessionState.collectAsState()
                var error by remember(state.sessionId, state.peer) { mutableStateOf<String?>(null) }
                var confirmAcceptAll by remember(state.sessionId) { mutableStateOf(false) }
                var peers by remember { mutableStateOf(Androidoscopy.rememberedPeers()) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Diagnostic session", style = MaterialTheme.typography.headlineMedium)
                        Text("An authorized PC can use all diagnostic tools enabled by this app, including tools that change app data.")
                        if (state.active) {
                            Text(state.address ?: "Waiting for a local network…")
                            Text(state.peer?.let { "Connected PC: $it" } ?: "Waiting for a PC")
                            state.remainingMs?.let { Text("Expires after inactivity: ${(it + 59_999) / 60_000} min remaining") }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Accept all", style = MaterialTheme.typography.titleMedium)
                                    Text("Automatically approve new PCs for this session only. One PC can connect at a time.",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = state.acceptAll, onCheckedChange = { enabled ->
                                    if (enabled) confirmAcceptAll = true
                                    else error = runCatching { Androidoscopy.setAcceptAllConnections(false) }.exceptionOrNull()?.message
                                })
                            }
                            if (state.acceptAll) Text("Automatic approval is on. Any PC that can reach this session can read data and run all enabled tools.",
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { Androidoscopy.stopSession() }) { Text("Stop session") }
                            OutlinedButton(onClick = { Androidoscopy.sessionActivity() }) { Text("Keep session active") }
                            OutlinedButton(onClick = { DashboardActivity.launch(this@SessionActivity) }) { Text("Open dashboard") }
                        } else {
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= 37 && applicationInfo.targetSdkVersion >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != 0) {
                                    localNetwork.launch("android.permission.ACCESS_LOCAL_NETWORK")
                                    error = "Grant local network access, then start the session."
                                } else error = runCatching { Androidoscopy.startSession() }.exceptionOrNull()?.message
                            }) { Text("Start diagnostic session") }
                        }
                        (error ?: state.reason)?.let { Text(it) }
                        peers.forEach { peer ->
                            OutlinedButton(onClick = { Androidoscopy.forgetPeer(peer); peers = Androidoscopy.rememberedPeers() }) {
                                Text("Forget PC $peer")
                            }
                        }
                    }
                }
                if (confirmAcceptAll && state.active) AlertDialog(
                    onDismissRequest = { confirmAcceptAll = false },
                    title = { Text("Accept all connection requests?") },
                    text = { Text("Any PC that can reach this session will be approved without comparing a code. It can read unredacted logs and use tools that change app data. This resets when the session ends. Turning it off does not disconnect an already approved PC.") },
                    confirmButton = { TextButton(onClick = {
                        error = runCatching { Androidoscopy.setAcceptAllConnections(true) }.exceptionOrNull()?.message
                        confirmAcceptAll = false
                    }) { Text("Enable for this session") } },
                    dismissButton = { TextButton(onClick = { confirmAcceptAll = false }) { Text("Cancel") } },
                )
                state.pairing?.takeUnless { state.acceptAll }?.let { request ->
                    AlertDialog(
                        onDismissRequest = { Androidoscopy.rejectPairing(request.id) },
                        title = { Text("Allow this PC?") },
                        text = { Text("Compare with Androidoscopy on your PC:\n\n${request.code}\n\nConnection from ${request.address}") },
                        confirmButton = { TextButton(onClick = { Androidoscopy.approvePairing(request.id) }) { Text("Numbers match · Allow") } },
                        dismissButton = { TextButton(onClick = { Androidoscopy.rejectPairing(request.id) }) { Text("Reject") } },
                    )
                }
            }
        }
    }
    companion object {
        private const val EXTRA_PALETTE = "com.lelloman.androidoscopy.ui.SESSION_PALETTE_V1"

        @JvmOverloads
        fun launch(context: Context, palette: SessionPalette? = null) =
            context.startActivity(createIntent(context, palette).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        /** The optional palette is a snapshot; relaunch to apply a changed host theme. */
        @JvmOverloads
        fun createIntent(context: Context, palette: SessionPalette? = null): Intent =
            Intent(context, SessionActivity::class.java).apply {
                palette?.let { putExtra(EXTRA_PALETTE, it.toArgbArray()) }
            }
    }
}
