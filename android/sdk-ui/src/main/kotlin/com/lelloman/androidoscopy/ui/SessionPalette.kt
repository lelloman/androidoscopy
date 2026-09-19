package com.lelloman.androidoscopy.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Host colors for the session screen and pairing dialogs (not the legacy dashboard).
 * Stored in the launch Intent, so activity recreation does not lose the palette.
 * Colors are ARGB integers. [fromColorScheme] snapshots the host's current light/dark theme.
 */
data class SessionPalette(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
) {
    internal fun toArgbArray() = intArrayOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer, background, onBackground,
        surface, onSurface, surfaceVariant, onSurfaceVariant, outline, outlineVariant,
        error, onError, errorContainer, onErrorContainer, surfaceContainerHigh, surfaceContainerHighest,
    )

    internal fun toColorScheme(): ColorScheme = lightColorScheme(
        primary = Color(primary), onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer), onPrimaryContainer = Color(onPrimaryContainer),
        secondary = Color(primary), onSecondary = Color(onPrimary),
        secondaryContainer = Color(primaryContainer), onSecondaryContainer = Color(onPrimaryContainer),
        tertiary = Color(primary), onTertiary = Color(onPrimary),
        tertiaryContainer = Color(primaryContainer), onTertiaryContainer = Color(onPrimaryContainer),
        background = Color(background), onBackground = Color(onBackground),
        surface = Color(surface), onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant), onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary), inverseSurface = Color(onSurface), inverseOnSurface = Color(surface),
        inversePrimary = Color(primaryContainer), outline = Color(outline), outlineVariant = Color(outlineVariant),
        error = Color(error), onError = Color(onError),
        errorContainer = Color(errorContainer), onErrorContainer = Color(onErrorContainer),
        surfaceDim = Color(surface), surfaceBright = Color(surface),
        surfaceContainerLowest = Color(background), surfaceContainerLow = Color(surface),
        surfaceContainer = Color(surfaceVariant), surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
    )

    companion object {
        fun fromColorScheme(colors: ColorScheme) = with(colors) {
            SessionPalette(
                primary.toArgb(), onPrimary.toArgb(), primaryContainer.toArgb(), onPrimaryContainer.toArgb(),
                background.toArgb(), onBackground.toArgb(), surface.toArgb(), onSurface.toArgb(),
                surfaceVariant.toArgb(), onSurfaceVariant.toArgb(), outline.toArgb(), outlineVariant.toArgb(),
                error.toArgb(), onError.toArgb(), errorContainer.toArgb(), onErrorContainer.toArgb(),
                surfaceContainerHigh.toArgb(), surfaceContainerHighest.toArgb(),
            )
        }

        internal fun fromArgbArray(colors: IntArray?): SessionPalette? {
            if (colors == null || colors.size != 18) return null
            return SessionPalette(
                colors[0], colors[1], colors[2], colors[3], colors[4], colors[5],
                colors[6], colors[7], colors[8], colors[9], colors[10], colors[11],
                colors[12], colors[13], colors[14], colors[15], colors[16], colors[17],
            )
        }
    }
}
