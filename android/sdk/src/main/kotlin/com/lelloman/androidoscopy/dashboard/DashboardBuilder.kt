package com.lelloman.androidoscopy.dashboard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class DashboardBuilder {
    private val sections = mutableListOf<JsonElement>()

    fun section(title: String, block: SectionBuilder.() -> Unit) {
        val builder = SectionBuilder(title)
        builder.block()
        sections.add(builder.build())
    }

    fun memorySection(includeActions: Boolean = false) {
        section("Memory") {
            layout = Layout.ROW
            row {
                gauge(
                    label = "Heap Usage",
                    valuePath = "\$.memory.heap_used_bytes",
                    maxPath = "\$.memory.heap_max_bytes",
                    format = Format.BYTES
                )
                badge(
                    label = "Pressure",
                    dataPath = "\$.memory.pressure_level",
                    variants = mapOf(
                        "LOW" to BadgeStyle.SUCCESS,
                        "MODERATE" to BadgeStyle.WARNING,
                        "HIGH" to BadgeStyle.DANGER,
                        "CRITICAL" to BadgeStyle.DANGER
                    )
                )
            }
            if (includeActions) {
                actions {
                    button(
                        label = "Force GC",
                        action = "force_gc",
                        style = ButtonStyle.SECONDARY
                    )
                    button(
                        label = "Clear Cache",
                        action = "clear_cache",
                        style = ButtonStyle.SECONDARY
                    )
                }
            }
        }
    }

    fun batterySection() {
        section("Battery") {
            layout = Layout.ROW
            row {
                gauge(
                    label = "Battery Level",
                    valuePath = "\$.battery.level",
                    maxPath = "100",
                    format = Format.PERCENT
                )
                badge(
                    label = "Status",
                    dataPath = "\$.battery.status",
                    variants = mapOf(
                        "CHARGING" to BadgeStyle.SUCCESS,
                        "FULL" to BadgeStyle.SUCCESS,
                        "DISCHARGING" to BadgeStyle.WARNING,
                        "NOT_CHARGING" to BadgeStyle.MUTED,
                        "UNKNOWN" to BadgeStyle.MUTED
                    )
                )
                number(
                    label = "Temperature",
                    dataPath = "\$.battery.temperature",
                    format = Format.NUMBER
                )
            }
        }
    }

    fun storageSection() {
        section("Storage") {
            layout = Layout.ROW
            row {
                gauge(
                    label = "Internal Storage",
                    valuePath = "\$.storage.internal_used_bytes",
                    maxPath = "\$.storage.internal_total_bytes",
                    format = Format.BYTES
                )
                bytes(
                    label = "App Data",
                    dataPath = "\$.storage.app_data_bytes"
                )
                bytes(
                    label = "Cache",
                    dataPath = "\$.storage.cache_bytes"
                )
            }
        }
    }

    fun threadSection() {
        section("Threads") {
            layout = Layout.ROW
            row {
                number(
                    label = "Active",
                    dataPath = "\$.threads.active_count"
                )
                number(
                    label = "Total",
                    dataPath = "\$.threads.total_count"
                )
            }
        }
    }

    fun networkSection() {
        section("Network") {
            layout = Layout.ROW
            row {
                badge(
                    label = "Connection",
                    dataPath = "\$.network.connection_type",
                    variants = mapOf(
                        "WIFI" to BadgeStyle.SUCCESS,
                        "CELLULAR" to BadgeStyle.INFO,
                        "ETHERNET" to BadgeStyle.SUCCESS,
                        "NONE" to BadgeStyle.DANGER,
                        "OTHER" to BadgeStyle.MUTED
                    )
                )
                badge(
                    label = "Status",
                    dataPath = "\$.network.is_connected",
                    variants = mapOf(
                        "true" to BadgeStyle.SUCCESS,
                        "false" to BadgeStyle.DANGER
                    )
                )
                number(
                    label = "Signal",
                    dataPath = "\$.network.wifi_signal_level"
                )
            }
        }
    }

    fun logsSection() {
        section("Logs") {
            layout = Layout.STACK
            fullWidth = true
            widget = WidgetBuilder.logViewer(defaultLevel = "DEBUG")
        }
    }

    // ============ Preset Dashboards ============
    // These convenience functions expand to multiple sections for common use cases

    /**
     * Performance monitoring preset.
     * Includes: Memory usage with alerts, thread count, and optionally memory charts.
     */
    fun performancePreset(includeCharts: Boolean = true) {
        section("Memory") {
            layout = Layout.ROW
            row {
                gauge(
                    label = "Heap Usage",
                    valuePath = "\$.memory.heap_used_bytes",
                    maxPath = "\$.memory.heap_max_bytes",
                    format = Format.BYTES,
                    alert = AlertConfig(
                        condition = AlertCondition.gte("\$.memory.heap_used_ratio", 0.9),
                        severity = AlertSeverity.WARNING,
                        message = "High memory usage (>90%)"
                    )
                )
                badge(
                    label = "Pressure",
                    dataPath = "\$.memory.pressure_level",
                    variants = mapOf(
                        "LOW" to BadgeStyle.SUCCESS,
                        "MODERATE" to BadgeStyle.WARNING,
                        "HIGH" to BadgeStyle.DANGER,
                        "CRITICAL" to BadgeStyle.DANGER
                    ),
                    alert = AlertConfig(
                        condition = AlertCondition.eq("\$.memory.pressure_level", "CRITICAL"),
                        severity = AlertSeverity.CRITICAL,
                        message = "Critical memory pressure!"
                    )
                )
                bytes(label = "Native Heap", dataPath = "\$.memory.native_heap_bytes")
            }
            if (includeCharts) {
                row {
                    chart(
                        label = "Heap Over Time",
                        dataPath = "\$.memory.heap_used_bytes",
                        format = Format.BYTES,
                        maxPoints = 120,
                        color = "#3b82f6"
                    )
                }
            }
        }
        threadSection()
    }

    /**
     * Device status preset.
     * Includes: Battery status, network connectivity, and storage usage.
     */
    fun deviceStatusPreset() {
        batterySection()
        networkSection()
        storageSection()
    }

    /**
     * Debugging preset.
     * Includes: Logs viewer and memory info for debugging sessions.
     */
    fun debuggingPreset(logLevel: String = "DEBUG") {
        section("Logs") {
            layout = Layout.STACK
            fullWidth = true
            widget = WidgetBuilder.logViewer(defaultLevel = logLevel)
        }
        memorySection(includeActions = true)
    }

    /**
     * Minimal preset.
     * Includes: Just essential metrics - memory gauge and network status.
     */
    fun minimalPreset() {
        section("Status") {
            layout = Layout.ROW
            row {
                gauge(
                    label = "Memory",
                    valuePath = "\$.memory.heap_used_bytes",
                    maxPath = "\$.memory.heap_max_bytes",
                    format = Format.BYTES
                )
                badge(
                    label = "Network",
                    dataPath = "\$.network.is_connected",
                    variants = mapOf(
                        "true" to BadgeStyle.SUCCESS,
                        "false" to BadgeStyle.DANGER
                    )
                )
            }
        }
    }

    /**
     * Complete system overview preset.
     * Includes all available system metrics in organized sections.
     */
    fun fullSystemPreset() {
        memorySection(includeActions = false)
        threadSection()
        batterySection()
        networkSection()
        storageSection()
    }

    /**
     * ANR (Application Not Responding) detection section.
     * Displays ANR history with expandable stack traces.
     * Requires: enableAnrDetection() in config
     */
    fun anrSection() {
        section("ANR Detection") {
            layout = Layout.STACK
            fullWidth = true
            collapsible = true

            row {
                number(
                    label = "ANR Count",
                    dataPath = "\$.anr.count",
                    alert = AlertConfig(
                        condition = AlertCondition.gt("\$.anr.count", 0),
                        severity = AlertSeverity.WARNING,
                        message = "ANR detected!"
                    )
                )
                text(
                    label = "Latest ANR",
                    dataPath = "\$.anr.latest.timestamp"
                )
            }

            table(dataPath = "\$.anr.history") {
                column("timestamp", "Time")
                column("duration_ms", "Duration (ms)", Format.NUMBER)
                column("thread_count", "Threads", Format.NUMBER)
                rowAction("view_trace", "View Trace")
            }
        }
    }

    /**
     * Network requests section for HTTP interceptor.
     * Shows request history with filtering, stats and expandable details.
     * Requires: AndroidoscopyInterceptor added to OkHttpClient and its dataProvider registered.
     */
    fun networkRequestsSection() {
        section("Network Requests") {
            layout = Layout.STACK
            fullWidth = true
            collapsible = true
            networkRequestViewer(dataPath = "\$.network.requests")
        }
    }

    /**
     * SharedPreferences viewer section.
     * Displays all SharedPreferences entries with file selection, edit/delete actions.
     * Requires: SharedPreferencesDataProvider registered with action handlers.
     *
     * @param prefsName Optional specific SharedPreferences file to show. If null, shows all.
     */
    fun sharedPreferencesSection(prefsName: String? = null) {
        val dataKey = if (prefsName != null) "prefs_$prefsName" else "prefs"
        section("SharedPreferences" + (prefsName?.let { " ($it)" } ?: "")) {
            layout = Layout.STACK
            fullWidth = true
            collapsible = true

            sharedPreferencesViewer(dataPath = "\$.$dataKey")
        }
    }

    /**
     * SQLite browser section.
     * Displays database tables with schema view, data browsing, and query execution.
     * Requires: SqliteDataProvider registered with action handlers.
     *
     * @param dbName Optional specific database to show. If null, shows all databases.
     */
    fun sqliteSection(dbName: String? = null) {
        val dataKey = if (dbName != null) "sqlite_$dbName" else "sqlite"
        section("SQLite" + (dbName?.let { " ($it)" } ?: "")) {
            layout = Layout.STACK
            fullWidth = true
            collapsible = true

            sqliteViewer(dataPath = "\$.$dataKey")
        }
    }

    /**
     * Permissions section.
     * Displays declared app permissions grouped by grant status as chip cards.
     * Requires: PermissionsDataProvider registered.
     */
    fun permissionsSection() {
        section("Permissions") {
            layout = Layout.STACK
            fullWidth = true
            collapsible = true

            permissionsViewer(dataPath = "\$.permissions")
        }
    }

    /**
     * Build info section.
     * Displays static build information like app name, version, SDK levels.
     * Requires: BuildInfoDataProvider registered.
     */
    fun buildInfoSection() {
        section("Build Info") {
            layout = Layout.FLOW
            fullWidth = true
            collapsible = true
            collapsedDefault = true

            row {
                text(label = "App Name", dataPath = "\$.build_info.app_name")
                text(label = "Package", dataPath = "\$.build_info.package_name")
                text(label = "Version", dataPath = "\$.build_info.version_name")
                number(label = "Version Code", dataPath = "\$.build_info.version_code")
                text(label = "Build Type", dataPath = "\$.build_info.build_type")
                text(label = "Flavor", dataPath = "\$.build_info.flavor")
                number(label = "Min SDK", dataPath = "\$.build_info.min_sdk")
                number(label = "Target SDK", dataPath = "\$.build_info.target_sdk")
                number(label = "API Level", dataPath = "\$.build_info.api_level")
                text(label = "Device", dataPath = "\$.build_info.device_model")
                text(label = "Manufacturer", dataPath = "\$.build_info.device_manufacturer")
                text(label = "Android", dataPath = "\$.build_info.android_version")
                text(label = "Git SHA", dataPath = "\$.build_info.git_sha")
            }
        }
    }

    /**
     * Memory leaks section for LeakCanary integration.
     * Displays detected leaks with stack traces.
     * Requires: LeakDataProvider registered from leakcanary module.
     */
    fun leaksSection() {
        section("Memory Leaks") {
            layout = Layout.STACK
            fullWidth = true
            collapsible = true

            row {
                number(
                    label = "Leak Count",
                    dataPath = "\$.leaks.leak_count",
                    alert = AlertConfig(
                        condition = AlertCondition.gt("\$.leaks.leak_count", 0),
                        severity = AlertSeverity.WARNING,
                        message = "Memory leaks detected!"
                    )
                )
                text(
                    label = "Latest Leak",
                    dataPath = "\$.leaks.latest.short_description"
                )
            }

            table(dataPath = "\$.leaks.leaks") {
                column("timestamp", "Time")
                column("leaking_object_class", "Class")
                column("retained_count", "Count", Format.NUMBER)
                column("retained_heap_bytes", "Size", Format.BYTES)
                rowAction("view_trace", "View Trace")
            }
        }
    }

    /**
     * WorkManager section.
     * Displays scheduled/running workers with status and cancel actions.
     * Requires: WorkManagerDataProvider registered from workmanager module.
     */
    fun workManagerSection() {
        section("WorkManager") {
            layout = Layout.STACK
            fullWidth = true
            collapsible = true

            row {
                number(
                    label = "Total",
                    dataPath = "\$.workmanager.worker_count"
                )
                number(
                    label = "Running",
                    dataPath = "\$.workmanager.running_count"
                )
                number(
                    label = "Enqueued",
                    dataPath = "\$.workmanager.enqueued_count"
                )
                number(
                    label = "Failed",
                    dataPath = "\$.workmanager.failed_count",
                    alert = AlertConfig(
                        condition = AlertCondition.gt("\$.workmanager.failed_count", 0),
                        severity = AlertSeverity.WARNING,
                        message = "Failed workers detected"
                    )
                )
            }

            actions {
                button(
                    label = "Refresh",
                    action = "workmanager_refresh",
                    style = ButtonStyle.SECONDARY
                )
                button(
                    label = "Cancel All",
                    action = "workmanager_cancel_all",
                    style = ButtonStyle.DANGER
                )
            }

            table(dataPath = "\$.workmanager.workers") {
                column("tags_str", "Tags")
                column("state", "State")
                column("attempt_count", "Attempts", Format.NUMBER)
                column("stop_reason", "Stop Reason")
                rowAction("workmanager_cancel", "Cancel")
            }
        }
    }

    /**
     * Coil image cache section.
     * Displays memory and disk cache stats with clear actions.
     * Requires: CoilDataProvider registered from coil module.
     */
    fun coilSection() {
        section("Image Cache (Coil)") {
            layout = Layout.STACK
            collapsible = true

            row {
                gauge(
                    label = "Memory Cache",
                    valuePath = "\$.coil.memory_cache.size",
                    maxPath = "\$.coil.memory_cache.max_size",
                    format = Format.BYTES
                )
                gauge(
                    label = "Disk Cache",
                    valuePath = "\$.coil.disk_cache.size",
                    maxPath = "\$.coil.disk_cache.max_size",
                    format = Format.BYTES
                )
            }

            row {
                text(label = "Memory", dataPath = "\$.coil.memory_size_mb")
                text(label = "Max", dataPath = "\$.coil.memory_max_mb")
                percent(label = "Usage", dataPath = "\$.coil.memory_percent")
            }

            row {
                text(label = "Disk", dataPath = "\$.coil.disk_size_mb")
                text(label = "Max", dataPath = "\$.coil.disk_max_mb")
                percent(label = "Usage", dataPath = "\$.coil.disk_percent")
            }

            actions {
                button(
                    label = "Refresh",
                    action = "coil_refresh",
                    style = ButtonStyle.SECONDARY
                )
                button(
                    label = "Clear Memory",
                    action = "coil_clear_memory",
                    style = ButtonStyle.SECONDARY
                )
                button(
                    label = "Clear Disk",
                    action = "coil_clear_disk",
                    style = ButtonStyle.SECONDARY
                )
                button(
                    label = "Clear All",
                    action = "coil_clear_all",
                    style = ButtonStyle.DANGER
                )
            }
        }
    }

    fun cacheSection(caches: List<CacheConfig>) {
        section("Caches") {
            layout = Layout.STACK
            table(dataPath = "\$.caches") {
                column("name", "Name")
                column("size", "Size", Format.NUMBER)
                column("hit_rate", "Hit Rate", Format.PERCENT)
                caches.forEach { cache ->
                    rowAction("clear_${cache.id}", "Clear") {
                        put("cache_id", cache.id)
                    }
                }
            }
        }
    }

    fun build(): JsonElement = buildJsonObject {
        put("sections", JsonArray(sections))
    }
}

