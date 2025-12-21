# Androidoscopy

A developer tool that eliminates the friction of debugging Android applications by providing a persistent, always-on debug service that apps can connect to automatically.

No more `adb forward` commands, no more port juggling - just start your app and see debug data in your browser.

## Features

- **Zero Configuration** - Once installed, it just works
- **Always Available** - Runs as background service, ready whenever you need it
- **Multi-Device** - Handle multiple emulators and physical devices simultaneously
- **App-Driven UI** - Apps define their own dashboard layout using a Kotlin DSL
- **Persistent Sessions** - View data even after app closes
- **Real-time Updates** - See metrics, logs, and execute actions in real-time

## Quick Start

### 1. Start the Server

```bash
cd server
cargo run --release
```

The server will start on:
- WebSocket: `wss://localhost:8889`
- Dashboard: `http://localhost:8880`

#### Install as a Service (optional)

To run the server automatically in the background:

```bash
# Install as systemd user service
androidoscopy install

# Start the service
systemctl --user start androidoscopy

# Enable on login (optional)
systemctl --user enable androidoscopy

# Check status
androidoscopy status

# Uninstall
androidoscopy uninstall
```

### 2. Add the SDK to Your Android App

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":sdk")) // or from Maven when published
}
```

### 3. Initialize in Your Application

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Androidoscopy.init(this) {
            // Optional: Specify host IP (auto-detected for emulators)
            // hostIp = "192.168.1.100"

            // Define your dashboard
            dashboard {
                // Built-in memory section
                memorySection()

                // Built-in logs section
                logsSection()

                // Custom section
                section("App Metrics") {
                    row {
                        number("Active Users", "$.metrics.active_users")
                        percent("Cache Hit Rate", "$.metrics.cache_hit_rate")
                    }
                }
            }

            // Register action handlers
            onAction("clear_cache") { args ->
                clearCache()
                ActionResult.success("Cache cleared")
            }
        }
    }
}
```

### 4. Send Data Updates

```kotlin
// Update metrics (debounced automatically)
Androidoscopy.updateData {
    put("metrics", mapOf(
        "active_users" to 42,
        "cache_hit_rate" to 0.95
    ))
}

// Log messages
Androidoscopy.log(LogLevel.INFO, "NetworkClient", "Request completed")
```

### 5. Open the Dashboard

Open `http://localhost:8880` in your browser to see connected apps and their data in real-time.

### 6. (Optional) Add the Embedded Dashboard

If you want to view the dashboard directly on the device, add the optional UI module:

```kotlin
dependencies {
    implementation(project(":sdk"))
    implementation(project(":sdk-ui"))  // Adds embedded dashboard Activity
}
```

This adds a separate launcher entry called "📊 Dashboard" that opens the dashboard directly on the device. You can also launch it programmatically:

```kotlin
import com.lelloman.androidoscopy.ui.DashboardActivity

// Launch from anywhere
DashboardActivity.launch(context)
```

## Project Structure

```
androidoscopy/
├── server/           # Rust WebSocket/HTTP server
├── android/
│   ├── app/          # Demo application
│   ├── sdk/          # Android SDK library (core)
│   └── sdk-ui/       # Embedded dashboard Activity (optional)
├── dashboard/        # Svelte web dashboard
└── e2e/              # End-to-end tests
```

## Architecture

```
┌────────────────────────────────────────────────────────┐
│                 Developer's Machine                     │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │        Androidoscopy Service (Rust + Axum)        │ │
│  │                                                    │ │
│  │  WebSocket Hub ────► Session Manager ◄──── HTTP   │ │
│  │   (port 9999)         (in-memory)       (port 8080)│ │
│  └───────────────────────────────────────────────────┘ │
│                          ▲                              │
└──────────────────────────┼──────────────────────────────┘
                           │
           WebSocket connections (apps → server)
                           │
     ┌─────────────────────┼─────────────────────┐
     ▼                     ▼                     ▼
 ┌────────┐           ┌────────┐           ┌────────┐
 │Emulator│           │Emulator│           │ Device │
 │+ App   │           │+ App   │           │+ App   │
 └────────┘           └────────┘           └────────┘
```

## Development

### Server

```bash
cd server
cargo fmt           # Format code
cargo clippy        # Lint
cargo test          # Run tests
cargo run           # Start development server
```

### Dashboard

```bash
cd dashboard
npm install
npm run dev         # Start dev server
npm run check       # Type check
npm test            # Run unit tests
npm run test:e2e    # Run E2E tests
```

### Android SDK

```bash
cd android
./gradlew :sdk:test             # Unit tests
./gradlew :sdk:connectedTest    # Instrumented tests
```

### E2E Tests

```bash
cd e2e
cargo test          # Run full stack tests
```

## Configuration

The server can be configured via `~/.androidoscopy/config.toml`:

```toml
[server]
http_port = 8880              # Dashboard HTTP port
websocket_port = 8889         # Android app WebSocket port
bind_address = "0.0.0.0"      # Listen on all interfaces (for physical devices)
udp_discovery_enabled = true  # Broadcast for device discovery

[session]
data_buffer_size = 1000
log_buffer_size = 50000
ended_session_ttl_seconds = 3600
```

## Protocol

Apps communicate with the server via WebSocket using a JSON protocol. See [DESIGN.md](DESIGN.md) for full protocol specification.

## License

MIT
