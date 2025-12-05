# Androidoscopy - Design Document

**Version:** 0.2.0-draft
**Status:** Brainstorming Phase
**Last Updated:** 2025-12-05

---

## Vision

**Androidoscopy** is a developer tool that eliminates the friction of debugging Android applications by providing a persistent, always-on debug service that apps can connect to automatically. No more `adb forward` commands, no more port juggling - just start your app and see debug data in your browser.

### Core Principles

1. **Zero Configuration** - Once installed, it just works
2. **Always Available** - Runs as background service, available whenever you need it
3. **Multi-Device** - Handle multiple emulators and physical devices simultaneously
4. **App-Driven UI** - Apps define their own dashboard layout; server is generic
5. **Language Agnostic** - While we start with Android/Kotlin, protocol should work for any platform
6. **Privacy First** - Debug data stays on localhost, never leaves your machine

---

## Problem Statement

### Current Pain Points

1. **Port Forwarding Tedium**
   - Must run `adb forward tcp:X tcp:Y` every time
   - Different ports for different apps/devices
   - Forget to forward = confusion why debug UI isn't working

2. **Single Device Limitation**
   - Traditional approach: one HTTP server per app
   - Can't easily compare metrics across devices
   - Can't see data after app closes

3. **Limited Visibility**
   - Debug UI dies when app crashes/closes
   - Can't see what happened before crash

4. **Reinventing the Wheel**
   - Every app builds its own debug HTTP server
   - No standardization
   - Can't reuse tooling

### What Androidoscopy Solves

- **One-time setup**: Install service once, use forever
- **Auto-connection**: Apps automatically connect on startup
- **Persistent dashboard**: View data even after app closes (session history)
- **Multi-device**: See all devices/emulators in one place
- **Extensible SDK**: Drop-in library with customizable dashboard

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Developer's Machine                      │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         Androidoscopy Service (Rust + Axum)            │ │
│  │                                                         │ │
│  │  ┌──────────────────┐         ┌────────────────────┐   │ │
│  │  │  WebSocket Hub   │         │   HTTP Server      │   │ │
│  │  │    (port 9999)   │         │   (port 8080)      │   │ │
│  │  │                  │         │                    │   │ │
│  │  │  - App conns     │         │  - Serve dashboard │   │ │
│  │  │  - Dashboard conn│         │    static files    │   │ │
│  │  │  - Message relay │         │                    │   │ │
│  │  └──────────────────┘         └────────────────────┘   │ │
│  │           │                                             │ │
│  │           ▼                                             │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │          Session Manager (in-memory)              │  │ │
│  │  │  - Track connected apps                           │  │ │
│  │  │  - Store UI schemas per session                   │  │ │
│  │  │  - Route messages: app ↔ dashboard                │  │ │
│  │  │  - Buffer recent data for dashboard sync          │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │                                                         │ │
│  │  Server has NO domain knowledge. It's a relay.         │ │
│  └────────────────────────────────────────────────────────┘ │
│                           ▲                                  │
│                           │                                  │
│                    WebSocket connections                     │
│                   (app initiates to host)                    │
└───────────────────────────┼──────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
  ┌──────────┐        ┌──────────┐       ┌──────────┐
  │ Emulator │        │ Emulator │       │  Device  │
  │          │        │          │       │          │
  │ ┌──────┐ │        │ ┌──────┐ │       │ ┌──────┐ │
  │ │ App  │ │        │ │ App  │ │       │ │ App  │ │
  │ │  +   │ │        │ │  +   │ │       │ │  +   │ │
  │ │ SDK  │ │        │ │ SDK  │ │       │ │ SDK  │ │
  │ └──────┘ │        │ └──────┘ │       │ └──────┘ │
  └──────────┘        └──────────┘       └──────────┘
