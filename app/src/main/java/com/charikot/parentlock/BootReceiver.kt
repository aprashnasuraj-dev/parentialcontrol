package com.charikot.parentlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AppPrefs.init(context.applicationContext)
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            SelfProtection.enforceUninstallBlock(context.applicationContext)
            ProtectionAccess.startIfReady(context.applicationContext)
        }
    }
}
