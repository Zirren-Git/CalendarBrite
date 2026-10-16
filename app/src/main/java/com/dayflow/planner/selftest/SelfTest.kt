package com.dayflow.planner.selftest

import com.dayflow.planner.data.Priorities
import com.dayflow.planner.data.SubtaskEntity
import com.dayflow.planner.data.TaskEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.HabitSchedule
import com.dayflow.planner.logic.Recurrence
import com.dayflow.planner.logic.RecurrenceLogic
import com.dayflow.planner.logic.Streaks
import com.dayflow.planner.logic.TaskSelector
import com.dayflow.planner.logic.Times
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure-logic self tests, run on the desktop JVM via `./gradlew :app:runSelfTests`.
 * Kept dependency-free (no JUnit is available in this offline toolchain); the
 * asserts run in a plain main() and System.exit(1) on any failure.
 */
object SelfTest {

    private var checks = 0
    private var failures = 0

    private fun check(name: String, condition: Boolean) {
        checks++
        if (!condition) {
            failures++
            println("FAIL  $name")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        dates()
        recurrence()
        habitSchedule()
        streaks()
        times()
        selector()
        println("SelfTest: $checks checks, $failures failures")
        if (failures > 0) System.exit(1)
    }

    private fun dates() {
        val mon = LocalDate.of(2026, 10, 12) // a Monday
        check("weekStart is Monday", Dates.weekStart(LocalDate.of(2026, 10, 17)) == mon)
        check("weekStart idempotent on Monday", Dates.weekStart(mon) == mon)
        check("epoch round-trip", Dates.fromEpochDay(Dates.toEpochDay(mon)) == mon)
        val grid = Dates.monthGrid(LocalDate.of(2026, 2, 1))
        check("monthGrid 42 cells", grid.size == 42)
        check("monthGrid starts Monday", grid.first().dayOfWeek == DayOfWeek.MONDAY)
        check(
            "monthGrid covers all Feb days",
            (1..28).all { d -> grid.contains(LocalDate.of(2026, 2, d)) },
        )
        val today = LocalDate.of(2026, 10, 16)
        check("pretty today", Dates.pretty(today, today) == "Today")
        check("pretty yesterday", Dates.pretty(today.minusDays(1), today) == "Yesterday")
        check("pretty tomorrow", Dates.pretty(today.plusDays(1), today) == "Tomorrow")
        check("greeting morning", Dates.greeting(8).contains("morning", true))
        check("greeting evening", Dates.greeting(21).contains("evening", true))
    }

    private fun recurrence() {
        val anchor = LocalDate.of(2026, 1, 6) // Tuesday
        check("daily on anchor", RecurrenceLogic.occursOn(Recurrence.DAILY, anchor, anchor))
        check("daily before anchor", !RecurrenceLogic.occursOn(Recurrence.DAILY, anchor, anchor.minusDays(1)))
        check("daily always after", RecurrenceLogic.occursOn(Recurrence.DAILY, anchor, anchor.plusDays(365)))
        check("weekly same weekday", RecurrenceLogic.occursOn(Recurrence.WEEKLY, anchor, anchor.plusWeeks(1)))
        check("weekly off day", !RecurrenceLogic.occursOn(Recurrence.WEEKLY, anchor, anchor.plusDays(1)))
        check("none only on anchor", RecurrenceLogic.occursOn(Recurrence.NONE, anchor, anchor))
        check("none not later", !RecurrenceLogic.occursOn(Recurrence.NONE, anchor, anchor.plusDays(1)))
        val jan31 = LocalDate.of(2026, 1, 31)
        check("monthly clamps to Feb 28", RecurrenceLogic.occursOn(Recurrence.MONTHLY, jan31, LocalDate.of(2026, 2, 28)))
        check("monthly skips Feb 27", !RecurrenceLogic.occursOn(Recurrence.MONTHLY, jan31, LocalDate.of(2026, 2, 27)))
        check("monthly leap day", RecurrenceLogic.occursOn(Recurrence.MONTHLY, jan31, LocalDate.of(2028, 2, 29)))
        check("monthly Mar 31", RecurrenceLogic.occursOn(Recurrence.MONTHLY, jan31, LocalDate.of(2026, 3, 31)))
        check(
            "next weekly from gap",
            RecurrenceLogic.nextOccurrence(Recurrence.WEEKLY, anchor, anchor.plusDays(3)) == anchor.plusWeeks(1),
        )
        check("next for NONE is null", RecurrenceLogic.nextOccurrence(Recurrence.NONE, anchor, anchor) == null)
        val days = RecurrenceLogic.occurrencesInRange(Recurrence.DAILY, anchor, anchor, anchor.plusDays(6))
        check("range count daily", days.size == 7)
        val partial = RecurrenceLogic.occurrencesInRange(Recurrence.DAILY, anchor, anchor.plusDays(2), anchor.plusDays(4))
        check("range starts after anchor", partial.size == 3 && partial.first() == anchor.plusDays(2))
        val empty = RecurrenceLogic.occurrencesInRange(Recurrence.DAILY, anchor, anchor.minusDays(5), anchor.minusDays(1))
        check("range before anchor empty", empty.isEmpty())
        val weeklyInRange = RecurrenceLogic.occurrencesInRange(Recurrence.WEEKLY, anchor, anchor, anchor.plusDays(20))
        check("weekly in 3 weeks = 3", weeklyInRange.size == 3)
    }

    private fun habitSchedule() {
        check("daily mask due", HabitSchedule(HabitSchedule.DAILY_MASK).isDue(LocalDate.of(2026, 10, 18)))
        val weekdays = HabitSchedule(HabitSchedule.WEEKDAYS_MASK)
        check("weekdays: Mon due", weekdays.isDue(LocalDate.of(2026, 10, 12)))
        check("weekdays: Sat not due", !weekdays.isDue(LocalDate.of(2026, 10, 17)))
        check("weekdays: Sun not due", !weekdays.isDue(LocalDate.of(2026, 10, 18)))
        val mask = HabitSchedule.maskOf(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY))
        check("maskOf Mon+Sun", HabitSchedule(mask).isDue(LocalDate.of(2026, 10, 18)) &&
            HabitSchedule(mask).isDue(LocalDate.of(2026, 10, 12)) &&
            !HabitSchedule(mask).isDue(LocalDate.of(2026, 10, 14)))
        val wed = LocalDate.of(2026, 10, 14)
        check("nextDue skips gaps", HabitSchedule(mask).nextDue(wed) == LocalDate.of(2026, 10, 18)) // Sunday
        check("previousDueOrOn non-due day", HabitSchedule(mask).previousDueOrOn(wed) == LocalDate.of(2026, 10, 12))
        check("previousDueOrOn due day", HabitSchedule(mask).previousDueOrOn(LocalDate.of(2026, 10, 18)) == LocalDate.of(2026, 10, 18))
        check("isDaily flag", HabitSchedule(0b1111111).isDaily && !HabitSchedule(0b0000001).isDaily)
    }