```

### Key Insight: App-Driven Dashboard

The server is **completely generic**. It doesn't know what "memory" or "cache" means. Each app:

1. Sends a **UI schema** at registration describing its dashboard layout
2. Sends **data updates** as opaque JSON blobs
3. The dashboard **renders dynamically** based on the schema

This means:
- Server code never changes for new metric types
- Apps have full control over their debug UI
- Same server works for any app, any domain
- SDK provides templates for common patterns (memory, logs, etc.)

---

## Components

### 1. Androidoscopy Service (Rust)

**Responsibilities:**
- Accept WebSocket connections from apps and dashboard
- Manage sessions (connect, disconnect, track active apps)
- Store UI schemas in memory
- Relay messages between apps and dashboard
- Serve dashboard static files

**What it does NOT do:**
- Persist data to disk (in-memory only for MVP)
- Understand app-specific data formats
- Validate metric contents
- Aggregate or transform data

**Technology:**
- **Framework:** Axum (modern, tokio-native HTTP framework)
- Built-in WebSocket support
- Tower middleware ecosystem
- Maintained by tokio team

### 2. Android SDK (Kotlin)

**Responsibilities:**
- Connect to service via WebSocket
- Provide UI schema builders for dashboard layout
- Offer built-in templates (memory, logs, cache, etc.)
- Allow custom widgets and actions
- Push data updates
- Handle reconnection logic

**WebSocket Client:** OkHttp
- Already a dependency in most Android apps
- Battle-tested WebSocket support
- Well-documented API

**SDK Philosophy:**
```kotlin
// The SDK provides building blocks, not a fixed structure
Androidoscopy.init(this) {
    dashboard {
        // Use built-in templates
        memorySection()
        logsSection()

        // Add custom sections
        section("Downloads") {
            row {
                number("Active", data = "$.downloads.active")
                number("Queued", data = "$.downloads.queued")
            }
            action("Cancel All") { downloadManager.cancelAll() }
        }
    }
}
```

### 3. Web Dashboard (Svelte)

**Responsibilities:**
- Connect to service via WebSocket
- Receive UI schemas from connected apps
- Dynamically render widgets based on schema
- Send user actions back to apps
- Show all connected apps/sessions

**Technology:** Svelte
- Compiles to vanilla JS with minimal runtime (~2KB)
- Less boilerplate than React/Vue
- Scoped CSS by default
- Fast builds with Vite

**Dashboard is a generic renderer** - it interprets the UI schema and renders appropriate widgets. It has no app-specific knowledge.

---

## Protocol Design

### Connection Lifecycle

```
App                                    Service
 │                                        │
 ├─── CONNECT (WebSocket) ───────────────>│
 │                                        │
 ├─── REGISTER (app info + UI schema) ───>│
 │                                        │
 │<─────── REGISTERED (session_id) ───────┤
 │                                        │
 ├─── DATA (metrics blob) ───────────────>│
 │                                        │
 ├─── LOG (log entry) ───────────────────>│
 │                                        │
 │<─────── ACTION (user triggered) ───────┤
 │                                        │
 ├─── ACTION_RESULT (success/fail) ──────>│
 │                                        │
