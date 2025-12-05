# Androidoscopy - Implementation Tasks

This document breaks down the DESIGN.md into actionable implementation tasks.

**Legend:**
- `[ ]` - Not started
- `[~]` - In progress
- `[x]` - Completed

---

## Phase 1: Project Setup

### 1.1 Repository Structure

- [x] **Create monorepo directory structure**
  ```
  androidoscopy/
  ├── server/           # Rust service
  ├── android/          # Android project
  │   ├── app/          # Demo app
  │   └── sdk/          # SDK library module
  ├── dashboard/        # Svelte web app
  ├── e2e/              # End-to-end tests
  ├── DESIGN.md
  ├── TODO.md
  └── README.md
  ```

- [x] **Initialize Rust project for server**
  ```bash
  cd server
  cargo init --name androidoscopy-server
  ```
  Add dependencies to `Cargo.toml`:
  - `axum` - HTTP/WebSocket framework
  - `tokio` - Async runtime
  - `serde` / `serde_json` - JSON serialization
  - `uuid` - Session IDs
  - `tracing` - Logging
  - `toml` - Config parsing

- [x] **Initialize Android SDK project**
  ```bash
  cd android
  # Created via Android Studio, SDK is in android/sdk module
  ```
  - Kotlin library module
  - Min SDK 24 (Android 7.0)
  - Dependencies: OkHttp, Kotlinx Serialization, Coroutines

- [x] **Initialize Svelte dashboard project**
  ```bash
  cd dashboard
  npm create vite@latest . -- --template svelte-ts
  ```
  Add dependencies:
  - `jsonpath-plus` - JSONPath evaluation
  - Testing: `vitest`, `@testing-library/svelte`, `playwright`

### 1.2 CI/CD Setup

- [x] **Create GitHub Actions workflow for server**
  ```yaml
  # .github/workflows/server.yml
  - cargo fmt --check
  - cargo clippy
  - cargo test
  ```

- [x] **Create GitHub Actions workflow for SDK**
  ```yaml
  # .github/workflows/sdk.yml
  - ./gradlew test
  - ./gradlew connectedTest (with emulator)
  ```

- [x] **Create GitHub Actions workflow for dashboard**
  ```yaml
  # .github/workflows/dashboard.yml
  - npm run lint
  - npm run test
  - npm run test:e2e
  ```

---

## Phase 2: Server Implementation (Rust)

### 2.1 Core Infrastructure

- [x] **Create basic Axum server skeleton**

  Set up entry point with HTTP and WebSocket servers:
  ```rust
  // src/main.rs
  #[tokio::main]
  async fn main() {
      let app = Router::new()
          .route("/ws/app", get(handle_app_ws))
          .route("/ws/dashboard", get(handle_dashboard_ws))
          .nest_service("/", ServeDir::new("static"));

      axum::serve(listener, app).await.unwrap();
  }
  ```

- [x] **Implement configuration loading**

  Parse `~/.androidoscopy/config.toml`:
  ```rust
  #[derive(Deserialize)]
  struct Config {
      server: ServerConfig,
      session: SessionConfig,
      logging: LoggingConfig,
  }

  struct ServerConfig {
      websocket_port: u16,  // default: 9999
      http_port: u16,       // default: 8080
      bind_address: String, // default: "127.0.0.1"
      max_connections: usize,
  }

  struct SessionConfig {
      ended_session_ttl_seconds: u64,
      data_buffer_size: usize,
      log_buffer_size: usize,
  }
  ```

- [x] **Set up tracing/logging**

  Configure `tracing` subscriber with configurable log level from config.

### 2.2 Message Protocol

- [x] **Define message types**

  Create structs for all protocol messages:
  ```rust
  // src/protocol.rs

  #[derive(Serialize, Deserialize)]
  #[serde(tag = "type")]
  enum AppMessage {
      Register { payload: RegisterPayload },
      Data { session_id: String, payload: Value },
      Log { session_id: String, payload: LogPayload },
      ActionResult { session_id: String, payload: ActionResultPayload },
  }

  #[derive(Serialize, Deserialize)]
  #[serde(tag = "type")]
  enum ServiceToAppMessage {
      Registered { payload: RegisteredPayload },
      Action { session_id: String, payload: ActionPayload },
      Error { payload: ErrorPayload },
  }

  // Similar for Dashboard messages...
  ```

- [x] **Implement message parsing with validation**

  Parse incoming JSON, validate required fields, enforce size limits:
  - Single message: 1 MB max
  - LOG message field: 64 KB max
  - LOG throwable field: 256 KB max

- [x] **Write unit tests for message parsing**

  Test valid messages, malformed JSON, missing fields, oversized payloads.