    private fun streaks() {
        val daily = HabitSchedule(HabitSchedule.DAILY_MASK)
        val t = LocalDate.of(2026, 10, 16)
        val three = setOf(t, t.minusDays(1), t.minusDays(2))
        check("streak of 3", Streaks.currentStreak(three, daily, t) == 3)
        val grace = setOf(t.minusDays(1), t.minusDays(2))
        check("grace today unchecked", Streaks.currentStreak(grace, daily, t) == 2)
        val broken = setOf(t.minusDays(1), t.minusDays(3))
        check("gap breaks streak", Streaks.currentStreak(broken, daily, t) == 1)
        check("empty streak", Streaks.currentStreak(emptySet(), daily, t) == 0)
        val weekdayOnly = HabitSchedule(1 shl DayOfWeek.FRIDAY.value - 1)
        val fri1 = LocalDate.of(2026, 10, 2)
        val fri2 = LocalDate.of(2026, 10, 9)
        val fri3 = LocalDate.of(2026, 10, 16)
        check(
            "weekly Fridays streak counts",
            Streaks.currentStreak(setOf(fri1, fri2, fri3), weekdayOnly, LocalDate.of(2026, 10, 17)) == 3,
        )
        val long = setOf(fri1, fri2, fri3, fri3.minusDays(7 * 4), fri3.minusDays(7 * 3))
        check("longest beats current", Streaks.longestStreak(long, weekdayOnly) >= 2)
        val week = Streaks.weekProgress(
            checked = setOf(fri1),
            schedule = HabitSchedule(HabitSchedule.WEEKDAYS_MASK),
            weekStart = fri1,
        )
        check("weekProgress 1/5", week == (1 to 5))
    }