```

### Message Format

All messages are JSON over WebSocket.

#### Base Structure

```json
{
  "type": "MESSAGE_TYPE",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "optional-uuid",
  "payload": {}
}
```

---

### App → Service Messages

#### REGISTER

Sent immediately after WebSocket connection. Includes app info and **UI schema**.

```json
{
  "type": "REGISTER",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "payload": {
    "protocol_version": "1.0",
    "app_name": "Pezzottify",
    "package_name": "com.lelloman.pezzottify",
    "version_name": "1.0.0",
    "version_code": 42,
    "device": {
      "device_id": "d3b07384-d9a3-4e6b-8b0d-324f5e8c1a2f",
      "manufacturer": "Google",
      "model": "Pixel 5",
      "android_version": "14",
      "api_level": 34,
      "is_emulator": true
    },
    "dashboard": {
      "sections": [
        {
          "id": "memory",
          "title": "Memory",
          "widgets": [
            {
              "type": "gauge",
              "label": "Heap Usage",
              "value": "$.memory.heap_used_bytes",
              "max": "$.memory.heap_max_bytes",
              "format": "bytes"
            },
            {
              "type": "badge",
              "label": "Pressure",
              "value": "$.memory.pressure_level",
              "variants": {
                "LOW": "success",
                "MODERATE": "warning",
                "HIGH": "danger",
                "CRITICAL": "danger"
              }
            }
          ]
        },
        {
          "id": "caches",
          "title": "Caches",
          "widget": {
            "type": "table",
            "data": "$.cache",
            "columns": [
              { "key": "name", "label": "Name" },
              { "key": "entry_count", "label": "Entries" },
              { "key": "size_bytes", "label": "Size", "format": "bytes" },
              { "key": "hit_rate", "label": "Hit Rate", "format": "percent" }
            ],
            "row_actions": [
              { "id": "clear_cache", "label": "Clear", "args": { "cache_name": "$.name" } }
            ]
          }
        },
        {
          "id": "actions",
          "title": "Actions",
          "widgets": [
            {
              "type": "button",
              "label": "Clear All Caches",
              "action": "clear_all_caches",
              "style": "danger"
            },
            {
              "type": "button",
              "label": "Refresh Token",
              "action": "refresh_token",
              "style": "primary"
            }
          ]
        },
        {
          "id": "logs",
          "title": "Logs",
          "widget": {
            "type": "log_viewer",
            "data": "$.logs"
          }
        }
      ]
    }
  }
}
```

**Notes:**
- `device_id`: UUID generated by SDK on first run, persisted in app storage
- `dashboard`: UI schema defining how to render this app's debug view
- Widget `value` fields use JSONPath syntax to reference data

#### REGISTERED (Service → App)

```json
{
  "type": "REGISTERED",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

#### DATA

Sends data updates. The server doesn't interpret this - it's forwarded to dashboard and rendered according to UI schema.

```json
{
  "type": "DATA",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "memory": {
      "pressure_level": "LOW",
      "heap_used_bytes": 45678900,
      "heap_max_bytes": 268435456
    },
    "cache": [
      { "name": "artists", "entry_count": 150, "size_bytes": 45000, "hit_rate": 0.94 },
      { "name": "albums", "entry_count": 300, "size_bytes": 120000, "hit_rate": 0.91 }
    ],
    "downloads": {
      "active": 3,
      "queued": 12
    }
  }
}
```

#### LOG

```json
{
  "type": "LOG",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "level": "ERROR",
    "tag": "NetworkClient",
    "message": "Failed to fetch artist data",
    "throwable": "java.net.SocketTimeoutException: timeout\n  at ..."
  }
}
```

#### ACTION_RESULT

Response to an ACTION from the dashboard.

```json
{
  "type": "ACTION_RESULT",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "action_id": "act-123",
    "success": true,
    "message": "Cleared 150 entries",
    "data": {
      "entries_removed": 150
    }
  }
}
```

---

### Service → App Messages

#### ACTION

Triggered by user in dashboard.

```json
{
  "type": "ACTION",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "action_id": "act-123",
    "action": "clear_cache",
    "args": {
      "cache_name": "artists"
    }
  }
}
```

#### ERROR

```json
{
  "type": "ERROR",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "error_code": "INVALID_MESSAGE",
    "message": "Unknown message type: FOOBAR"
  }
}
```

---

### Dashboard ↔ Service Protocol

The dashboard connects via WebSocket to receive real-time updates.

#### Connection Flow

```
Dashboard                              Service
    │                                     │
    ├─── CONNECT (WebSocket) ────────────>│
    │                                     │
    │<─────── SYNC (current state) ───────┤
    │                                     │
    │<─────── SESSION_STARTED ────────────┤  (when app connects)
    │                                     │
    │<─────── SESSION_DATA ───────────────┤  (forwarded DATA)
    │                                     │
    │<─────── SESSION_LOG ────────────────┤  (forwarded LOG)
    │                                     │
    │<─────── SESSION_ENDED ──────────────┤  (when app disconnects)
    │                                     │
    ├─── ACTION ─────────────────────────>│  (user clicks button)
    │                                     │
    │<─────── ACTION_RESULT ──────────────┤  (forwarded from app)
```

#### SYNC (Service → Dashboard)

Sent on dashboard connect with all active sessions.

```json
{
  "type": "SYNC",
  "payload": {
    "sessions": [
      {
        "session_id": "550e8400-e29b-41d4-a716-446655440000",
        "app_name": "Pezzottify",
        "package_name": "com.lelloman.pezzottify",
        "version_name": "1.0.0",
        "device": {
          "model": "Pixel 5",
          "is_emulator": true
        },
        "started_at": "2024-12-02T14:30:00.000Z",
        "dashboard": { ... },
        "latest_data": { ... },
        "recent_logs": [ ... ]
      }
    ]
  }
}
```

#### SESSION_STARTED (Service → Dashboard)

```json
{
  "type": "SESSION_STARTED",
  "payload": {
    "session_id": "...",
    "app_name": "...",
    "device": { ... },
    "dashboard": { ... }
  }
}
```

#### SESSION_DATA (Service → Dashboard)

Forwarded DATA from app.

```json
{
  "type": "SESSION_DATA",
  "payload": {
    "session_id": "...",
    "timestamp": "...",
    "data": { ... }
  }
}
```

#### SESSION_LOG (Service → Dashboard)

Forwarded LOG from app.

```json
{
  "type": "SESSION_LOG",
  "payload": {
    "session_id": "...",
    "timestamp": "...",
    "level": "ERROR",
    "tag": "...",
    "message": "..."
  }
}
```

#### SESSION_ENDED (Service → Dashboard)

```json
{
  "type": "SESSION_ENDED",
  "payload": {
    "session_id": "..."
  }
}
```

#### ACTION (Dashboard → Service)

```json
{
  "type": "ACTION",
  "payload": {
    "session_id": "...",
    "action": "clear_cache",
    "args": { "cache_name": "artists" }
  }
}
```

---

### Message Size Limits

| Field | Max Size |
|-------|----------|
| Single message | 1 MB |
| LOG `message` field | 64 KB |
| LOG `throwable` field | 256 KB |

---

## UI Schema Specification

The UI schema defines how the dashboard renders an app's debug view. The schema supports flexible layouts and conditional rendering.

### Widget Types

The dashboard supports a fixed set of built-in widget types. Custom widgets are not supported - if a use case isn't covered, we add a new built-in type.

| Type | Description | Properties |
|------|-------------|------------|
| `gauge` | Progress bar with value/max | `value`, `max`, `format`, `thresholds` |
| `number` | Simple numeric display | `value`, `format`, `thresholds` |
| `text` | Text display | `value` |
| `badge` | Colored label | `value`, `variants` |
| `table` | Data table | `data`, `columns`, `row_actions` |
| `log_viewer` | Scrolling log display | `data` (see Log Viewer section) |
| `button` | Action trigger | `action`, `args`, `style` |

### Layout System

Sections support flexible layouts:

```json
{
  "id": "overview",
  "title": "Overview",
  "layout": "grid",
  "columns": 3,
  "collapsible": true,
  "collapsed_default": false,
  "widgets": [...]
}
```

**Layout Types:**
| Layout | Description |
|--------|-------------|
| `row` | Widgets in a horizontal row (default) |
| `grid` | CSS grid with configurable columns |
| `stack` | Vertical stack of widgets |

**Section Options:**
| Option | Description |
|--------|-------------|
| `collapsible` | Section can be collapsed/expanded |
| `collapsed_default` | Start collapsed |
| `columns` | Number of grid columns (for `grid` layout) |

### Conditional Rendering

Widgets can be shown/hidden based on data values:

```json
{
  "type": "badge",
  "label": "Warning",
  "value": "$.status",
  "visible_when": {
    "path": "$.error_count",
    "operator": "gt",
    "value": 0
  }
}
```

**Operators:**
| Operator | Description |
|----------|-------------|
| `eq` | Equals |
| `neq` | Not equals |
| `gt` | Greater than |
| `gte` | Greater than or equal |
| `lt` | Less than |
| `lte` | Less than or equal |
| `exists` | Value is not null/undefined |

### Threshold-Based Styling

Widgets can change appearance based on value thresholds:

```json
{
  "type": "gauge",
  "label": "Heap Usage",
  "value": "$.memory.heap_used_bytes",
  "max": "$.memory.heap_max_bytes",
  "format": "bytes",
  "thresholds": [
    { "max": 0.7, "style": "success" },
    { "max": 0.9, "style": "warning" },
    { "max": 1.0, "style": "danger" }
  ]
}
```

For gauges, thresholds are percentages of value/max. For numbers, thresholds compare the raw value.

**Threshold Styles:**
| Style | Color |
|-------|-------|
| `success` | Green |
| `warning` | Yellow/Orange |
| `danger` | Red |
| `info` | Blue |
| `muted` | Gray |

### Value References (JSONPath)

Widget values use JSONPath to reference data:
- `$.memory.heap_used_bytes` - Direct path
- `$.cache[0].name` - Array access
- `$.cache` - Entire array (for tables)

### Formats

| Format | Example |
|--------|---------|
| `bytes` | 45678900 → "43.5 MB" |
| `percent` | 0.94 → "94%" |
| `duration` | 3600000 → "1h 0m" |
| `number` | 1234567 → "1,234,567" |

### Button Styles

| Style | Use Case |
|-------|----------|
| `primary` | Main actions |
| `secondary` | Alternative actions |
| `danger` | Destructive actions (clear, delete) |

### Actions

Actions can require user input and specify how to handle results.

#### Action Arguments

Actions can collect user input via a dialog before executing:

```json
{
  "type": "button",
  "label": "Set Max Downloads",
  "action": "set_max_downloads",
  "style": "primary",
  "args_dialog": {
    "title": "Set Maximum Concurrent Downloads",
    "fields": [
      { "key": "max", "label": "Maximum", "type": "number", "default": 3, "min": 1, "max": 10 }
    ]
  }
}
```

**Field Types:**
| Type | Description |
|------|-------------|
| `text` | Single-line text input |
| `number` | Numeric input with optional min/max |
| `select` | Dropdown with options |
| `checkbox` | Boolean toggle |

**Select Example:**
```json
{ "key": "cache_type", "label": "Cache", "type": "select", "options": [
  { "value": "all", "label": "All Caches" },
  { "value": "images", "label": "Images Only" }
]}
```

When `args_dialog` is present, clicking the button opens a dialog. User fills in fields and confirms, then the action is sent with collected args.

#### Result Handling

Actions specify how to display results via `result_display`:

```json
{
  "type": "button",
  "label": "Refresh Token",
  "action": "refresh_token",
  "result_display": {
    "type": "toast"
  }
}
```

**Result Display Types:**
| Type | Description |
|------|-------------|
| `toast` | Show result message in toast notification (default) |
| `dialog` | Show result in a modal dialog |
| `update` | Update a text widget with the result |

**Update Example:**
```json
{
  "type": "button",
  "label": "Get Token",
  "action": "get_current_token",
  "result_display": {
    "type": "update",
    "target_id": "token_display",
    "mode": "replace",
    "value": "$.data.token"
  }
}
```

| Mode | Description |
|------|-------------|
| `replace` | Replace widget content with result value |
| `append` | Append result value to existing content |

#### Button State Feedback

| State | Appearance |
|-------|------------|
| Idle | Normal styling |
| Loading | Spinner, disabled |
| Success | Brief green flash, then idle |
| Error | Red border + error icon, clears on next click |

### Log Viewer Widget

The `log_viewer` widget displays scrollable logs with filtering and search.

**Features:**
| Feature | Behavior |
|---------|----------|
| Scrolling | Infinite scroll, newest logs at bottom |
| Level filter | Dropdown to filter by level (VERBOSE, DEBUG, INFO, WARN, ERROR) |
| Tag filter | Text input to filter by tag (substring match) |
| Text search | Filter logs by message content (substring match) |
| Auto-scroll | Enabled by default, pauses when user scrolls up |
| Jump to bottom | Button appears when auto-scroll is paused |

**Schema Example:**
```json
{
  "id": "logs",
  "title": "Logs",
  "widget": {
    "type": "log_viewer",
    "data": "$.logs",
    "default_level": "INFO",
    "default_tag_filter": ""
  }
}
```

**Log Entry Display:**
```
[14:30:05.123] ERROR NetworkClient
    Failed to fetch artist data
    java.net.SocketTimeoutException: timeout
        at okhttp3.internal...
```

---

## SDK Design

### Initialization

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Androidoscopy.init(this) {
            // Optional: override auto-detected values
            appName = "Pezzottify"

            // Define dashboard layout
            dashboard {
                // Built-in templates
                memorySection()
                logsSection()

                // Custom section
                section("Downloads") {
                    row {
                        number("Active", "$.downloads.active")
                        number("Queued", "$.downloads.queued")
                        bytes("Total Size", "$.downloads.total_bytes")
                    }
                    actions {
                        button("Cancel All", action = "cancel_downloads", style = Style.DANGER)
                    }
                }

                // Cache section with built-in template
                cacheSection(
                    caches = listOf(
                        CacheConfig("artists", artistCache),
                        CacheConfig("albums", albumCache)
                    )
                )
            }

            // Register action handlers
            onAction("cancel_downloads") {
                downloadManager.cancelAll()
                ActionResult.success("Cancelled all downloads")
            }

            onAction("clear_cache") { args ->
                val cacheName = args["cache_name"] as String
                val removed = cacheManager.clear(cacheName)
                ActionResult.success("Cleared $removed entries")
            }
        }
    }
}
```

### Dashboard DSL

```kotlin
fun dashboard(block: DashboardBuilder.() -> Unit)

