package com.lelloman.androidoscopy.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test

class SessionPaletteTest {
    @Test fun `light and dark palettes round trip all serialized roles`() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { colors ->
            val palette = SessionPalette.fromColorScheme(colors)
            val restored = SessionPalette.fromArgbArray(palette.toArgbArray())!!
            assertEquals(palette, restored)
            assertEquals(palette, SessionPalette.fromColorScheme(restored.toColorScheme()))
        }
    }

    @Test fun `host accent and dialog surface survive reconstruction`() {
        val colors = darkColorScheme(primary = Color(0xFF44CC88), surfaceContainerHigh = Color(0xFF123456))
        val restored = SessionPalette.fromColorScheme(colors).toColorScheme()
        assertEquals(colors.primary.toArgb(), restored.primary.toArgb())
        assertEquals(colors.onPrimary.toArgb(), restored.onPrimary.toArgb())
        assertEquals(colors.surfaceContainerHigh.toArgb(), restored.surfaceContainerHigh.toArgb())
        assertEquals(colors.error.toArgb(), restored.error.toArgb())
    }

    @Test fun `missing or malformed intent payload uses the default theme`() {
        assertNull(SessionPalette.fromArgbArray(null))
        assertNull(SessionPalette.fromArgbArray(intArrayOf()))
        assertNull(SessionPalette.fromArgbArray(IntArray(17)))
        assertNull(SessionPalette.fromArgbArray(IntArray(19)))
    }
}
