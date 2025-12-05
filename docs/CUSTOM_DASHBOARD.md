# Custom Dashboard Tutorial

This tutorial walks you through creating custom dashboard sections and widgets for your Android app using the Androidoscopy SDK.

## Understanding the Dashboard Architecture

The Androidoscopy dashboard is **app-driven**: your Android app defines the layout and widgets, and the web dashboard renders them dynamically. This means you can customize the debug interface without modifying the server or dashboard code.

```
Your App                    Server                  Dashboard
┌─────────┐                ┌─────────┐             ┌─────────┐
│Dashboard│ ──REGISTER──►  │ Relays  │ ──SYNC──►  │ Renders │
│  DSL    │                │ Schema  │            │ Widgets │
└─────────┘                └─────────┘             └─────────┘
     │                          │                       ▲
     │ ──────DATA────────►      │ ──SESSION_DATA──►     │
     │ ◄─────ACTION───────      │ ◄───ACTION────────    │
```

## Tutorial: Building a Network Monitor

Let's build a custom dashboard section that monitors network activity.

### Step 1: Define the Data Structure

First, plan what data you want to display:

```kotlin
// Your data model
data class NetworkStats(
    val activeRequests: Int,
    val bytesDownloaded: Long,
    val bytesUploaded: Long,
    val averageLatency: Long,
    val errorCount: Int,
    val requests: List<RequestInfo>
)

data class RequestInfo(
    val id: String,
    val url: String,
    val method: String,
    val status: Int,
    val duration: Long
)
```

### Step 2: Create the Dashboard Section

```kotlin
Androidoscopy.init(this) {
    dashboard {
        // Network overview section
        section("Network Monitor") {
            layout = Layout.GRID
            columns = 2

            row {
                number("Active Requests", "\$.network.active_requests")
                number("Error Count", "\$.network.error_count")
            }

            row {
                bytes("Downloaded", "\$.network.bytes_downloaded")
                bytes("Uploaded", "\$.network.bytes_uploaded")
            }

            row {
                gauge(
                    label = "Latency",
                    valuePath = "\$.network.average_latency",
                    maxPath = "\$.network.latency_threshold",
                    format = Format.DURATION
                )
                badge(
                    label = "Status",
                    dataPath = "\$.network.health_status",
                    variants = mapOf(
                        "healthy" to BadgeStyle.SUCCESS,
                        "degraded" to BadgeStyle.WARNING,
                        "unhealthy" to BadgeStyle.DANGER
                    )
                )
            }
        }

        // Recent requests table
        section("Recent Requests") {
            collapsible = true

            table(dataPath = "\$.network.recent_requests") {
                column("method", "Method")
                column("url", "URL")
                column("status", "Status", Format.NUMBER)
                column("duration", "Duration", Format.DURATION)

                rowAction("retry_request", "Retry") {
                    put("request_id", "\$.id")
                }
            }
        }

        // Network actions
        section("Network Actions") {
            actions {
                button(
                    label = "Clear Cache",
                    action = "clear_network_cache",
                    style = ButtonStyle.PRIMARY
                )

                button(
                    label = "Simulate Offline",
                    action = "toggle_offline",
                    style = ButtonStyle.SECONDARY
                )

                button(
                    label = "Add Latency",
                    action = "add_latency",
                    style = ButtonStyle.SECONDARY
                ) {
                    title = "Configure Latency"
                    numberField("delay_ms", "Delay (ms)", default = 500, min = 0, max = 10000)
                }
            }
        }
    }

    // Register action handlers
    onAction("clear_network_cache") { _ ->
        networkCache.clear()
        ActionResult.success("Network cache cleared")
    }

    onAction("toggle_offline") { _ ->
        isOfflineMode = !isOfflineMode
        ActionResult.success(if (isOfflineMode) "Offline mode enabled" else "Online mode restored")
    }

    onAction("add_latency") { args ->
        val delayMs = (args["delay_ms"] as String).toLong()
        artificialLatency = delayMs
        ActionResult.success("Added ${delayMs}ms latency")
    }

    onAction("retry_request") { args ->
        val requestId = args["request_id"] as String
        retryRequest(requestId)
        ActionResult.success("Request retried")
    }
}
```

### Step 3: Send Data Updates

Create a helper to send network stats:

```kotlin
class NetworkMonitor {
    private val recentRequests = mutableListOf<RequestInfo>()
    private var bytesDownloaded = 0L
    private var bytesUploaded = 0L
    private var errorCount = 0
    private var latencySum = 0L
    private var requestCount = 0

    fun onRequestCompleted(request: RequestInfo) {
        recentRequests.add(0, request)
        if (recentRequests.size > 50) {
            recentRequests.removeLast()
        }

        latencySum += request.duration
        requestCount++

        if (request.status >= 400) {
            errorCount++
        }

        updateDashboard()
    }

    fun onBytesTransferred(downloaded: Long, uploaded: Long) {
        bytesDownloaded += downloaded
        bytesUploaded += uploaded
        updateDashboard()
    }

    private fun updateDashboard() {
        val avgLatency = if (requestCount > 0) latencySum / requestCount else 0
        val healthStatus = when {
            errorCount > 10 -> "unhealthy"
            avgLatency > 1000 -> "degraded"
            else -> "healthy"
        }

        Androidoscopy.updateData {
            put("network", mapOf(
                "active_requests" to activeRequestCount,
                "bytes_downloaded" to bytesDownloaded,
                "bytes_uploaded" to bytesUploaded,
                "average_latency" to avgLatency,
                "latency_threshold" to 2000,  // For the gauge
                "error_count" to errorCount,
                "health_status" to healthStatus,
                "recent_requests" to recentRequests.map { req ->
                    mapOf(
                        "id" to req.id,
                        "url" to req.url,
                        "method" to req.method,
                        "status" to req.status,
                        "duration" to req.duration
                    )
                }
            ))
        }
    }
}
```

