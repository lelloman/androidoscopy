# Androidoscopy - Implementation Tasks (Phases 2-4)

This document contains actionable implementation tasks for the remaining phases of Androidoscopy development.

**Legend:**
- `[ ]` - Not started
- `[~]` - In progress
- `[x]` - Completed

---

## Phase 2: Enhanced Widgets

### 2.1 Chart Widget (Time Series)

The chart widget displays time-series data with automatic scrolling and configurable appearance.

#### 2.1.1 Dashboard Implementation

- [ ] **Define ChartWidget TypeScript interface**

  Add to `dashboard/src/lib/types/protocol.ts`:
  ```typescript
  export interface ChartWidget extends BaseWidget {
      type: 'chart';
      label: string;
      data_path: string;           // JSONPath to array of {timestamp, value} or just values
      format?: 'number' | 'bytes' | 'percent';
      max_points?: number;         // Max data points to display (default: 60)
      time_window_seconds?: number; // Alternative: show last N seconds
      min?: number;                // Optional fixed min Y-axis
      max?: number;                // Optional fixed max Y-axis
      color?: string;              // Line color (default: primary)
      fill?: boolean;              // Fill area under line (default: false)
      thresholds?: Threshold[];    // Color zones
  }
  ```

- [ ] **Create Chart.svelte component**

  Location: `dashboard/src/lib/widgets/Chart.svelte`

  Features:
  - SVG-based line chart (no external dependencies)
  - Responsive sizing
  - Auto-scaling Y-axis (unless min/max specified)
  - Hover tooltips showing value and time
  - Threshold-based color zones
  - Smooth animations on new data points

- [ ] **Register Chart widget in Widget.svelte dispatcher**

- [ ] **Write unit tests for Chart widget**

  Test cases:
  - Empty data renders placeholder
  - Single data point renders correctly
  - Data exceeding max_points truncates oldest
  - Threshold colors apply correctly
  - Format functions work (bytes, percent)

#### 2.1.2 SDK Implementation

- [ ] **Add ChartWidget to Kotlin DSL**

  Add to `RowBuilder`:
  ```kotlin
  fun chart(
      label: String,
      dataPath: String,
      format: Format = Format.NUMBER,
      maxPoints: Int = 60,
      timeWindowSeconds: Int? = null,
      min: Number? = null,
      max: Number? = null,
      fill: Boolean = false,
      thresholds: List<Threshold>? = null
  )
  ```

- [ ] **Add chart() to SectionBuilder for full-width charts**

  ```kotlin
  fun chart(
      label: String,
      dataPath: String,
      block: ChartBuilder.() -> Unit = {}
  )
  ```

- [ ] **Create ChartBuilder for advanced configuration**

  ```kotlin
  class ChartBuilder {
      var format: Format = Format.NUMBER
      var maxPoints: Int = 60
      var timeWindowSeconds: Int? = null
      var min: Number? = null
      var max: Number? = null
      var fill: Boolean = false
      var color: String? = null

      fun threshold(value: Number, style: ThresholdStyle)
  }
  ```

- [ ] **Write unit tests for Chart DSL**

#### 2.1.3 Data Collection Support

- [ ] **Add TimeSeriesCollector utility to SDK**

  ```kotlin
  class TimeSeriesCollector(
      private val maxPoints: Int = 60,
      private val key: String
  ) {
      fun record(value: Number)
      fun toData(): List<Map<String, Any>>  // [{timestamp, value}, ...]
  }
  ```

  This helper makes it easy to collect time-series data for charts.

- [ ] **Add built-in CPU/Memory chart template**

  ```kotlin
  fun DashboardBuilder.performanceChartsSection() {
      section("Performance") {
          chart("Heap Usage", "$.performance.heap_history") {
              format = Format.BYTES
              fill = true
              thresholds {
                  threshold(0.75, ThresholdStyle.WARNING)
                  threshold(0.90, ThresholdStyle.DANGER)
              }
          }
      }
  }
  ```

---

### 2.2 Sparkline Widget

A compact inline chart for displaying trends within rows.

#### 2.2.1 Dashboard Implementation

