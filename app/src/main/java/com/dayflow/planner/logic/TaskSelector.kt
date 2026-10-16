package com.dayflow.planner.logic

import com.dayflow.planner.data.Priorities
import com.dayflow.planner.data.TaskEntity
import java.time.LocalDate

/**
 * A task "as shown on a specific day": either a one-off task due that day, or
 * an occurrence of a recurring template. Pure logic — unit-tested on the JVM.
 */
data class TaskInstance(
    val task: TaskEntity,
    val day: LocalDate?,
    val recurring: Boolean,
    val done: Boolean,
    val subtasksDone: Int = 0,
    val subtasksTotal: Int = 0,
) {
    val overdue: Boolean
        get() = !done && day != null && day.isBefore(LocalDate.now())

    val sortKey: Int
        get() = when {
            !recurring && done -> 3
            overdue -> 0
            task.dueTime == null -> 2
            else -> 1
        }
}

object TaskSelector {

    /** tasks on [day] including recurring occurrences, sorted for display. */
    fun forDay(
        tasks: List<TaskEntity>,
        completions: Map<Long, Set<Long>>,
        day: LocalDate,
        subtaskProgress: Map<Long, Pair<Int, Int>> = emptyMap(),
    ): List<TaskInstance> = instancesOn(tasks, completions, listOf(day), subtaskProgress)

    /** Occurrences within [fromDay]..(fromDay + dayCount - 1). */
    fun forRange(
        tasks: List<TaskEntity>,
        completions: Map<Long, Set<Long>>,
        fromDay: LocalDate,
        dayCount: Int,
        subtaskProgress: Map<Long, Pair<Int, Int>> = emptyMap(),
    ): Map<LocalDate, List<TaskInstance>> {
        val days = (0 until dayCount).map { fromDay.plusDays(it.toLong()) }
        val byDay = days.associateWith { mutableListOf<TaskInstance>() }
        for (task in tasks) {
            val rule = runCatching { Recurrence.valueOf(task.recurrence) }.getOrDefault(Recurrence.NONE)
            if (rule == Recurrence.NONE) {
                val dueDay = task.dueDay ?: continue
                val date = Dates.fromEpochDay(dueDay)
                if (date in days) byDay.getValue(date).add(instance(task, date, false, completions, subtaskProgress))
            } else {
                val anchor = Dates.fromEpochDay(task.anchorDay)
                for (date in days) {
                    if (RecurrenceLogic.occursOn(rule, anchor, date)) {
                        byDay.getValue(date).add(instance(task, date, true, completions, subtaskProgress))
                    }
                }
            }
        }
        return byDay.mapValues { (_, list) -> sortForDay(list) }
    }

    /** The "All" view: one row per stored task (templates once, not per-day). */
    fun asList(
        tasks: List<TaskEntity>,
        subtaskProgress: Map<Long, Pair<Int, Int>> = emptyMap(),
    ): List<TaskInstance> = tasks.map { t ->
        TaskInstance(
            task = t,
            day = if (t.isRecurring) Dates.fromEpochDay(t.anchorDay) else t.dueDay?.let(Dates::fromEpochDay),
            recurring = t.isRecurring,
            done = !t.isRecurring && t.completedAt != null,
            subtasksDone = subtaskProgress[t.id]?.first ?: 0,
            subtasksTotal = subtaskProgress[t.id]?.second ?: 0,
        )
    }.sortedWith(
        compareBy<TaskInstance> { it.sortKey }
            .thenByDescending { it.task.priority }
            .thenByDescending { it.task.id },
    )

    /** Completed-tasks view: newest completion first. */
    fun completedList(
        tasks: List<TaskEntity>,
        subtaskProgress: Map<Long, Pair<Int, Int>> = emptyMap(),
    ): List<TaskInstance> = tasks
        .filter { !it.isRecurring && it.completedAt != null }
        .sortedByDescending { it.completedAt }
        .map {
            TaskInstance(
                task = it,
                day = it.dueDay?.let(Dates::fromEpochDay),
                recurring = false,
                done = true,
                subtasksDone = subtaskProgress[it.id]?.first ?: 0,
                subtasksTotal = subtaskProgress[it.id]?.second ?: 0,
            )
        }

    fun matches(
        instance: TaskInstance,
        query: String,
        tag: String? = null,
        priority: Int? = null,
    ): Boolean {
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val hit = instance.task.title.lowercase().contains(q) ||
                instance.task.notes.lowercase().contains(q) ||
                instance.task.tags.any { it.lowercase().contains(q) }
            if (!hit) return false
        }
        if (tag != null && instance.task.tags.none { it.equals(tag, ignoreCase = true) }) return false
        if (priority != null && instance.task.priority != priority) return false
        return true
    }

    /**
     * Today's dashboard figures: (doneCount, totalCount) of the visible list.
     */
    fun progress(instances: List<TaskInstance>): Pair<Int, Int> {
        val total = instances.size
        val done = instances.count { it.done }
        return done to total
    }

    private fun instancesOn(
        tasks: List<TaskEntity>,
        completions: Map<Long, Set<Long>>,
        days: List<LocalDate>,
        subtaskProgress: Map<Long, Pair<Int, Int>>,
    ): List<TaskInstance> {
        val out = mutableListOf<TaskInstance>()
        val daySet = days.toSet()
        for (task in tasks) {
            val rule = runCatching { Recurrence.valueOf(task.recurrence) }.getOrDefault(Recurrence.NONE)
            if (rule == Recurrence.NONE) {
                val dueDay = task.dueDay?.let(Dates::fromEpochDay)
                if (dueDay in daySet) out.add(instance(task, dueDay, false, completions, subtaskProgress))
            } else {
                val anchor = Dates.fromEpochDay(task.anchorDay)
                for (date in daySet) {
                    if (RecurrenceLogic.occursOn(rule, anchor, date)) {
                        out.add(instance(task, date, true, completions, subtaskProgress))
                    }
                }
            }
        }
        return sortForDay(out)
    }

    private fun instance(
        task: TaskEntity,
        day: LocalDate?,
        recurring: Boolean,
        completions: Map<Long, Set<Long>>,
        subtaskProgress: Map<Long, Pair<Int, Int>>,
    ): TaskInstance {
        val done = if (recurring) {
            day != null && completions[task.id]?.contains(Dates.toEpochDay(day)) == true
        } else {
            task.completedAt != null
        }
        val prog = subtaskProgress[task.id]
        return TaskInstance(task, day, recurring, done, prog?.first ?: 0, prog?.second ?: 0)
    }

    private fun sortForDay(list: List<TaskInstance>): List<TaskInstance> = list.sortedWith(
        compareBy<TaskInstance> { it.sortKey }
            .thenBy { it.task.dueTime ?: Int.MAX_VALUE }
            .thenByDescending { it.task.priority }
            .thenByDescending { it.task.id },
    )
}
