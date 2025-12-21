package com.lelloman.androidoscopy.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import com.lelloman.androidoscopy.ui.util.Formatter
import com.lelloman.androidoscopy.ui.util.JsonPath
import kotlin.math.roundToInt

@Composable
fun GaugeWidget(
    label: String,
    valuePath: String,
    maxPath: String,
    format: String,
    data: Map<String, Any>,
    modifier: Modifier = Modifier
) {
    val value = JsonPath.evaluate(valuePath, data)
    val max = JsonPath.evaluate(maxPath, data)
    val percent = Formatter.calculatePercent(value, max)
    val formattedValue = Formatter.format(value, format)
    val percentText = "${(percent * 100).roundToInt()}%"

    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(DashboardColors.Surface)
            .border(1.dp, DashboardColors.Border, shape)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DashboardColors.TextSecondary
        )

        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(80.dp)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // Draw the gauge arc
            val trackColor = DashboardColors.Border
            val progressColor = getGaugeColor(percent)
            val strokeWidth = 8.dp

            Canvas(modifier = Modifier.fillMaxSize()) {
                val diameter = size.minDimension
                val radius = diameter / 2
                val strokeWidthPx = strokeWidth.toPx()

                // Background track (full arc)
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                    size = Size(diameter - strokeWidthPx, diameter - strokeWidthPx),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )

                // Progress arc
                drawArc(
                    color = progressColor,
                    startAngle = 135f,
                    sweepAngle = 270f * percent,
                    useCenter = false,
                    topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                    size = Size(diameter - strokeWidthPx, diameter - strokeWidthPx),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            // Center text
            Text(
                text = percentText,
                style = MaterialTheme.typography.titleMedium,
                color = DashboardColors.TextPrimary
            )
        }

        Text(
            text = formattedValue,
            style = MaterialTheme.typography.bodySmall,
            color = DashboardColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun getGaugeColor(percent: Float) = when {
    percent >= 0.9f -> DashboardColors.Danger
    percent >= 0.75f -> DashboardColors.Warning
    else -> DashboardColors.Primary
}
