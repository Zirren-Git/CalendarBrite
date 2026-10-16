package com.dayflow.planner.logic

import java.time.LocalDate

/**
 * Which weekdays a habit is due on. Bit 0 = Monday … bit 6 = Sunday.
 * 0b111_1111 means "every day".
 */
data class HabitSchedule(val daysMask: Int) {

    val isDaily: Boolean get() = daysMask == DAILY_MASK

    fun isDue(day: LocalDate): Boolean =
        daysMask and (1 shl (day.dayOfWeek.value - 1)) != 0

    /** Next due day strictly after [day]. */
    fun nextDue(day: LocalDate): LocalDate {
        var d = day.plusDays(1)
        while (!isDue(d)) d = d.plusDays(1)
        return d
    }

    /** Last due day on or before [day]. */
    fun previousDueOrOn(day: LocalDate): LocalDate {
        var d = day
        while (!isDue(d)) d = d.minusDays(1)
        return d
    }

    /** Last due day strictly before [day]. */
    fun previousDue(day: LocalDate): LocalDate = previousDueOrOn(day.minusDays(1))

    companion object {
        const val DAILY_MASK = 0b1111111
        const val WEEKDAYS_MASK = 0b0011111
        const val WEEKEND_MASK = 0b1100000

        fun maskOf(days: Set<java.time.DayOfWeek>): Int =
            days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    }
}

object Streaks {

    /**
     * Consecutive due-day check-ins ending today (or the latest due day).
     * An unchecked day *today* does not break the streak yet (grace period).
     */
    fun currentStreak(checked: Set<LocalDate>, schedule: HabitSchedule, today: LocalDate): Int {
        var day = schedule.previousDueOrOn(today)
        if (!checked.contains(day)) {
            if (day != today) return 0
            day = schedule.previousDue(today)
        }
        var count = 0
        while (checked.contains(day) && count < 3650) {
            count++
            day = schedule.previousDue(day)
        }
        return count
    }

    /**
     * Longest run of consecutive due-day check-ins over all history. Two
     * check-ins belong to the same run when no scheduled day lies between them.
     */
    fun longestStreak(checked: Set<LocalDate>, schedule: HabitSchedule): Int {
        if (checked.isEmpty()) return 0
        val days = checked.filter { schedule.isDue(it) }.sorted()
        if (days.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until days.size) {
            val a = days[i - 1]
            val b = days[i]
            if (schedule.nextDue(a) == b) run++ else run = 1
            if (run > best) best = run
        }
        return best
    }

    /** (completed, due) pair for the inclusive week starting [weekStart]. */
    fun weekProgress(
        checked: Set<LocalDate>,
        schedule: HabitSchedule,
        weekStart: LocalDate,
    ): Pair<Int, Int> {
        var done = 0
        var due = 0
        for (i in 0L until 7L) {
            val day = weekStart.plusDays(i)
            if (schedule.isDue(day)) {
                due++
                if (checked.contains(day)) done++
            }
        }
        return done to due
    }
}
