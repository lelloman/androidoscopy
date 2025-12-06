package com.lelloman.androidoscopy.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class StorageDataProvider(
    private val context: Context,
    override val interval: Duration = 30.seconds
) : DataProvider {

    override val key = "storage"

    override suspend fun collect(): Map<String, Any> {
        val appDataDir = context.filesDir
        val appCacheDir = context.cacheDir
        val externalCacheDir = context.externalCacheDir

        val appDataSize = calculateDirectorySize(appDataDir)
        val cacheSize = calculateDirectorySize(appCacheDir) +
                (externalCacheDir?.let { calculateDirectorySize(it) } ?: 0L)

        val internalStorage = getStorageStats(Environment.getDataDirectory())
        val externalStorage = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            getStorageStats(Environment.getExternalStorageDirectory())
        } else {
            null
        }

        return buildMap {
            put("app_data_bytes", appDataSize)
            put("cache_bytes", cacheSize)
            put("internal_available_bytes", internalStorage.availableBytes)
            put("internal_total_bytes", internalStorage.totalBytes)
            put("internal_used_bytes", internalStorage.totalBytes - internalStorage.availableBytes)
            if (externalStorage != null) {
                put("external_available_bytes", externalStorage.availableBytes)
                put("external_total_bytes", externalStorage.totalBytes)
                put("external_used_bytes", externalStorage.totalBytes - externalStorage.availableBytes)
            }
        }
    }

    private fun calculateDirectorySize(directory: java.io.File): Long {
        if (!directory.exists()) return 0L
        if (directory.isFile) return directory.length()

        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    private fun getStorageStats(path: java.io.File): StorageStats {
        val statFs = StatFs(path.absolutePath)
        val blockSize = statFs.blockSizeLong
        val totalBlocks = statFs.blockCountLong
        val availableBlocks = statFs.availableBlocksLong

        return StorageStats(
            totalBytes = totalBlocks * blockSize,
            availableBytes = availableBlocks * blockSize
        )
    }

    private data class StorageStats(
        val totalBytes: Long,
        val availableBytes: Long
    )
}