data class CacheConfig(
    val id: String,
    val name: String
)

class SectionBuilder(private val title: String) {
    var layout: Layout = Layout.ROW
    var collapsible: Boolean = false
    var collapsedDefault: Boolean = false
    var columns: Int? = null
    var widget: JsonElement? = null
    /** When true, this section spans the full width in flow layout */
    var fullWidth: Boolean = false

    private val widgets = mutableListOf<JsonElement>()

    fun row(block: RowBuilder.() -> Unit) {
        val builder = RowBuilder()
        builder.block()
        widgets.addAll(builder.build())
    }

    fun table(dataPath: String, block: TableBuilder.() -> Unit) {
        val builder = TableBuilder(dataPath)
        builder.block()
        widgets.add(builder.build())
    }

    fun actions(block: ActionsBuilder.() -> Unit) {
        val builder = ActionsBuilder()
        builder.block()
        widgets.addAll(builder.build())
    }

    fun networkRequestViewer(dataPath: String) {
        widgets.add(WidgetBuilder.networkRequestViewer(dataPath))
    }

    fun sharedPreferencesViewer(dataPath: String) {
        widgets.add(WidgetBuilder.sharedPreferencesViewer(dataPath))
    }

    fun sqliteViewer(dataPath: String) {
        widgets.add(WidgetBuilder.sqliteViewer(dataPath))
    }

