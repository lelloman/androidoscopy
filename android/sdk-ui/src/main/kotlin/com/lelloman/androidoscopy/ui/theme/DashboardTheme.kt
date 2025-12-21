package com.lelloman.androidoscopy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Colors matching the web dashboard
object DashboardColors {
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF171717)
    val SurfaceVariant = Color(0xFF1F1F1F)
    val Border = Color(0xFF333333)
    val BorderLight = Color(0xFF404040)

    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFA0A0A0)
    val TextMuted = Color(0xFF666666)

    val Primary = Color(0xFF3B82F6)      // Blue
    val Success = Color(0xFF22C55E)      // Green
    val Warning = Color(0xFFF59E0B)      // Orange/Amber
    val Danger = Color(0xFFEF4444)       // Red
    val Info = Color(0xFF0EA5E9)         // Light blue

    val BadgeSuccess = Color(0xFF166534)
    val BadgeSuccessText = Color(0xFF86EFAC)
    val BadgeWarning = Color(0xFF854D0E)
    val BadgeWarningText = Color(0xFFFDE047)
    val BadgeDanger = Color(0xFF991B1B)
    val BadgeDangerText = Color(0xFFFCA5A5)
    val BadgeInfo = Color(0xFF0C4A6E)
    val BadgeInfoText = Color(0xFF7DD3FC)
    val BadgeMuted = Color(0xFF374151)
    val BadgeMutedText = Color(0xFF9CA3AF)

    val ChartLine = Primary
    val ChartGrid = Border

    val LogVerbose = TextMuted
    val LogDebug = TextSecondary
    val LogInfo = Info
    val LogWarn = Warning
    val LogError = Danger
}

private val DarkColorScheme = darkColorScheme(
    primary = DashboardColors.Primary,
    onPrimary = Color.White,
    secondary = DashboardColors.Info,
    onSecondary = Color.White,
    tertiary = DashboardColors.Success,
    onTertiary = Color.White,
    background = DashboardColors.Background,
    onBackground = DashboardColors.TextPrimary,
    surface = DashboardColors.Surface,
    onSurface = DashboardColors.TextPrimary,
    surfaceVariant = DashboardColors.SurfaceVariant,
    onSurfaceVariant = DashboardColors.TextSecondary,
    error = DashboardColors.Danger,
    onError = Color.White,
    outline = DashboardColors.Border,
    outlineVariant = DashboardColors.BorderLight
)

private val DashboardTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun DashboardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = DashboardTypography,
        content = content
    )
}
