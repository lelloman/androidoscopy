package com.lelloman.androidoscopy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lelloman.androidoscopy.LogEntry
import com.lelloman.androidoscopy.ui.util.JsonPath
import com.lelloman.androidoscopy.ui.widgets.BadgeWidget
import com.lelloman.androidoscopy.ui.widgets.ButtonWidget
import com.lelloman.androidoscopy.ui.widgets.ChartDataStore
import com.lelloman.androidoscopy.ui.widgets.ChartWidget
import com.lelloman.androidoscopy.ui.widgets.GaugeWidget
import com.lelloman.androidoscopy.ui.widgets.LogViewerWidget
import com.lelloman.androidoscopy.ui.widgets.NumberWidget
import com.lelloman.androidoscopy.ui.widgets.TableColumn
import com.lelloman.androidoscopy.ui.widgets.TableRowAction
import com.lelloman.androidoscopy.ui.widgets.TableWidget
import com.lelloman.androidoscopy.ui.widgets.TextWidget
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun WidgetRenderer(
    schema: JsonObject,
    data: Map<String, Any>,
    logs: List<LogEntry>,
    chartDataStores: MutableMap<String, ChartDataStore>,
    onAction: suspend (String, Map<String, Any>) -> Unit,
    modifier: Modifier = Modifier
) {
    val type = schema["type"]?.jsonPrimitive?.content ?: return

    when (type) {
        "number" -> {
            val label = schema["label"]?.jsonPrimitive?.content ?: ""
            val dataPath = schema["data_path"]?.jsonPrimitive?.content ?: ""
            val format = schema["format"]?.jsonPrimitive?.content ?: "number"

            NumberWidget(
                label = label,
                dataPath = dataPath,
                format = format,
                data = data,
                modifier = modifier
            )
        }

        "text" -> {
            val label = schema["label"]?.jsonPrimitive?.content ?: ""
            val dataPath = schema["data_path"]?.jsonPrimitive?.content ?: ""

            TextWidget(
                label = label,
                dataPath = dataPath,
                data = data,
                modifier = modifier
            )
        }

        "badge" -> {
            val label = schema["label"]?.jsonPrimitive?.content ?: ""
            val dataPath = schema["data_path"]?.jsonPrimitive?.content ?: ""
            val variantsJson = schema["variants"]?.jsonObject ?: JsonObject(emptyMap())
            val variants = variantsJson.mapValues { it.value.jsonPrimitive.content }

            BadgeWidget(
                label = label,
                dataPath = dataPath,
                variants = variants,
                data = data,
                modifier = modifier
            )
        }

        "gauge" -> {
            val label = schema["label"]?.jsonPrimitive?.content ?: ""
            val valuePath = schema["value_path"]?.jsonPrimitive?.content ?: ""
            val maxPath = schema["max_path"]?.jsonPrimitive?.content ?: ""
            val format = schema["format"]?.jsonPrimitive?.content ?: "number"

            GaugeWidget(
                label = label,
                valuePath = valuePath,
                maxPath = maxPath,
                format = format,
                data = data,
                modifier = modifier
            )
        }

        "button" -> {
            val label = schema["label"]?.jsonPrimitive?.content ?: ""
            val action = schema["action"]?.jsonPrimitive?.content ?: ""
            val style = schema["style"]?.jsonPrimitive?.content ?: "secondary"

            ButtonWidget(
                label = label,
                action = action,
                style = style,
                onAction = onAction,
                modifier = modifier
            )
        }

        "chart" -> {
            val label = schema["label"]?.jsonPrimitive?.content ?: ""
            val dataPath = schema["data_path"]?.jsonPrimitive?.content ?: ""
            val format = schema["format"]?.jsonPrimitive?.content ?: "number"
            val maxPoints = schema["max_points"]?.jsonPrimitive?.content?.toIntOrNull() ?: 60
            val color = schema["color"]?.jsonPrimitive?.content

            // Get or create chart data store for this path
            val chartKey = "$label:$dataPath"
            val chartStore = chartDataStores.getOrPut(chartKey) { ChartDataStore(maxPoints) }

            // Add current data point when it changes
            val currentValue = JsonPath.evaluateAsNumber(dataPath, data)
            LaunchedEffect(currentValue) {
                currentValue?.let { chartStore.addPoint(it) }
            }

            ChartWidget(
                label = label,
                dataPath = dataPath,
                format = format,
                color = color,
                dataPoints = chartStore.dataPoints,
                data = data,
                modifier = modifier
            )
        }

        "log_viewer" -> {
            val defaultLevel = schema["default_level"]?.jsonPrimitive?.content ?: "DEBUG"

            LogViewerWidget(
                logs = logs,
                defaultLevel = defaultLevel,
                modifier = modifier
            )
        }

        "table" -> {
            val dataPath = schema["data_path"]?.jsonPrimitive?.content ?: ""
            val columnsJson = schema["columns"]?.jsonArray ?: return
            val rowActionsJson = schema["row_actions"]?.jsonArray

            val columns = columnsJson.map { col ->
                val colObj = col.jsonObject
                TableColumn(
                    key = colObj["key"]?.jsonPrimitive?.content ?: "",
                    label = colObj["label"]?.jsonPrimitive?.content ?: "",
                    format = colObj["format"]?.jsonPrimitive?.content ?: "text"
                )
            }

            val rowActions = rowActionsJson?.map { action ->
                val actionObj = action.jsonObject
                val argsObj = actionObj["args"]?.jsonObject
                TableRowAction(
                    id = actionObj["id"]?.jsonPrimitive?.content ?: "",
                    label = actionObj["label"]?.jsonPrimitive?.content ?: "",
                    args = argsObj?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
                )
            } ?: emptyList()

            TableWidget(
                dataPath = dataPath,
                columns = columns,
                rowActions = rowActions,
                data = data,
                onAction = onAction,
                modifier = modifier
            )
        }

        // Placeholder for specialized viewers - show as "not supported" for now
        "network_request_viewer",
        "shared_preferences_viewer",
        "sqlite_viewer",
        "permissions_viewer" -> {
            TextWidget(
                label = type.replace("_", " ").replaceFirstChar { it.uppercase() },
                dataPath = "",
                data = mapOf("" to "Not yet supported in embedded view"),
                modifier = modifier
            )
        }
    }
}
