# Diagnostic sessions (protocol v2)

The app defines its own tools, schemas and suspend handlers. The desktop discovers
and connects to the phone over LAN, exposes those tools through MCP, and renders
the app's existing dashboard. There is no ADB requirement or cloud relay.

## Android integration

Include `sdk` in **implementation**, not debugImplementation, for release support.
Include `sdk-ui` for the optional non-exported session screen. Both publish as
`com.github.lelloman.androidoscopy:<module>:2.0.0` to Maven Local; JitPack versions
follow the selected Git tag or commit. Optional integrations remain opt-in.

```kotlin
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.tools.Tool
import com.lelloman.androidoscopy.tools.ToolResult
import com.lelloman.androidoscopy.ui.SessionActivity
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes

// Application.onCreate, in every build variant:
Androidoscopy.init(this) {
    appName = "My app"
    releaseIdleTimeout = 15.minutes
    tool(Tool("player.status", "Read player status", readOnly = true) {
        ToolResult.json(buildJsonObject { put("playing", player.isPlaying) })
    })
    // Prefer factories: resources are created only when a session starts.
    dataProvider { MyDataProvider() }
    // Existing dashboard { ... } and onAction(...) APIs still work.
}

// App settings button; opening the screen does not start release diagnostics:
SessionActivity.launch(context)
```

`FLAG_DEBUGGABLE` determines policy, not the SDK's build variant. Debug apps start
automatically after initialization, without an inactivity deadline. Set
`sessionMode = SessionMode.MANUAL` to opt out of automatic debug startup. Release
apps **always** start inactive; `startSession(idleTimeout = N.minutes)` must be
called from a foreground Activity. Default idle timeout is 15 minutes, configurable
from 1 millisecond to 24 hours. Debug availability lasts only while the process lives.

Custom UI can observe `sessionState`, call `startSession`, `stopSession`,
`sessionActivity`, `approvePairing(request.id)` and `rejectPairing(request.id)`.
Compare the complete eight-digit number shown on both devices before approving.
Approval grants the entire configured diagnostic surface, including mutations.
Release sessions require approval on every activation; paired PCs reconnect only
within that activation. Debug pairings are remembered until explicitly forgotten
on the phone and desktop. Never auto-approve a request in production.

Accepted tool/action calls, successful pairing, and explicit session activity reset
the idle deadline. Discovery, reconnects, pings, data/log streaming and dashboard
polling do not. Expiration uses elapsed/monotonic time and cannot be revived by a
late request. Stop/expiry closes sockets and discovery, cancels handlers and
collectors, clears session buffers, and discards release credentials. Cancellation
is cooperative: handlers must not launch unscoped jobs or block indefinitely.

Release sessions use a connected-device foreground service with a Stop notification.
Notification permission is requested by sdk-ui on Android 13+. On OS versions
requiring local-network permission, grant it before starting. Wi-Fi/Ethernet IPv4
is currently required; no cellular listener is opened. OS process termination ends
the session; diagnostics never automatically restart a release session.

Tools accept an object JSON Schema (Draft 2020-12), optional output schema,
description, read-only hint, timeout (default 60s), and suspend handler. Pattern
validation uses Java regex syntax. Values retain
nested arrays/objects, booleans, numbers and null. References (`$ref`, `$dynamicRef`,
`$recursiveRef`) are intentionally unsupported; schemas never fetch remote resources.
Use `ToolResult.text`, `.json`, or `.image`. Schema errors, exceptions, timeouts and
oversized results become tool errors. At most 16 calls run per phone connection;
frames/results are limited to 1 MiB. Use `registerTool`/`unregisterTool` for dynamic
manifests. Read-only hints are descriptive, not an authorization boundary.

`BuiltInTools.snapshot()` and `.logs()` are opt-in wrappers for existing collectors.
Legacy dashboard actions are not automatically exposed to MCP: register a `Tool`
with an explicit schema to expose one. Collectors should implement `close()` to
release resources and clear history; prefer factory registration for restartability.
The OkHttp integration captures only during active sessions, strips query strings
and common secret headers. Preference display redacts common secret key names.
These filters are not a guarantee: apps must select safe tools, logs, and fields.
Diagnostic access can reveal private data and alter application state.

## Desktop

```sh
cd server/dashboard && npm ci && npm run build
cd .. && cargo build --release
target/release/androidoscopy run
```

Open the token-bearing loopback URL printed by the controller. Select a discovered
device or enter its displayed IP:port; compare the number and approve on the phone.
The dashboard includes app tools with JSON arguments as well as existing widgets.

```sh
androidoscopy devices
androidoscopy connect 192.168.1.20:12345
androidoscopy disconnect DEVICE_ID
androidoscopy forget DEVICE_ID
androidoscopy mcp --device DEVICE_ID
```

Configure any stdio MCP client to run `androidoscopy mcp --device DEVICE_ID`.
The controller must already be running and the device paired. The bridge exposes
the device's current tool list, sends list-change notifications, forwards structured
results and cancellation, and removes tools on disconnect. MCP stdout is JSON-RPC
only. Disconnected/expired tools cannot be executed from retained dashboard history.

