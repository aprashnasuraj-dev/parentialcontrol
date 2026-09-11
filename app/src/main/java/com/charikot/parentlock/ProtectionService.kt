package com.charikot.parentlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

class ProtectionService : Service() {
    companion object {
        private const val CHANNEL = "charikot_protection"
        private const val NOTIFICATION_ID = 1701
    }

    private lateinit var wm: WindowManager
    private lateinit var usage: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastQuery = 0L
    private var foregroundPackage: String? = null
    private var overlay: View? = null
    private var overlayPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        AppPrefs.init(applicationContext)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        seedForeground()
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }

    private val poll = object : Runnable {
        override fun run() {
            try {
                if (!AppPrefs.protectionEnabled() || !ProtectionAccess.ready(this@ProtectionService)) {
                    hideOverlay()
                } else {
                    updateForeground()
                    evaluate()
                }
            } catch (_: Throwable) {
            }
            handler.postDelayed(this, 450L)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val name = if (AppPrefs.language() == AppLanguage.NEPALI) "Charikot सुरक्षा" else "Charikot protection"
            val channel = NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = if (AppPrefs.language() == AppLanguage.NEPALI) {
                "रोकिएका एप निगरानी गर्न Charikot चलिरहेको छ।"
            } else {
                "Charikot is running to monitor selected locked apps."
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle("Charikot")
                .setContentText(if (AppPrefs.language() == AppLanguage.NEPALI) "एप सुरक्षा चालु छ" else "App protection is active")
                .setOngoing(true)
                .setContentIntent(pending)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(applicationInfo.icon)
                .setContentTitle("Charikot")
                .setContentText(if (AppPrefs.language() == AppLanguage.NEPALI) "एप सुरक्षा चालु छ" else "App protection is active")
                .setOngoing(true)
                .setContentIntent(pending)
                .build()
        }
    }

    private fun seedForeground() {
        val now = System.currentTimeMillis()
        lastQuery = now - 60_000L
        try {
            foregroundPackage = usage.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000L,
                now
            )?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (_: Throwable) {
        }
    }

    private fun updateForeground() {
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(lastQuery, now + 10L)
        val event = UsageEvents.Event()
        var latestTs = 0L
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            if ((type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND) && event.timeStamp >= latestTs) {
                latestTs = event.timeStamp
                latest = event.packageName
            }
        }
        if (latest != null) foregroundPackage = latest
        lastQuery = now
    }

    private fun evaluate() {
        val pkg = foregroundPackage ?: return
        if (pkg == packageName || !AppPrefs.isBlocked(pkg) || !ScheduleChecker.shouldBlock()) {
            hideOverlay()
            return
        }
        showOverlay(pkg)
    }

    private fun showOverlay(pkg: String) {
        if (!ProtectionAccess.hasOverlayAccess(this)) return
        if (overlay != null && overlayPackage == pkg) return
        hideOverlay()

        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
        AppPrefs.recordAttempt(pkg, label)
        overlayPackage = pkg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(42), dp(28), dp(28))
            setBackgroundColor(Color.rgb(245, 248, 252))
        }

        root.addView(TextView(this).apply {
            text = "🔒"
            textSize = 52f
            gravity = Gravity.CENTER
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = T.blockedNow
            textSize = 28f
            setTextColor(Color.rgb(16, 32, 51))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = T.unlockFromCharikotOnly
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }, matchWrap())

        root.addView(Button(this).apply {
            text = T.openCharikot
            isAllCaps = false
            textSize = 16f
            setOnClickListener {
                hideOverlay()
                val parent = Intent(this@ProtectionService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(parent)
            }
        }, buttonLp())

        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(Button(this).apply {
            text = T.goHome
            isAllCaps = false
            textSize = 16f
            setOnClickListener {
                hideOverlay()
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(home)
            }
        }, buttonLp())

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        overlay = root
        try {
            wm.addView(root, params)
        } catch (_: Exception) {
            overlay = null
            overlayPackage = null
        }
    }

    private fun hideOverlay() {
        overlay?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlay = null
        overlayPackage = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun buttonLp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(54)
    ).apply {
        topMargin = dp(7)
    }
}
