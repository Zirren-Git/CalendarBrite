package com.dayflow.planner.logic

import java.time.LocalDate

enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY }

/**
 * Pure recurring-task date logic.
 *
 * A recurring task is a *template*: the user sets an [anchor] start day and a
 * rule; each day the template "occurs" if [occursOn] says so. Completion of an
 * occurrence is recorded separately (per day), so the template never gets
 * stuck completed forever.
 *
 * - NONE: the task is a plain one-off and lives on its due day (handled by
 *   the caller; [occursOn] returns `day == anchor` for NONE).
 * - DAILY: every day on/after the anchor.
 * - WEEKLY: same weekday as the anchor (on/after the anchor).
 * - MONTHLY: same day-of-month as the anchor, clamped to the last day of
 *   shorter months (an anchor on Jan 31 occurs on Feb 28/29, Mar 31, …).
 */
object RecurrenceLogic {

    /** Safety horizon when scanning for the next occurrence. */
    const val MAX_SCAN_DAYS = 400

    fun occursOn(rule: Recurrence, anchor: LocalDate, day: LocalDate): Boolean {
        if (day.isBefore(anchor)) return false
        return when (rule) {
            Recurrence.NONE -> day == anchor
            Recurrence.DAILY -> true
            Recurrence.WEEKLY -> day.dayOfWeek == anchor.dayOfWeek
            Recurrence.MONTHLY -> {
                val target = minOf(anchor.dayOfMonth, day.lengthOfMonth())
                day.dayOfMonth == target
            }
        }
    }

    /**
     * First occurrence on or after [from], or `null` when the rule has no
     * future occurrence within the scan horizon (or is NONE).
     */
    fun nextOccurrence(rule: Recurrence, anchor: LocalDate, from: LocalDate): LocalDate? {
        if (rule == Recurrence.NONE) return null
        var day = if (from.isBefore(anchor)) anchor else from
        repeat(MAX_SCAN_DAYS) {
            if (occursOn(rule, anchor, day)) return day
            day = day.plusDays(1)
        }
        return null
    }

    /** All occurrences within the inclusive range [from]..[to] (capped). */
    fun occurrencesInRange(rule: Recurrence, anchor: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (rule == Recurrence.NONE || to.isBefore(from)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var day = if (from.isBefore(anchor)) anchor else from
        var guard = 0
        while (!day.isAfter(to) && guard++ < MAX_SCAN_DAYS) {
            if (occursOn(rule, anchor, day)) result += day
            day = day.plusDays(1)
        }
        return result
    }
}
