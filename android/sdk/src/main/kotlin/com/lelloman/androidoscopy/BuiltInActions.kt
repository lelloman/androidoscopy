package com.lelloman.androidoscopy

import android.content.Context
import java.io.File

object BuiltInActions {
    const val FORCE_GC = "force_gc"
    const val CLEAR_CACHE = "clear_cache"

    fun forceGc(): ActionHandler = { _ ->
        System.gc()
        Runtime.getRuntime().gc()
        ActionResult.success("Garbage collection triggered")
    }

    fun clearCache(context: Context): ActionHandler = { _ ->
        val cleared = clearDirectory(context.cacheDir) +
                (context.externalCacheDir?.let { clearDirectory(it) } ?: 0L)

        ActionResult.success(
            "Cleared ${formatBytes(cleared)} of cache",
            mapOf("bytes_cleared" to cleared)
        )
    }

    private fun clearDirectory(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L

        var cleared = 0L
        dir.listFiles()?.forEach { file ->
            cleared += if (file.isDirectory) {
                val size = getDirectorySize(file)
                file.deleteRecursively()
                size
            } else {
                val size = file.length()
                file.delete()
                size
            }
        }
        return cleared
    }

    private fun getDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()

        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return size
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${String.format("%.1f", kb)} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${String.format("%.1f", mb)} MB"
        val gb = mb / 1024.0
        return "${String.format("%.1f", gb)} GB"
    }
}