    fun permissionsViewer(dataPath: String) {
        widgets.add(WidgetBuilder.permissionsViewer(dataPath))
    }

    fun build(): JsonElement = buildJsonObject {
        put("id", JsonPrimitive(title.lowercase().replace(" ", "_")))
        put("title", JsonPrimitive(title))
        put("layout", JsonPrimitive(layout.value))
        if (collapsible) {
            put("collapsible", JsonPrimitive(true))
            put("collapsed_default", JsonPrimitive(collapsedDefault))
        }
        if (fullWidth) {
            put("full_width", JsonPrimitive(true))
        }
        columns?.let { put("columns", JsonPrimitive(it)) }

        if (widget != null) {
            put("widget", widget!!)
        } else if (widgets.isNotEmpty()) {
            put("widgets", JsonArray(widgets))
        }
    }
}

enum class Layout(val value: String) {
    ROW("row"),
    GRID("grid"),
    STACK("stack"),
    FLOW("flow")
}

enum class Format(val value: String) {
    NUMBER("number"),
    BYTES("bytes"),
    PERCENT("percent"),
    TEXT("text"),
    DURATION("duration")
}

enum class BadgeStyle(val value: String) {
    SUCCESS("success"),
    WARNING("warning"),
    DANGER("danger"),
    INFO("info"),
    MUTED("muted")
}