    private fun times() {
        check("label pads", Times.label(9 * 60 + 5) == "09:05")
        check("label midnight", Times.label(0) == "00:00")
        check("label end of day", Times.label(23 * 60 + 59) == "23:59")
        check("parse ok", Times.parse("07:30") == 7 * 60 + 30)
        check("parse one-digit hour", Times.parse("9:05") == 9 * 60 + 5)
        check("parse garbage null", Times.parse("lunch") == null)
        check("parse bad minute null", Times.parse("12:5") == null)
    }

    private fun selector() {
        val today = LocalDate.now()
        val oneOff = TaskEntity(
            id = 1, title = "Buy milk", dueDay = Dates.toEpochDay(today),
            priority = Priorities.HIGH, tags = listOf("errand"),
        )
        val past = TaskEntity(id = 2, title = "Old", dueDay = Dates.toEpochDay(today.minusDays(2)))
        val recurring = TaskEntity(
            id = 3, title = "Stretch", recurrence = Recurrence.DAILY.name,
            anchorDay = Dates.toEpochDay(today.minusDays(3)),
        )
        val completedRecurring = 3L to Dates.toEpochDay(today)
        val tasks = listOf(oneOff, past, recurring)
        val completions = mapOf(3L to setOf(Dates.toEpochDay(today)))

        val todayList = TaskSelector.forDay(tasks, completions, today)
        check("today shows one-off + occurrence", todayList.size == 2)
        check("past one-off hidden", todayList.none { it.task.id == 2L })
        val stretch = todayList.first { it.task.id == 3L }
        check("recurring instance done via completion", stretch.done && stretch.recurring)
        check("recurring sorts after open (done)", todayList.first().task.id == 1L)
        val yesterday = TaskSelector.forDay(tasks, completions, today.minusDays(2))
        check("yesterday shows past + unfinished occurrence", yesterday.size == 2)
        check("yesterday occurrence not done", yesterday.first { it.task.id == 3L }.done.not())

        val range = TaskSelector.forRange(tasks, completions, today.minusDays(1), 3)
        check("range days = 3", range.size == 3)
        check("range has occurrence each day", range.values.count { list -> list.any { it.task.id == 3L } } == 3)

        val all = TaskSelector.asList(tasks)
        check("asList one row per template", all.size == 3)
        val done = TaskSelector.completedList(listOf(oneOff.copy(completedAt = 123L), recurring))
        check("completedList only done one-offs", done.size == 1 && done.first().task.id == 1L)

        check("matches query", TaskSelector.matches(all.first { it.task.id == 1L }, "MILK"))
        check("matches tag", TaskSelector.matches(all.first { it.task.id == 1L }, "", tag = "errand"))
        check("matches priority", TaskSelector.matches(all.first { it.task.id == 1L }, "", priority = Priorities.HIGH))
        check("matches rejects", !TaskSelector.matches(all.first { it.task.id == 1L }, "taxes"))
        check("matches tag rejects", !TaskSelector.matches(all.first { it.task.id == 1L }, "", tag = "work"))

        val withSubs = TaskSelector.forDay(
            tasks,
            completions,
            today,
            subtaskProgress = mapOf(1L to (1 to 2)),
        )
        val milk = withSubs.first { it.task.id == 1L }
        check("subtask progress plumbed", milk.subtasksDone == 1 && milk.subtasksTotal == 2)
        val prog = TaskSelector.progress(withSubs)
        check("progress done/total", prog == (1 to 2))

        // TaskEntity recurrence helpers used by data layer
        check("isRecurring true", recurring.isRecurring && !oneOff.isRecurring)
    }
}
