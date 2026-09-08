package com.charikot.parentlock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private enum class Screen { AUTH, DASHBOARD, APPS, SCHEDULE, REPORTS, SETTINGS }
    private var screen = Screen.AUTH
    private var authorizedUntil = 0L
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(applicationContext)
        window.statusBarColor = Color.rgb(16, 32, 51)
        window.navigationBarColor = Color.rgb(16, 32, 51)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 248, 252))
        }
        setContentView(root)
        if (!AppPrefs.pinConfigured()) showSetup() else showAuth()
    }

    override fun onResume() {
        super.onResume()
        ProtectionAccess.startIfReady(this)
        if (AppPrefs.pinConfigured() && screen != Screen.AUTH && !isAuthorized()) {
            showAuth()
        } else if (screen == Screen.DASHBOARD && isAuthorized()) {
            showDashboard()
        }
    }

    override fun onStop() {
        super.onStop()
        if (AppPrefs.pinConfigured() && screen != Screen.AUTH) authorizedUntil = 0L
    }

    override fun onBackPressed() {
        when (screen) {
            Screen.APPS, Screen.SCHEDULE, Screen.REPORTS, Screen.SETTINGS -> showDashboard()
            Screen.DASHBOARD -> {
                authorizedUntil = 0L
                showAuth()
            }
            else -> super.onBackPressed()
        }
    }

    private fun isAuthorized() = System.currentTimeMillis() < authorizedUntil

    private fun showSetup() {
        screen = Screen.AUTH
        root.removeAllViews()
        root.addView(header("Charikot", T.parentControl, showBack = false))
        val content = verticalScroll()
        content.addView(heroCard(T.welcome, T.serviceDisclosure))
        content.addView(sectionTitle(T.language))
        content.addView(languageButtons { showSetup() })
        content.addView(sectionTitle(T.createPin))
        val pin1 = pinField(T.pinHint)
        val pin2 = pinField(T.confirmPin)
        content.addView(pin1, fieldLp())
        content.addView(pin2, fieldLp())
        content.addView(primaryButton(T.continueText) {
            val a = pin1.text.toString()
            val b = pin2.text.toString()
            if (!(a.length == 4 || a.length == 6) || !a.all { it.isDigit() }) {
                pin1.error = T.invalidPin
            } else if (a != b) {
                pin2.error = T.pinsMismatch
            } else if (PinManager.setPin(a)) {
                authorizedUntil = System.currentTimeMillis() + 5 * 60_000L
                showDashboard()
            }
        }, buttonMarginLp())
    }

    private fun showAuth() {
        screen = Screen.AUTH
        root.removeAllViews()
        root.addView(header("Charikot", T.parentArea, showBack = false))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
        }
        content.addView(TextView(this).apply { text = "🔐"; textSize = 54f; gravity = Gravity.CENTER }, lp(-1, -2))
        content.addView(TextView(this).apply {
            text = T.enterParentPin
            textSize = 25f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(16, 32, 51))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }, lp(-1, -2))
        val pin = pinField(T.pinHint)
        content.addView(pin, fieldLp())
        content.addView(primaryButton(T.unlock) {
            if (PinManager.verify(pin.text.toString())) {
                authorizedUntil = System.currentTimeMillis() + 5 * 60_000L
                hideKeyboard(pin)
                showDashboard()
            } else {
                pin.text?.clear(); pin.error = T.wrongPin
            }
        }, buttonMarginLp())
        content.addView(languageButtons { showAuth() })
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun showDashboard() {
        if (!isAuthorized()) return showAuth()
        screen = Screen.DASHBOARD
        root.removeAllViews()
        root.addView(header("Charikot", T.parentControl, showBack = false))
        val content = verticalScroll()

        val protection = AppPrefs.protectionEnabled()
        val usageAccess = ProtectionAccess.hasUsageAccess(this)
        val overlayAccess = ProtectionAccess.hasOverlayAccess(this)
        val service = usageAccess && overlayAccess
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(if (protection) Color.rgb(227, 244, 235) else Color.rgb(255, 238, 238), 18)
        }
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusRow.addView(TextView(this).apply {
            text = if (protection) T.protectionOn else T.protectionOff
            textSize = 21f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(16, 32, 51))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        statusRow.addView(Switch(this).apply {
            isChecked = protection
            setOnCheckedChangeListener { _, checked ->
                AppPrefs.setProtectionEnabled(checked)
                if (checked) ProtectionAccess.startIfReady(this@MainActivity) else ProtectionAccess.stop(this@MainActivity)
                showDashboard()
            }
        })
        statusCard.addView(statusRow)
        statusCard.addView(TextView(this).apply {
            text = if (service) "✓ ${T.protectionAccessReady}" else "⚠ ${T.protectionAccessNeeded}"
            textSize = 15f
            setTextColor(if (service) Color.rgb(31, 115, 73) else Color.rgb(180, 70, 20))
            setPadding(0, dp(8), 0, dp(4))
        })
        statusCard.addView(TextView(this).apply {
            text = (if (usageAccess) "✓ " else "○ ") + (if (usageAccess) T.usageAccessOn else T.usageAccessOff)
            textSize = 14f
            setTextColor(if (usageAccess) Color.rgb(31, 115, 73) else Color.DKGRAY)
        })
        statusCard.addView(TextView(this).apply {
            text = (if (overlayAccess) "✓ " else "○ ") + (if (overlayAccess) T.overlayAccessOn else T.overlayAccessOff)
            textSize = 14f
            setTextColor(if (overlayAccess) Color.rgb(31, 115, 73) else Color.DKGRAY)
        })
        if (!usageAccess) statusCard.addView(primaryButton(T.grantUsageAccess) { showProtectionDisclosure(true) }, buttonMarginLp())
        if (!overlayAccess) statusCard.addView(primaryButton(T.grantOverlayAccess) { showProtectionDisclosure(false) }, buttonMarginLp())
        if (service) {
            ProtectionAccess.startIfReady(this)
            requestNotificationPermissionIfNeeded()
        }
        content.addView(statusCard, cardLp())

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard(T.lockedApps, AppPrefs.blockedApps().size.toString()), LinearLayout.LayoutParams(0, dp(104), 1f).apply { rightMargin = dp(6) })
        stats.addView(statCard(T.attemptsToday, AppPrefs.attemptsToday().toString()), LinearLayout.LayoutParams(0, dp(104), 1f).apply { leftMargin = dp(6) })
        content.addView(stats, cardLp())

        content.addView(primaryActionCard("🔒", T.lockUnlockApps, T.lockUnlockSubtitle) { showApps() }, cardLp())
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(smallActionCard("🕒", T.schedule) { showSchedule() }, LinearLayout.LayoutParams(0, dp(112), 1f).apply { rightMargin = dp(6) })
        row.addView(smallActionCard("📋", T.reports) { showReports() }, LinearLayout.LayoutParams(0, dp(112), 1f).apply { leftMargin = dp(6) })
        content.addView(row, cardLp())
        content.addView(outlineButton("⚙ ${T.settings}") { showSettings() }, buttonMarginLp())
    }

    private fun showApps() {
        if (!isAuthorized()) return showAuth()
        screen = Screen.APPS
        root.removeAllViews()
        root.addView(header(T.lockUnlockApps, T.lockUnlockSubtitle, true))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(8))
        }
        val search = EditText(this).apply {
            hint = T.searchApps
            textSize = 16f
            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = rounded(Color.WHITE, 14, Color.LTGRAY)
        }
        container.addView(search, lp(-1, dp(52)).apply { bottomMargin = dp(10) })
        val list = ListView(this).apply { dividerHeight = 1 }
        container.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))

        val all = InstalledApps.load(this)
        val adapter = AppListAdapter(this, all)
        list.adapter = adapter
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { adapter.filter(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private inner class AppListAdapter(private val context: Context, private val all: List<LaunchableApp>) : BaseAdapter() {
        private var shown = all
        fun filter(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isBlank()) all.sortedWith(compareByDescending<LaunchableApp> { AppPrefs.isBlocked(it.packageName) }.thenBy { it.label.lowercase() })
            else all.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            notifyDataSetChanged()
        }
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val app = shown[position]
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8)); setBackgroundColor(Color.WHITE)
            }
            row.addView(ImageView(context).apply { setImageDrawable(app.icon); adjustViewBounds = true }, lp(dp(46), dp(46)).apply { rightMargin = dp(12) })
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(context).apply { text = app.label; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(25, 36, 48)) })
            texts.addView(TextView(context).apply { text = app.packageName; textSize = 11f; setTextColor(Color.GRAY) })
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            val sw = Switch(context).apply {
                isChecked = AppPrefs.isBlocked(app.packageName)
                contentDescription = if (isChecked) T.locked else T.allowed
                setOnCheckedChangeListener { _, checked ->
                    AppPrefs.setBlocked(app.packageName, checked)
                    Toast.makeText(context, "${app.label}: ${if (checked) T.locked else T.allowed}", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(sw)
            return row
        }
    }

    private fun showSchedule() {
        if (!isAuthorized()) return showAuth()
        screen = Screen.SCHEDULE
        root.removeAllViews()
        root.addView(header(T.schedule, "", true))
        val content = verticalScroll()
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val always = RadioButton(this).apply { text = T.alwaysBlock; textSize = 16f; isChecked = AppPrefs.scheduleAlways() }
        val timed = RadioButton(this).apply { text = T.scheduledBlock; textSize = 16f; isChecked = !AppPrefs.scheduleAlways() }
        group.addView(always); group.addView(timed)
        content.addView(group, cardLp())

        var start = AppPrefs.scheduleStartMinute()
        var end = AppPrefs.scheduleEndMinute()
        val startBtn = outlineButton("${T.startTime}: ${formatMinutes(start)}") { }
        val endBtn = outlineButton("${T.endTime}: ${formatMinutes(end)}") { }
        startBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> start = h * 60 + m; startBtn.text = "${T.startTime}: ${formatMinutes(start)}" }, start / 60, start % 60, true).show()
        }
        endBtn.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> end = h * 60 + m; endBtn.text = "${T.endTime}: ${formatMinutes(end)}" }, end / 60, end % 60, true).show()
        }
        content.addView(startBtn, buttonMarginLp()); content.addView(endBtn, buttonMarginLp())
        content.addView(sectionTitle(T.days))
        val labels = listOf(T.monday, T.tuesday, T.wednesday, T.thursday, T.friday, T.saturday, T.sunday)
        val checks = ArrayList<CheckBox>()
        val dayWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(Color.WHITE, 14); setPadding(dp(12), dp(6), dp(12), dp(6)) }
        val currentMask = AppPrefs.scheduleDaysMask()
        for (i in 0..6) {
            val cb = CheckBox(this).apply { text = labels[i]; textSize = 16f; isChecked = currentMask and (1 shl i) != 0 }
            checks.add(cb); dayWrap.addView(cb)
        }
        content.addView(dayWrap, cardLp())
        content.addView(primaryButton(T.save) {
            var mask = 0
            checks.forEachIndexed { i, cb -> if (cb.isChecked) mask = mask or (1 shl i) }
            AppPrefs.saveSchedule(always.isChecked, start, end, mask)
            Toast.makeText(this, T.scheduleSaved, Toast.LENGTH_SHORT).show()
            showDashboard()
        }, buttonMarginLp())
    }

    private fun showReports() {
        if (!isAuthorized()) return showAuth()
        screen = Screen.REPORTS
        root.removeAllViews()
        root.addView(header(T.reports, "${T.attemptsToday}: ${AppPrefs.attemptsToday()}", true))
        val content = verticalScroll()
        val arr = AppPrefs.attemptsJson()
        if (arr.length() == 0) {
            content.addView(heroCard("📋", T.noAttempts), cardLp())
        } else {
            val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val label = obj.optString("label", obj.optString("package"))
                val pkg = obj.optString("package")
                val whenText = formatter.format(Date(obj.optLong("time")))
                val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(Color.WHITE, 14) }
                card.addView(TextView(this).apply { text = label; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(25,36,48)) })
                card.addView(TextView(this).apply { text = whenText; textSize = 13f; setTextColor(Color.DKGRAY) })
                card.addView(TextView(this).apply { text = pkg; textSize = 11f; setTextColor(Color.GRAY) })
                content.addView(card, cardLp())
            }
        }
        content.addView(outlineButton(T.clearReports) {
            AlertDialog.Builder(this).setMessage(T.clearReports + "?").setNegativeButton(T.cancel, null).setPositiveButton(T.ok) { _, _ -> AppPrefs.clearAttempts(); showReports() }.show()
        }, buttonMarginLp())
    }

    private fun showSettings() {
        if (!isAuthorized()) return showAuth()
        screen = Screen.SETTINGS
        root.removeAllViews()
        root.addView(header(T.settings, "Charikot 0.1.1", true))
        val content = verticalScroll()
        content.addView(sectionTitle(T.language))
        content.addView(languageButtons { showSettings() })
        content.addView(outlineButton(T.changePin) { showChangePinDialog() }, buttonMarginLp())
        content.addView(outlineButton(T.lockParentArea) { authorizedUntil = 0L; showAuth() }, buttonMarginLp())
        content.addView(sectionTitle(T.about))
        content.addView(heroCard(T.about, T.limitation), cardLp())
    }

    private fun showChangePinDialog() {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), 0) }
        val a = pinField(T.newPin); val b = pinField(T.confirmPin)
        wrap.addView(a, fieldLp()); wrap.addView(b, fieldLp())
        val dialog = AlertDialog.Builder(this).setTitle(T.changePin).setView(wrap).setNegativeButton(T.cancel, null).setPositiveButton(T.save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val x = a.text.toString(); val y = b.text.toString()
                when {
                    !(x.length == 4 || x.length == 6) || !x.all { it.isDigit() } -> a.error = T.invalidPin
                    x != y -> b.error = T.pinsMismatch
                    PinManager.setPin(x) -> { Toast.makeText(this, T.pinChanged, Toast.LENGTH_SHORT).show(); dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    private fun showProtectionDisclosure(usage: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(T.serviceDisclosureTitle)
            .setMessage(T.serviceDisclosure)
            .setNegativeButton(T.cancel, null)
            .setPositiveButton(T.continueText) { _, _ ->
                if (usage) ProtectionAccess.openUsageSettings(this) else ProtectionAccess.openOverlaySettings(this)
            }
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1701)
        }
    }

    private fun header(title: String, subtitle: String, showBack: Boolean): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(12), dp(16), dp(12)); setBackgroundColor(Color.rgb(16, 32, 51))
            if (showBack) addView(Button(this@MainActivity).apply {
                text = "‹"; textSize = 30f; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { showDashboard() }
            }, lp(dp(52), dp(56)))
            val textWrap = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            textWrap.addView(TextView(this@MainActivity).apply {
                text = title; textSize = 23f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            })
            if (subtitle.isNotBlank()) textWrap.addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 12f; setTextColor(Color.rgb(204, 220, 238)) })
            addView(textWrap, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun verticalScroll(): LinearLayout {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(28)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return body
    }

    private fun languageButtons(onChanged: () -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val en = outlineButton(T.english) { AppPrefs.setLanguage(AppLanguage.ENGLISH); onChanged() }
        val ne = outlineButton(T.nepali) { AppPrefs.setLanguage(AppLanguage.NEPALI); onChanged() }
        row.addView(en, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(6) })
        row.addView(ne, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(6) })
        return row
    }

    private fun heroCard(title: String, subtitle: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = rounded(Color.WHITE, 18)
        addView(TextView(this@MainActivity).apply { text = title; textSize = 21f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(16,32,51)) })
        addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(0, dp(7), 0, 0) })
    }

    private fun statCard(label: String, value: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(Color.WHITE, 16)
        addView(TextView(this@MainActivity).apply { text = value; textSize = 28f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(13,71,161)); gravity = Gravity.CENTER })
        addView(TextView(this@MainActivity).apply { text = label; textSize = 13f; setTextColor(Color.DKGRAY); gravity = Gravity.CENTER })
    }

    private fun primaryActionCard(icon: String, title: String, subtitle: String, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = rounded(Color.rgb(13,71,161), 18)
        addView(TextView(this@MainActivity).apply { text = "$icon  $title"; textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE) })
        addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 13f; setTextColor(Color.rgb(222,235,255)); setPadding(0, dp(6), 0, 0) })
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }

    private fun smallActionCard(icon: String, title: String, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10)); background = rounded(Color.WHITE, 16)
        addView(TextView(this@MainActivity).apply { text = icon; textSize = 28f; gravity = Gravity.CENTER })
        addView(TextView(this@MainActivity).apply { text = title; textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(16,32,51)); gravity = Gravity.CENTER })
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }

    private fun primaryButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 16f; isAllCaps = false; setTextColor(Color.WHITE); background = rounded(Color.rgb(13,71,161), 14); setOnClickListener { action() }
    }

    private fun outlineButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 15f; isAllCaps = false; setTextColor(Color.rgb(13,71,161)); background = rounded(Color.WHITE, 14, Color.rgb(180,196,215)); setOnClickListener { action() }
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.rgb(16,32,51)); setPadding(0, dp(18), 0, dp(8))
    }

    private fun pinField(hintValue: String): EditText = EditText(this).apply {
        hint = hintValue; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        textSize = 20f; gravity = Gravity.CENTER; setSingleLine(true); background = rounded(Color.WHITE, 14, Color.rgb(185,198,213)); setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radiusDp).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun cardLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    private fun fieldLp() = LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(12) }
    private fun buttonMarginLp() = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10); bottomMargin = dp(4) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun formatMinutes(v: Int) = String.format("%02d:%02d", v / 60, v % 60)
    private fun hideKeyboard(v: View) { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) }
}
