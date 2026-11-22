package com.devekene.kalenda.domain.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LocalDay(
    val year: Int,
    val month: Int, // 1..12
    val dayOfMonth: Int // 1..31
) : Comparable<LocalDay> {

    override fun compareTo(other: LocalDay): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return dayOfMonth.compareTo(other.dayOfMonth)
    }

    fun toCalendar(hour: Int = 0, minute: Int = 0, second: Int = 0, millisecond: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, millisecond)
        }

    fun startOfDayMillis(): Long = toCalendar(0, 0, 0, 0).timeInMillis

    fun endOfDayMillis(): Long = toCalendar(23, 59, 59, 999).timeInMillis

    fun atTimeMillis(hour: Int, minute: Int): Long = toCalendar(hour, minute, 0, 0).timeInMillis

    fun plusDays(days: Int): LocalDay {
        val cal = toCalendar(12, 0, 0, 0)
        cal.add(Calendar.DAY_OF_YEAR, days)
        return fromCalendar(cal)
    }

    /** Returns 1 = Sunday .. 7 = Saturday matching Calendar.DAY_OF_WEEK */
    fun dayOfWeek(): Int = toCalendar(12, 0, 0, 0).get(Calendar.DAY_OF_WEEK)

    fun isWeekend(): Boolean {
        val dow = dayOfWeek()
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    fun toYearMonth(): YearMonthKey = YearMonthKey(year, month)

    fun formatFullDate(): String {
        val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return fmt.format(Date(atTimeMillis(12, 0)))
    }

    fun formatShortDate(): String {
        val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
        return fmt.format(Date(atTimeMillis(12, 0)))
    }

    fun formatWeekdayShort(): String {
        val fmt = SimpleDateFormat("EEE", Locale.getDefault())
        return fmt.format(Date(atTimeMillis(12, 0)))
    }

    companion object {
        fun today(): LocalDay = fromCalendar(Calendar.getInstance())

        fun fromEpochMillis(millis: Long): LocalDay {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return fromCalendar(cal)
        }

        fun fromCalendar(cal: Calendar): LocalDay = LocalDay(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}

data class YearMonthKey(
    val year: Int,
    val month: Int // 1..12
) {
    fun plusMonths(delta: Int): YearMonthKey {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, delta)
        }
        return YearMonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun daysInMonth(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** 0 = Sunday .. 6 = Saturday */
    fun firstDayOffsetSundayStart(): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    fun startMillis(): Long = LocalDay(year, month, 1).startOfDayMillis()

    fun endMillis(): Long = LocalDay(year, month, daysInMonth()).endOfDayMillis()

    fun formatMonthYear(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return fmt.format(cal.time)
    }

    companion object {
        fun current(): YearMonthKey {
            val today = LocalDay.today()
            return YearMonthKey(today.year, today.month)
        }
    }
}

object DateTimeUtils {
    fun formatTime(millis: Long): String {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatHourLabel(hour24: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, 0)
        }
        val fmt = SimpleDateFormat("h a", Locale.getDefault())
        return fmt.format(cal.time)
    }

    fun formatTimeRange(startMillis: Long, endMillis: Long, isAllDay: Boolean): String {
        if (isAllDay) return "All day"
        return "${formatTime(startMillis)} – ${formatTime(endMillis)}"
    }

    fun hourOf(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    fun minuteOf(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.MINUTE)
    }

    fun durationMinutes(startMillis: Long, endMillis: Long): Int =
        ((endMillis - startMillis) / 60_000L).toInt().coerceAtLeast(15)

    fun moveEventToDayAndMinutes(
        originalStart: Long,
        originalEnd: Long,
        targetDay: LocalDay,
        newStartMinutesFromMidnight: Int
    ): Pair<Long, Long> {
        val durationMs = (originalEnd - originalStart).coerceAtLeast(30 * 60_000L)
        val clampedMinutes = newStartMinutesFromMidnight.coerceIn(0, 23 * 60 + 45)
        val newHour = clampedMinutes / 60
        val newMin = clampedMinutes % 60
        val newStart = targetDay.atTimeMillis(newHour, newMin)
        val maxEnd = targetDay.endOfDayMillis()
        val newEnd = (newStart + durationMs).coerceAtMost(maxEnd)
        return newStart to newEnd.coerceAtLeast(newStart + 15 * 60_000L)
    }
}
