package com.lelloman.androidoscopy.ui.util

import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Formats values for display in dashboard widgets.
 * Matches the formatting logic from the web dashboard.
 */
object Formatter {

    private val decimalFormat = DecimalFormat("#,##0.#")
    private val percentFormat = DecimalFormat("0.0")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Formats a value based on the specified format type.
     */
    fun format(value: Any?, formatType: String): String {
        if (value == null) return "-"

        return when (formatType.lowercase()) {
            "bytes" -> formatBytes(value)
            "percent" -> formatPercent(value)
            "duration" -> formatDuration(value)
            "number" -> formatNumber(value)
            "text" -> value.toString()
            else -> value.toString()
        }
    }

    /**
     * Formats bytes into human-readable format (KB, MB, GB).
     */
    fun formatBytes(value: Any?): String {
        val bytes = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: return "-"
            else -> return "-"
        }

        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${decimalFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    /**
     * Formats a value as a percentage.
     */
    fun formatPercent(value: Any?): String {
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: return "-"
            else -> return "-"
        }

        // If value is already 0-100, don't multiply
        val percentValue = if (number <= 1.0 && number >= 0.0) {
            number * 100
        } else {
            number
        }

        return "${percentFormat.format(percentValue)}%"
    }

    /**
     * Formats a duration in milliseconds to human-readable format.
     */
    fun formatDuration(value: Any?): String {
        val ms = when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: return "-"
            else -> return "-"
        }

        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60_000 -> "${decimalFormat.format(ms / 1000.0)}s"
            ms < 3600_000 -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
            else -> "${ms / 3600_000}h ${(ms % 3600_000) / 60_000}m"
        }
    }

    /**
     * Formats a number with thousands separators.
     */
    fun formatNumber(value: Any?): String {
        return when (value) {
            is Number -> {
                if (value.toDouble() == value.toLong().toDouble()) {
                    decimalFormat.format(value.toLong())
                } else {
                    decimalFormat.format(value.toDouble())
                }
            }
            is String -> value.toLongOrNull()?.let { decimalFormat.format(it) }
                ?: value.toDoubleOrNull()?.let { decimalFormat.format(it) }
                ?: value
            else -> "-"
        }
    }

    /**
     * Formats an ISO timestamp to time only (HH:mm:ss).
     */
    fun formatTime(timestamp: String): String {
        return try {
            val instant = Instant.parse(timestamp)
            timeFormatter.format(instant)
        } catch (e: Exception) {
            timestamp
        }
    }

    /**
     * Calculates percentage from value and max.
     */
    fun calculatePercent(value: Any?, max: Any?): Float {
        val v = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: return 0f
            else -> return 0f
        }

        val m = when (max) {
            is Number -> max.toDouble()
            is String -> max.toDoubleOrNull() ?: return 0f
            else -> return 0f
        }

        if (m == 0.0) return 0f
        return (v / m).toFloat().coerceIn(0f, 1f)
    }
}
