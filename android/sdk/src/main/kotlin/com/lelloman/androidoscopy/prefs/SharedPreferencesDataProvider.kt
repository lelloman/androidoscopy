package com.lelloman.androidoscopy.prefs

import android.content.Context
import android.content.SharedPreferences
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.data.DataProvider
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that exposes SharedPreferences to the dashboard.
 * Supports viewing all preferences, editing values, adding keys, and deleting keys.
 *
 * @param context Application context for accessing SharedPreferences
 * @param prefsName Optional specific SharedPreferences file to monitor. If null, monitors all.
 */
class SharedPreferencesDataProvider(
    private val context: Context,
    private val prefsName: String? = null
) : DataProvider {

    override val key: String = if (prefsName != null) "prefs_$prefsName" else "prefs"
    override val interval: Duration = 2.seconds

    /**
     * Get action handlers for SharedPreferences operations.
     */
    fun getActionHandlers(): Map<String, suspend (Map<String, Any>) -> ActionResult> = mapOf(
        "prefs_set" to ::handleSetValue,
        "prefs_delete" to ::handleDeleteKey,
        "prefs_add" to ::handleAddKey,
        "prefs_refresh" to ::handleRefresh
    )

    override suspend fun collect(): Map<String, Any> {
        val prefsFiles = getPrefsFiles()
        val selectedFiles = if (prefsName != null) {
            prefsFiles.filter { it == prefsName }
        } else {
            prefsFiles
        }

        val allPrefs = selectedFiles.map { fileName ->
            val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
            mapOf(
                "name" to fileName,
                "entries" to prefs.all.map { (key, value) ->
                    mapOf(
                        "key" to key,
                        "value" to value.toString(),
                        "type" to getValueType(value),
                        "prefs_file" to fileName
                    )
                }
            )
        }

        val flatEntries = allPrefs.flatMap { file ->
            @Suppress("UNCHECKED_CAST")
            (file["entries"] as List<Map<String, Any>>).map { entry ->
                entry + ("prefs_file" to file["name"]!!)
            }
        }

        return mapOf(
            "files" to allPrefs,
            "file_count" to allPrefs.size,
            "entries" to flatEntries,
            "entry_count" to flatEntries.size
        )
    }

    private fun getPrefsFiles(): List<String> {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        if (!prefsDir.exists() || !prefsDir.isDirectory) {
            return emptyList()
        }

        return prefsDir.listFiles()
            ?.filter { it.extension == "xml" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    private fun getValueType(value: Any?): String = when (value) {
        is String -> "String"
        is Int -> "Int"
        is Long -> "Long"
        is Float -> "Float"
        is Boolean -> "Boolean"
        is Set<*> -> "StringSet"
        null -> "null"
        else -> value::class.simpleName ?: "Unknown"
    }

    private suspend fun handleSetValue(args: Map<String, Any>): ActionResult {
        val prefsFile = args["prefs_file"] as? String
            ?: return ActionResult.failure("Missing prefs_file")
        val key = args["key"] as? String
            ?: return ActionResult.failure("Missing key")
        val value = args["value"] as? String
            ?: return ActionResult.failure("Missing value")
        val type = args["type"] as? String
            ?: return ActionResult.failure("Missing type")

        val prefs = context.getSharedPreferences(prefsFile, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        try {
            when (type) {
                "String" -> editor.putString(key, value)
                "Int" -> editor.putInt(key, value.toInt())
                "Long" -> editor.putLong(key, value.toLong())
                "Float" -> editor.putFloat(key, value.toFloat())
                "Boolean" -> editor.putBoolean(key, value.toBoolean())
                "StringSet" -> {
                    val set = value.split(",").map { it.trim() }.toSet()
                    editor.putStringSet(key, set)
                }
                else -> return ActionResult.failure("Unknown type: $type")
            }
            editor.apply()
            return ActionResult.success("Value updated")
        } catch (e: Exception) {
            return ActionResult.failure("Failed to update: ${e.message}")
        }
    }

    private suspend fun handleDeleteKey(args: Map<String, Any>): ActionResult {
        val prefsFile = args["prefs_file"] as? String
            ?: return ActionResult.failure("Missing prefs_file")
        val key = args["key"] as? String
            ?: return ActionResult.failure("Missing key")

        val prefs = context.getSharedPreferences(prefsFile, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()

        return ActionResult.success("Key deleted")
    }

    private suspend fun handleAddKey(args: Map<String, Any>): ActionResult {
        val prefsFile = args["prefs_file"] as? String
            ?: return ActionResult.failure("Missing prefs_file")
        val key = args["key"] as? String
            ?: return ActionResult.failure("Missing key")
        val value = args["value"] as? String
            ?: return ActionResult.failure("Missing value")
        val type = args["type"] as? String
            ?: return ActionResult.failure("Missing type")

        val prefs = context.getSharedPreferences(prefsFile, Context.MODE_PRIVATE)

        if (prefs.contains(key)) {
            return ActionResult.failure("Key already exists")
        }

        return handleSetValue(args)
    }

    private suspend fun handleRefresh(args: Map<String, Any>): ActionResult {
        return ActionResult.success("Refreshed", collect())
    }
}