class DashboardBuilder {
    // Built-in templates
    fun memorySection()
    fun logsSection()
    fun cacheSection(caches: List<CacheConfig>)

    // Custom sections
    fun section(title: String, block: SectionBuilder.() -> Unit)
}

class SectionBuilder {
    fun row(block: RowBuilder.() -> Unit)
    fun table(dataPath: String, block: TableBuilder.() -> Unit)
    fun actions(block: ActionsBuilder.() -> Unit)
    fun widget(widget: Widget)
}

class RowBuilder {
    fun number(label: String, dataPath: String)
    fun text(label: String, dataPath: String)
    fun bytes(label: String, dataPath: String)
    fun percent(label: String, dataPath: String)
    fun gauge(label: String, valuePath: String, maxPath: String)
    fun badge(label: String, dataPath: String, variants: Map<String, BadgeStyle>)
}

class TableBuilder {
    fun column(key: String, label: String, format: Format = Format.TEXT)
    fun rowAction(id: String, label: String, argsBuilder: (RowContext) -> Map<String, Any>)
}

class ActionsBuilder {
    fun button(label: String, action: String, style: Style = Style.PRIMARY)

    // Button with argument dialog
    fun button(
        label: String,
        action: String,
        style: Style = Style.PRIMARY,
        argsDialog: ArgsDialogBuilder.() -> Unit
    )

