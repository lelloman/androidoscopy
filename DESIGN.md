# Androidoscopy - Design Document

**Version:** 0.1.0-draft
**Status:** Brainstorming Phase
**Last Updated:** 2025-12-04

---

## Vision

**Androidoscopy** is a developer tool that eliminates the friction of debugging Android applications by providing a persistent, always-on debug service that apps can connect to automatically. No more `adb forward` commands, no more port juggling - just start your app and see debug data in your browser.

### Core Principles

1. **Zero Configuration** - Once installed, it just works
2. **Always Available** - Runs as background service, available whenever you need it
3. **Multi-Device** - Handle multiple emulators and physical devices simultaneously
4. **Extensible** - Easy to add new debug data types and visualizations
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
   - Can't see historical data after app closes

3. **Limited Visibility**
   - Debug UI dies when app crashes/closes
   - Can't see what happened before crash
   - No aggregated view across multiple runs

4. **Reinventing the Wheel**
   - Every app builds its own debug HTTP server
   - No standardization
   - Can't reuse tooling

### What Androidoscopy Solves

- **One-time setup**: Install service once, use forever
- **Auto-connection**: Apps automatically connect on startup
- **Persistent dashboard**: View data even after app closes
- **Multi-device**: See all devices/emulators in one place
- **Extensible SDK**: Drop-in library for any Android app
- **Cross-session history**: Track trends over time

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Developer's Machine                      │
│                                                               │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         Androidoscopy Service (Rust)                   │ │
│  │                                                          │ │
│  │  ┌──────────────────┐         ┌────────────────────┐  │ │
│  │  │  WebSocket Server│         │   HTTP Server      │  │ │
│  │  │    (port 9999)   │         │   (port 8080)      │  │ │
│  │  │                  │         │                    │  │ │
│  │  │  - Accept app    │         │  - Serve dashboard │  │ │
│  │  │    connections   │         │  - REST API        │  │ │
│  │  │  - Protocol      │         │  - WebSocket for   │  │ │
│  │  │    handling      │         │    live updates    │  │ │
│  │  └──────────────────┘         └────────────────────┘  │ │
│  │           │                              ▲             │ │
│  │           ▼                              │             │ │
│  │  ┌──────────────────────────────────────────────────┐ │ │
│  │  │          Session Manager                         │ │ │
│  │  │  - Track connected apps                          │ │ │
│  │  │  - Route messages                                │ │ │
│  │  │  - Maintain state                                │ │ │
│  │  └──────────────────────────────────────────────────┘ │ │
│  │           │                                            │ │
│  │           ▼                                            │ │
│  │  ┌──────────────────────────────────────────────────┐ │ │
│  │  │          Data Store (Optional SQLite)            │ │ │
│  │  │  - Session history                               │ │ │
│  │  │  - Metrics over time                             │ │ │
│  │  │  - Logs                                           │ │ │
│  │  └──────────────────────────────────────────────────┘ │ │
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

### Components

#### 1. Androidoscopy Service (Rust)

**Responsibilities:**
- Accept WebSocket connections from apps
- Manage app sessions (connect, disconnect, heartbeat)
- Store and aggregate debug data
- Serve web dashboard
- Provide REST API for querying data

**Why Rust?**
- Low resource usage (runs 24/7 as daemon)
- Type safety for protocol handling
- Fast, efficient for real-time data streaming

**Framework:** Axum
- Modern, tokio-native HTTP framework
- Built-in WebSocket support
- Tower middleware ecosystem
- Maintained by tokio team

#### 2. Android SDK (Kotlin)

**Responsibilities:**
- Connect to service via WebSocket
- Send app metadata on registration
- Push debug data (metrics, logs, events)
- Handle reconnection logic
- Provide easy-to-use API for app developers

**WebSocket Client:** OkHttp
- Already a dependency in most Android apps
- Battle-tested WebSocket support
- Well-documented API