- [ ] **Define SparklineWidget TypeScript interface**

  ```typescript
  export interface SparklineWidget extends BaseWidget {
      type: 'sparkline';
      label: string;
      data_path: string;           // JSONPath to array of numbers
      format?: 'number' | 'bytes' | 'percent';
      max_points?: number;         // Default: 20
      show_current_value?: boolean; // Show latest value next to sparkline
      color?: string;
      height?: number;             // Default: 24px
  }
  ```

- [ ] **Create Sparkline.svelte component**

  Features:
  - Compact SVG line (no axes, minimal chrome)
  - Optional current value display
  - Hover to see value
  - Polyline-based for simplicity

- [ ] **Register Sparkline widget in dispatcher**

- [ ] **Write unit tests for Sparkline widget**

#### 2.2.2 SDK Implementation

- [ ] **Add sparkline() to RowBuilder**

  ```kotlin
  fun sparkline(
      label: String,
      dataPath: String,
      format: Format = Format.NUMBER,
      maxPoints: Int = 20,
      showCurrentValue: Boolean = true
  )
  ```

- [ ] **Write unit tests for Sparkline DSL**

---

### 2.3 JSON Viewer Widget

Display complex JSON objects with syntax highlighting and collapsible nodes.

#### 2.3.1 Dashboard Implementation

- [ ] **Define JsonViewerWidget TypeScript interface**

  ```typescript
  export interface JsonViewerWidget extends BaseWidget {
      type: 'json_viewer';
      label?: string;
      data_path: string;           // JSONPath to object/array to display
      collapsed_depth?: number;    // Collapse nodes deeper than this (default: 2)
      max_height?: number;         // Max height in pixels (scrollable)
      copyable?: boolean;          // Show copy button (default: true)
  }
  ```

- [ ] **Create JsonViewer.svelte component**

  Features:
  - Recursive tree rendering
  - Syntax highlighting (keys, strings, numbers, booleans, null)
  - Collapsible objects/arrays with item count
  - Copy to clipboard button
  - Max height with scroll

- [ ] **Register JsonViewer widget in dispatcher**

- [ ] **Write unit tests for JsonViewer widget**

#### 2.3.2 SDK Implementation

- [ ] **Add jsonViewer() to SectionBuilder**

  ```kotlin
  fun jsonViewer(
      dataPath: String,
      label: String? = null,
      collapsedDepth: Int = 2,
      maxHeight: Int? = null,
      copyable: Boolean = true
  )
  ```

- [ ] **Write unit tests for JsonViewer DSL**

---

### 2.4 Key-Value Widget

Display a map/dictionary as a clean key-value list.

#### 2.4.1 Dashboard Implementation

- [ ] **Define KeyValueWidget TypeScript interface**

  ```typescript
  export interface KeyValueWidget extends BaseWidget {
      type: 'key_value';
      label?: string;
      data_path: string;           // JSONPath to object
      format?: Record<string, 'number' | 'bytes' | 'percent' | 'text' | 'duration'>;
      copyable?: boolean;          // Allow copying values
  }
  ```

- [ ] **Create KeyValue.svelte component**

  Features:
  - Two-column layout (key: value)
  - Per-key format specification
  - Optional copy button per value
  - Handles nested objects by showing [Object] with expand option

- [ ] **Register KeyValue widget in dispatcher**

- [ ] **Write unit tests for KeyValue widget**

#### 2.4.2 SDK Implementation

- [ ] **Add keyValue() to SectionBuilder**

  ```kotlin
  fun keyValue(
      dataPath: String,
      label: String? = null,
      formats: Map<String, Format> = emptyMap(),
      copyable: Boolean = false
  )
  ```

- [ ] **Write unit tests for KeyValue DSL**

---

### 2.5 Status Indicator Widget

A simple colored dot/icon with label for quick status visibility.

#### 2.5.1 Dashboard Implementation

