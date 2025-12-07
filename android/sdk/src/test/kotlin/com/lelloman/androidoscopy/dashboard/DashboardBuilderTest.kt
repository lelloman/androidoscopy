package com.lelloman.androidoscopy.dashboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardBuilderTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `builds empty dashboard`() {
        val builder = DashboardBuilder()
        val result = builder.build()

        assertTrue(result is JsonObject)
        val sections = (result as JsonObject)["sections"]
        assertNotNull(sections)
        assertTrue(sections is JsonArray)
        assertEquals(0, (sections as JsonArray).size)
    }

    @Test
    fun `builds dashboard with single section`() {
        val builder = DashboardBuilder()
        builder.section("Test Section") {
            layout = Layout.ROW
        }

        val result = builder.build() as JsonObject
        val sections = result["sections"]?.jsonArray

        assertNotNull(sections)
        assertEquals(1, sections!!.size)

        val section = sections[0].jsonObject
        assertEquals("test_section", section["id"]?.jsonPrimitive?.content)
        assertEquals("Test Section", section["title"]?.jsonPrimitive?.content)
        assertEquals("row", section["layout"]?.jsonPrimitive?.content)
    }

    @Test
    fun `builds section with number widget`() {
        val builder = DashboardBuilder()
        builder.section("Stats") {
            row {
                number("Count", "\$.count", Format.NUMBER)
            }
        }

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject
        val widgets = section?.get("widgets")?.jsonArray

        assertNotNull(widgets)
        assertEquals(1, widgets!!.size)

        val widget = widgets[0].jsonObject
        assertEquals("number", widget["type"]?.jsonPrimitive?.content)
        assertEquals("Count", widget["label"]?.jsonPrimitive?.content)
        assertEquals("\$.count", widget["data_path"]?.jsonPrimitive?.content)
        assertEquals("number", widget["format"]?.jsonPrimitive?.content)
    }

    @Test
    fun `builds section with gauge widget`() {
        val builder = DashboardBuilder()
        builder.section("Memory") {
            row {
                gauge("Heap", "\$.heap.used", "\$.heap.max", Format.BYTES)
            }
        }

        val result = builder.build() as JsonObject
        val widgets = result["sections"]?.jsonArray?.get(0)?.jsonObject?.get("widgets")?.jsonArray
        val widget = widgets?.get(0)?.jsonObject

        assertEquals("gauge", widget?.get("type")?.jsonPrimitive?.content)
        assertEquals("Heap", widget?.get("label")?.jsonPrimitive?.content)
        assertEquals("\$.heap.used", widget?.get("value_path")?.jsonPrimitive?.content)
        assertEquals("\$.heap.max", widget?.get("max_path")?.jsonPrimitive?.content)
        assertEquals("bytes", widget?.get("format")?.jsonPrimitive?.content)
    }

    @Test
    fun `builds section with badge widget`() {
        val builder = DashboardBuilder()
        builder.section("Status") {
            row {
                badge("State", "\$.state", mapOf(
                    "ACTIVE" to BadgeStyle.SUCCESS,
                    "INACTIVE" to BadgeStyle.MUTED
                ))
            }
        }

        val result = builder.build() as JsonObject
        val widget = result["sections"]?.jsonArray?.get(0)?.jsonObject
            ?.get("widgets")?.jsonArray?.get(0)?.jsonObject

        assertEquals("badge", widget?.get("type")?.jsonPrimitive?.content)
        val variants = widget?.get("variants")?.jsonObject
        assertEquals("success", variants?.get("ACTIVE")?.jsonPrimitive?.content)
        assertEquals("muted", variants?.get("INACTIVE")?.jsonPrimitive?.content)
    }

    @Test
    fun `builds section with table`() {
        val builder = DashboardBuilder()
        builder.section("Data") {
            table("\$.items") {
                column("name", "Name")
                column("size", "Size", Format.BYTES)
            }
        }

        val result = builder.build() as JsonObject
        val widget = result["sections"]?.jsonArray?.get(0)?.jsonObject
            ?.get("widgets")?.jsonArray?.get(0)?.jsonObject

        assertEquals("table", widget?.get("type")?.jsonPrimitive?.content)
        assertEquals("\$.items", widget?.get("data_path")?.jsonPrimitive?.content)

        val columns = widget?.get("columns")?.jsonArray
        assertEquals(2, columns?.size)
        assertEquals("name", columns?.get(0)?.jsonObject?.get("key")?.jsonPrimitive?.content)
        assertEquals("Name", columns?.get(0)?.jsonObject?.get("label")?.jsonPrimitive?.content)
        assertEquals("bytes", columns?.get(1)?.jsonObject?.get("format")?.jsonPrimitive?.content)
    }

    @Test
    fun `builds section with table row actions`() {
        val builder = DashboardBuilder()
        builder.section("Caches") {
            table("\$.caches") {
                column("name", "Name")
                rowAction("clear", "Clear") {
                    put("force", "true")
                }
            }
        }

        val result = builder.build() as JsonObject
        val widget = result["sections"]?.jsonArray?.get(0)?.jsonObject
            ?.get("widgets")?.jsonArray?.get(0)?.jsonObject
        val rowActions = widget?.get("row_actions")?.jsonArray

        assertEquals(1, rowActions?.size)
        val action = rowActions?.get(0)?.jsonObject
        assertEquals("clear", action?.get("id")?.jsonPrimitive?.content)
        assertEquals("Clear", action?.get("label")?.jsonPrimitive?.content)
        assertEquals("true", action?.get("args")?.jsonObject?.get("force")?.jsonPrimitive?.content)
    }

    @Test
    fun `builds section with action button`() {
        val builder = DashboardBuilder()
        builder.section("Actions") {
            actions {
                button("Refresh", "refresh", ButtonStyle.PRIMARY)
            }
        }

        val result = builder.build() as JsonObject
        val widget = result["sections"]?.jsonArray?.get(0)?.jsonObject
            ?.get("widgets")?.jsonArray?.get(0)?.jsonObject

        assertEquals("button", widget?.get("type")?.jsonPrimitive?.content)
        assertEquals("Refresh", widget?.get("label")?.jsonPrimitive?.content)
        assertEquals("refresh", widget?.get("action")?.jsonPrimitive?.content)
        assertEquals("primary", widget?.get("style")?.jsonPrimitive?.content)
    }

    @Test
    fun `builds button with args dialog`() {
        val builder = DashboardBuilder()
        builder.section("Actions") {
            actions {
                button("Configure", "configure", ButtonStyle.SECONDARY) {
                    title = "Configuration"
                    textField("name", "Name", "default")
                    numberField("count", "Count", 10, min = 0, max = 100)
                    checkboxField("enabled", "Enabled", true)
                    selectField("mode", "Mode", listOf(
                        SelectOption("fast", "Fast"),
                        SelectOption("slow", "Slow")
                    ))
                }
            }
        }

        val result = builder.build() as JsonObject
        val widget = result["sections"]?.jsonArray?.get(0)?.jsonObject
            ?.get("widgets")?.jsonArray?.get(0)?.jsonObject
        val dialog = widget?.get("args_dialog")?.jsonObject

        assertEquals("Configuration", dialog?.get("title")?.jsonPrimitive?.content)

        val fields = dialog?.get("fields")?.jsonArray
        assertEquals(4, fields?.size)

        // Text field
        val textField = fields?.get(0)?.jsonObject
        assertEquals("text", textField?.get("type")?.jsonPrimitive?.content)
        assertEquals("name", textField?.get("key")?.jsonPrimitive?.content)

        // Number field
        val numberField = fields?.get(1)?.jsonObject
        assertEquals("number", numberField?.get("type")?.jsonPrimitive?.content)
        assertEquals(0, numberField?.get("min")?.jsonPrimitive?.content?.toInt())
        assertEquals(100, numberField?.get("max")?.jsonPrimitive?.content?.toInt())

        // Checkbox field
        val checkboxField = fields?.get(2)?.jsonObject
        assertEquals("checkbox", checkboxField?.get("type")?.jsonPrimitive?.content)
        assertEquals(true, checkboxField?.get("default")?.jsonPrimitive?.content?.toBoolean())

        // Select field
        val selectField = fields?.get(3)?.jsonObject
        assertEquals("select", selectField?.get("type")?.jsonPrimitive?.content)
        val options = selectField?.get("options")?.jsonArray
        assertEquals(2, options?.size)
    }

    @Test
    fun `memorySection creates proper memory section`() {
        val builder = DashboardBuilder()
        builder.memorySection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("memory", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("Memory", section?.get("title")?.jsonPrimitive?.content)

        val widgets = section?.get("widgets")?.jsonArray
        assertEquals(2, widgets?.size)

        // Gauge widget
        val gauge = widgets?.get(0)?.jsonObject
        assertEquals("gauge", gauge?.get("type")?.jsonPrimitive?.content)

        // Badge widget
        val badge = widgets?.get(1)?.jsonObject
        assertEquals("badge", badge?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `logsSection creates proper logs section`() {
        val builder = DashboardBuilder()
        builder.logsSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("logs", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("Logs", section?.get("title")?.jsonPrimitive?.content)
        assertEquals("stack", section?.get("layout")?.jsonPrimitive?.content)

        val widget = section?.get("widget")?.jsonObject
        assertEquals("log_viewer", widget?.get("type")?.jsonPrimitive?.content)
        assertEquals("DEBUG", widget?.get("default_level")?.jsonPrimitive?.content)
    }

    @Test
    fun `cacheSection creates proper cache section`() {
        val builder = DashboardBuilder()
        builder.cacheSection(listOf(
            CacheConfig("cache1", "Image Cache"),
            CacheConfig("cache2", "Data Cache")
        ))

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("caches", section?.get("id")?.jsonPrimitive?.content)

        val widget = section?.get("widgets")?.jsonArray?.get(0)?.jsonObject
        assertEquals("table", widget?.get("type")?.jsonPrimitive?.content)

        val rowActions = widget?.get("row_actions")?.jsonArray
        assertEquals(2, rowActions?.size)
        assertEquals("clear_cache1", rowActions?.get(0)?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals("clear_cache2", rowActions?.get(1)?.jsonObject?.get("id")?.jsonPrimitive?.content)
    }

    @Test
    fun `collapsible section has correct properties`() {
        val builder = DashboardBuilder()
        builder.section("Debug") {
            collapsible = true
            collapsedDefault = true
        }

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals(true, section?.get("collapsible")?.jsonPrimitive?.content?.toBoolean())
        assertEquals(true, section?.get("collapsed_default")?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun `grid layout with columns is configured correctly`() {
        val builder = DashboardBuilder()
        builder.section("Grid") {
            layout = Layout.GRID
            columns = 3
        }

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("grid", section?.get("layout")?.jsonPrimitive?.content)
        assertEquals(3, section?.get("columns")?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `anrSection creates proper ANR section`() {
        val builder = DashboardBuilder()
        builder.anrSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("anr_detection", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("ANR Detection", section?.get("title")?.jsonPrimitive?.content)
        assertEquals(true, section?.get("collapsible")?.jsonPrimitive?.content?.toBoolean())
        assertEquals(true, section?.get("full_width")?.jsonPrimitive?.content?.toBoolean())

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)
        assertTrue(widgets!!.size >= 2)

        // Check for alert on ANR count
        val numberWidget = widgets.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "number" &&
            it.jsonObject["label"]?.jsonPrimitive?.content == "ANR Count"
        }?.jsonObject
        assertNotNull(numberWidget)
        assertNotNull(numberWidget?.get("alert"))
    }

    @Test
    fun `networkRequestsSection creates proper network section`() {
        val builder = DashboardBuilder()
        builder.networkRequestsSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("network_requests", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("Network Requests", section?.get("title")?.jsonPrimitive?.content)

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)

        // Should have a network_request_viewer widget
        val viewer = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "network_request_viewer"
        }?.jsonObject
        assertNotNull(viewer)
        assertEquals("\$.network.requests", viewer?.get("data_path")?.jsonPrimitive?.content)
    }

    @Test
    fun `sharedPreferencesSection creates proper prefs section`() {
        val builder = DashboardBuilder()
        builder.sharedPreferencesSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("sharedpreferences", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("SharedPreferences", section?.get("title")?.jsonPrimitive?.content)

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)

        // Should have shared_preferences_viewer widget
        val viewer = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "shared_preferences_viewer"
        }?.jsonObject
        assertNotNull(viewer)
        assertEquals("\$.prefs", viewer?.get("data_path")?.jsonPrimitive?.content)
    }

    @Test
    fun `sharedPreferencesSection with name uses correct data key`() {
        val builder = DashboardBuilder()
        builder.sharedPreferencesSection("my_prefs")

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("SharedPreferences (my_prefs)", section?.get("title")?.jsonPrimitive?.content)

        // Check shared_preferences_viewer uses correct data path
        val widgets = section?.get("widgets")?.jsonArray
        val viewer = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "shared_preferences_viewer"
        }?.jsonObject
        assertNotNull(viewer)
        assertEquals("\$.prefs_my_prefs", viewer?.get("data_path")?.jsonPrimitive?.content)
    }

    @Test
    fun `sqliteSection creates proper SQLite section`() {
        val builder = DashboardBuilder()
        builder.sqliteSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("sqlite", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("SQLite", section?.get("title")?.jsonPrimitive?.content)

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)

        // Should have query button
        val queryButton = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "button" &&
            it.jsonObject["action"]?.jsonPrimitive?.content == "sqlite_query"
        }?.jsonObject
        assertNotNull(queryButton)
    }

    @Test
    fun `permissionsSection creates proper permissions section`() {
        val builder = DashboardBuilder()
        builder.permissionsSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("permissions", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("Permissions", section?.get("title")?.jsonPrimitive?.content)

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)

        // Should have table with permissions data
        val table = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "table"
        }?.jsonObject
        assertNotNull(table)
        assertEquals("\$.permissions.permissions", table?.get("data_path")?.jsonPrimitive?.content)
    }

    @Test
    fun `buildInfoSection creates proper build info section`() {
        val builder = DashboardBuilder()
        builder.buildInfoSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("build_info", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("Build Info", section?.get("title")?.jsonPrimitive?.content)
        assertEquals(true, section?.get("collapsed_default")?.jsonPrimitive?.content?.toBoolean())

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)
        assertTrue(widgets!!.size >= 4) // Has multiple rows of info
    }

    @Test
    fun `leaksSection creates proper leaks section`() {
        val builder = DashboardBuilder()
        builder.leaksSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("memory_leaks", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("Memory Leaks", section?.get("title")?.jsonPrimitive?.content)

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)

        // Should have alert on leak count
        val leakCountWidget = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "number" &&
            it.jsonObject["label"]?.jsonPrimitive?.content == "Leak Count"
        }?.jsonObject
        assertNotNull(leakCountWidget)
        assertNotNull(leakCountWidget?.get("alert"))
    }

    @Test
    fun `workManagerSection creates proper WorkManager section`() {
        val builder = DashboardBuilder()
        builder.workManagerSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("workmanager", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("WorkManager", section?.get("title")?.jsonPrimitive?.content)

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)

        // Should have cancel all button
        val cancelAllButton = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "button" &&
            it.jsonObject["action"]?.jsonPrimitive?.content == "workmanager_cancel_all"
        }?.jsonObject
        assertNotNull(cancelAllButton)
        assertEquals("danger", cancelAllButton?.get("style")?.jsonPrimitive?.content)
    }

    @Test
    fun `coilSection creates proper Coil section`() {
        val builder = DashboardBuilder()
        builder.coilSection()

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals("image_cache_(coil)", section?.get("id")?.jsonPrimitive?.content)
        assertEquals("Image Cache (Coil)", section?.get("title")?.jsonPrimitive?.content)

        val widgets = section?.get("widgets")?.jsonArray
        assertNotNull(widgets)

        // Should have gauges for memory and disk cache
        val gauges = widgets?.filter {
            it.jsonObject["type"]?.jsonPrimitive?.content == "gauge"
        }
        assertEquals(2, gauges?.size)

        // Should have clear all button
        val clearAllButton = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "button" &&
            it.jsonObject["action"]?.jsonPrimitive?.content == "coil_clear_all"
        }?.jsonObject
        assertNotNull(clearAllButton)
    }

    @Test
    fun `performancePreset creates multiple sections`() {
        val builder = DashboardBuilder()
        builder.performancePreset()

        val result = builder.build() as JsonObject
        val sections = result["sections"]?.jsonArray

        assertNotNull(sections)
        assertEquals(2, sections!!.size) // Memory and Threads

        assertEquals("memory", sections[0].jsonObject["id"]?.jsonPrimitive?.content)
        assertEquals("threads", sections[1].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `performancePreset includes chart when requested`() {
        val builder = DashboardBuilder()
        builder.performancePreset(includeCharts = true)

        val result = builder.build() as JsonObject
        val memorySection = result["sections"]?.jsonArray?.get(0)?.jsonObject
        val widgets = memorySection?.get("widgets")?.jsonArray

        val chart = widgets?.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.content == "chart"
        }
        assertNotNull(chart)
    }

    @Test
    fun `deviceStatusPreset creates battery network storage sections`() {
        val builder = DashboardBuilder()
        builder.deviceStatusPreset()

        val result = builder.build() as JsonObject
        val sections = result["sections"]?.jsonArray

        assertNotNull(sections)
        assertEquals(3, sections!!.size)

        assertEquals("battery", sections[0].jsonObject["id"]?.jsonPrimitive?.content)
        assertEquals("network", sections[1].jsonObject["id"]?.jsonPrimitive?.content)
        assertEquals("storage", sections[2].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `debuggingPreset creates logs and memory sections`() {
        val builder = DashboardBuilder()
        builder.debuggingPreset()

        val result = builder.build() as JsonObject
        val sections = result["sections"]?.jsonArray

        assertNotNull(sections)
        assertEquals(2, sections!!.size)

        assertEquals("logs", sections[0].jsonObject["id"]?.jsonPrimitive?.content)
        assertEquals("memory", sections[1].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `minimalPreset creates single status section`() {
        val builder = DashboardBuilder()
        builder.minimalPreset()

        val result = builder.build() as JsonObject
        val sections = result["sections"]?.jsonArray

        assertNotNull(sections)
        assertEquals(1, sections!!.size)
        assertEquals("status", sections[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fullSystemPreset creates all system sections`() {
        val builder = DashboardBuilder()
        builder.fullSystemPreset()

        val result = builder.build() as JsonObject
        val sections = result["sections"]?.jsonArray

        assertNotNull(sections)
        assertEquals(5, sections!!.size)
    }

    @Test
    fun `alert configuration is serialized correctly`() {
        val builder = DashboardBuilder()
        builder.section("Test") {
            row {
                number(
                    label = "Value",
                    dataPath = "\$.value",
                    alert = AlertConfig(
                        condition = AlertCondition.gt("\$.value", 100),
                        severity = AlertSeverity.CRITICAL,
                        message = "Value too high!"
                    )
                )
            }
        }

        val result = builder.build() as JsonObject
        val widget = result["sections"]?.jsonArray?.get(0)?.jsonObject
            ?.get("widgets")?.jsonArray?.get(0)?.jsonObject
        val alert = widget?.get("alert")?.jsonObject

        assertNotNull(alert)
        assertEquals("critical", alert?.get("severity")?.jsonPrimitive?.content)
        assertEquals("Value too high!", alert?.get("message")?.jsonPrimitive?.content)

        val condition = alert?.get("condition")?.jsonObject
        assertEquals("\$.value", condition?.get("path")?.jsonPrimitive?.content)
        assertEquals("gt", condition?.get("operator")?.jsonPrimitive?.content)
        assertEquals(100, condition?.get("value")?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `fullWidth property is serialized`() {
        val builder = DashboardBuilder()
        builder.section("Wide") {
            fullWidth = true
        }

        val result = builder.build() as JsonObject
        val section = result["sections"]?.jsonArray?.get(0)?.jsonObject

        assertEquals(true, section?.get("full_width")?.jsonPrimitive?.content?.toBoolean())
    }
}
