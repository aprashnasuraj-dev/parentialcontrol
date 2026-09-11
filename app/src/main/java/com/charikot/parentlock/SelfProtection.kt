package com.charikot.parentlock

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object SelfProtection {
    fun adminComponent(context: Context) = ComponentName(context, CharikotDeviceAdminReceiver::class.java)

    private fun manager(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isAdminActive(context: Context): Boolean =
        manager(context).isAdminActive(adminComponent(context))

    fun isDeviceOwner(context: Context): Boolean =
        manager(context).isDeviceOwnerApp(context.packageName)

    fun requestAdmin(activity: Activity) {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(activity))
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, T.deviceAdminExplanation)
        }
        activity.startActivity(intent)
    }

    /**
     * Full uninstall blocking is only available when Charikot has been provisioned
     * as Device Owner. Standard Device Admin still adds an explicit deactivation
     * step before uninstall on supported Android builds, but is not absolute.
     */
    fun enforceUninstallBlock(context: Context) {
        if (!isDeviceOwner(context)) return
        try {
            manager(context).setUninstallBlocked(adminComponent(context), context.packageName, true)
        } catch (_: SecurityException) {
        } catch (_: RuntimeException) {
        }
    }
}
