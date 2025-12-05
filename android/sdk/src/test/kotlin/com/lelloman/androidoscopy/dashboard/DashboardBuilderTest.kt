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
}
