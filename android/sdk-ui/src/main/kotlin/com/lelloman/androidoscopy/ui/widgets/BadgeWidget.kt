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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lelloman.androidoscopy.ui.theme.DashboardColors
import com.lelloman.androidoscopy.ui.util.JsonPath

data class BadgeColors(
    val background: Color,
    val text: Color
)

@Composable
fun BadgeWidget(
    label: String,
    dataPath: String,
    variants: Map<String, String>,
    data: Map<String, Any>,
    modifier: Modifier = Modifier
) {
    val value = JsonPath.evaluateAsString(dataPath, data) ?: "-"
    val style = variants[value] ?: "muted"
    val colors = getBadgeColors(style)

    val shape = RoundedCornerShape(8.dp)
    val badgeShape = RoundedCornerShape(4.dp)

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
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = colors.text,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(badgeShape)
                .background(colors.background)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun getBadgeColors(style: String): BadgeColors {
    return when (style.lowercase()) {
        "success" -> BadgeColors(DashboardColors.BadgeSuccess, DashboardColors.BadgeSuccessText)
        "warning" -> BadgeColors(DashboardColors.BadgeWarning, DashboardColors.BadgeWarningText)
        "danger" -> BadgeColors(DashboardColors.BadgeDanger, DashboardColors.BadgeDangerText)
        "info" -> BadgeColors(DashboardColors.BadgeInfo, DashboardColors.BadgeInfoText)
        else -> BadgeColors(DashboardColors.BadgeMuted, DashboardColors.BadgeMutedText)
    }
}
