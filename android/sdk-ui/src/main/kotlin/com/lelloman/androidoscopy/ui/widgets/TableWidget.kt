package com.lelloman.androidoscopy.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import com.lelloman.androidoscopy.ui.util.Formatter
import com.lelloman.androidoscopy.ui.util.JsonPath
import kotlinx.coroutines.launch

data class TableColumn(
    val key: String,
    val label: String,
    val format: String = "text"
)

data class TableRowAction(
    val id: String,
    val label: String,
    val args: Map<String, String> = emptyMap()
)

@Composable
fun TableWidget(
    dataPath: String,
    columns: List<TableColumn>,
    rowActions: List<TableRowAction>,
    data: Map<String, Any>,
    onAction: suspend (String, Map<String, Any>) -> Unit,
    modifier: Modifier = Modifier
) {
    val tableData = JsonPath.evaluateAsList(dataPath, data) ?: emptyList()

    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(DashboardColors.Surface)
            .border(1.dp, DashboardColors.Border, shape)
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DashboardColors.SurfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            columns.forEach { column ->
                Text(
                    text = column.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = DashboardColors.TextSecondary,
                    modifier = Modifier.widthIn(min = 80.dp)
                )
            }
            if (rowActions.isNotEmpty()) {
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.labelMedium,
                    color = DashboardColors.TextSecondary,
                    modifier = Modifier.widthIn(min = 80.dp)
                )
            }
        }

        HorizontalDivider(color = DashboardColors.Border)

        // Data rows
        if (tableData.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No data",
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardColors.TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(200.dp)
            ) {
                items(tableData) { rowData ->
                    TableRow(
                        rowData = rowData,
                        columns = columns,
                        rowActions = rowActions,
                        onAction = onAction
                    )
                    HorizontalDivider(color = DashboardColors.Border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    rowData: Any,
    columns: List<TableColumn>,
    rowActions: List<TableRowAction>,
    onAction: suspend (String, Map<String, Any>) -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    val rowMap = rowData as? Map<String, Any> ?: return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.forEach { column ->
            val value = rowMap[column.key]
            val formattedValue = Formatter.format(value, column.format)

            Text(
                text = formattedValue,
                style = MaterialTheme.typography.bodySmall,
                color = DashboardColors.TextPrimary,
                modifier = Modifier.widthIn(min = 80.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (rowActions.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowActions.forEach { action ->
                    TableActionButton(
                        action = action,
                        rowData = rowMap,
                        onAction = onAction
                    )
                }
            }
        }
    }
}

@Composable
private fun TableActionButton(
    action: TableRowAction,
    rowData: Map<String, Any>,
    onAction: suspend (String, Map<String, Any>) -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    TextButton(
        onClick = {
            scope.launch {
                // Merge action args with row data
                val args = action.args.toMutableMap<String, Any>()
                args.putAll(rowData)
                onAction(action.id, args)
            }
        }
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            color = DashboardColors.Primary
        )
    }
}

