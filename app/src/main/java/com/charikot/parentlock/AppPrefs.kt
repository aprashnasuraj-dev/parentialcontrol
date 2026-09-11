package com.charikot.parentlock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object AppPrefs {
    private lateinit var prefs: SharedPreferences
    private const val NAME = "charikot_local_settings"

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    fun language(): AppLanguage = if (prefs.getString("language", "ne") == "en") AppLanguage.ENGLISH else AppLanguage.NEPALI
    fun setLanguage(language: AppLanguage) = prefs.edit().putString("language", if (language == AppLanguage.ENGLISH) "en" else "ne").apply()

    fun protectionEnabled(): Boolean = prefs.getBoolean("protection_enabled", true)
    fun setProtectionEnabled(value: Boolean) = prefs.edit().putBoolean("protection_enabled", value).apply()

    fun pinConfigured(): Boolean = prefs.contains("pin_hash") && prefs.contains("pin_salt")
    fun pinSalt(): ByteArray? = prefs.getString("pin_salt", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
    fun pinHash(): String? = prefs.getString("pin_hash", null)
    fun storePin(salt: ByteArray, hash: ByteArray) {
        prefs.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun blockedApps(): Set<String> = prefs.getStringSet("blocked_apps", emptySet())?.toSet() ?: emptySet()
    fun isBlocked(pkg: String): Boolean = blockedApps().contains(pkg)
    fun setBlocked(pkg: String, blocked: Boolean) {
        val next = blockedApps().toMutableSet()
        if (blocked) next.add(pkg) else next.remove(pkg)
        prefs.edit().putStringSet("blocked_apps", next).apply()
    }

    fun scheduleAlways(): Boolean = prefs.getBoolean("schedule_always", true)
    fun setScheduleAlways(value: Boolean) = prefs.edit().putBoolean("schedule_always", value).apply()
    fun scheduleStartMinute(): Int = prefs.getInt("schedule_start", 8 * 60)
    fun scheduleEndMinute(): Int = prefs.getInt("schedule_end", 20 * 60)
    fun scheduleDaysMask(): Int = prefs.getInt("schedule_days", 0b1111111)
    fun saveSchedule(always: Boolean, start: Int, end: Int, mask: Int) {
        prefs.edit().putBoolean("schedule_always", always).putInt("schedule_start", start)
            .putInt("schedule_end", end).putInt("schedule_days", mask).apply()
    }

    fun recordAttempt(pkg: String, label: String, now: Long = System.currentTimeMillis()) {
        val arr = attemptsJson()
        val out = JSONArray()
        out.put(JSONObject().put("package", pkg).put("label", label).put("time", now))
        val limit = minOf(arr.length(), 99)
        for (i in 0 until limit) out.put(arr.optJSONObject(i))
        prefs.edit().putString("attempts", out.toString()).apply()
    }

    fun attemptsJson(): JSONArray = try { JSONArray(prefs.getString("attempts", "[]")) } catch (_: Exception) { JSONArray() }
    fun clearAttempts() = prefs.edit().remove("attempts").apply()

    fun attemptsToday(): Int {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val arr = attemptsJson()
        var count = 0
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i)?.optLong("time") ?: continue
            if (Instant.ofEpochMilli(t).atZone(zone).toLocalDate() == today) count++
        }
        return count
    }
}