The controller binds only `127.0.0.1`. Every v2 API and WebSocket requires its random
token, checks Host and browser Origin, and uses an HttpOnly SameSite=Strict cookie
after login. API clients can use `Authorization: Bearer TOKEN`. State lives under
the OS local data directory in `androidoscopy` (override `ANDROIDOSCOPY_STATE_DIR`).
On Unix the directory is 0700 and credentials/token files are 0600. Protect that
directory and do not share login URLs. Release credentials are never written to disk.
Ended snapshots are retained in memory for the configured session TTL (default 1h).

## Wire protocol and pairing

The phone advertises `_androidoscopy._tcp.` with install ID and version 2. It listens
on an OS-assigned port on its LAN address. TLS 1.3 is mandatory. The desktop verifies
TLS handshake signatures but quarantines self-signed certificates until pairing;
no app metadata, manifests, logs, or tools are sent before authorization.

Each frame is a big-endian unsigned 32-bit byte length followed by UTF-8 JSON object,
1..1,048,576 bytes. On a new TLS connection both sides derive 32 bytes using exporter
label `EXPORTER-Androidoscopy-v2` and no context. Pairing transcript:

1. Phone `HELLO {version:2, device, session}`.
2. Desktop chooses random 32-byte C and sends `PAIR {peer, commitment}` where
   commitment = HMAC-SHA256(exporter, C).
3. Phone chooses random 32-byte S and sends `CHALLENGE {nonce:S}`.
4. Desktop `REVEAL {nonce:C}`; phone checks commitment before displaying anything.
5. Both display the first four HMAC-SHA256(exporter, C || S) bytes as an unsigned
   big-endian integer modulo 100,000,000, zero-padded to eight digits.
6. Phone approval within 60s creates random 32-byte credential K. `AUTHORIZED`
   contains K, session, remember flag, remainingMs, and app/dashboard metadata.

Nonces, commitments and credentials are lowercase hex. Attempts are rate-limited
on device. Compare numbers on a trusted desktop and phone; neither UI should accept
an unverified number. The desktop pins the authorized certificate fingerprint.
Reconnect sends `RESUME {peer, proof}`, where proof is
HMAC-SHA256(K, UTF8("resume:" + session + ":") || exporter). The returned credential
must match K, authenticating the phone as well. Debug K is encrypted using an
Android Keystore AES-GCM key under no-backup storage; release K is memory-only.

After authorization all frames carry `session`. Phone sends `TOOLS {revision,tools}`,
`DATA {data}`, `LOG {log}`, `RESULT {id,result}`, and `PONG {remainingMs}`. Desktop
sends `CALL {id,name,arguments}`, legacy `ACTION`, `CANCEL {id}`, `PING`, or explicit
`ACTIVITY`. A new release activation gets a new session ID and requires new approval.
Wrong credentials, stale sessions, malformed/oversized frames and failed pairing
close the connection without app data. Network reconnection never starts diagnostics.

## Migration and verification

Protocol v1 and v2 are not wire compatible. `hostIp`/`port` are deprecated no-ops.
Run `androidoscopy legacy` explicitly for old SDKs and open `/?legacy=1`; this is a
separate legacy server with its original security model, never a v2 access path.
Do not use it for release diagnostics. Old integration docs describe v1 unless they
link here. The sdk-ui dashboard is no longer exported to external applications.

```sh
cd android
ANDROID_HOME=/path/to/sdk ./gradlew :sdk:testDebugUnitTest :okhttp:testDebugUnitTest \
  :app:assembleDebug :app:assembleRelease :sdk:publishToMavenLocal :sdk-ui:publishToMavenLocal
cd ../server
cargo test --all-targets
cd dashboard && npm run check && npm run build
```

The demo's release APK is minified and debug-key signed for local testing only.
Before shipping an integration, test actual device pairing/rejection, stop and
inactivity expiry, notification Stop, background use, network loss/reconnect, and
fresh release activation. Linking the library alone is not a security review of
the tools an app chooses to expose.

### Verification performed

The desktop suite covers bounded framing, TLS exporter/SAS agreement, stale-session
rejection, local API authentication/Origin/Host checks, typed calls and cancellation.
Android unit tests cover idle deadlines, schema validation, typed values, inactive
HTTP capture and redaction. `:sdk:connectedDebugAndroidTest` exercises the actual
Keystore-backed TLS 1.3 handshake and framing on Android 16; it passed on both
the emulator and the physical CPH2493 phone.

The minified release demo was also exercised on an isolated emulator: inactive
startup, explicit activation, matching desktop/device pairing codes, tool discovery,
a successful app-defined call over MCP, and Stop removing the MCP tools and foreground
service. A temporary test-only tunnel crossed the emulator's NAT; it is not an SDK
transport. Direct physical-phone LAN discovery worked, but connection testing was
blocked by TCP reachability and then device disconnection, so that check remains
for the consuming app's integration pass.
