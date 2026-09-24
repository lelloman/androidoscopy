# Androidoscopy

**Protocol v2:** app-defined diagnostic tools are now available over paired LAN
sessions and desktop MCP. Debug apps start automatically; release apps require
explicit activation and expire after inactivity. See [Sessions v2](docs/SESSIONS_V2.md)
for setup, security, migration, and testing. The original v1 guide below applies
only to the explicit `androidoscopy legacy` server.

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

### Host colors for the session screen

SDK/UI 2.0.2 also adds a session-only **Accept all** switch. It defaults off and
requires a local foreground confirmation to enable. Any PC able to reach the
session can then pair without comparing a code and access every enabled tool,
including mutating tools and unredacted data. TLS and protocol validation remain
enabled; only one PC may be connected at a time. Stop, expiry, or process death
resets the switch. Turning it off does not revoke an existing connection (use
Stop for that). Automatically approved PCs are never saved as trusted peers.
Hosts can observe `SessionState.acceptAll` and call
`Androidoscopy.setAcceptAllConnections(enabled)` from their foreground UI.
The setting is not exposed as a remote tool.

SDK/UI 2.0.1 supports an optional `SessionPalette`. From a Compose host, pass the
current Material color scheme when opening the session controls:

```kotlin
val palette = SessionPalette.fromColorScheme(MaterialTheme.colorScheme)
Button(onClick = { SessionActivity.launch(context, palette) }) {
    Text("Open diagnostics")
}
```

Import `SessionPalette` and `SessionActivity` from `com.lelloman.androidoscopy.ui`.
Non-Compose callers can construct `SessionPalette` with ARGB colors and use
`SessionActivity.createIntent(context, palette)`. The palette covers session
controls and pairing dialogs, not the legacy dashboard. It travels in the Intent
and survives activity/process recreation; it is a snapshot, so relaunch to pick up
changes to the host theme. No palette means a system light/dark Material theme.
Opening the screen still never starts a release session automatically.

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

The device pairing limit is supplied by a Rust JNI library built from the
reviewed `simple-server` rate-limit API. Install Rust with the four Android
targets (`aarch64-linux-android`, `armv7-linux-androideabi`,
`i686-linux-android`, `x86_64-linux-android`) and Android NDK 27.0.12077973.
Run `./scripts/checkout-simple-server.sh` from the repository root before an
Android SDK build. Gradle builds and packages all four ABIs into the SDK AAR;
there is no runtime download. SDK unit tests also build a host JNI library.
CI and JitPack use `./scripts/prepare-android-native.sh` for these build inputs.

```bash
cd android
./gradlew :sdk:test             # Unit tests
./gradlew :sdk:connectedTest    # Instrumented tests
./gradlew :sdk:assembleRelease  # AAR with all four native ABIs
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

## Shared server lifecycle

See [Step 02 lifecycle](docs/step-02-lifecycle.md) for shutdown scope and checks.
Before a fresh Rust build, run `./scripts/checkout-simple-server.sh` to provision
the reviewed sibling dependency at `66b5259b22c6c48687f822beca497f03ad12c2f7`
(`simple-server.rev`; the revision must be published first).