**Integration Example:**
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Androidoscopy.init(this) {
            appName = "Pezzottify"
            enableMemoryMonitoring = true
            enableCacheMetrics = true
            customMetrics = listOf(
                MetricProvider { mapOf("custom_stat" to getValue()) }
            )
        }
    }
}
```

#### 3. Web Dashboard

**Responsibilities:**
- Show all connected apps/devices
- Display real-time metrics
- Show historical data
- Allow filtering/searching
- Trigger actions (clear cache, etc.)

**Technology:** Svelte
- Compiles to vanilla JS with minimal runtime (~2KB)
- Less boilerplate than React/Vue
- Scoped CSS by default
- Fast builds with Vite

---

## Protocol Design

### Connection Lifecycle

```
App                                    Service
 │                                        │
 ├─── CONNECT (WebSocket) ───────────────>│
 │                                        │
 │<─────── CONNECTED (ack) ───────────────┤
 │                                        │
 ├─── REGISTER (app metadata) ──────────>│
 │                                        │
 │<─────── REGISTERED (session_id) ───────┤
 │                                        │
 ├─── METRICS (cache stats) ─────────────>│
 │                                        │
 ├─── EVENT (user action) ───────────────>│
 │                                        │
 │<─────── COMMAND (clear_cache) ─────────┤
 │                                        │
 ├─── RESULT (success) ──────────────────>│
 │                                        │
 │<─────── SHUTDOWN (service restart) ────┤
 │                                        │
 │         (connection closes)            │
 │                                        │
 │      ... reconnection attempts ...     │
 │                                        │
 ├─── CONNECT (WebSocket) ───────────────>│
 │                                        │
 ├─── REGISTER (with resume_token) ──────>│
 │                                        │
 │<─────── REGISTERED (same session_id) ──┤
```

### Session Recovery

When a connection drops unexpectedly, the app can attempt to resume its session within a cooldown period (default: 1 minute).

The REGISTER message can include an optional `resume_token` to request session recovery:

```json
{
  "type": "REGISTER",
  "payload": {
    "protocol_version": "1.0",
    "resume_token": "550e8400-e29b-41d4-a716-446655440000",
    "app_name": "Pezzottify",
    ...
  }
}
```

If the session is still valid (within cooldown period), the service responds with the same `session_id`, maintaining continuity. Otherwise, a new session is created.

### Connection Health

Connection health is determined by the WebSocket connection state. No application-level heartbeat is implemented—we rely on the underlying WebSocket protocol for connection monitoring.

### Message Format

All messages are JSON over WebSocket.

#### Base Message Structure

```json
{
  "type": "MESSAGE_TYPE",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "optional-uuid",
  "payload": {}
}
```

**Notes:**
- `timestamp`: ISO 8601 format (e.g., `yyyy-MM-ddTHH:mm:ss.SSSZ`)
- `protocol_version`: Included in REGISTER message for compatibility checking

#### REGISTER Message (App → Service)

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
    "device_info": {
      "device_id": "d3b07384-d9a3-4e6b-8b0d-324f5e8c1a2f",
      "manufacturer": "Google",
      "model": "Pixel 5",
      "android_version": "14",
      "api_level": 34,
      "is_emulator": true
    },
    "capabilities": [
      "memory_metrics",
      "cache_metrics",
      "logs",
      "custom_events"
    ]
  }
}
```

**Notes:**
- `device_id`: UUID generated by the SDK on first run, persisted in app storage. Resets if app storage is cleared.

#### REGISTERED Response (Service → App)

```json
{
  "type": "REGISTERED",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "config": {
      "metrics_interval_ms": 5000
    }
  }
}
```

#### METRICS (App → Service)

```json
{
  "type": "METRICS",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "memory": {
      "pressure_level": "LOW",
      "heap_used_bytes": 45678900,
      "heap_max_bytes": 268435456,
      "available_memory_mb": 1024
    },
    "cache": {
      "artists": {
        "entry_count": 150,
        "size_bytes": 45000,
        "hits": 1200,
        "misses": 80,
        "evictions": 5
      },
      "albums": {
        "entry_count": 300,
        "size_bytes": 120000,
        "hits": 2500,
        "misses": 150,
        "evictions": 12
      }
    },
    "custom": {
      "active_downloads": 3,
      "queued_tracks": 42
    }
  }
}
```

**Notes:**
- `pressure_level`: One of `LOW`, `MODERATE`, `HIGH`, `CRITICAL`
- `cache`: Keys are arbitrary cache names defined by the app (e.g., "artists", "albums")

#### EVENT (App → Service)

```json
{
  "type": "EVENT",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "event_name": "cache_cleared",
    "event_category": "user_action",
    "data": {
      "cache_type": "all",
      "entries_removed": 450
    }
  }
}
```