    // Button with result display configuration
    fun button(
        label: String,
        action: String,
        style: Style = Style.PRIMARY,
        resultDisplay: ResultDisplay = ResultDisplay.Toast
    )
}

class ArgsDialogBuilder {
    var title: String = ""
    fun textField(key: String, label: String, default: String = "")
    fun numberField(key: String, label: String, default: Int = 0, min: Int? = null, max: Int? = null)
    fun selectField(key: String, label: String, options: List<SelectOption>)
    fun checkboxField(key: String, label: String, default: Boolean = false)
}

sealed class ResultDisplay {
    object Toast : ResultDisplay()
    object Dialog : ResultDisplay()
    data class UpdateWidget(val targetId: String, val mode: UpdateMode, val valuePath: String) : ResultDisplay()
}

enum class UpdateMode { REPLACE, APPEND }

// Example usage:
actions {
    button("Set Max Downloads", "set_max_downloads") {
        title = "Set Maximum Concurrent Downloads"
        numberField("max", "Maximum", default = 3, min = 1, max = 10)
    }

    button(
        "Get Token",
        "get_current_token",
        resultDisplay = ResultDisplay.UpdateWidget("token_display", UpdateMode.REPLACE, "$.data.token")
    )
}
```

### Data Updates

The SDK supports both **polling** (for regular metrics) and **push** (for event-driven updates):

```kotlin
// OPTION 1: Periodic polling via data providers
// Good for: memory stats, cache stats, queue sizes
Androidoscopy.registerDataProvider("memory", interval = 5.seconds) {
    mapOf(
        "heap_used_bytes" to Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
        "heap_max_bytes" to Runtime.getRuntime().maxMemory()
    )
}

