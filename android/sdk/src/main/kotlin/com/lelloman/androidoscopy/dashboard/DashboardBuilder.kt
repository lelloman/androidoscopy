package com.lelloman.androidoscopy.dashboard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

    fun memorySection() {
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

    fun logsSection() {
        section("Logs") {
            layout = Layout.STACK
            widget = WidgetBuilder.logViewer(defaultLevel = "DEBUG")
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

    fun build(): JsonElement = buildJsonObject {
        put("id", JsonPrimitive(title.lowercase().replace(" ", "_")))
        put("title", JsonPrimitive(title))
        put("layout", JsonPrimitive(layout.value))
        if (collapsible) {
            put("collapsible", JsonPrimitive(true))
            put("collapsed_default", JsonPrimitive(collapsedDefault))
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
    STACK("stack")
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

class RowBuilder {
    private val widgets = mutableListOf<JsonElement>()

    fun number(label: String, dataPath: String, format: Format = Format.NUMBER) {
        widgets.add(WidgetBuilder.number(label, dataPath, format))
    }

    fun text(label: String, dataPath: String) {
        widgets.add(WidgetBuilder.text(label, dataPath))
    }

    fun bytes(label: String, dataPath: String) {
        widgets.add(WidgetBuilder.number(label, dataPath, Format.BYTES))
    }

    fun percent(label: String, dataPath: String) {
        widgets.add(WidgetBuilder.number(label, dataPath, Format.PERCENT))
    }

    fun gauge(label: String, valuePath: String, maxPath: String, format: Format = Format.NUMBER) {
        widgets.add(WidgetBuilder.gauge(label, valuePath, maxPath, format))
    }

    fun badge(label: String, dataPath: String, variants: Map<String, BadgeStyle>) {
        widgets.add(WidgetBuilder.badge(label, dataPath, variants))
    }

    fun chart(
        label: String,
        dataPath: String,
        format: Format = Format.NUMBER,
        maxPoints: Int = 60,
        color: String? = null
    ) {
        widgets.add(WidgetBuilder.chart(label, dataPath, format, maxPoints, color))
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
    fun number(label: String, dataPath: String, format: Format): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("number"))
        put("label", JsonPrimitive(label))
        put("data_path", JsonPrimitive(dataPath))
        put("format", JsonPrimitive(format.value))
    }

    fun text(label: String, dataPath: String): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("text"))
        put("label", JsonPrimitive(label))
        put("data_path", JsonPrimitive(dataPath))
    }

    fun gauge(label: String, valuePath: String, maxPath: String, format: Format): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("gauge"))
        put("label", JsonPrimitive(label))
        put("value_path", JsonPrimitive(valuePath))
        put("max_path", JsonPrimitive(maxPath))
        put("format", JsonPrimitive(format.value))
    }

    fun badge(label: String, dataPath: String, variants: Map<String, BadgeStyle>): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("badge"))
        put("label", JsonPrimitive(label))
        put("data_path", JsonPrimitive(dataPath))
        put("variants", buildJsonObject {
            variants.forEach { (k, v) -> put(k, JsonPrimitive(v.value)) }
        })
    }

    fun logViewer(defaultLevel: String = "DEBUG"): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("log_viewer"))
        put("default_level", JsonPrimitive(defaultLevel))
    }

    fun chart(
        label: String,
        dataPath: String,
        format: Format,
        maxPoints: Int,
        color: String?
    ): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("chart"))
        put("label", JsonPrimitive(label))
        put("data_path", JsonPrimitive(dataPath))
        put("format", JsonPrimitive(format.value))
        put("max_points", JsonPrimitive(maxPoints))
        color?.let { put("color", JsonPrimitive(it)) }
    }
}
