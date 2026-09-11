package com.charikot.parentlock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class CharikotDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        AppPrefs.init(context.applicationContext)
        SelfProtection.enforceUninstallBlock(context.applicationContext)
        Toast.makeText(context, T.deviceAdminEnabled, Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        AppPrefs.init(context.applicationContext)
        return T.deviceAdminDisableWarning
    }
}