### 2.3 Session Management

- [x] **Create Session struct**
  ```rust
  struct Session {
      id: String,
      app_name: String,
      package_name: String,
      version_name: String,
      device: DeviceInfo,
      dashboard_schema: Value,
      started_at: DateTime<Utc>,
      ended_at: Option<DateTime<Utc>>,
      data_buffer: RingBuffer<DataMessage>,
      log_buffer: RingBuffer<LogMessage>,
      app_sender: Option<mpsc::Sender<ServiceToAppMessage>>,
  }
  ```

- [x] **Implement SessionManager**
  ```rust
  struct SessionManager {
      sessions: HashMap<String, Session>,
      dashboard_senders: Vec<mpsc::Sender<DashboardMessage>>,
  }

  impl SessionManager {
      fn create_session(&mut self, register: RegisterPayload) -> String;
      fn end_session(&mut self, session_id: &str);
      fn get_session(&self, session_id: &str) -> Option<&Session>;
      fn get_active_sessions(&self) -> Vec<&Session>;
      fn add_data(&mut self, session_id: &str, data: Value);
      fn add_log(&mut self, session_id: &str, log: LogPayload);
      fn cleanup_ended_sessions(&mut self); // Remove sessions past TTL
  }
  ```

- [x] **Implement ring buffer for DATA and LOG messages**

  Fixed-size buffer that overwrites oldest entries:
  ```rust
  struct RingBuffer<T> {
      items: VecDeque<T>,
      capacity: usize,
  }

  impl<T> RingBuffer<T> {
      fn push(&mut self, item: T);
      fn iter(&self) -> impl Iterator<Item = &T>;
  }
  ```

- [x] **Write unit tests for SessionManager**

  Test session lifecycle, buffer limits, TTL cleanup.

### 2.4 WebSocket Handling

- [x] **Implement app WebSocket handler**
  ```rust
  async fn handle_app_ws(ws: WebSocketUpgrade, State(state): State<AppState>) -> impl IntoResponse {
      ws.on_upgrade(|socket| handle_app_connection(socket, state))
  }

  async fn handle_app_connection(socket: WebSocket, state: AppState) {
      // 1. Wait for REGISTER message
      // 2. Create session, send REGISTERED
      // 3. Loop: receive messages, update session, broadcast to dashboards
      // 4. On disconnect: mark session ended
  }
  ```

- [x] **Implement dashboard WebSocket handler**
  ```rust
  async fn handle_dashboard_ws(ws: WebSocketUpgrade, State(state): State<AppState>) -> impl IntoResponse {
      ws.on_upgrade(|socket| handle_dashboard_connection(socket, state))
  }

  async fn handle_dashboard_connection(socket: WebSocket, state: AppState) {
      // 1. Send SYNC with all active sessions
      // 2. Subscribe to session events
      // 3. Loop: forward session events, receive ACTIONs
  }
  ```

- [x] **Implement message routing**

  Route messages between apps and dashboards:
  - App DATA/LOG → broadcast to all connected dashboards
  - Dashboard ACTION → route to specific app by session_id

- [x] **Handle connection errors gracefully**

  Log disconnections, clean up resources, don't crash on malformed messages.

- [x] **Write integration tests for WebSocket handling**

  Use `tokio-tungstenite` as test client. Test:
  - App connection and registration
  - Dashboard connection and SYNC
  - Message routing between app and dashboard
  - Disconnection handling

### 2.5 UDP Discovery

- [x] **Implement UDP broadcast sender**
  ```rust
  async fn broadcast_presence(config: &Config) {
      let socket = UdpSocket::bind("0.0.0.0:0").await?;
      socket.set_broadcast(true)?;

      let message = json!({
          "service": "androidoscopy",
          "version": "1.0",
          "websocket_port": config.server.websocket_port,
          "http_port": config.server.http_port
      });

      loop {
          socket.send_to(message.as_bytes(), "255.255.255.255:9998").await?;
          tokio::time::sleep(Duration::from_secs(5)).await;
      }
  }
  ```

- [x] **Add config option to enable/disable UDP broadcast**

  Some networks block broadcast; make it optional.

- [x] **Write integration test for UDP discovery**

  Start server, listen for broadcast on port 9998, verify message format.

### 2.6 Static File Serving

- [x] **Serve dashboard static files**
  ```rust
  // Serve from ./dashboard/dist or configured path
  .nest_service("/", ServeDir::new(&config.dashboard.static_dir))
  ```

- [x] **Add fallback for SPA routing**

  Return index.html for non-file routes (for client-side routing).

---

## Phase 3: Android SDK Implementation (Kotlin)

### 3.1 Core Infrastructure