#### LOG (App → Service)

```json
{
  "type": "LOG",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "level": "ERROR",
    "tag": "NetworkClient",
    "message": "Failed to fetch artist data",
    "throwable": "java.net.SocketTimeoutException: timeout\n  at ...",
    "thread": "OkHttp Dispatcher"
  }
}
```

**Notes:**
- `level`: Android log levels - `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `ASSERT`

#### COMMAND (Service → App)

```json
{
  "type": "COMMAND",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "command_id": "cmd-123",
    "command": "clear_cache",
    "args": {
      "cache_type": "artists"
    }
  }
}
```

**Command Timeout:**
The service expects a RESULT within 30 seconds. If no response is received, the dashboard displays a timeout error for that command.

#### RESULT (App → Service)

```json
{
  "type": "RESULT",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "command_id": "cmd-123",
    "success": true,
    "data": {
      "entries_removed": 150
    },
    "error": null
  }
}
```

#### ERROR (Service → App)

Sent when the service encounters an error processing a message from the app.

```json
{
  "type": "ERROR",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "error_code": "INVALID_MESSAGE",
    "message": "Unknown message type: FOOBAR",
    "original_message_type": "FOOBAR"
  }
}
```

**Error Codes:**
- `INVALID_MESSAGE` - Malformed or unknown message type
- `PROTOCOL_VERSION_MISMATCH` - Incompatible protocol version
- `SESSION_NOT_FOUND` - Invalid or expired session_id
- `PAYLOAD_TOO_LARGE` - Message exceeds size limit
- `INTERNAL_ERROR` - Server-side error

**App Error Handling Strategy:**
When the SDK receives errors from the service, it should log aggressively to logcat. The dashboard will display app-side errors for visibility.

#### SHUTDOWN (Service → App)

Sent when the service is shutting down gracefully, allowing apps to enter reconnection mode.

```json
{
  "type": "SHUTDOWN",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "payload": {
    "reason": "Service restarting",
    "reconnect_after_ms": 5000
  }
}
```

Upon receiving SHUTDOWN, the app SDK enters a polling/reconnection phase, attempting to reconnect for a configurable duration (default: 5 minutes) before declaring an error state.

#### BATCH (App → Service)

Allows sending multiple messages in a single WebSocket frame to reduce overhead.

```json
{
  "type": "BATCH",
  "timestamp": "2024-12-02T14:30:00.000Z",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "messages": [
      {
        "type": "LOG",
        "timestamp": "2024-12-02T14:30:00.000Z",
        "payload": { "level": "INFO", "tag": "App", "message": "Started" }
      },
      {
        "type": "METRICS",
        "timestamp": "2024-12-02T14:30:00.100Z",
        "payload": { "memory": { "heap_used_bytes": 45678900 } }
      }
    ]
  }
}
```

---

### Message Size Limits

To prevent runaway messages (e.g., huge stacktraces), the following limits apply:

| Field | Max Size |
|-------|----------|
| Single message | 1 MB |
| LOG `message` field | 64 KB |
| LOG `throwable` field | 256 KB |
| BATCH `messages` array | 100 messages |

**Truncation Strategy:**
- Messages exceeding limits are truncated with a `[TRUNCATED]` marker appended
- The service responds with an ERROR (code: `PAYLOAD_TOO_LARGE`) if the entire message exceeds 1 MB

---

### Dashboard WebSocket Protocol

The dashboard connects to the service via WebSocket to receive real-time updates. Data is always persisted to SQLite regardless of dashboard connection status.

#### Connection Flow

```
Dashboard                              Service
    │                                     │
    ├─── CONNECT (WebSocket) ────────────>│
    │                                     │
    │<─────── SYNC (initial state) ───────┤
    │                                     │
    │<─────── APP_CONNECTED ──────────────┤  (when app connects)
    │                                     │
    │<─────── METRICS_UPDATE ─────────────┤  (live metrics)
    │                                     │
    │<─────── LOG ────────────────────────┤  (live logs)
    │                                     │
    │<─────── EVENT ──────────────────────┤  (live events)
    │                                     │
    │<─────── APP_DISCONNECTED ───────────┤  (when app disconnects)
