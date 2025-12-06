# Androidoscopy Roadmap

## General Features

- [x] **Logs export**
  - Export button in LogViewer widget
  - Format options: Plain text, JSON, CSV, Logcat format
  - Exports currently buffered logs (respects active filters)
  - Increase default log ring buffer from 5,000 to 50,000 entries
- [x] **Multi-machine support** (physical device on same LAN)
  - Auto-detect local network IPs and include in TLS certificate SANs
  - bind_address already configurable (set to 0.0.0.0)
  - UDP discovery already broadcasts for device finding
  - No auth needed (LAN-only is acceptable security model)
- [x] **Alert system**
  - Defined in Kotlin DSL (app-side, since no dashboard persistence)
  - Widget-level alert config: threshold condition, severity, message
  - Dashboard displays: visual highlight (red border/glow) + toast notification
  - Severity levels: INFO, WARNING, CRITICAL
  - Example DSL:
    ```kotlin
    numberWidget("heap_used") {
        alert {
            condition { it > 0.9 }
            severity = WARNING
            message = "High memory!"
        }
    }
    ```

## Dashboard

- [x] **Full-screen widget mode**
  - Expand icon in widget header (LogViewer, Chart, Table only)
  - Modal overlay fills viewport
  - Close via ESC key or click outside
  - Dashboard-only feature (no protocol changes)
- [x] **Chart improvements**
  - Zoom (mouse wheel on time axis) & pan (click-drag), reset button
  - Export: PNG image, CSV raw data
  - Visual: tooltip on hover, grid lines, min/max/avg annotations, time window selector
- [x] **Preset dashboards**
  - SDK convenience functions that expand to multiple sections
  - Examples: `performancePreset()` (memory + threads), `networkPreset()` (stats + requests)
  - Just Kotlin DSL helpers, no new widget type needed

## Android SDK

- [x] **ANR detection**
  - Watchdog thread pattern: post to main, detect if response >4s
  - Report: timestamp, main thread stack trace, all threads (for deadlocks)
  - Dashboard: `anrSection()` with ANR history and expandable stack traces
  - Config: `enableAnrDetection(thresholdMs = 4000)` in init
- [x] **HTTP interceptor**
  - `AndroidoscopyInterceptor` for OkHttp (user adds to client builder)
  - Captures: method, URL, headers, status, duration, body size, errors
  - Dashboard: `networkRequestsSection()` - table with row action for details
  - Separate module (depends on OkHttp)
- [x] **SharedPreferences viewer**
  - List all SharedPreferences files, show key-value pairs with types
  - Edit: modify values, add new keys, delete keys (type-aware inputs)
  - Dashboard: `sharedPreferencesSection()` or `sharedPreferencesSection("name")`
  - Table with key/value/type columns, row actions for edit/delete
  - Refresh button in section header
- [x] **SQLite browser**
  - List all databases, tables, show schema (columns, types, constraints)
  - Browse: paginated table view, sort by column
  - Raw SQL: input field, run any query (SELECT, INSERT, UPDATE, DELETE)
  - Edit: row actions for edit/delete, insert new row
  - Dashboard: `sqliteSection()` or `sqliteSection("db_name.db")`
  - UI: database/table dropdowns, schema view, query input, results table
- [x] **Permission checker**
  - List declared permissions with grant status (granted/denied badge)
  - Show dangerous vs normal distinction
  - Refresh: on app resume (lifecycle-aware) + manual refresh button
  - Dashboard: `permissionsSection()` - table with permission name, status, type
- [x] **Build info section**
  - Show: app/package name, version name/code, build type, flavor, min/target SDK
  - Optional git SHA (user provides via `gitSha = BuildConfig.GIT_SHA`)
  - Dashboard: `buildInfoSection()` - static key-value display
  - Data from BuildConfig + PackageManager

## Integration Modules (separate modules)

- [x] **Timber integration**
  - Separate module: `androidoscopy-timber`
  - `AndroidoscopyTree` extends `Timber.Tree`, pipes to `Androidoscopy.log()`
  - Usage: `Timber.plant(AndroidoscopyTree())`
  - Maps Timber priority to Androidoscopy log levels
- [x] **LeakCanary integration**
  - Separate module: `androidoscopy-leakcanary`
  - `AndroidoscopyLeakListener` implements `OnHeapAnalyzedListener`
  - Reports: leak signature, trace, retained count, timestamp
  - Dashboard: `leaksSection()` - list with expandable trace details
- [ ] **WorkManager viewer**
  - Separate module: `androidoscopy-workmanager`
  - Reactive via `WorkManager.getWorkInfosFlow()` - no polling
  - Shows: worker class, state badge, tags, constraints, attempt count, next run time
  - Dashboard: `workManagerSection()` - table with live updates
  - Row action: cancel work
- [ ] **Coil plugin** - Image cache stats and viewer