- [x] **Create library module structure**
  ```
  sdk/
  ├── src/main/kotlin/com/lelloman/androidoscopy/
  │   ├── Androidoscopy.kt           # Main entry point
  │   ├── AndroidoscopyConfig.kt     # Configuration DSL
  │   ├── connection/
  │   │   ├── WebSocketClient.kt
  │   │   └── ReconnectionManager.kt
  │   ├── dashboard/
  │   │   ├── DashboardBuilder.kt
  │   │   ├── SectionBuilder.kt
  │   │   └── widgets/
  │   ├── protocol/
  │   │   ├── Messages.kt
  │   │   └── JsonPathEvaluator.kt
  │   └── data/
  │       └── DataProvider.kt
  ```

- [x] **Implement main Androidoscopy entry point**
  ```kotlin
  object Androidoscopy {
      fun init(context: Context, config: AndroidoscopyConfig.() -> Unit)
      fun updateData(block: MutableMap<String, Any>.() -> Unit)
      fun log(level: LogLevel, tag: String?, message: String, throwable: Throwable?)
      internal fun sendAction(action: String, args: Map<String, Any>)
  }
  ```

- [x] **Implement configuration DSL**
  ```kotlin
  class AndroidoscopyConfig {
      var appName: String? = null
      var hostIp: String? = null
      var port: Int = 9999

      private var dashboardBuilder: DashboardBuilder? = null
      private val actionHandlers = mutableMapOf<String, ActionHandler>()

      fun dashboard(block: DashboardBuilder.() -> Unit)
      fun onAction(action: String, handler: ActionHandler)
  }
  ```

### 3.2 Connection Management

- [x] **Implement WebSocket client using OkHttp**
  ```kotlin
  class WebSocketClient(
      private val url: String,
      private val listener: WebSocketListener
  ) {
      private var webSocket: WebSocket? = null

      fun connect()
      fun send(message: String)
      fun disconnect()
  }
  ```

- [x] **Implement emulator detection**
  ```kotlin
  fun isEmulator(): Boolean {
      return Build.FINGERPRINT.contains("generic") ||
             Build.MODEL.contains("Emulator") ||
             Build.MANUFACTURER.contains("Genymotion") ||
             // ... other checks
  }
  ```

- [x] **Implement emulator IP resolution**
  ```kotlin
  private val KNOWN_EMULATOR_HOST_IPS = listOf(
      "10.0.2.2",    // Android Emulator (AVD), BlueStacks
      "10.0.3.2",    // Genymotion
      "192.168.56.1" // Android-x86
  )

  suspend fun findServiceHost(): String? {
      for (ip in KNOWN_EMULATOR_HOST_IPS) {
          if (canConnect(ip)) return ip
      }
      return null
  }
  ```

- [x] **Implement UDP discovery listener**
  ```kotlin
  class DiscoveryListener {
      suspend fun discoverService(timeoutMs: Long = 10_000): ServiceInfo? {
          val socket = DatagramSocket(9998)
          socket.soTimeout = timeoutMs.toInt()

          val buffer = ByteArray(1024)
          val packet = DatagramPacket(buffer, buffer.size)

          return try {
              socket.receive(packet)
              parseServiceInfo(packet)
          } catch (e: SocketTimeoutException) {
              null
          }
      }
  }
  ```

- [x] **Implement ReconnectionManager with exponential backoff**
  ```kotlin
  class ReconnectionManager(
      private val initialDelay: Duration = 1.seconds,
      private val maxDelay: Duration = 30.seconds,
      private val factor: Double = 2.0
  ) {
      private var currentDelay = initialDelay

      fun nextDelay(): Duration
      fun reset()
  }
  ```

- [x] **Implement connection lifecycle management**

  Handle app lifecycle (start connection on init, reconnect on network change).

- [x] **Write unit tests for connection logic**

  Test emulator detection, IP resolution, backoff calculation.

- [x] **Write integration tests with MockWebServer**

  Test full connection flow, registration, message exchange.

### 3.3 Dashboard DSL

- [x] **Implement DashboardBuilder**
  ```kotlin
  class DashboardBuilder {
      private val sections = mutableListOf<Section>()

      fun memorySection()
      fun logsSection()
      fun cacheSection(caches: List<CacheConfig>)
      fun section(title: String, block: SectionBuilder.() -> Unit)

      internal fun build(): DashboardSchema
  }
  ```

- [x] **Implement SectionBuilder**
  ```kotlin
  class SectionBuilder(private val title: String) {
      var layout: Layout = Layout.ROW
      var collapsible: Boolean = false
      var collapsedDefault: Boolean = false

      fun row(block: RowBuilder.() -> Unit)
      fun table(dataPath: String, block: TableBuilder.() -> Unit)
      fun actions(block: ActionsBuilder.() -> Unit)
  }
  ```