```

#### Message Formats (Service → Dashboard)

**SYNC** - Sent immediately on dashboard connection with current state:
```json
{
  "type": "SYNC",
  "payload": {
    "sessions": [
      {
        "session_id": "550e8400-e29b-41d4-a716-446655440000",
        "app_name": "Pezzottify",
        "package_name": "com.lelloman.pezzottify",
        "device": { "model": "Pixel 5", "is_emulator": true },
        "started_at": "2024-12-02T14:30:00.000Z",
        "is_active": true,
        "latest_metrics": { ... }
      }
    ]
  }
}
```

**APP_CONNECTED** - When an app registers:
```json
{
  "type": "APP_CONNECTED",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "app_name": "Pezzottify",
    "package_name": "com.lelloman.pezzottify",
    "device": {
      "model": "Pixel 5",
      "android_version": "14",
      "is_emulator": true
    }
  }
}
```

**APP_DISCONNECTED** - When an app disconnects:
```json
{
  "type": "APP_DISCONNECTED",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**METRICS_UPDATE** - Forwarded metrics from app:
```json
{
  "type": "METRICS_UPDATE",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2024-12-02T14:30:00.000Z",
    "metrics": {
      "memory": { ... },
      "cache": { ... },
      "custom": { ... }
    }
  }
}
```

**LOG** - Forwarded log from app:
```json
{
  "type": "LOG",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2024-12-02T14:30:00.000Z",
    "level": "ERROR",
    "tag": "NetworkClient",
    "message": "Connection failed"
  }
}
```

**EVENT** - Forwarded event from app:
```json
{
  "type": "EVENT",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2024-12-02T14:30:00.000Z",
    "event_name": "cache_cleared",
    "data": { ... }
  }
}
```

#### Message Formats (Dashboard → Service)

**COMMAND** - Trigger action on an app:
```json
{
  "type": "COMMAND",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "command": "clear_cache",
    "args": { "cache_type": "artists" }
  }
}
```

**COMMAND_RESULT** - Forwarded back to dashboard after app responds:
```json
{
  "type": "COMMAND_RESULT",
  "payload": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "command_id": "cmd-123",
    "success": true,
    "data": { "entries_removed": 150 }
  }
}
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

```kotlin
private val KNOWN_EMULATOR_HOST_IPS = listOf(
    "10.0.2.2",   // Android Emulator (AVD), BlueStacks
    "10.0.3.2",   // Genymotion
    "192.168.56.1" // Android-x86
)

val serviceUrl = if (isEmulator()) {
    // Try known emulator IPs in order
    findReachableHost(KNOWN_EMULATOR_HOST_IPS, port = 9999)
        ?: "ws://10.0.2.2:9999" // fallback to most common
} else {
    // Fall back to configured IP or discovery
    "ws://${config.hostIp}:9999"
}
```

### Physical Device Connection

**Options:**

1. **Manual Configuration**
   - User sets host IP in app settings
   - Most reliable, works in any network scenario
   - Fallback when discovery fails

2. **Custom Discovery Protocol**
   - Service broadcasts presence on local network
   - App discovers automatically without relying on mDNS
   - Requires both device and host on same WiFi network
   - **🔴 NEEDS DISCUSSION:** Design custom discovery mechanism

**Connection Priority:**
1. **Emulator** → Automatic via known emulator IPs
2. **Physical device, same network** → Custom discovery protocol
3. **Physical device, discovery fails** → Fall back to manual IP configuration

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

## Data Storage

### In-Memory (Required)

- Current sessions
- Recent metrics (last N minutes)
- Active app connections

### Persistent Storage (Optional - SQLite)

**Tables:**

```sql
CREATE TABLE sessions (
    id TEXT PRIMARY KEY,
    app_name TEXT NOT NULL,
    package_name TEXT NOT NULL,
    version_name TEXT,
    device_id TEXT NOT NULL,
    device_model TEXT,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    is_active BOOLEAN DEFAULT 1
);

CREATE TABLE metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    metric_type TEXT NOT NULL,  -- 'memory', 'cache', 'custom'
    data JSON NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE TABLE events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    event_name TEXT NOT NULL,
    event_category TEXT,
    data JSON,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE TABLE logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    level TEXT NOT NULL,
    tag TEXT,
    message TEXT NOT NULL,
    throwable TEXT,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);