enum class AlertSeverity(val value: String) {
    INFO("info"),
    WARNING("warning"),
    CRITICAL("critical")
}

data class AlertConfig(
    val condition: AlertCondition,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val message: String = "Alert triggered"
)

data class AlertCondition(
    val path: String,
    val operator: String,
    val value: Any? = null
) {
    companion object {
        fun gt(path: String, value: Number) = AlertCondition(path, "gt", value)
        fun gte(path: String, value: Number) = AlertCondition(path, "gte", value)
        fun lt(path: String, value: Number) = AlertCondition(path, "lt", value)
        fun lte(path: String, value: Number) = AlertCondition(path, "lte", value)
        fun eq(path: String, value: Any) = AlertCondition(path, "eq", value)
        fun neq(path: String, value: Any) = AlertCondition(path, "neq", value)
    }
}

class RowBuilder {
    private val widgets = mutableListOf<JsonElement>()

    fun number(
        label: String,
        dataPath: String,
        format: Format = Format.NUMBER,
        alert: AlertConfig? = null
    ) {
        widgets.add(WidgetBuilder.number(label, dataPath, format, alert))
    }

    fun text(label: String, dataPath: String, alert: AlertConfig? = null) {
        widgets.add(WidgetBuilder.text(label, dataPath, alert))
    }

