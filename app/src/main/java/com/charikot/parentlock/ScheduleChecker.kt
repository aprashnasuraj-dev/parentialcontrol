package com.charikot.parentlock

import java.time.DayOfWeek
import java.time.LocalDateTime

object ScheduleChecker {
    fun shouldBlock(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (AppPrefs.scheduleAlways()) return true
        val start = AppPrefs.scheduleStartMinute()
        val end = AppPrefs.scheduleEndMinute()
        val mask = AppPrefs.scheduleDaysMask()
        if (mask == 0) return false
        val minute = now.hour * 60 + now.minute
        val todayIndex = now.dayOfWeek.value - 1
        val todaySelected = mask and (1 shl todayIndex) != 0
        if (start == end) return todaySelected
        if (start < end) return todaySelected && minute in start until end
        if (minute >= start) return todaySelected
        val previousIndex = (todayIndex + 6) % 7
        val previousSelected = mask and (1 shl previousIndex) != 0
        return minute < end && previousSelected
    }
}