- [ ] **Define StatusWidget TypeScript interface**

  ```typescript
  export interface StatusWidget extends BaseWidget {
      type: 'status';
      label: string;
      data_path: string;           // JSONPath to status value
      variants: Record<string, {
          color: 'success' | 'warning' | 'danger' | 'info' | 'muted';
          icon?: 'check' | 'warning' | 'error' | 'info' | 'sync' | 'offline';
          pulse?: boolean;         // Animated pulse effect
      }>;
      default_variant?: string;    // Fallback if value doesn't match
  }
  ```

- [ ] **Create Status.svelte component**

  Features:
  - Colored dot/circle indicator
  - Optional icon inside
  - Optional pulse animation (for active/syncing states)
  - Label text next to indicator

- [ ] **Register Status widget in dispatcher**

- [ ] **Write unit tests for Status widget**

#### 2.5.2 SDK Implementation

- [ ] **Add status() to RowBuilder**

  ```kotlin
  fun status(
      label: String,
      dataPath: String,
      variants: Map<String, StatusVariant>,
      defaultVariant: StatusVariant? = null
  )

  data class StatusVariant(
      val color: BadgeStyle,
      val icon: StatusIcon? = null,
      val pulse: Boolean = false
  )

  enum class StatusIcon {
      CHECK, WARNING, ERROR, INFO, SYNC, OFFLINE
  }
  ```

- [ ] **Write unit tests for Status DSL**

---

### 2.6 Progress Widget

A progress bar for discrete task progress (different from gauge which shows resource usage).

#### 2.6.1 Dashboard Implementation

- [ ] **Define ProgressWidget TypeScript interface**

  ```typescript
  export interface ProgressWidget extends BaseWidget {
      type: 'progress';
      label: string;
      value_path: string;          // Current progress (0-100 or 0-max)
      max_path?: string;           // Optional max value path (default: 100)
      show_percent?: boolean;      // Show percentage text
      show_value?: boolean;        // Show value/max text
      color?: 'primary' | 'success' | 'warning' | 'danger';
      striped?: boolean;           // Striped pattern
      animated?: boolean;          // Animated stripes
  }
  ```

- [ ] **Create Progress.svelte component**

  Features:
  - Horizontal progress bar
  - Percentage or value display
  - Optional striped/animated styling
  - Configurable color

- [ ] **Register Progress widget in dispatcher**

- [ ] **Write unit tests for Progress widget**

#### 2.6.2 SDK Implementation

- [ ] **Add progress() to RowBuilder**

  ```kotlin
  fun progress(
      label: String,
      valuePath: String,
      maxPath: String? = null,
      showPercent: Boolean = true,
      showValue: Boolean = false,
      color: ProgressColor = ProgressColor.PRIMARY,
      striped: Boolean = false,
      animated: Boolean = false
  )
  ```

- [ ] **Write unit tests for Progress DSL**

---

### 2.7 Update Widget Union Types

- [ ] **Update Widget union type in protocol.ts**

  ```typescript
  export type Widget =
      | NumberWidget
      | TextWidget
      | GaugeWidget
      | BadgeWidget
      | TableWidget
      | ButtonWidget
      | LogViewerWidget
      | ChartWidget      // NEW
      | SparklineWidget  // NEW
      | JsonViewerWidget // NEW
      | KeyValueWidget   // NEW
      | StatusWidget     // NEW
      | ProgressWidget;  // NEW
  ```

- [ ] **Update format.ts with any new format types needed**

---

### 2.8 Documentation Updates

- [ ] **Update SDK integration guide with new widgets**

- [ ] **Add examples for each new widget type**

- [ ] **Update DESIGN.md widget table**

---

## Phase 3: Polish

### 3.1 Export Session Data

Allow exporting session data as JSON for offline analysis or sharing.

#### 3.1.1 Dashboard Implementation

- [ ] **Add export button to SessionCard header**

  Button in session card that triggers export.

- [ ] **Implement exportSession() function**

  ```typescript
  function exportSession(session: Session): void {
      const data = {
          session_id: session.session_id,
          app_name: session.app_name,
          package_name: session.package_name,
          version_name: session.version_name,
          device: session.device,
          started_at: session.started_at,
          ended_at: session.ended_at,
          dashboard_schema: session.dashboard,
          latest_data: session.latest_data,
          logs: session.logs,
          exported_at: new Date().toISOString()
      };

      downloadJson(data, `${session.app_name}-${session.session_id}.json`);
  }
  ```