- [x] **Implement RowBuilder with widget methods**
  ```kotlin
  class RowBuilder {
      fun number(label: String, dataPath: String, format: Format = Format.NUMBER)
      fun text(label: String, dataPath: String)
      fun bytes(label: String, dataPath: String)
      fun percent(label: String, dataPath: String)
      fun gauge(label: String, valuePath: String, maxPath: String, format: Format = Format.NUMBER)
      fun badge(label: String, dataPath: String, variants: Map<String, BadgeStyle>)
  }
  ```

- [x] **Implement TableBuilder**
  ```kotlin
  class TableBuilder(private val dataPath: String) {
      fun column(key: String, label: String, format: Format = Format.TEXT)
      fun rowAction(id: String, label: String, args: Map<String, String> = emptyMap())
  }
  ```

- [x] **Implement ActionsBuilder with dialog support**
  ```kotlin
  class ActionsBuilder {
      fun button(
          label: String,
          action: String,
          style: Style = Style.PRIMARY,
          resultDisplay: ResultDisplay = ResultDisplay.Toast
      )

      fun button(
          label: String,
          action: String,
          style: Style = Style.PRIMARY,
          argsDialog: ArgsDialogBuilder.() -> Unit
      )
  }

  class ArgsDialogBuilder {
      var title: String = ""
      fun textField(key: String, label: String, default: String = "")
      fun numberField(key: String, label: String, default: Int = 0, min: Int? = null, max: Int? = null)
      fun selectField(key: String, label: String, options: List<SelectOption>)
      fun checkboxField(key: String, label: String, default: Boolean = false)
  }
  ```

- [x] **Implement built-in templates**
  ```kotlin
  // In DashboardBuilder
  fun memorySection() {
      section("Memory") {
          row {
              gauge("Heap Usage", "$.memory.heap_used_bytes", "$.memory.heap_max_bytes", Format.BYTES)
              badge("Pressure", "$.memory.pressure_level", mapOf(
                  "LOW" to BadgeStyle.SUCCESS,
                  "MODERATE" to BadgeStyle.WARNING,
                  "HIGH" to BadgeStyle.DANGER,
                  "CRITICAL" to BadgeStyle.DANGER
              ))
          }
      }
  }
  ```

- [x] **Write unit tests for DSL builders**

  Verify generated JSON schema matches expected format.

### 3.4 Data Providers

- [x] **Implement DataProvider interface**
  ```kotlin
  interface DataProvider {
      val key: String
      val interval: Duration
      suspend fun collect(): Map<String, Any>
  }
  ```

- [x] **Implement data provider scheduling**
  ```kotlin
  class DataProviderManager(private val scope: CoroutineScope) {
      private val providers = mutableListOf<DataProvider>()

      fun register(provider: DataProvider)
      fun start()
      fun stop()
  }
  ```

- [x] **Implement built-in MemoryDataProvider**
  ```kotlin
  class MemoryDataProvider(override val interval: Duration = 5.seconds) : DataProvider {
      override val key = "memory"

      override suspend fun collect(): Map<String, Any> {
          val runtime = Runtime.getRuntime()
          return mapOf(
              "heap_used_bytes" to runtime.totalMemory() - runtime.freeMemory(),
              "heap_max_bytes" to runtime.maxMemory(),
              "pressure_level" to getMemoryPressure()
          )
      }
  }
  ```

- [x] **Implement debouncing for manual updateData calls**

  Batch rapid calls within 100ms window.

- [x] **Write unit tests for data providers**

### 3.5 Action Handling

- [x] **Implement action handler registration**
  ```kotlin
  typealias ActionHandler = suspend (args: Map<String, Any>) -> ActionResult

  data class ActionResult(
      val success: Boolean,
      val message: String? = null,
      val data: Map<String, Any>? = null
  ) {
      companion object {
          fun success(message: String? = null, data: Map<String, Any>? = null) =
              ActionResult(true, message, data)
          fun failure(message: String) =
              ActionResult(false, message)
      }
  }
  ```

- [x] **Implement action dispatch on receiving ACTION message**
  ```kotlin
  private suspend fun handleAction(action: ActionPayload) {
      val handler = actionHandlers[action.action]
      val result = if (handler != null) {
          try {
              handler(action.args)
          } catch (e: Exception) {
              ActionResult.failure(e.message ?: "Unknown error")
          }
      } else {
          ActionResult.failure("Unknown action: ${action.action}")
      }

      sendActionResult(action.actionId, result)
  }
  ```

- [x] **Write unit tests for action handling**

