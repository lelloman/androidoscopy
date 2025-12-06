package com.lelloman.androidoscopy.coil

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.data.DataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that exposes Coil image cache stats to the dashboard.
 * Shows memory cache and disk cache usage, with clear actions.
 *
 * Usage:
 * ```kotlin
 * val coilDataProvider = CoilDataProvider(imageLoader)
 * Androidoscopy.registerDataProvider(coilDataProvider)
 * ```
 */
class CoilDataProvider(
    private val imageLoader: ImageLoader
) : DataProvider {

    override val key: String = "coil"
    override val interval: Duration = 2.seconds

    /**
     * Get action handlers for Coil cache operations.
     */
    fun getActionHandlers(): Map<String, suspend (Map<String, Any>) -> ActionResult> = mapOf(
        "coil_clear_memory" to ::handleClearMemory,
        "coil_clear_disk" to ::handleClearDisk,
        "coil_clear_all" to ::handleClearAll,
        "coil_refresh" to ::handleRefresh
    )

    override suspend fun collect(): Map<String, Any> {
        val memoryCache = imageLoader.memoryCache
        val diskCache = imageLoader.diskCache

        val memoryCacheStats = memoryCache?.let { cache ->
            mapOf(
                "size" to cache.size,
                "max_size" to cache.maxSize,
                "size_mb" to formatMb(cache.size),
                "max_size_mb" to formatMb(cache.maxSize),
                "usage_percent" to if (cache.maxSize > 0) {
                    (cache.size.toDouble() / cache.maxSize * 100).toInt()
                } else 0
            )
        } ?: mapOf(
            "size" to 0L,
            "max_size" to 0L,
            "size_mb" to "0.0",
            "max_size_mb" to "0.0",
            "usage_percent" to 0
        )

        val diskCacheStats = diskCache?.let { cache ->
            mapOf(
                "size" to cache.size,
                "max_size" to cache.maxSize,
                "size_mb" to formatMb(cache.size),
                "max_size_mb" to formatMb(cache.maxSize),
                "usage_percent" to if (cache.maxSize > 0) {
                    (cache.size.toDouble() / cache.maxSize * 100).toInt()
                } else 0,
                "directory" to (cache.directory.toString())
            )
        } ?: mapOf(
            "size" to 0L,
            "max_size" to 0L,
            "size_mb" to "0.0",
            "max_size_mb" to "0.0",
            "usage_percent" to 0,
            "directory" to ""
        )

        return mapOf(
            "memory_cache" to memoryCacheStats,
            "disk_cache" to diskCacheStats,
            "memory_cache_enabled" to (memoryCache != null),
            "disk_cache_enabled" to (diskCache != null),
            "memory_size_mb" to (memoryCacheStats["size_mb"] ?: "0.0"),
            "memory_max_mb" to (memoryCacheStats["max_size_mb"] ?: "0.0"),
            "memory_percent" to (memoryCacheStats["usage_percent"] ?: 0),
            "disk_size_mb" to (diskCacheStats["size_mb"] ?: "0.0"),
            "disk_max_mb" to (diskCacheStats["max_size_mb"] ?: "0.0"),
            "disk_percent" to (diskCacheStats["usage_percent"] ?: 0)
        )
    }

    private fun formatMb(bytes: Long): String {
        return String.format("%.1f", bytes / (1024.0 * 1024.0))
    }

    private suspend fun handleClearMemory(args: Map<String, Any>): ActionResult {
        return try {
            imageLoader.memoryCache?.clear()
            ActionResult.success("Memory cache cleared", collect())
        } catch (e: Exception) {
            ActionResult.failure("Failed to clear memory cache: ${e.message}")
        }
    }

    private suspend fun handleClearDisk(args: Map<String, Any>): ActionResult {
        return try {
            imageLoader.diskCache?.clear()
            ActionResult.success("Disk cache cleared", collect())
        } catch (e: Exception) {
            ActionResult.failure("Failed to clear disk cache: ${e.message}")
        }
    }

    private suspend fun handleClearAll(args: Map<String, Any>): ActionResult {
        return try {
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            ActionResult.success("All caches cleared", collect())
        } catch (e: Exception) {
            ActionResult.failure("Failed to clear caches: ${e.message}")
        }
    }

    private suspend fun handleRefresh(args: Map<String, Any>): ActionResult {
        return ActionResult.success("Refreshed", collect())
    }
}