// OPTION 2: Push on demand
// Good for: events, state changes, completion notifications
Androidoscopy.updateData {
    put("downloads", mapOf(
        "active" to downloadManager.activeCount,
        "queued" to downloadManager.queuedCount,
        "last_completed" to lastDownload.name
    ))
}

// The SDK internally debounces rapid pushes to prevent flooding
// Multiple updateData calls within 100ms are batched together
```

**When to use which:**
| Use Case | Strategy |
|----------|----------|
| Memory/CPU stats | Polling (5-10s interval) |
| Cache statistics | Polling (5s interval) |
| Download progress | Push on change |
| Error occurred | Push immediately |
| User action completed | Push immediately |

### Logging Integration

```kotlin
// Timber integration
class AndroidoscopyTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Androidoscopy.log(
            level = priority.toLogLevel(),
            tag = tag,
            message = message,
            throwable = t
        )
    }
}

Timber.plant(AndroidoscopyTree())
```

---

## Connection Strategy

### Emulator Connection

Different emulators use different special IPs to reach the host machine:

| Emulator | Host IP |
|----------|---------|
| Android Emulator (AVD) | `10.0.2.2` |
| Genymotion | `10.0.3.2` |
| Android-x86 | `192.168.56.1` |
| BlueStacks | `10.0.2.2` |

SDK automatically tries known emulator IPs when `isEmulator()` returns true.

### Physical Device Connection

Physical devices discover the service via UDP broadcast:

1. **Service broadcasts presence** on the local network every 5 seconds
2. **SDK listens** for broadcast when `isEmulator()` returns false
3. **SDK connects** to the discovered service IP

#### UDP Discovery Protocol

**Service Broadcast (port 9998):**
```json
{
  "service": "androidoscopy",
  "version": "1.0",
  "websocket_port": 9999,
  "http_port": 8080
}
```

**SDK Discovery Flow:**
```kotlin
// 1. Listen for UDP broadcast on port 9998
// 2. Parse broadcast, extract host IP from packet source
// 3. Connect to ws://{host_ip}:{websocket_port}
// 4. If no broadcast received within 10s, fall back to manual config
```

**Fallback:** If discovery fails, user can manually configure host IP:
```kotlin
Androidoscopy.init(this) {
    hostIp = "192.168.1.100"  // Manual override
}
```

**Network Requirements:**
- Device and host must be on same WiFi network
- UDP broadcast must not be blocked by router
- Some corporate/guest networks may block this

### Reconnection Logic

```kotlin
class ReconnectionManager {
    private val backoff = ExponentialBackoff(
        initialDelay = 1.seconds,
        maxDelay = 30.seconds,
        factor = 2.0
    )

