package com.lelloman.androidoscopy.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.data.DataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Data provider that exposes app permissions to the dashboard.
 * Shows declared permissions with grant status and protection level.
 */
class PermissionsDataProvider(
    private val context: Context
) : DataProvider {

    override val key: String = "permissions"
    override val interval: Duration = 2.seconds

    /**
     * Get action handlers for permission operations.
     */
    fun getActionHandlers(): Map<String, suspend (Map<String, Any>) -> ActionResult> = mapOf(
        "permissions_refresh" to ::handleRefresh
    )

    override suspend fun collect(): Map<String, Any> {
        val permissions = getAppPermissions()

        val grantedCount = permissions.count { it["status"] == "GRANTED" }
        val deniedCount = permissions.count { it["status"] == "DENIED" }
        val dangerousCount = permissions.count { it["protection_level"] == "DANGEROUS" }

        return mapOf(
            "permissions" to permissions,
            "total_count" to permissions.size,
            "granted_count" to grantedCount,
            "denied_count" to deniedCount,
            "dangerous_count" to dangerousCount
        )
    }

    private fun getAppPermissions(): List<Map<String, Any>> {
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }

        val requestedPermissions = packageInfo.requestedPermissions ?: return emptyList()

        return requestedPermissions.mapIndexed { index, permission ->
            val isGranted = context.checkSelfPermission(permission) ==
                    PackageManager.PERMISSION_GRANTED

            val permissionInfo = try {
                context.packageManager.getPermissionInfo(permission, 0)
            } catch (e: Exception) {
                null
            }

            val protectionLevel = getProtectionLevel(permissionInfo)
            val label = permissionInfo?.loadLabel(context.packageManager)?.toString()
                ?: permission.substringAfterLast('.')

            mapOf(
                "name" to permission,
                "label" to label,
                "status" to if (isGranted) "GRANTED" else "DENIED",
                "protection_level" to protectionLevel,
                "is_dangerous" to (protectionLevel == "DANGEROUS"),
                "group" to (permissionInfo?.group ?: "")
            )
        }
    }

    private fun getProtectionLevel(permissionInfo: PermissionInfo?): String {
        if (permissionInfo == null) return "UNKNOWN"

        val protection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissionInfo.protection
        } else {
            @Suppress("DEPRECATION")
            permissionInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        }

        return when (protection) {
            PermissionInfo.PROTECTION_NORMAL -> "NORMAL"
            PermissionInfo.PROTECTION_DANGEROUS -> "DANGEROUS"
            PermissionInfo.PROTECTION_SIGNATURE -> "SIGNATURE"
            PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM -> "SIGNATURE_OR_SYSTEM"
            else -> "UNKNOWN"
        }
    }

    private suspend fun handleRefresh(args: Map<String, Any>): ActionResult {
        return ActionResult.success("Refreshed", collect())
    }
}
