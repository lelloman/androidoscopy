# Androidoscopy SDK Integration Guide

This guide walks you through integrating the Androidoscopy SDK into your Android application.

## Prerequisites

- Android Studio Arctic Fox or later
- Kotlin 1.8+
- Android SDK 24 (Android 7.0) or higher
- Androidoscopy server running on your development machine

## Step 1: Add the Dependency

Add the SDK to your app's `build.gradle.kts`:

```kotlin
dependencies {
    debugImplementation(project(":sdk"))
    // or when published to Maven:
    // debugImplementation("com.lelloman:androidoscopy-sdk:1.0.0")
}
```

Note: Using `debugImplementation` ensures the SDK is only included in debug builds.

## Step 2: Initialize the SDK

In your `Application` class, initialize Androidoscopy:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Only initialize in debug builds
        if (BuildConfig.DEBUG) {
            Androidoscopy.init(this) {
                // Your configuration here
            }
        }
    }
}
```

## Step 3: Configure the Dashboard

The SDK uses a Kotlin DSL to define your dashboard layout:

```kotlin
Androidoscopy.init(this) {
    // Optional: Custom app name (defaults to package name)
    appName = "My Awesome App"

    // Optional: Specify server IP (auto-detected for emulators)
    // hostIp = "192.168.1.100"

    // Define your dashboard layout
    dashboard {
        // Use built-in sections
        memorySection()
        logsSection()

        // Add custom sections
        section("Network Stats") {
            row {
                number("Active Connections", "\$.network.active_connections")
                bytes("Data Transferred", "\$.network.bytes_transferred")
            }
        }
    }
}
```

## Dashboard DSL Reference

### Built-in Sections

```kotlin
dashboard {
    // Memory metrics (heap usage, pressure level)
    memorySection()

    // Log viewer with filtering
    logsSection()

    // Cache table with clear actions
    cacheSection(listOf(
        CacheConfig("image", "Image Cache"),
        CacheConfig("network", "Network Cache")
    ))
}
```

### Custom Sections

```kotlin
section("Section Title") {
    // Layout options: ROW, GRID, STACK
    layout = Layout.ROW

    // Optional: Make section collapsible
    collapsible = true
    collapsedDefault = false

    // For grid layout, specify columns
    // columns = 3

    // Add widgets via row builder
    row {
        // ... widgets
    }
}
```

### Widget Types

#### Number Widget
```kotlin
row {
    number("Label", "\$.path.to.value")
    number("With Format", "\$.path", Format.BYTES)
}
```

#### Text Widget
```kotlin
row {
    text("Status", "\$.status.message")
}
```

#### Gauge Widget
```kotlin
row {
    gauge(
        label = "Memory Usage",
        valuePath = "\$.memory.used",
        maxPath = "\$.memory.max",
        format = Format.BYTES
    )
}
```

#### Badge Widget
```kotlin
row {
    badge(
        label = "Status",
        dataPath = "\$.status",
        variants = mapOf(
            "OK" to BadgeStyle.SUCCESS,
            "WARNING" to BadgeStyle.WARNING,
            "ERROR" to BadgeStyle.DANGER
        )
    )
}
```

### Tables

```kotlin
section("Items") {
    table(dataPath = "\$.items") {
        column("name", "Name")
        column("count", "Count", Format.NUMBER)
        column("size", "Size", Format.BYTES)

        // Add row actions
        rowAction("delete_item", "Delete") {
            put("item_id", "\$.id")  // JSONPath from row
        }
    }
}
```

### Action Buttons

```kotlin
section("Actions") {
    actions {
        // Simple button
        button(
            label = "Refresh",
            action = "refresh_data",
            style = ButtonStyle.PRIMARY
        )

        // Button with confirmation dialog
        button(
            label = "Clear All",
            action = "clear_all",
            style = ButtonStyle.DANGER
        ) {
            title = "Confirm Clear"
            textField("confirm", "Type 'DELETE' to confirm")
        }

        // Button with input dialog
        button(
            label = "Add Item",
            action = "add_item",
            style = ButtonStyle.SECONDARY
        ) {
            title = "Add New Item"
            textField("name", "Item Name")
            numberField("quantity", "Quantity", default = 1, min = 1, max = 100)
            selectField("category", "Category", listOf(
                SelectOption("electronics", "Electronics"),
                SelectOption("clothing", "Clothing"),
                SelectOption("food", "Food")
            ))
        }
    }
}
```

## Step 4: Send Data Updates

Use `updateData` to send metrics to the dashboard:

```kotlin
// Update single values
Androidoscopy.updateData {
    put("network", mapOf(
        "active_connections" to connectionCount,
        "bytes_transferred" to totalBytes
    ))
}

