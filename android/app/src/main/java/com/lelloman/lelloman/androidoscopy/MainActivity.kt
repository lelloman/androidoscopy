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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.ConnectionState
import com.lelloman.lelloman.androidoscopy.ui.theme.AndroidoscopyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import timber.log.Timber
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
    val scope = rememberCoroutineScope()

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
            text = "Showcasing all SDK features and integrations",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Connection Status Card
        ConnectionStatusCard(connectionState)

        // OkHttp Demo
        SectionCard(title = "Network Requests (OkHttp)") {
            Text(
                text = "Make HTTP requests to see them in the dashboard:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val request = Request.Builder()
                                    .url("https://httpbin.org/get")
                                    .build()
                                app.okHttpClient.newCall(request).execute().use { response ->
                                    Timber.d("GET request: ${response.code}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Request failed")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("GET")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val request = Request.Builder()
                                    .url("https://httpbin.org/post")
                                    .post(okhttp3.RequestBody.create(null, "test"))
                                    .build()
                                app.okHttpClient.newCall(request).execute().use { response ->
                                    Timber.d("POST request: ${response.code}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Request failed")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("POST")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val request = Request.Builder()
                                    .url("https://httpbin.org/status/404")
                                    .build()
                                app.okHttpClient.newCall(request).execute().use { response ->
                                    Timber.d("404 request: ${response.code}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Request failed")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("404")
                }
            }
        }

        // Coil Demo
        SectionCard(title = "Image Loading (Coil)") {
            Text(
                text = "Load images to populate cache stats:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(app)
                        .data("https://picsum.photos/100/100?random=${Random.nextInt()}")
                        .build(),
                    contentDescription = "Random image",
                    imageLoader = app.imageLoader,
                    modifier = Modifier.size(60.dp)
                )
                AsyncImage(
                    model = ImageRequest.Builder(app)
                        .data("https://picsum.photos/100/100?random=${Random.nextInt()}")
                        .build(),
                    contentDescription = "Random image",
                    imageLoader = app.imageLoader,
                    modifier = Modifier.size(60.dp)
                )
                AsyncImage(
                    model = ImageRequest.Builder(app)
                        .data("https://picsum.photos/100/100?random=${Random.nextInt()}")
                        .build(),
                    contentDescription = "Random image",
                    imageLoader = app.imageLoader,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    // Load multiple images to fill cache
                    repeat(5) {
                        Timber.d("Loading image $it")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load More Images")
            }
        }

        // Custom Data Section
        SectionCard(title = "Custom Metrics") {
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

        // Logging Section
        SectionCard(title = "Logging (Timber)") {
            Text(
                text = "All logs via Timber are forwarded to the dashboard:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LogButton("V", Color(0xFF9E9E9E), Modifier.weight(1f)) {
                    Timber.v("Verbose log message")
                }
                LogButton("D", Color(0xFF4CAF50), Modifier.weight(1f)) {
                    Timber.d("Debug log message")
                }
                LogButton("I", Color(0xFF2196F3), Modifier.weight(1f)) {
                    Timber.i("Info log message")
                }
                LogButton("W", Color(0xFFFF9800), Modifier.weight(1f)) {
                    Timber.w("Warning log message")
                }
                LogButton("E", Color(0xFFF44336), Modifier.weight(1f)) {
                    Timber.e("Error log message")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val levels = listOf(
                        { Timber.v("Random verbose #${Random.nextInt(1000)}") },
                        { Timber.d("Random debug #${Random.nextInt(1000)}") },
                        { Timber.i("Random info #${Random.nextInt(1000)}") },
                        { Timber.w("Random warning #${Random.nextInt(1000)}") },
                        { Timber.e("Random error #${Random.nextInt(1000)}") }
                    )
                    levels.random()()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Random Log")
            }
        }

        // Dashboard Actions Section
        SectionCard(title = "Dashboard Actions") {
            Text(
                text = "These actions can be triggered from the web dashboard:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            DataProviderItem("Force GC", "Triggers garbage collection")
            DataProviderItem("Clear Cache", "Clears app cache directories")
            DataProviderItem("Reset Counter", "Resets the click counter")
            DataProviderItem("Trigger ANR", "Blocks main thread for 5 seconds")
            DataProviderItem("Cancel Work", "Cancel WorkManager jobs")
            DataProviderItem("Clear Image Cache", "Clear Coil memory/disk cache")
            DataProviderItem("Execute SQL", "Run queries on the demo database")
            DataProviderItem("Edit Preferences", "Modify SharedPreferences entries")
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
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier
    ) {
        Text(label)
    }
}