### 3.6 Logging Integration

- [x] **Implement Androidoscopy.log()**
  ```kotlin
  fun log(level: LogLevel, tag: String?, message: String, throwable: Throwable? = null) {
      val logMessage = LogMessage(
          level = level,
          tag = tag,
          message = message.take(65536), // 64KB limit
          throwable = throwable?.stackTraceToString()?.take(262144) // 256KB limit
      )
      sendLog(logMessage)
  }
  ```

- [x] **Implement Timber tree for easy integration**
  ```kotlin
  class AndroidoscopyTree : Timber.Tree() {
      override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
          Androidoscopy.log(priority.toLogLevel(), tag, message, t)
      }
  }
  ```

- [x] **Write unit tests for logging**

### 3.7 Protocol Implementation

- [x] **Define message data classes**
  ```kotlin
  @Serializable
  sealed class AppMessage {
      abstract val type: String
      abstract val timestamp: String
  }

  @Serializable
  @SerialName("REGISTER")
  data class RegisterMessage(
      override val timestamp: String,
      val payload: RegisterPayload
  ) : AppMessage()

  // ... other message types
  ```

- [x] **Implement JSON serialization with kotlinx.serialization**

- [x] **Implement device info collection**
  ```kotlin
  fun collectDeviceInfo(context: Context): DeviceInfo {
      return DeviceInfo(
          deviceId = getOrCreateDeviceId(context),
          manufacturer = Build.MANUFACTURER,
          model = Build.MODEL,
          androidVersion = Build.VERSION.RELEASE,
          apiLevel = Build.VERSION.SDK_INT,
          isEmulator = isEmulator()
      )
  }
  ```

- [x] **Write unit tests for message serialization**

---

## Phase 4: Dashboard Implementation (Svelte)

### 4.1 Core Infrastructure

- [x] **Set up Svelte project with TypeScript**

  Configure Vite, add paths, set up testing.

- [x] **Define TypeScript types for protocol**
  ```typescript
  // src/lib/types.ts

  interface Session {
      session_id: string;
      app_name: string;
      package_name: string;
      version_name: string;
      device: DeviceInfo;
      dashboard: DashboardSchema;
      started_at: string;
      latest_data?: Record<string, unknown>;
      recent_logs?: LogEntry[];
  }

  interface DashboardSchema {
      sections: Section[];
  }

  interface Section {
      id: string;
      title: string;
      layout?: 'row' | 'grid' | 'stack';
      columns?: number;
      collapsible?: boolean;
      collapsed_default?: boolean;
      widgets?: Widget[];
      widget?: Widget;
  }

  // ... widget types
  ```

