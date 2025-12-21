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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.LogEntry
import com.lelloman.androidoscopy.protocol.LogLevel
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import com.lelloman.androidoscopy.ui.util.Formatter

@Composable
fun LogViewerWidget(
    logs: List<LogEntry>,
    defaultLevel: String,
    modifier: Modifier = Modifier
) {
    var selectedLevel by remember { mutableStateOf(parseLogLevel(defaultLevel)) }
    val filteredLogs = logs.filter { it.level.ordinal >= selectedLevel.ordinal }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(DashboardColors.Surface)
            .border(1.dp, DashboardColors.Border, shape)
            .padding(12.dp)
    ) {
        // Level filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = level },
                    label = { Text(level.name.first().toString()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = getLogLevelColor(level).copy(alpha = 0.2f),
                        selectedLabelColor = getLogLevelColor(level)
                    )
                )
            }
        }

        // Log entries
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DashboardColors.Background)
        ) {
            if (filteredLogs.isEmpty()) {
                Text(
                    text = "No logs",
                    style = MaterialTheme.typography.bodySmall,
                    color = DashboardColors.TextMuted,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(8.dp)
                ) {
                    items(filteredLogs) { logEntry ->
                        LogEntryRow(logEntry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(logEntry: LogEntry) {
    val levelColor = getLogLevelColor(logEntry.level)
    val timeText = Formatter.formatTime(logEntry.timestamp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Timestamp
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = DashboardColors.TextMuted,
            fontFamily = FontFamily.Monospace
        )

        // Level indicator
        Text(
            text = logEntry.level.name.first().toString(),
            style = MaterialTheme.typography.labelSmall,
            color = levelColor,
            fontFamily = FontFamily.Monospace
        )

        // Tag (if present)
        logEntry.tag?.let { tag ->
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = DashboardColors.Primary,
                fontFamily = FontFamily.Monospace
            )
        }

        // Message
        Text(
            text = logEntry.message,
            style = MaterialTheme.typography.labelSmall,
            color = DashboardColors.TextPrimary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }

    // Show throwable if present
    logEntry.throwable?.let { throwable ->
        Text(
            text = throwable,
            style = MaterialTheme.typography.labelSmall,
            color = DashboardColors.Danger,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp)
        )
    }
}

private fun getLogLevelColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.VERBOSE -> DashboardColors.LogVerbose
        LogLevel.DEBUG -> DashboardColors.LogDebug
        LogLevel.INFO -> DashboardColors.LogInfo
        LogLevel.WARN -> DashboardColors.LogWarn
        LogLevel.ERROR -> DashboardColors.LogError
    }
}

private fun parseLogLevel(level: String): LogLevel {
    return when (level.uppercase()) {
        "VERBOSE", "V" -> LogLevel.VERBOSE
        "DEBUG", "D" -> LogLevel.DEBUG
        "INFO", "I" -> LogLevel.INFO
        "WARN", "WARNING", "W" -> LogLevel.WARN
        "ERROR", "E" -> LogLevel.ERROR
        else -> LogLevel.DEBUG
    }
}