    fun bytes(label: String, dataPath: String, alert: AlertConfig? = null) {
        widgets.add(WidgetBuilder.number(label, dataPath, Format.BYTES, alert))
    }

    fun percent(label: String, dataPath: String, alert: AlertConfig? = null) {
        widgets.add(WidgetBuilder.number(label, dataPath, Format.PERCENT, alert))
    }

    fun gauge(
        label: String,
        valuePath: String,
        maxPath: String,
        format: Format = Format.NUMBER,
        alert: AlertConfig? = null
    ) {
        widgets.add(WidgetBuilder.gauge(label, valuePath, maxPath, format, alert))
    }

    fun badge(
        label: String,
        dataPath: String,
        variants: Map<String, BadgeStyle>,
        alert: AlertConfig? = null
    ) {
        widgets.add(WidgetBuilder.badge(label, dataPath, variants, alert))
    }

    fun chart(
        label: String,
        dataPath: String,
        format: Format = Format.NUMBER,
        maxPoints: Int = 60,
        color: String? = null,
        alert: AlertConfig? = null
    ) {
        widgets.add(WidgetBuilder.chart(label, dataPath, format, maxPoints, color, alert))
    }

    fun build(): List<JsonElement> = widgets
}

class TableBuilder(private val dataPath: String) {
    private val columns = mutableListOf<JsonElement>()
    private val rowActions = mutableListOf<JsonElement>()