// Update nested data
Androidoscopy.updateData {
    put("user", mapOf(
        "logged_in" to true,
        "session_duration" to sessionMs
    ))
    put("cache", mapOf(
        "hit_rate" to hitRate,
        "size" to cacheSize
    ))
}
```

Data updates are automatically debounced (100ms) to prevent flooding.

## Step 5: Handle Actions

Register handlers for dashboard actions:

```kotlin
Androidoscopy.init(this) {
    // ...

    onAction("refresh_data") { args ->
        refreshData()
        ActionResult.success("Data refreshed")
    }

    onAction("clear_cache") { args ->
        val cacheId = args["cache_id"] as? String
        if (cacheId != null) {
            clearCache(cacheId)
            ActionResult.success("Cache cleared")
        } else {
            ActionResult.failure("Missing cache_id")
        }
    }

    onAction("add_item") { args ->
        val name = args["name"] as String
        val quantity = (args["quantity"] as String).toInt()
        val category = args["category"] as String

        addItem(name, quantity, category)
        ActionResult.success("Item added", data = mapOf("id" to newItemId))
    }
}
```

## Step 6: Integrate Logging

```kotlin
// Log directly
Androidoscopy.log(LogLevel.INFO, "MyTag", "Something happened")
Androidoscopy.log(LogLevel.ERROR, "NetworkClient", "Request failed", exception)

// Log levels: VERBOSE, DEBUG, INFO, WARN, ERROR
```

If you use Timber, you can create a custom tree that forwards to Androidoscopy:

```kotlin
class AndroidoscopyTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.VERBOSE -> LogLevel.VERBOSE
            Log.DEBUG -> LogLevel.DEBUG
            Log.INFO -> LogLevel.INFO
            Log.WARN -> LogLevel.WARN
            else -> LogLevel.ERROR
        }
        Androidoscopy.log(level, tag, message, t)
    }
}
```

## Connection States

Monitor the connection state if needed:

```kotlin
lifecycleScope.launch {
    Androidoscopy.connectionState.collect { state ->
        when (state) {
            is ConnectionState.Disconnected -> { /* Not connected */ }
            is ConnectionState.Connecting -> { /* Connecting... */ }
            is ConnectionState.Connected -> { /* Ready! sessionId = state.sessionId */ }
            is ConnectionState.Error -> { /* Error: state.message */ }
        }
    }
}
```

## Configuration Options

```kotlin
Androidoscopy.init(this) {
    // App name shown in dashboard (optional)
    appName = "My App"

    // Server host IP (optional, auto-detected for emulators)
    hostIp = "192.168.1.100"

    // Server port (default: 9999)
    port = 9999

    // Auto-connect on init (default: true)
    autoConnect = true

    // Enable log forwarding (default: true)
    enableLogging = true

    // Dashboard definition (required)
    dashboard { /* ... */ }

    // Action handlers
    onAction("action_name") { args -> ActionResult.success() }
}
```

## JSONPath Syntax

Data paths use JSONPath syntax to reference values:

| Path | Description |
|------|-------------|
| `$.value` | Root-level key |
| `$.nested.value` | Nested object |
| `$.items[0]` | Array index |
| `$.items[0].name` | Array item property |

## Format Types

| Format | Description | Example |
|--------|-------------|---------|
| `NUMBER` | Locale-formatted number | 1,234,567 |
| `BYTES` | Human-readable bytes | 1.5 MB |
| `PERCENT` | Percentage (0-1 or 0-100) | 75% |
| `TEXT` | Plain text | Hello |
| `DURATION` | Milliseconds to human | 2h 30m |

## Troubleshooting

### App doesn't connect

1. Ensure the server is running: `cargo run --release` in the `server/` directory
2. Check if the server is accessible: visit `http://localhost:8080`
3. For physical devices, ensure they're on the same network and specify `hostIp`
4. Check logcat for connection errors

### Data not updating

1. Verify `updateData` is being called
2. Check that JSONPaths in widgets match your data structure
3. Look for parsing errors in server logs

### Actions not working

1. Ensure action IDs match between dashboard and handlers
2. Check that handlers are registered before connecting
3. Verify handler isn't throwing exceptions