- [ ] **Add downloadJson utility**

  Create blob, trigger download with proper filename.

- [ ] **Add keyboard shortcut (Cmd/Ctrl+E) for export**

- [ ] **Write tests for export functionality**

#### 3.1.2 Export Options Dialog (Optional Enhancement)

- [ ] **Create ExportOptionsDialog component**

  Options:
  - Include logs (checkbox, default: true)
  - Include dashboard schema (checkbox, default: true)
  - Time range (last N minutes / all)
  - Format: JSON / JSON (pretty) / CSV (logs only)

- [ ] **Implement CSV export for logs**

  Columns: timestamp, level, tag, message

---

### 3.2 Dashboard Themes

Support dark and light mode with user preference persistence.

#### 3.2.1 Theme Infrastructure

- [ ] **Define theme CSS variables**

  Create `dashboard/src/lib/styles/themes.css`:
  ```css
  :root {
      /* Light theme (default) */
      --bg-primary: #ffffff;
      --bg-secondary: #f5f5f5;
      --bg-tertiary: #e8e8e8;
      --text-primary: #1a1a1a;
      --text-secondary: #666666;
      --text-muted: #999999;
      --border-color: #e0e0e0;
      --card-bg: #ffffff;
      --card-shadow: 0 1px 3px rgba(0,0,0,0.1);

      /* Semantic colors */
      --primary: #2563eb;
      --success: #16a34a;
      --warning: #d97706;
      --danger: #dc2626;
      --info: #0891b2;

      /* Chart colors */
      --chart-line: #2563eb;
      --chart-fill: rgba(37, 99, 235, 0.1);
      --chart-grid: #e5e7eb;
  }

  [data-theme="dark"] {
      --bg-primary: #0f0f0f;
      --bg-secondary: #1a1a1a;
      --bg-tertiary: #262626;
      --text-primary: #f5f5f5;
      --text-secondary: #a3a3a3;
      --text-muted: #737373;
      --border-color: #333333;
      --card-bg: #1a1a1a;
      --card-shadow: 0 1px 3px rgba(0,0,0,0.3);

      /* Semantic colors (slightly adjusted for dark) */
      --primary: #3b82f6;
      --success: #22c55e;
      --warning: #f59e0b;
      --danger: #ef4444;
      --info: #06b6d4;

      /* Chart colors */
      --chart-line: #3b82f6;
      --chart-fill: rgba(59, 130, 246, 0.15);
      --chart-grid: #333333;
  }
  ```

- [ ] **Create theme store**

  `dashboard/src/lib/stores/theme.ts`:
  ```typescript
  import { writable } from 'svelte/store';

  type Theme = 'light' | 'dark' | 'system';

  function createThemeStore() {
      const stored = localStorage.getItem('theme') as Theme | null;
      const { subscribe, set } = writable<Theme>(stored || 'system');

      return {
          subscribe,
          set: (theme: Theme) => {
              localStorage.setItem('theme', theme);
              applyTheme(theme);
              set(theme);
          },
          toggle: () => { /* cycle through themes */ }
      };
  }

  function applyTheme(theme: Theme) {
      const isDark = theme === 'dark' ||
          (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
      document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
  }

  export const theme = createThemeStore();
  ```

- [ ] **Listen for system theme changes when using 'system' mode**

#### 3.2.2 Theme Toggle UI

- [ ] **Create ThemeToggle component**

  `dashboard/src/lib/components/ThemeToggle.svelte`:
  - Sun/moon icon toggle button
  - Dropdown for light/dark/system selection
  - Keyboard shortcut (Cmd/Ctrl+Shift+T)

- [ ] **Add ThemeToggle to App header**

- [ ] **Update all components to use CSS variables**

  Audit and update:
  - SessionCard
  - Section
  - All widgets (Number, Text, Gauge, Badge, Button, Table, LogViewer, etc.)
  - Dialogs
  - Toasts

#### 3.2.3 Theme Testing

- [ ] **Write visual regression tests for both themes**