    suspend fun maintainConnection() {
        while (true) {
            try {
                connect()
                backoff.reset()
                // Connection successful, handle messages
            } catch (e: Exception) {
                val delay = backoff.nextDelay()
                logger.warn("Connection failed, retry in $delay")
                delay(delay)
            }
        }
    }
}
```

---

## Testing Strategy

Comprehensive automated testing across all components.

### Service (Rust)

**Unit Tests:**
- Message parsing and serialization
- Session manager logic (create, destroy, lookup)
- Buffer management (ring buffer for DATA/LOG)
- Configuration parsing

**Integration Tests:**
- WebSocket connection handling (connect, disconnect, reconnect)
- Message routing (app → dashboard, dashboard → app)
- Multi-client scenarios (multiple apps, multiple dashboards)
- Error handling (malformed messages, oversized payloads)
- UDP broadcast discovery

**Tools:** `cargo test`, `tokio-test` for async, `wiremock` for HTTP mocking

### SDK (Kotlin)

**Unit Tests:**
- DSL builders (dashboard schema generation)
- Data provider scheduling
- Message serialization
- JSONPath evaluation
- Reconnection backoff logic

**Integration Tests:**
- WebSocket connection to real/mock server
- Full registration flow
- Data/log sending and receiving
- Action handling round-trip

**Instrumented Tests (on emulator):**
- Emulator IP detection
- Lifecycle integration (app start/stop)
- Memory pressure callbacks

**Tools:** JUnit 5, MockK, Turbine (for Flow testing), MockWebServer

### Dashboard (Svelte)

**Unit Tests:**
- Widget components (gauge, number, badge, table, etc.)
- JSONPath value extraction
- Format functions (bytes, percent, duration)
- Threshold evaluation
- Conditional rendering logic

**Component Tests:**
- Section rendering with mock data
- Layout variations (row, grid, stack)
- Action button interactions
- Log viewer scrolling/filtering

**E2E Tests:**
- Full dashboard with mock WebSocket server
- Session list rendering
- Real-time data updates
- Action triggering and result display

**Tools:** Vitest, Testing Library, Playwright

### End-to-End Tests

**Full Stack Integration:**
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Test SDK   │────>│   Service   │<────│  Dashboard  │
│  (JVM)      │     │   (Real)    │     │  (Playwright)│
└─────────────┘     └─────────────┘     └─────────────┘
```

**Scenarios:**
- App connects, dashboard sees it appear
- App sends DATA, dashboard renders widgets
- App sends LOG, log viewer updates
- Dashboard triggers ACTION, app receives and responds
- App disconnects, dashboard shows session ended
- Service restart, app reconnects

**CI Pipeline:**
```yaml
test:
  - cargo test                    # Service unit + integration
  - ./gradlew test                # SDK unit tests
  - ./gradlew connectedTest       # SDK instrumented (emulator)
  - npm run test                  # Dashboard unit + component
  - npm run test:e2e              # Dashboard E2E
  - ./scripts/e2e-full-stack.sh   # Full stack integration
```

