package com.lelloman.lelloman.androidoscopy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.ConnectionState
import com.lelloman.androidoscopy.protocol.LogLevel
import com.lelloman.lelloman.androidoscopy.ui.theme.AndroidoscopyTheme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SampleApplication

        setContent {
            AndroidoscopyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DemoScreen(
                        app = app,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DemoScreen(
    app: SampleApplication,
    modifier: Modifier = Modifier
) {
    val connectionState by Androidoscopy.connectionState.collectAsStateWithLifecycle()
    val clickCount by app.clickCount.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Androidoscopy Demo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "This app showcases all SDK features",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Connection Status Card
        ConnectionStatusCard(connectionState)

        // Data Providers Section
        SectionCard(title = "Data Providers") {
            Text(
                text = "The SDK automatically collects and sends data from registered providers:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            DataProviderItem("Memory", "Heap usage, native heap, pressure level")
            DataProviderItem("Battery", "Level, status, health, temperature")
            DataProviderItem("Storage", "Internal/external storage, app data, cache size")
            DataProviderItem("Threads", "Active count, total count, thread details")
            DataProviderItem("Network", "Connection type, WiFi signal, bandwidth")
        }

        // Custom Data Section
        SectionCard(title = "Custom Data") {
            Text(
                text = "Apps can send custom data to the dashboard:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Click Counter",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Current value: $clickCount",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(onClick = { app.incrementClickCount() }) {
                    Text("Click Me!")
                }
            }
        }

        // Actions Section
        SectionCard(title = "Dashboard Actions") {
            Text(
                text = "Actions can be triggered from the web dashboard:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            DataProviderItem("Force GC", "Triggers garbage collection (in Memory section)")
            DataProviderItem("Clear Cache", "Clears app cache directories (in Memory section)")
            DataProviderItem("Reset Counter", "Resets the click counter (custom action)")
            DataProviderItem("Say Hello", "Returns a greeting message (custom action)")
        }

        // Logging Section
        SectionCard(title = "Logging") {
            Text(
                text = "Send logs to the dashboard at different levels:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LogButton("V", LogLevel.VERBOSE, Color(0xFF9E9E9E), Modifier.weight(1f))
                LogButton("D", LogLevel.DEBUG, Color(0xFF4CAF50), Modifier.weight(1f))
                LogButton("I", LogLevel.INFO, Color(0xFF2196F3), Modifier.weight(1f))
                LogButton("W", LogLevel.WARN, Color(0xFFFF9800), Modifier.weight(1f))
                LogButton("E", LogLevel.ERROR, Color(0xFFF44336), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    Androidoscopy.log(
                        LogLevel.entries.random(),
                        "DemoApp",
                        "Random log message #${Random.nextInt(1000)}"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Random Log")
            }
        }

        // Dashboard Schema Section
        SectionCard(title = "Dashboard Configuration") {
            Text(
                text = "The dashboard layout is defined via a Kotlin DSL:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = """
                        dashboard {
                            memorySection(includeActions = true)
                            batterySection()
                            storageSection()
                            threadSection()
                            logsSection()

                            section("Custom") {
                                row {
                                    number("Clicks", "$.clicks")
                                }
                            }
                        }
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ConnectionStatusCard(state: ConnectionState) {
    val (statusText, statusColor) = when (state) {
        is ConnectionState.Connected -> "Connected" to Color(0xFF4CAF50)
        is ConnectionState.Connecting -> "Connecting..." to Color(0xFFFF9800)
        is ConnectionState.Disconnected -> "Disconnected" to Color(0xFF9E9E9E)
        is ConnectionState.Error -> "Error: ${state.message}" to Color(0xFFF44336)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(
                color = statusColor,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(12.dp)
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor
                )
                if (state is ConnectionState.Connected) {
                    Text(
                        text = "Session: ${state.sessionId.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

@Composable
fun DataProviderItem(name: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LogButton(
    label: String,
    level: LogLevel,
    color: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            Androidoscopy.log(level, "DemoApp", "$label level log message")
        },
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier
    ) {
        Text(label)
    }
}
