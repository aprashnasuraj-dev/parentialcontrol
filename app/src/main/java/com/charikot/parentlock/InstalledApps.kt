package com.charikot.parentlock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

 data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable)

object InstalledApps {
    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val homePackages = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        ).map { it.activityInfo.packageName }.toSet()

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        return pm.queryIntentActivities(launcher, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || pkg in homePackages || !seen.add(pkg)) return@mapNotNull null
                val label = ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
                val icon = try { ri.loadIcon(pm) } catch (_: Exception) { pm.defaultActivityIcon }
                LaunchableApp(pkg, label, icon)
            }
            .sortedWith(compareByDescending<LaunchableApp> { AppPrefs.isBlocked(it.packageName) }.thenBy { it.label.lowercase() })
    }
}