## Advanced Techniques

### Conditional Sections

Show sections based on app state:

```kotlin
Androidoscopy.init(this) {
    dashboard {
        // Always show memory
        memorySection()

        // Only add auth section if logged in
        if (userManager.isLoggedIn) {
            section("User Session") {
                row {
                    text("User", "\$.user.name")
                    text("Role", "\$.user.role")
                }
            }
        }

        // Debug-only section
        if (BuildConfig.EXTRA_DEBUG) {
            section("Internal State") {
                row {
                    text("State Machine", "\$.internal.state")
                    number("Queue Size", "\$.internal.queue_size")
                }
            }
        }
    }
}
```

### Dynamic Widget Updates

Update the dashboard schema when app state changes:

```kotlin
fun onFeatureToggled(featureId: String, enabled: Boolean) {
    // Disconnect, reconfigure, and reconnect
    Androidoscopy.disconnect()

    Androidoscopy.init(context) {
        dashboard {
            memorySection()

            if (enabled && featureId == "network_monitor") {
                section("Network Monitor") {
                    // ... network widgets
                }
            }
        }
    }
}
```

### Thresholds and Warnings

Use badges to highlight concerning values:

```kotlin
section("Performance") {
    row {
        badge(
            label = "Frame Rate",
            dataPath = "\$.performance.fps_status",
            variants = mapOf(
                "smooth" to BadgeStyle.SUCCESS,    // 60+ FPS
                "acceptable" to BadgeStyle.INFO,   // 30-59 FPS
                "janky" to BadgeStyle.WARNING,     // 15-29 FPS
                "critical" to BadgeStyle.DANGER    // <15 FPS
            )
        )
    }
}

// In your rendering callback
fun onFrameRendered(frameTimeMs: Long) {
    val fps = 1000 / frameTimeMs
    val status = when {
        fps >= 60 -> "smooth"
        fps >= 30 -> "acceptable"
        fps >= 15 -> "janky"
        else -> "critical"
    }

    Androidoscopy.updateData {
        put("performance", mapOf(
            "fps" to fps,
            "fps_status" to status
        ))
    }
}
```

### Complex Tables with Actions

```kotlin
section("Database Tables") {
    table(dataPath = "\$.database.tables") {
        column("name", "Table Name")
        column("row_count", "Rows", Format.NUMBER)
        column("size_bytes", "Size", Format.BYTES)

        // Multiple row actions
        rowAction("view_table", "View")
        rowAction("clear_table", "Clear") {
            put("table_name", "\$.name")
        }
        rowAction("export_table", "Export") {
            put("table_name", "\$.name")
            put("format", "json")
        }
    }
}
```

### Actions with Complex Dialogs

```kotlin
section("Configuration") {
    actions {
        button(
            label = "Configure Endpoint",
            action = "configure_endpoint",
            style = ButtonStyle.PRIMARY
        ) {
            title = "API Configuration"

            selectField("environment", "Environment", listOf(
                SelectOption("prod", "Production"),
                SelectOption("staging", "Staging"),
                SelectOption("dev", "Development")
            ))

            textField("base_url", "Base URL", default = "https://api.example.com")
            numberField("timeout", "Timeout (s)", default = 30, min = 5, max = 120)
            checkboxField("enable_logging", "Enable Request Logging", default = true)
        }
    }
}

onAction("configure_endpoint") { args ->
    val env = args["environment"] as String
    val baseUrl = args["base_url"] as String
    val timeout = (args["timeout"] as String).toInt()
    val logging = args["enable_logging"] == "true"

    apiClient.configure(env, baseUrl, timeout, logging)
    ActionResult.success("Endpoint configured for $env")
}
```

## Best Practices

### 1. Organize Sections Logically

```kotlin
dashboard {
    // Critical metrics first
    memorySection()

    // Feature-specific sections
    section("Authentication") { /* ... */ }
    section("Data Sync") { /* ... */ }

    // Diagnostic sections (collapsible)
    section("Network") {
        collapsible = true
        // ...
    }

    // Logs at the bottom
    logsSection()
}
```

### 2. Use Meaningful Labels

```kotlin
// Good
number("Heap Usage", "\$.memory.heap_used_bytes")
number("Cache Hit Rate", "\$.cache.hit_rate")

// Avoid
number("Value 1", "\$.v1")
number("x", "\$.x")
```

### 3. Limit Data Update Frequency

The SDK debounces updates automatically, but avoid unnecessary calls:

```kotlin
// Bad: Update on every frame
fun onDraw() {
    Androidoscopy.updateData { /* ... */ }
}

// Good: Update on meaningful changes
fun onMetricsChanged(metrics: Metrics) {
    Androidoscopy.updateData {
        put("metrics", metrics.toMap())
    }
}
```

### 4. Handle Large Data Sets

For tables with many rows, limit the data:

```kotlin
Androidoscopy.updateData {
    put("logs", recentLogs.take(100).map { it.toMap() })
    put("requests", recentRequests.take(50).map { it.toMap() })
}
```

### 5. Provide Action Feedback

Always return meaningful results:

```kotlin
onAction("clear_cache") { _ ->
    try {
        val clearedBytes = cache.clear()
        ActionResult.success("Cleared ${formatBytes(clearedBytes)}")
    } catch (e: Exception) {
        ActionResult.failure("Failed: ${e.message}")
    }
}
```

## Next Steps

- See [SDK_INTEGRATION.md](SDK_INTEGRATION.md) for complete API reference
- Check the demo app in `android/app/` for more examples
- Read [DESIGN.md](../DESIGN.md) for protocol details