    fun column(key: String, label: String, format: Format = Format.TEXT) {
        columns.add(buildJsonObject {
            put("key", JsonPrimitive(key))
            put("label", JsonPrimitive(label))
            put("format", JsonPrimitive(format.value))
        })
    }

    fun rowAction(id: String, label: String, args: MutableMap<String, String>.() -> Unit = {}) {
        val argsMap = mutableMapOf<String, String>()
        argsMap.args()
        rowActions.add(buildJsonObject {
            put("id", JsonPrimitive(id))
            put("label", JsonPrimitive(label))
            if (argsMap.isNotEmpty()) {
                put("args", buildJsonObject {
                    argsMap.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                })
            }
        })
    }

    fun build(): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("table"))
        put("data_path", JsonPrimitive(dataPath))
        put("columns", JsonArray(columns))
        if (rowActions.isNotEmpty()) {
            put("row_actions", JsonArray(rowActions))
        }
    }
}

class ActionsBuilder {
    private val buttons = mutableListOf<JsonElement>()

    fun button(
        label: String,
        action: String,
        style: ButtonStyle = ButtonStyle.PRIMARY,
        resultDisplay: ResultDisplay = ResultDisplay.Toast
    ) {
        buttons.add(buildJsonObject {
            put("type", JsonPrimitive("button"))
            put("label", JsonPrimitive(label))
            put("action", JsonPrimitive(action))
            put("style", JsonPrimitive(style.value))
            put("result_display", buildJsonObject {
                put("type", JsonPrimitive(resultDisplay.type))
            })
        })
    }

    fun button(
        label: String,
        action: String,
        style: ButtonStyle = ButtonStyle.PRIMARY,
        argsDialog: ArgsDialogBuilder.() -> Unit
    ) {
        val dialogBuilder = ArgsDialogBuilder()
        dialogBuilder.argsDialog()
        buttons.add(buildJsonObject {
            put("type", JsonPrimitive("button"))
            put("label", JsonPrimitive(label))
            put("action", JsonPrimitive(action))
            put("style", JsonPrimitive(style.value))
            put("args_dialog", dialogBuilder.build())
        })
    }

    fun build(): List<JsonElement> = buttons
}

enum class ButtonStyle(val value: String) {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    DANGER("danger")
}

sealed class ResultDisplay(val type: String) {
    data object Toast : ResultDisplay("toast")
    data object Inline : ResultDisplay("inline")
    data object None : ResultDisplay("none")
}

class ArgsDialogBuilder {
    var title: String = ""
    private val fields = mutableListOf<JsonElement>()

    fun textField(key: String, label: String, default: String = "") {
        fields.add(buildJsonObject {
            put("type", JsonPrimitive("text"))
            put("key", JsonPrimitive(key))
            put("label", JsonPrimitive(label))
            put("default", JsonPrimitive(default))
        })
    }

    fun numberField(key: String, label: String, default: Int = 0, min: Int? = null, max: Int? = null) {
        fields.add(buildJsonObject {
            put("type", JsonPrimitive("number"))
            put("key", JsonPrimitive(key))
            put("label", JsonPrimitive(label))
            put("default", JsonPrimitive(default))
            min?.let { put("min", JsonPrimitive(it)) }
            max?.let { put("max", JsonPrimitive(it)) }
        })
    }

