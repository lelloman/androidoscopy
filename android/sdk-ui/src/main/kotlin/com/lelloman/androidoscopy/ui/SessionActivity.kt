package com.lelloman.androidoscopy.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.Androidoscopy

/** Optional reusable session controls. Opening this screen never activates a release session. */
class SessionActivity : ComponentActivity() {
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val localNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme {
                val state by Androidoscopy.sessionState.collectAsState()
                var error by remember { mutableStateOf<String?>(null) }
                var peers by remember { mutableStateOf(Androidoscopy.rememberedPeers()) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Diagnostic session", style = MaterialTheme.typography.headlineMedium)
                        Text("An authorized PC can use all diagnostic tools enabled by this app, including tools that change app data.")
                        if (state.active) {
                            Text(state.address ?: "Waiting for a local network…")
                            Text(state.peer?.let { "Connected PC: $it" } ?: "Waiting for a PC")
                            state.remainingMs?.let { Text("Expires after inactivity: ${(it + 59_999) / 60_000} min remaining") }
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
                state.pairing?.let { request ->
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
        fun launch(context: Context) = context.startActivity(Intent(context, SessionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