- [ ] **Test theme persistence across page reloads**

- [ ] **Test system theme detection**

---

### 3.3 Performance Optimizations

#### 3.3.1 Log Viewer Virtualization

- [ ] **Implement virtual scrolling for LogViewer**

  Only render visible log entries + buffer. Essential for sessions with 1000s of logs.

  Options:
  - Use `svelte-virtual-list` or similar
  - Or implement custom virtualization

- [ ] **Add log entry recycling**

  Reuse DOM nodes when scrolling.

- [ ] **Benchmark and document performance**

#### 3.3.2 Data Update Debouncing

- [ ] **Debounce rapid data updates in connection store**

  If multiple SESSION_DATA messages arrive within 16ms (one frame), batch them.

- [ ] **Add requestAnimationFrame throttling for chart updates**

#### 3.3.3 Memory Management

- [ ] **Implement log buffer limit in dashboard**

  Keep only last N logs in memory per session (configurable, default: 5000).

- [ ] **Add data history limit for charts**

  Automatically prune old data points beyond max_points.

- [ ] **Profile memory usage with long-running sessions**

---

## Phase 4: Custom Widget Plugins (Partial)

### 4.1 Plugin System Architecture

#### 4.1.1 Design Plugin API

- [ ] **Define PluginWidget interface**

  ```typescript
  // dashboard/src/lib/plugins/types.ts

  export interface WidgetPlugin {
      /** Unique identifier for this widget type */
      type: string;

      /** Display name for documentation/errors */
      name: string;

      /** Svelte component that renders the widget */
      component: typeof SvelteComponent;

      /** JSON Schema for widget configuration validation */
      configSchema?: object;

      /** Default configuration values */
      defaults?: Record<string, unknown>;
  }

  export interface PluginContext {
      /** Evaluate a JSONPath against session data */
      evaluatePath: (path: string) => unknown;

      /** Format a value using built-in formatters */
      formatValue: (value: unknown, format: string) => string;

      /** Send an action to the app */
      sendAction: (action: string, args?: Record<string, unknown>) => Promise<ActionResult>;

      /** Current session ID */
      sessionId: string;

      /** Show a toast notification */
      showToast: (message: string, type: 'success' | 'error' | 'info') => void;
  }
  ```

- [ ] **Define plugin registration API**

  ```typescript
  // dashboard/src/lib/plugins/registry.ts

  class PluginRegistry {
      private plugins = new Map<string, WidgetPlugin>();

      register(plugin: WidgetPlugin): void;
      unregister(type: string): void;
      get(type: string): WidgetPlugin | undefined;
      getAll(): WidgetPlugin[];
      has(type: string): boolean;
  }

  export const pluginRegistry = new PluginRegistry();
  ```

#### 4.1.2 Plugin Loading

- [ ] **Create plugin loader**

  ```typescript
  // dashboard/src/lib/plugins/loader.ts

  interface PluginManifest {
      name: string;
      version: string;
      widgets: WidgetPlugin[];
  }

  async function loadPlugin(url: string): Promise<PluginManifest>;
  async function loadPluginsFromConfig(): Promise<void>;
  ```

- [ ] **Add plugin configuration to dashboard**

  ```typescript
  // Plugin config in localStorage or server config
  interface PluginConfig {
      enabled: string[];  // List of enabled plugin URLs/names
      settings: Record<string, unknown>;  // Per-plugin settings
  }
  ```

- [ ] **Create PluginWidget wrapper component**

  Wraps plugin components with error boundary, context provision, and standard styling.

#### 4.1.3 Update Widget Dispatcher for Plugins

- [ ] **Modify Widget.svelte to check plugin registry**

  ```svelte
  {#if builtinWidgetTypes.includes(widget.type)}
      <!-- existing built-in widget handling -->
  {:else if pluginRegistry.has(widget.type)}
      <PluginWidget plugin={pluginRegistry.get(widget.type)} {widget} {data} {sessionId} />
  {:else}
      <div class="unknown-widget">Unknown widget type: {widget.type}</div>
  {/if}
  ```

---

### 4.2 SDK Plugin Support

