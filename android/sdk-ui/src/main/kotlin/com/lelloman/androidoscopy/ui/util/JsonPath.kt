package com.lelloman.androidoscopy.ui.util

/**
 * Simple JSONPath evaluator for extracting values from nested maps.
 * Supports basic path syntax like "$.memory.heap_used_bytes" or "$.battery.level"
 */
object JsonPath {

    /**
     * Evaluates a JSONPath expression against a data map.
     * @param path The JSONPath expression (e.g., "$.memory.heap_used_bytes")
     * @param data The data map to evaluate against
     * @return The value at the path, or null if not found
     */
    fun evaluate(path: String, data: Map<String, Any>): Any? {
        if (path.isBlank()) return null

        // Handle constant values (numbers as strings)
        if (path.toDoubleOrNull() != null) {
            return path.toDouble()
        }

        // Remove leading $. if present
        val normalizedPath = path.removePrefix("$.")

        // Split by . and handle array notation
        val parts = normalizedPath.split(".")

        var current: Any? = data

        for (part in parts) {
            if (current == null) return null

            // Handle array notation like "items[0]"
            val arrayMatch = Regex("""(\w+)\[(\d+)]""").matchEntire(part)
            if (arrayMatch != null) {
                val key = arrayMatch.groupValues[1]
                val index = arrayMatch.groupValues[2].toInt()

                current = when (current) {
                    is Map<*, *> -> current[key]
                    else -> return null
                }

                current = when (current) {
                    is List<*> -> current.getOrNull(index)
                    else -> return null
                }
            } else {
                // Simple key access
                current = when (current) {
                    is Map<*, *> -> current[part]
                    else -> return null
                }
            }
        }

        return current
    }

    /**
     * Evaluates a JSONPath expression and returns the result as a Double.
     * @param path The JSONPath expression
     * @param data The data map to evaluate against
     * @return The numeric value, or null if not found or not a number
     */
    fun evaluateAsNumber(path: String, data: Map<String, Any>): Double? {
        val value = evaluate(path, data) ?: return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    /**
     * Evaluates a JSONPath expression and returns the result as a Long.
     * @param path The JSONPath expression
     * @param data The data map to evaluate against
     * @return The numeric value as Long, or null if not found or not a number
     */
    fun evaluateAsLong(path: String, data: Map<String, Any>): Long? {
        val value = evaluate(path, data) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    /**
     * Evaluates a JSONPath expression and returns the result as a String.
     * @param path The JSONPath expression
     * @param data The data map to evaluate against
     * @return The string value, or null if not found
     */
    fun evaluateAsString(path: String, data: Map<String, Any>): String? {
        val value = evaluate(path, data) ?: return null
        return value.toString()
    }

    /**
     * Evaluates a JSONPath expression and returns the result as a Boolean.
     * @param path The JSONPath expression
     * @param data The data map to evaluate against
     * @return The boolean value, or null if not found or not a boolean
     */
    fun evaluateAsBoolean(path: String, data: Map<String, Any>): Boolean? {
        val value = evaluate(path, data) ?: return null
        return when (value) {
            is Boolean -> value
            is String -> value.lowercase() == "true"
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    /**
     * Evaluates a JSONPath expression and returns the result as a List.
     * @param path The JSONPath expression
     * @param data The data map to evaluate against
     * @return The list value, or null if not found or not a list
     */
    @Suppress("UNCHECKED_CAST")
    fun evaluateAsList(path: String, data: Map<String, Any>): List<Any>? {
        val value = evaluate(path, data) ?: return null
        return value as? List<Any>
    }

    /**
     * Evaluates a JSONPath expression and returns the result as a Map.
     * @param path The JSONPath expression
     * @param data The data map to evaluate against
     * @return The map value, or null if not found or not a map
     */
    @Suppress("UNCHECKED_CAST")
    fun evaluateAsMap(path: String, data: Map<String, Any>): Map<String, Any>? {
        val value = evaluate(path, data) ?: return null
        return value as? Map<String, Any>
    }
}
