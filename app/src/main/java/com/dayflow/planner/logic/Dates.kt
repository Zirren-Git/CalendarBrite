package com.dayflow.planner.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Pure date helpers shared by screens and view models. No Android dependencies
 * so it is unit-testable on the desktop JVM (see :app:runSelfTests).
 */
object Dates {

    val iso: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

    fun fromEpochDay(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    fun toEpochDay(day: LocalDate): Long = day.toEpochDay()

    /** Monday-based first day of week. */
    fun weekStart(day: LocalDate): LocalDate =
        day.minusDays((day.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    fun monthStart(day: LocalDate): LocalDate = day.withDayOfMonth(1)

    /** ISO week number (1..53). */
    fun weekOfYear(day: LocalDate): Int =
        day.get(WeekFields.ISO.weekOfWeekBasedYear())

    /**
     * 42 cells (6 weeks) covering the month of [anchor]; grid starts Monday.
     */
    fun monthGrid(anchor: LocalDate): List<LocalDate> {
        val first = anchor.withDayOfMonth(1)
        val offset = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val start = first.minusDays(offset.toLong())
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }

    /** [today] = "Today", tomorrow = "Tomorrow", otherwise e.g. "Fri, 24 Oct". */
    fun pretty(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(iso)
    }

    fun dayLabel(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))

    fun shortMonthLabel(year: Int, month: java.time.Month): String =
        LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

    fun greeting(hour: Int): String = when {
        hour < 5 -> "Still up?"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 22 -> "Good evening"
        else -> "Time to wind down"
    }
}

/** Minutes-from-midnight <-> "HH:mm" helpers for due times. */
object Times {
    fun label(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun parse(hhmm: String): Int? {
        val m = Regex("""(\d{1,2}):(\d{2})""").matchEntire(hhmm.trim()) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }
}