#### 4.2.1 Custom Widget DSL

- [ ] **Add customWidget() to builders**

  ```kotlin
  // In RowBuilder or SectionBuilder
  fun customWidget(
      type: String,
      config: JsonObject
  )

  // Or with builder
  fun customWidget(
      type: String,
      block: CustomWidgetBuilder.() -> Unit
  )

  class CustomWidgetBuilder {
      private val config = mutableMapOf<String, Any?>()

      fun set(key: String, value: Any?)
      operator fun String.invoke(value: Any?) = set(this, value)

      internal fun build(): JsonObject
  }
  ```

- [ ] **Usage example**

  ```kotlin
  section("Custom") {
      customWidget("my-chart") {
          "dataPath" to "$.metrics.history"
          "color" to "#ff0000"
          "animated" to true
      }
  }
  ```

- [ ] **Write unit tests for custom widget DSL**

---

### 4.3 Example Plugins

#### 4.3.1 Histogram Plugin

- [ ] **Create Histogram widget plugin**

  `dashboard/src/lib/plugins/examples/histogram/`

  Features:
  - Bar chart showing distribution
  - Configurable bucket count
  - Hover to see bucket range and count

- [ ] **Document Histogram plugin usage**

#### 4.3.2 Network Request Logger Plugin

- [ ] **Create NetworkLog widget plugin**

  Displays HTTP requests with:
  - Method, URL, status code
  - Request/response time
  - Expandable request/response bodies
  - Filter by status code, method, URL pattern

- [ ] **Document NetworkLog plugin usage**

#### 4.3.3 Markdown Widget Plugin

- [ ] **Create Markdown widget plugin**

  Renders markdown content from data path. Useful for:
  - Release notes
  - Help text
  - Dynamic documentation

- [ ] **Document Markdown plugin usage**

---

### 4.4 Plugin Documentation

- [ ] **Write plugin development guide**

  Topics:
  - Plugin structure
  - Available context APIs
  - Styling guidelines
  - Testing plugins
  - Publishing plugins

- [ ] **Create plugin template/starter**

  Scaffold for new plugin development with:
  - TypeScript setup
  - Example component
  - Test setup
  - Build configuration

- [ ] **Add plugin showcase section to README**

---

## Testing Checklist

### Phase 2 Tests

- [ ] All new widget components have unit tests
- [ ] All new DSL builders have unit tests
- [ ] E2E tests cover new widgets rendering with mock data
- [ ] Visual regression tests for new widgets

### Phase 3 Tests

- [ ] Export functionality tested (JSON structure, download)
- [ ] Theme switching tested (persistence, all components)
- [ ] Performance benchmarks documented
- [ ] Virtual scrolling tested with 10k+ logs

### Phase 4 Tests

- [ ] Plugin registration/unregistration tested
- [ ] Plugin error boundary tested
- [ ] Plugin context API tested
- [ ] Example plugins have test coverage

---

## Checklist Summary

| Phase | Section | Tasks | Status |
|-------|---------|-------|--------|
| 2.1 | Chart Widget | 12 | Not Started |
| 2.2 | Sparkline Widget | 6 | Not Started |
| 2.3 | JSON Viewer Widget | 6 | Not Started |
| 2.4 | Key-Value Widget | 6 | Not Started |
| 2.5 | Status Indicator Widget | 6 | Not Started |
| 2.6 | Progress Widget | 6 | Not Started |
| 2.7 | Update Union Types | 2 | Not Started |
| 2.8 | Documentation Updates | 3 | Not Started |
| 3.1 | Export Session Data | 6 | Not Started |
| 3.2 | Dashboard Themes | 9 | Not Started |
| 3.3 | Performance Optimizations | 7 | Not Started |
| 4.1 | Plugin Architecture | 6 | Not Started |
| 4.2 | SDK Plugin Support | 3 | Not Started |
| 4.3 | Example Plugins | 6 | Not Started |
| 4.4 | Plugin Documentation | 3 | Not Started |
| - | Testing Checklist | 12 | Not Started |
| **Total** | | **99** | **Not Started** |

---

*Created: 2025-12-06*
