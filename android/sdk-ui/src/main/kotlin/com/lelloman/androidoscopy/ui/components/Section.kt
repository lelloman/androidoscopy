package com.lelloman.androidoscopy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.LogEntry
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import com.lelloman.androidoscopy.ui.widgets.ChartDataStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Section(
    schema: JsonObject,
    data: Map<String, Any>,
    logs: List<LogEntry>,
    chartDataStores: MutableMap<String, ChartDataStore>,
    onAction: suspend (String, Map<String, Any>) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = schema["title"]?.jsonPrimitive?.content ?: "Section"
    val layout = schema["layout"]?.jsonPrimitive?.content ?: "row"
    val collapsible = schema["collapsible"]?.jsonPrimitive?.booleanOrNull ?: false
    val collapsedDefault = schema["collapsed_default"]?.jsonPrimitive?.booleanOrNull ?: false
    val fullWidth = schema["full_width"]?.jsonPrimitive?.booleanOrNull ?: false
    val columns = schema["columns"]?.jsonPrimitive?.intOrNull ?: 2

    var isExpanded by remember { mutableStateOf(!collapsedDefault) }

    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DashboardColors.Surface)
            .border(1.dp, DashboardColors.Border, shape)
    ) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (collapsible) {
                        Modifier.clickable { isExpanded = !isExpanded }
                    } else {
                        Modifier
                    }
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = DashboardColors.TextPrimary
            )

            if (collapsible) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashboardColors.TextSecondary
                )
            }
        }

        // Section content
        AnimatedVisibility(
            visible = isExpanded || !collapsible,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Check if there's a single widget (like log_viewer)
                val singleWidget = schema["widget"]?.jsonObject
                if (singleWidget != null) {
                    WidgetRenderer(
                        schema = singleWidget,
                        data = data,
                        logs = logs,
                        chartDataStores = chartDataStores,
                        onAction = onAction,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Render widgets array based on layout
                    val widgets = schema["widgets"]?.jsonArray ?: return@Column

                    when (layout) {
                        "row" -> {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                widgets.forEach { widget ->
                                    WidgetRenderer(
                                        schema = widget.jsonObject,
                                        data = data,
                                        logs = logs,
                                        chartDataStores = chartDataStores,
                                        onAction = onAction
                                    )
                                }
                            }
                        }
                        "stack" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                widgets.forEach { widget ->
                                    WidgetRenderer(
                                        schema = widget.jsonObject,
                                        data = data,
                                        logs = logs,
                                        chartDataStores = chartDataStores,
                                        onAction = onAction,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        "grid" -> {
                            // Simple grid using FlowRow with fixed width items
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                maxItemsInEachRow = columns
                            ) {
                                widgets.forEach { widget ->
                                    WidgetRenderer(
                                        schema = widget.jsonObject,
                                        data = data,
                                        logs = logs,
                                        chartDataStores = chartDataStores,
                                        onAction = onAction,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        "flow" -> {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                widgets.forEach { widget ->
                                    WidgetRenderer(
                                        schema = widget.jsonObject,
                                        data = data,
                                        logs = logs,
                                        chartDataStores = chartDataStores,
                                        onAction = onAction
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
