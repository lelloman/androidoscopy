package com.lelloman.androidoscopy.buildinfo

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.lelloman.androidoscopy.data.DataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Data provider that exposes build information to the dashboard.
 * Shows app name, package name, version, SDK levels, and optional git SHA.
 */
class BuildInfoDataProvider(
    private val context: Context,
    private val gitSha: String? = null,
    private val buildType: String? = null,
    private val flavor: String? = null
) : DataProvider {

    override val key: String = "build_info"
    // Build info is static, refresh infrequently
    override val interval: Duration = 5.minutes

    override suspend fun collect(): Map<String, Any> {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }

        val appInfo = try {
            context.packageManager.getApplicationInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }

        val versionCode = if (packageInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } else {
            0L
        }

        val appName = appInfo?.let {
            context.packageManager.getApplicationLabel(it).toString()
        } ?: context.packageName

        val isDebuggable = appInfo?.flags?.and(ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo?.minSdkVersion ?: 0
        } else {
            0
        }

        val targetSdk = appInfo?.targetSdkVersion ?: 0

        return buildMap {
            put("app_name", appName)
            put("package_name", context.packageName)
            put("version_name", packageInfo?.versionName ?: "unknown")
            put("version_code", versionCode)
            put("min_sdk", minSdk)
            put("target_sdk", targetSdk)
            put("compile_sdk", Build.VERSION.SDK_INT)
            put("is_debuggable", isDebuggable)
            buildType?.let { put("build_type", it) }
            flavor?.let { put("flavor", it) }
            gitSha?.let { put("git_sha", it) }

            // Device info for context
            put("device_model", Build.MODEL)
            put("device_manufacturer", Build.MANUFACTURER)
            put("android_version", Build.VERSION.RELEASE)
            put("api_level", Build.VERSION.SDK_INT)
        }
    }
}
