package com.lelloman.androidoscopy.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import com.lelloman.androidoscopy.ui.util.Formatter
import com.lelloman.androidoscopy.ui.util.JsonPath

/**
 * Stores historical data points for the chart.
 * This is maintained externally and passed in on each recomposition.
 */
class ChartDataStore(private val maxPoints: Int = 60) {
    private val _dataPoints = mutableListOf<Double>()
    val dataPoints: List<Double> get() = _dataPoints.toList()

    fun addPoint(value: Double) {
        _dataPoints.add(value)
        while (_dataPoints.size > maxPoints) {
            _dataPoints.removeAt(0)
        }
    }

    fun clear() {
        _dataPoints.clear()
    }
}

@Composable
fun ChartWidget(
    label: String,
    dataPath: String,
    format: String,
    color: String?,
    dataPoints: List<Double>,
    data: Map<String, Any>,
    modifier: Modifier = Modifier
) {
    val currentValue = JsonPath.evaluate(dataPath, data)
    val formattedValue = Formatter.format(currentValue, format)

    val chartColor = color?.let { parseColor(it) } ?: DashboardColors.ChartLine
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(DashboardColors.Surface)
            .border(1.dp, DashboardColors.Border, shape)
            .padding(12.dp)
    ) {
        Text(
            text = "$label: $formattedValue",
            style = MaterialTheme.typography.labelMedium,
            color = DashboardColors.TextSecondary
        )

        if (dataPoints.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val width = size.width
                val height = size.height
                val padding = 4.dp.toPx()

                val effectiveHeight = height - padding * 2
                val effectiveWidth = width - padding * 2

                // Calculate min/max for scaling
                val minValue = dataPoints.minOrNull() ?: 0.0
                val maxValue = dataPoints.maxOrNull() ?: 1.0
                val range = (maxValue - minValue).coerceAtLeast(1.0)

                // Draw grid lines
                val gridLines = 3
                for (i in 0..gridLines) {
                    val y = padding + effectiveHeight * i / gridLines
                    drawLine(
                        color = DashboardColors.ChartGrid,
                        start = Offset(padding, y),
                        end = Offset(width - padding, y),
                        strokeWidth = 1f
                    )
                }

                // Draw the line chart
                if (dataPoints.size >= 2) {
                    val path = Path()
                    val stepX = effectiveWidth / (dataPoints.size - 1).coerceAtLeast(1)

                    dataPoints.forEachIndexed { index, value ->
                        val x = padding + index * stepX
                        val normalizedValue = (value - minValue) / range
                        val y = padding + effectiveHeight * (1 - normalizedValue.toFloat())

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = chartColor,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw current point
                    val lastX = padding + (dataPoints.size - 1) * stepX
                    val lastNormalized = (dataPoints.last() - minValue) / range
                    val lastY = padding + effectiveHeight * (1 - lastNormalized.toFloat())
                    drawCircle(
                        color = chartColor,
                        radius = 4.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }
        } else {
            // No data placeholder
            Text(
                text = "No data",
                style = MaterialTheme.typography.bodySmall,
                color = DashboardColors.TextMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun parseColor(colorString: String): Color {
    return try {
        val hex = colorString.removePrefix("#")
        val colorLong = hex.toLong(16)
        when (hex.length) {
            6 -> Color(0xFF000000 or colorLong)
            8 -> Color(colorLong)
            else -> DashboardColors.ChartLine
        }
    } catch (e: Exception) {
        DashboardColors.ChartLine
    }
}