---

## Security Considerations

### Network Security

1. **Localhost Only**
   - Service binds to `127.0.0.1` by default
   - Only accessible from local machine
   - Emulators connect via special IPs (10.0.2.2, etc.) that route to host localhost
   - No authentication required for MVP

2. **Future: Authentication for Physical Devices**
   - One-time pairing flow (like Bluetooth) when physical device discovery is added
   - Not implemented in MVP - physical devices require manual IP configuration

### Data Privacy

1. **No External Network Calls**
   - All data stays on local machine
   - No telemetry, no analytics
   - No cloud dependencies

2. **Sensitive Data Filtering**
   - SDK should not automatically send:
     - User credentials
     - API keys
     - Personal data
   - Developer controls what gets sent via dashboard schema

3. **Debug Builds Only**
   - SDK is `debugImplementation` only
   - Automatically excluded from release builds
   - No risk of shipping debug code

---

## Installation & Deployment

### Service Installation

```bash
# Clone repo
git clone https://github.com/lelloman/androidoscopy.git
cd androidoscopy

# Build
cargo build --release

# Run
./target/release/androidoscopy
```

### Service Configuration

```toml
# ~/.androidoscopy/config.toml

[server]
websocket_port = 9999
http_port = 8080
bind_address = "127.0.0.1"
max_connections = 100

[session]
# How long to keep ended sessions in memory
ended_session_ttl_seconds = 300

# How many recent DATA messages to buffer per session
data_buffer_size = 100

# How many recent logs to buffer per session
log_buffer_size = 1000

[logging]
level = "info"
```

### Android SDK Installation (Gradle)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    debugImplementation("com.github.lelloman:androidoscopy-sdk:0.1.0")
}
```

---

## Comparison to Existing Tools

### vs. Android Studio Profiler

| Feature | Androidoscopy | AS Profiler |
|---------|---------------|-------------|
| Setup | One-time install | Built-in |
| Multi-device | Yes | Single device |
| Custom metrics | Easy (SDK DSL) | Limited |
| Browser-based | Yes | IDE-based |
| Lightweight | Yes | Heavy |
| App-defined UI | Yes | No |

### vs. Flipper (Meta)

| Feature | Androidoscopy | Flipper |
|---------|---------------|---------|
| Desktop app | No (browser) | Yes (Electron) |
| Plugin system | App-defined schemas | Desktop plugins |
| Setup complexity | Simple | Complex |
| Resource usage | Low | High |
| Dependencies | Minimal | Heavy |

---

## Roadmap

### Phase 1: MVP (v0.1.0)
- [ ] Rust service (WebSocket relay + static file serving)
- [ ] Session management (in-memory)
- [ ] Android SDK with dashboard DSL
- [ ] Built-in templates (memory, logs)
- [ ] Svelte dashboard with dynamic widget rendering
- [ ] Basic widget types (gauge, number, text, badge, table, button)
- [ ] Action handling

### Phase 2: Enhanced Widgets (v0.2.0)
- [ ] Log viewer widget with filtering
- [ ] Chart widget (time series)
- [ ] More format options
- [ ] Conditional styling (thresholds, colors)
- [ ] Collapsible sections

### Phase 3: Polish (v0.3.0)
- [ ] Physical device discovery protocol
- [ ] Session history (keep ended sessions longer)
- [ ] Export session data (JSON)
- [ ] Dashboard themes
- [ ] Performance optimizations

### Phase 4: Advanced (v1.0.0)
- [ ] Persistent storage (optional SQLite)
- [ ] Cross-session analytics
- [ ] Custom widget types via plugins
- [ ] iOS SDK
- [ ] Flutter SDK

---

## Success Metrics

1. **Adoption**
   - GitHub stars
   - Number of apps integrating SDK
   - Community contributions

2. **Developer Experience**
   - Time to integrate (target: < 10 minutes)
   - Lines of code to add basic debug view (target: < 20)

3. **Performance**
   - Service memory usage (target: < 50MB)
   - Dashboard load time (target: < 1s)
   - Message latency (target: < 100ms)

---

## License

**Dual-licensed:** MIT OR Apache-2.0

---

**Questions? Ideas? Concerns?**

This is a living document. Let's brainstorm, debate, and refine before writing code!