```

**Retention Policy:**
- Keep last 7 days of data by default
- Configurable via service config
- Auto-cleanup on startup

---

## Dashboard Design

### Main View - Connected Apps

```
┌─────────────────────────────────────────────────────────────┐
│  Androidoscopy                                    [Settings] │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Connected Apps (3)                           [Auto-refresh] │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 📱 Pezzottify v1.0.0                    [Disconnect]  │  │
│  │    Pixel 5 Emulator (Android 14)                      │  │
│  │    Session: 00:15:32                                  │  │
│  │                                                        │  │
│  │    Memory: 45 MB / 256 MB  ██░░░░░░░░ 17%            │  │
│  │    Cache Hit Rate: 94%                                │  │
│  │    Last Update: 2s ago                                │  │
│  │                                            [View Details] │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 📱 TestApp v2.1.0                       [Disconnect]  │  │
│  │    Samsung Galaxy S21 (Android 13)                    │  │
│  │    Session: 01:23:45                                  │  │
│  │    ...                                                │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                               │
│  Recent Sessions (5)                          [View All]     │
│  • Pezzottify - ended 5m ago                                │
│  • OtherApp - ended 1h ago                                  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### App Detail View

```
┌─────────────────────────────────────────────────────────────┐
│  ← Back          Pezzottify v1.0.0                           │
│                  Pixel 5 Emulator • Session: 00:15:32        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  [Memory] [Cache] [Events] [Logs] [Custom]                  │
│                                                               │
│  ┌─ Memory ──────────────────────────────────────────────┐  │
│  │  Pressure: LOW                                         │  │
│  │  Heap: 45 MB / 256 MB                                 │  │
│  │  ████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ 17%        │  │
│  │  Available: 1024 MB                                   │  │
│  │                                                        │  │
│  │  [Chart: Memory over time]                            │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
│  ┌─ Cache Statistics ────────────────────────────────────┐  │
│  │                                                        │  │
│  │  Artists    150 entries  45 KB   Hits: 1200  (94%)   │  │
│  │  Albums     300 entries  120 KB  Hits: 2500  (94%)   │  │
│  │  Tracks     500 entries  200 KB  Hits: 4000  (95%)   │  │
│  │  Images     200 entries  2.5 MB  Hits: 1800  (90%)   │  │
│  │                                                        │  │
│  │  Total Hit Rate: 94%  ████████░░ Evictions: 42       │  │
│  │                                                        │  │
│  │  [Clear All Cache]  [Clear by Type ▼]                │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## SDK Design

### Initialization

```kotlin
object Androidoscopy {
    fun init(
        context: Context,
        config: AndroidoscopyConfig.() -> Unit
    ) {
        val configuration = AndroidoscopyConfig().apply(config)
        // Start service, connect
    }
}

class AndroidoscopyConfig {
    var appName: String? = null  // Auto-detect from manifest if null
    var hostIp: String? = null   // Auto-detect emulator, otherwise required
    var port: Int = 9999

    var enableMemoryMonitoring: Boolean = true
    var enableCacheMetrics: Boolean = false
    var enableLogForwarding: Boolean = false

    var memoryUpdateInterval: Duration = 10.seconds
    var metricsUpdateInterval: Duration = 5.seconds

    var customMetrics: List<MetricProvider> = emptyList()
    var commandHandlers: Map<String, CommandHandler> = emptyMap()
}
```

### Metric Providers

```kotlin
interface MetricProvider {
    val name: String
    fun collect(): Map<String, Any>
}

// Example usage:
class DownloadQueueMetric(
    private val downloadManager: DownloadManager
) : MetricProvider {
    override val name = "download_queue"

    override fun collect(): Map<String, Any> = mapOf(
        "active" to downloadManager.activeCount,
        "queued" to downloadManager.queuedCount,
        "total_bytes" to downloadManager.totalBytes
    )
}

// In app initialization:
Androidoscopy.init(this) {
    customMetrics = listOf(
        DownloadQueueMetric(downloadManager)
    )
}
```

### Command Handlers

```kotlin
interface CommandHandler {
    suspend fun handle(args: Map<String, Any>): CommandResult
}

data class CommandResult(
    val success: Boolean,
    val data: Map<String, Any>? = null,
    val error: String? = null
)

// Example:
class ClearCacheHandler(
    private val cache: StaticsCache
) : CommandHandler {
    override suspend fun handle(args: Map<String, Any>): CommandResult {
        val cacheType = args["cache_type"] as? String
        val entriesRemoved = cache.clear(cacheType)

        return CommandResult(
            success = true,
            data = mapOf("entries_removed" to entriesRemoved)
        )
    }
}