- [x] **Implement WebSocket connection store**
  ```typescript
  // src/lib/stores/connection.ts
  import { writable } from 'svelte/store';

  export const connected = writable(false);
  export const sessions = writable<Map<string, Session>>(new Map());

  export function connect() {
      const ws = new WebSocket(`ws://${location.hostname}:9999/ws/dashboard`);

      ws.onmessage = (event) => {
          const message = JSON.parse(event.data);
          handleMessage(message);
      };
  }
  ```

- [x] **Implement message handling**
  ```typescript
  function handleMessage(message: DashboardMessage) {
      switch (message.type) {
          case 'SYNC':
              handleSync(message.payload);
              break;
          case 'SESSION_STARTED':
              handleSessionStarted(message.payload);
              break;
          case 'SESSION_DATA':
              handleSessionData(message.payload);
              break;
          case 'SESSION_LOG':
              handleSessionLog(message.payload);
              break;
          case 'SESSION_ENDED':
              handleSessionEnded(message.payload);
              break;
          case 'ACTION_RESULT':
              handleActionResult(message.payload);
              break;
      }
  }
  ```

### 4.2 JSONPath Evaluation

- [x] **Implement JSONPath value extraction**
  ```typescript
  // src/lib/jsonpath.ts
  import { JSONPath } from 'jsonpath-plus';

  export function evaluatePath(data: unknown, path: string): unknown {
      if (!path.startsWith('$')) return path;
      const result = JSONPath({ path, json: data, wrap: false });
      return result;
  }
  ```

- [x] **Write unit tests for JSONPath evaluation**

  Test direct paths, array access, nested objects.

### 4.3 Format Functions

- [x] **Implement format utilities**
  ```typescript
  // src/lib/format.ts

  export function formatBytes(bytes: number): string {
      const units = ['B', 'KB', 'MB', 'GB'];
      let i = 0;
      while (bytes >= 1024 && i < units.length - 1) {
          bytes /= 1024;
          i++;
      }
      return `${bytes.toFixed(1)} ${units[i]}`;
  }

  export function formatPercent(value: number): string {
      return `${(value * 100).toFixed(0)}%`;
  }

  export function formatDuration(ms: number): string {
      const hours = Math.floor(ms / 3600000);
      const minutes = Math.floor((ms % 3600000) / 60000);
      return `${hours}h ${minutes}m`;
  }

  export function formatNumber(value: number): string {
      return value.toLocaleString();
  }
  ```

- [x] **Write unit tests for format functions**

### 4.4 Widget Components

- [x] **Implement Gauge widget**
  ```svelte
  <!-- src/lib/widgets/Gauge.svelte -->
  <script lang="ts">
      export let label: string;
      export let value: number;
      export let max: number;
      export let format: string = 'number';
      export let thresholds: Threshold[] = [];

      $: percentage = max > 0 ? (value / max) * 100 : 0;
      $: style = getThresholdStyle(percentage / 100, thresholds);
  </script>

  <div class="gauge">
      <div class="label">{label}</div>
      <div class="bar">
          <div class="fill {style}" style="width: {percentage}%"></div>
      </div>
      <div class="value">{formatValue(value, format)} / {formatValue(max, format)}</div>
  </div>
  ```

- [x] **Implement Number widget**

- [x] **Implement Text widget**

- [x] **Implement Badge widget**
  ```svelte
  <!-- src/lib/widgets/Badge.svelte -->
  <script lang="ts">
      export let label: string;
      export let value: string;
      export let variants: Record<string, string> = {};

      $: style = variants[value] || 'muted';
  </script>

  <div class="badge-container">
      <span class="label">{label}</span>
      <span class="badge {style}">{value}</span>
  </div>
  ```

- [x] **Implement Table widget**
  ```svelte
  <!-- src/lib/widgets/Table.svelte -->
  <script lang="ts">
      export let data: unknown[];
      export let columns: Column[];
      export let rowActions: RowAction[] = [];

      function handleRowAction(action: RowAction, row: unknown) {
          const args = buildArgs(action.args, row);
          sendAction(action.id, args);
      }
  </script>

  <table>
      <thead>
          <tr>
              {#each columns as col}
                  <th>{col.label}</th>
              {/each}
              {#if rowActions.length > 0}
                  <th>Actions</th>
              {/if}
          </tr>
      </thead>
      <tbody>
          {#each data as row}
              <tr>
                  {#each columns as col}
                      <td>{formatValue(row[col.key], col.format)}</td>
                  {/each}
                  {#if rowActions.length > 0}
                      <td>
                          {#each rowActions as action}
                              <button on:click={() => handleRowAction(action, row)}>
                                  {action.label}
                              </button>
                          {/each}
                      </td>
                  {/if}
              </tr>
          {/each}
      </tbody>
  </table>
  ```

- [x] **Implement Button widget with states**
  ```svelte
  <!-- src/lib/widgets/Button.svelte -->
  <script lang="ts">
      export let label: string;
      export let action: string;
      export let style: string = 'primary';
      export let argsDialog: ArgsDialog | undefined = undefined;
      export let resultDisplay: ResultDisplay = { type: 'toast' };

      let state: 'idle' | 'loading' | 'success' | 'error' = 'idle';
      let showDialog = false;

      async function handleClick() {
          if (argsDialog) {
              showDialog = true;
          } else {
              executeAction({});
          }
      }

      async function executeAction(args: Record<string, unknown>) {
          state = 'loading';
          const result = await sendAction(action, args);
          state = result.success ? 'success' : 'error';
          handleResult(result, resultDisplay);
          setTimeout(() => state = 'idle', 2000);
      }
  </script>
  ```

- [x] **Write component tests for all widgets**

### 4.5 Log Viewer Widget

- [x] **Implement LogViewer component**
  ```svelte
  <!-- src/lib/widgets/LogViewer.svelte -->
  <script lang="ts">
      export let logs: LogEntry[] = [];
      export let defaultLevel: string = 'DEBUG';

      let levelFilter = defaultLevel;
      let tagFilter = '';
      let searchFilter = '';
      let autoScroll = true;
      let container: HTMLElement;

      $: filteredLogs = logs.filter(log =>
          LOG_LEVELS.indexOf(log.level) >= LOG_LEVELS.indexOf(levelFilter) &&
          (!tagFilter || log.tag?.includes(tagFilter)) &&
          (!searchFilter || log.message.includes(searchFilter))
      );

      function handleScroll() {
          const atBottom = container.scrollHeight - container.scrollTop <= container.clientHeight + 50;
          autoScroll = atBottom;
      }

      $: if (autoScroll && container) {
          tick().then(() => container.scrollTop = container.scrollHeight);
      }
  </script>

  <div class="log-viewer">
      <div class="filters">
          <select bind:value={levelFilter}>
              {#each LOG_LEVELS as level}
                  <option value={level}>{level}</option>
              {/each}
          </select>
          <input placeholder="Filter by tag" bind:value={tagFilter} />
          <input placeholder="Search..." bind:value={searchFilter} />
      </div>

      <div class="logs" bind:this={container} on:scroll={handleScroll}>
          {#each filteredLogs as log}
              <LogEntry {log} />
          {/each}
      </div>

      {#if !autoScroll}
          <button class="jump-to-bottom" on:click={() => autoScroll = true}>
              Jump to bottom
          </button>
      {/if}
  </div>
  ```

- [x] **Implement LogEntry component**
  ```svelte
  <!-- src/lib/widgets/LogEntry.svelte -->
  <script lang="ts">
      export let log: LogEntry;

      let expanded = false;
  </script>

  <div class="log-entry {log.level.toLowerCase()}">
      <span class="timestamp">[{formatTime(log.timestamp)}]</span>
      <span class="level">{log.level}</span>
      <span class="tag">{log.tag || '-'}</span>
      <span class="message">{log.message}</span>
      {#if log.throwable}
          <button on:click={() => expanded = !expanded}>
              {expanded ? '▼' : '▶'}
          </button>
          {#if expanded}
              <pre class="throwable">{log.throwable}</pre>
          {/if}
      {/if}
  </div>
  ```

- [x] **Write component tests for LogViewer**

### 4.6 Layout System

- [x] **Implement Section component**
  ```svelte
  <!-- src/lib/layout/Section.svelte -->
  <script lang="ts">
      export let section: Section;
      export let data: unknown;

      let collapsed = section.collapsed_default ?? false;
  </script>

  <div class="section">
      <div class="header" on:click={() => section.collapsible && (collapsed = !collapsed)}>
          <h3>{section.title}</h3>
          {#if section.collapsible}
              <span class="toggle">{collapsed ? '▶' : '▼'}</span>
          {/if}
      </div>

      {#if !collapsed}
          <div class="content layout-{section.layout || 'row'}"
               style:--columns={section.columns}>
              {#if section.widgets}
                  {#each section.widgets as widget}
                      <Widget {widget} {data} />
                  {/each}
              {:else if section.widget}
                  <Widget widget={section.widget} {data} />
              {/if}
          </div>
      {/if}
  </div>
  ```

- [x] **Implement Widget dispatcher component**
  ```svelte
  <!-- src/lib/layout/Widget.svelte -->
  <script lang="ts">
      import Gauge from '../widgets/Gauge.svelte';
      import Number from '../widgets/Number.svelte';
      // ... other imports

      export let widget: WidgetSchema;
      export let data: unknown;

      $: resolvedProps = resolveWidgetProps(widget, data);
  </script>

  {#if widget.type === 'gauge'}
      <Gauge {...resolvedProps} />
  {:else if widget.type === 'number'}
      <Number {...resolvedProps} />
  <!-- ... other widget types -->
  {/if}
  ```

- [x] **Implement conditional rendering (visible_when)**
  ```typescript
  function evaluateCondition(condition: VisibleWhen, data: unknown): boolean {
      const value = evaluatePath(data, condition.path);
      switch (condition.operator) {
          case 'eq': return value === condition.value;
          case 'neq': return value !== condition.value;
          case 'gt': return value > condition.value;
          case 'gte': return value >= condition.value;
          case 'lt': return value < condition.value;
          case 'lte': return value <= condition.value;
          case 'exists': return value != null;
      }
  }
  ```

- [x] **Implement threshold-based styling**

- [x] **Write component tests for layout**

### 4.7 Action Dialogs

- [x] **Implement ArgsDialog component**
  ```svelte
  <!-- src/lib/dialogs/ArgsDialog.svelte -->
  <script lang="ts">
      import { createEventDispatcher } from 'svelte';

      export let dialog: ArgsDialogSchema;
      export let open = false;

      const dispatch = createEventDispatcher();
      let values: Record<string, unknown> = {};

      function initValues() {
          values = {};
          for (const field of dialog.fields) {
              values[field.key] = field.default ?? getDefaultForType(field.type);
          }
      }

      function handleSubmit() {
          dispatch('submit', values);
          open = false;
      }

      $: if (open) initValues();
  </script>

  {#if open}
      <div class="dialog-overlay" on:click={() => open = false}>
          <div class="dialog" on:click|stopPropagation>
              <h2>{dialog.title}</h2>

              <form on:submit|preventDefault={handleSubmit}>
                  {#each dialog.fields as field}
                      <DialogField {field} bind:value={values[field.key]} />
                  {/each}

                  <div class="actions">
                      <button type="button" on:click={() => open = false}>Cancel</button>
                      <button type="submit">Confirm</button>
                  </div>
              </form>
          </div>
      </div>
  {/if}
  ```

- [x] **Implement DialogField component**

  Render appropriate input based on field type (text, number, select, checkbox).

- [ ] **Write component tests for dialogs**

### 4.8 Toast Notifications

- [x] **Implement Toast store and component**
  ```typescript
  // src/lib/stores/toasts.ts
  import { writable } from 'svelte/store';

  interface Toast {
      id: string;
      message: string;
      type: 'success' | 'error' | 'info';
      duration: number;
  }

  export const toasts = writable<Toast[]>([]);

  export function showToast(message: string, type: 'success' | 'error' | 'info' = 'info') {
      const id = crypto.randomUUID();
      const duration = type === 'error' ? 5000 : 3000;

      toasts.update(t => [...t, { id, message, type, duration }]);

      setTimeout(() => {
          toasts.update(t => t.filter(toast => toast.id !== id));
      }, duration);
  }
  ```

- [x] **Implement ToastContainer component**

### 4.9 Main App Layout

- [x] **Implement App.svelte**
  ```svelte
  <!-- src/App.svelte -->
  <script lang="ts">
      import { onMount } from 'svelte';
      import { connect, sessions, connected } from './lib/stores/connection';
      import SessionCard from './lib/SessionCard.svelte';
      import ToastContainer from './lib/ToastContainer.svelte';

      onMount(() => {
          connect();
      });
  </script>

  <main>
      <header>
          <h1>Androidoscopy</h1>
          <span class="status" class:connected={$connected}>
              {$connected ? 'Connected' : 'Disconnected'}
          </span>
      </header>

      <div class="sessions">
          {#if $sessions.size === 0}
              <p class="empty">No connected apps</p>
          {:else}
              {#each [...$sessions.values()] as session}
                  <SessionCard {session} />
              {/each}
          {/if}
      </div>

      <ToastContainer />
  </main>
  ```

- [x] **Implement SessionCard component**

  Display session info and render dashboard sections.

- [x] **Add basic styling**

  Clean, minimal design with dark/light mode support.

### 4.10 E2E Tests

- [ ] **Set up Playwright**
  ```typescript
  // playwright.config.ts
  export default {
      testDir: './e2e',
      use: {
          baseURL: 'http://localhost:8080',
      },
      webServer: {
          command: 'npm run preview',
          port: 8080,
      },
  };
  ```

- [ ] **Write E2E tests**
  - Session list displays connected apps
  - Widgets render with correct data
  - Actions trigger and show results
  - Log viewer filters work
  - Auto-scroll behavior

---

## Phase 5: Integration & E2E Testing

### 5.1 Full Stack Integration Tests

- [ ] **Create E2E test harness**
  ```
  e2e/
  ├── src/
  │   ├── mock_app.rs       # Simulates Android SDK
  │   └── test_runner.rs    # Orchestrates tests
  ├── tests/
  │   ├── connection_test.rs
  │   ├── data_flow_test.rs
  │   └── action_test.rs
  ```

- [ ] **Implement mock app client in Rust**

  Simulates SDK behavior for testing server + dashboard without real Android.

- [ ] **Write full stack test scenarios**
  - App connects → Dashboard shows session
  - App sends DATA → Dashboard widgets update
  - App sends LOG → Log viewer updates
  - Dashboard sends ACTION → App receives and responds
  - App disconnects → Dashboard shows ended

- [ ] **Set up CI job for full stack tests**

---

## Phase 6: Documentation & Polish

### 6.1 Documentation

- [ ] **Write README.md with quick start guide**

- [ ] **Write SDK integration guide**

  Step-by-step for adding to an Android project.

- [ ] **Write custom dashboard tutorial**

  How to create custom sections and widgets.

- [ ] **Add inline code documentation**

### 6.2 Polish

- [x] **Add error boundaries to dashboard**

- [ ] **Handle edge cases in all components**

- [ ] **Performance optimization**
  - Virtual scrolling for log viewer if needed
  - Debounce rapid data updates

- [ ] **Accessibility review**

---

## Checklist Summary

| Phase | Tasks | Status |
|-------|-------|--------|
| 1. Project Setup | 8 | Completed |
| 2. Server | 22 | Completed |
| 3. SDK | 28 | Completed |
| 4. Dashboard | 32 | In progress |
| 5. Integration | 5 | Not started |
| 6. Documentation | 6 | Not started |
| **Total** | **101** | |

---

*Last updated: 2025-12-05*
