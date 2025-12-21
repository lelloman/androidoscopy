package com.lelloman.androidoscopy.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import com.lelloman.androidoscopy.ui.util.Formatter
import com.lelloman.androidoscopy.ui.util.JsonPath

@Composable
fun NumberWidget(
    label: String,
    dataPath: String,
    format: String,
    data: Map<String, Any>,
    modifier: Modifier = Modifier
) {
    val value = JsonPath.evaluate(dataPath, data)
    val formattedValue = Formatter.format(value, format)

    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .clip(shape)
            .background(DashboardColors.Surface)
            .border(1.dp, DashboardColors.Border, shape)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = DashboardColors.TextSecondary
        )
        Text(
            text = formattedValue,
            style = MaterialTheme.typography.headlineMedium,
            color = DashboardColors.TextPrimary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