// Registration:
Androidoscopy.init(this) {
    commandHandlers = mapOf(
        "clear_cache" to ClearCacheHandler(cache)
    )
}
```

### Log Forwarding (Optional)

```kotlin
// Timber integration:
class AndroidoscopyTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Androidoscopy.logEvent(
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

## Installation & Deployment

### Service Installation

```bash
# Clone repo
git clone https://github.com/lelloman/androidoscopy.git
cd androidoscopy

# Build
cargo build --release

# Run
./target/release/androidoscopy --config config.toml
```

Installation scripts and service integration (systemd, launchd, etc.) will be defined at implementation time.

### Service Configuration

```toml
# /etc/androidoscopy/config.toml

[server]
websocket_port = 9999
http_port = 8080
bind_address = "127.0.0.1"  # Only localhost by default
max_connections = 100       # Maximum concurrent app connections

[storage]
enabled = true
database_path = "/var/lib/androidoscopy/data.db"
retention_days = 7

[dashboard]
static_dir = "/usr/share/androidoscopy/dashboard"

[logging]
level = "info"
file = "/var/log/androidoscopy/service.log"
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
    debugImplementation("com.github.lelloman:androidoscopy:0.1.0")
}
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
   - Developer controls what gets sent

3. **Debug Builds Only**
   - SDK is `debugImplementation` only
   - Automatically excluded from release builds
   - No risk of shipping debug code

---

## Extensibility

### Plugin System (Future)

Allow community-contributed plugins for:
- Custom visualizations
- New metric types
- Integration with other tools (Charles Proxy, etc.)
- Export formats (CSV, JSON, etc.)

```rust
// Example plugin trait
trait AndroidoscopyPlugin {
    fn name(&self) -> &str;
    fn handle_metrics(&self, metrics: &Metrics) -> PluginResult;
    fn dashboard_component(&self) -> Option<DashboardComponent>;
}
```

### Language Support (Future)

While starting with Kotlin/Android, protocol is language-agnostic:
- **iOS SDK** (Swift)
- **Flutter SDK** (Dart)
- **React Native SDK** (JavaScript)
- **Unity SDK** (C#)

Same service, different client SDKs.

---

## Comparison to Existing Tools

### vs. Android Studio Profiler

| Feature | Androidoscopy | AS Profiler |
|---------|---------------|-------------|
| Setup | One-time install | Built-in |
| Multi-device | Yes | Single device |
| Custom metrics | Easy (SDK) | Limited |
| Persistent data | Yes | Session only |
| Browser-based | Yes | IDE-based |
| Lightweight | Yes | Heavy |
| Offline access | Yes (local) | Requires IDE |

**Use Case**: Androidoscopy is for quick, always-available debugging. AS Profiler is for deep performance analysis.

### vs. Stetho (Facebook)

| Feature | Androidoscopy | Stetho |
|---------|---------------|--------|
| Maintained | Yes (new) | Deprecated |
| Chrome DevTools | No | Yes |
| Custom UI | Yes | No |
| Multi-device | Yes | No |
| Requires adb | No | Yes |
| WebSocket | Yes | HTTP only |

**Use Case**: Androidoscopy replaces Stetho with modern tech and better UX.

### vs. Flipper (Meta)

| Feature | Androidoscopy | Flipper |
|---------|---------------|---------|
| Desktop app | No (browser) | Yes (Electron) |
| Plugin system | Future | Yes |
| Setup complexity | Simple | Complex |
| Resource usage | Low | High |
| Dependencies | Minimal | Heavy |

**Use Case**: Androidoscopy is lightweight alternative. Flipper is more feature-rich but heavier.

---

## Roadmap

### Phase 1: MVP (v0.1.0)
- [ ] Basic Rust service (WebSocket + HTTP)
- [ ] Session management
- [ ] Android SDK with basic metrics (memory, custom)
- [ ] Simple web dashboard (connected apps, metrics display)
- [ ] Documentation

### Phase 2: Core Features (v0.2.0)
- [ ] Cache metrics support
- [ ] Event tracking
- [ ] Log forwarding
- [ ] Command execution (clear cache, etc.)
- [ ] Historical data (SQLite storage)
- [ ] Dashboard improvements (charts, filtering)

### Phase 3: Polish (v0.3.0)
- [ ] mDNS device discovery
- [ ] Export data (CSV, JSON)
- [ ] Configuration UI in dashboard
- [ ] Better error handling and reconnection
- [ ] Performance optimizations

### Phase 4: Advanced (v1.0.0)
- [ ] Plugin system
- [ ] Network traffic inspection
- [ ] Screenshot capture
- [ ] Database inspection
- [ ] SharedPreferences viewer
- [ ] Custom dashboard themes

### Future Ideas
- [ ] iOS SDK
- [ ] Flutter SDK
- [ ] React Native SDK
- [ ] Desktop client (Tauri)
- [ ] Cloud sync (optional)
- [ ] Team collaboration features

---

## Open Questions & Decisions Needed

### 1. Capabilities Field Usage

The REGISTER message includes `capabilities`, but how does the service use them?

**Decision:** 🔴 NEEDS DISCUSSION - Define capability semantics

### 2. Metrics Aggregation Strategy

Storing every METRICS message at 5-second intervals = 17,280 rows per day per app. Need downsampling strategy for historical data.

**Decision:** 🔴 NEEDS DISCUSSION - Define aggregation/downsampling rules

### 3. Physical Device Discovery Protocol

Custom discovery mechanism to replace unreliable mDNS.

**Decision:** 🔴 NEEDS DISCUSSION - Design UDP broadcast or similar approach

### 4. Testing Strategy

Need clear vision for unit tests, integration tests, and how to test the SDK without a real device.

**Decision:** 🔴 NEEDS DISCUSSION - Define testing approach from the start

### 5. Session Recovery Token (resume_token)

The session recovery section mentions `resume_token` in the REGISTER message, but it's unclear where the app gets this token initially. Is it the same as `session_id`? Should it be returned in the REGISTERED response?

**Decision:** 🔴 NEEDS DISCUSSION - Define token lifecycle and relationship with session_id

### 6. CONNECTED Message

The connection lifecycle diagram shows a `CONNECTED (ack)` message from service to app, but this message type is not defined in the message formats section.

**Decision:** 🔴 NEEDS DISCUSSION - Define CONNECTED message or remove from diagram

### 7. REST API Endpoints

The dashboard needs a REST API for querying sessions, metrics, logs, etc. No endpoints are currently specified.

**Decision:** 🔴 NEEDS DISCUSSION - Define REST API contract

### 8. JSON Column Querying

The database schema uses `data JSON` columns for metrics, events, and logs. How will these be queried and filtered efficiently?

**Decision:** 🔴 NEEDS DISCUSSION - Define query patterns and whether to extract common fields

### 9. Custom Metrics Value Types

The SDK uses `Map<String, Any>` for custom metrics, but what types are actually valid for serialization? Primitives only? Nested objects? Arrays?

**Decision:** 🔴 NEEDS DISCUSSION - Define supported value types

### 10. Session Manager and Data Store Interaction

The architecture diagram shows Session Manager and Data Store as separate components, but the data flow between them isn't clear. When does data get persisted? Synchronously or async?

**Decision:** 🔴 NEEDS DISCUSSION - Clarify component responsibilities and data flow

---

## Success Metrics

How do we know Androidoscopy is successful?

1. **Adoption**
   - GitHub stars
   - Number of apps integrating SDK
   - Community contributions

2. **Usage**
   - Active service installs
   - Average session duration
   - Number of connected apps per user

3. **Developer Feedback**
   - Time saved vs. traditional debugging
   - Feature requests
   - Bug reports (few = good quality)

4. **Community**
   - Contributors
   - Plugins created
   - Forks/derivatives

---

## License

**Dual-licensed:** MIT OR Apache-2.0
- Permissive, allows commercial use
- Encourages adoption
- Standard practice in Rust ecosystem

---

## Next Steps

1. **Make Technology Decisions** (answer open questions above)
2. **Create GitHub Repository**
3. **Implement MVP**
   - Rust service skeleton
   - Android SDK skeleton
   - Basic protocol
   - Simple dashboard
4. **Dogfood with Pezzottify**
5. **Iterate based on real usage**
6. **Publish v0.1.0**

---

**Questions? Ideas? Concerns?**

This is a living document. Let's brainstorm, debate, and refine before writing code!