    fun selectField(key: String, label: String, options: List<SelectOption>) {
        fields.add(buildJsonObject {
            put("type", JsonPrimitive("select"))
            put("key", JsonPrimitive(key))
            put("label", JsonPrimitive(label))
            put("options", buildJsonArray {
                options.forEach { option ->
                    add(buildJsonObject {
                        put("value", JsonPrimitive(option.value))
                        put("label", JsonPrimitive(option.label))
                    })
                }
            })
        })
    }

    fun checkboxField(key: String, label: String, default: Boolean = false) {
        fields.add(buildJsonObject {
            put("type", JsonPrimitive("checkbox"))
            put("key", JsonPrimitive(key))
            put("label", JsonPrimitive(label))
            put("default", JsonPrimitive(default))
        })
    }

    fun build(): JsonElement = buildJsonObject {
        put("title", JsonPrimitive(title))
        put("fields", JsonArray(fields))
    }
}

data class SelectOption(val value: String, val label: String)

object WidgetBuilder {
    private fun JsonObjectBuilder.addAlert(alert: AlertConfig?) {
        alert?.let {
            put("alert", buildJsonObject {
                put("condition", buildJsonObject {
                    put("path", JsonPrimitive(it.condition.path))
                    put("operator", JsonPrimitive(it.condition.operator))
                    it.condition.value?.let { v ->
                        when (v) {
                            is Number -> put("value", JsonPrimitive(v))
                            is Boolean -> put("value", JsonPrimitive(v))
                            else -> put("value", JsonPrimitive(v.toString()))
                        }
                    }
                })
                put("severity", JsonPrimitive(it.severity.value))
                put("message", JsonPrimitive(it.message))
            })
        }
    }

    fun number(label: String, dataPath: String, format: Format, alert: AlertConfig? = null): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("number"))
        put("label", JsonPrimitive(label))
        put("data_path", JsonPrimitive(dataPath))
        put("format", JsonPrimitive(format.value))
        addAlert(alert)
    }

    fun text(label: String, dataPath: String, alert: AlertConfig? = null): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("text"))
        put("label", JsonPrimitive(label))
        put("data_path", JsonPrimitive(dataPath))
        addAlert(alert)
    }

    fun gauge(label: String, valuePath: String, maxPath: String, format: Format, alert: AlertConfig? = null): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("gauge"))
        put("label", JsonPrimitive(label))
        put("value_path", JsonPrimitive(valuePath))
        put("max_path", JsonPrimitive(maxPath))
        put("format", JsonPrimitive(format.value))
        addAlert(alert)
    }

    fun badge(label: String, dataPath: String, variants: Map<String, BadgeStyle>, alert: AlertConfig? = null): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("badge"))
        put("label", JsonPrimitive(label))
        put("data_path", JsonPrimitive(dataPath))
        put("variants", buildJsonObject {
            variants.forEach { (k, v) -> put(k, JsonPrimitive(v.value)) }
        })
        addAlert(alert)
    }

    fun logViewer(defaultLevel: String = "DEBUG"): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("log_viewer"))
        put("default_level", JsonPrimitive(defaultLevel))
    }

    fun networkRequestViewer(dataPath: String): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("network_request_viewer"))
        put("data_path", JsonPrimitive(dataPath))
    }

    fun sharedPreferencesViewer(dataPath: String): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("shared_preferences_viewer"))
        put("data_path", JsonPrimitive(dataPath))
    }

    fun sqliteViewer(dataPath: String): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("sqlite_viewer"))
        put("data_path", JsonPrimitive(dataPath))
    }

    fun permissionsViewer(dataPath: String): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("permissions_viewer"))
        put("data_path", JsonPrimitive(dataPath))
    }

    fun chart(
        label: String,
        dataPath: String,
        format: Format,
        maxPoints: Int,
        color: String?,
        alert: AlertConfig? = null
    ): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("chart"))
        put("label", JsonPrimitive(label))
        put("data_path", JsonPrimitive(dataPath))
        put("format", JsonPrimitive(format.value))
        put("max_points", JsonPrimitive(maxPoints))
        color?.let { put("color", JsonPrimitive(it)) }
        addAlert(alert)
    }
}
