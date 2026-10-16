package com.dayflow.planner.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.HabitEntity
import com.dayflow.planner.data.TaskEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.HabitSchedule
import com.dayflow.planner.logic.Streaks
import com.dayflow.planner.logic.TaskInstance
import com.dayflow.planner.logic.TaskSelector
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayUiState(
    val today: LocalDate = LocalDate.now(),
    val tasks: List<TaskInstance> = emptyList(),
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val overdueCount: Int = 0,
    val habitsDue: List<HabitToday> = emptyList(),
    val loaded: Boolean = false,
)

data class HabitToday(
    val habit: HabitEntity,
    val checked: Boolean,
    val streak: Int,
)

class TodayViewModel(app: DayFlowApp) : ViewModel() {

    private val repo = app.container.repository
    private val settings = app.container.settings
    private val scheduler = app

    data class RawSnapshot(
        val tasks: List<TaskEntity>,
        val completions: Map<Long, Set<Long>>,
        val subtasks: Map<Long, Pair<Int, Int>>,
        val habits: List<HabitEntity>,
        val checkIns: Map<Long, Set<Long>>,
    )

    val state: StateFlow<TodayUiState> = combine(
        repo.observeTasks(),
        repo.observeCompletionsSince(LocalDate.now().minusDays(400).toEpochDay()),
        repo.observeSubtasks(),
        repo.observeActiveHabits(),
        repo.observeCheckInsSince(LocalDate.now().minusDays(400).toEpochDay()),
    ) { tasks, completions, subtasks, habits, checkIns ->
        val compByTask = completions.groupBy { it.taskId }.mapValues { e -> e.value.map { it.dayEpoch }.toSet() }
        val subByTask = subtasks.groupBy { it.taskId }
            .mapValues { e -> e.value.count { s -> s.done } to e.value.size }
        val checkByHabit = checkIns.groupBy { it.habitId }
            .mapValues { e -> e.value.map { Dates.fromEpochDay(it.dayEpoch) }.toSet() }

        val today = LocalDate.now()
        val showDone = settings.showCompletedInToday.value
        val all = TaskSelector.forDay(tasks, compByTask, today, subByTask)
        val visible = if (showDone) all else all.filter { !it.done }
        val (done, total) = TaskSelector.progress(all)

        val habitToday = habits
            .filter { HabitSchedule(it.daysMask).isDue(today) }
            .map { h ->
                val set = checkByHabit[h.id] ?: emptySet()
                HabitToday(h, set.contains(today), Streaks.currentStreak(set, HabitSchedule(h.daysMask), today))
            }

        TodayUiState(
            today = today,
            tasks = visible,
            doneCount = done,
            totalCount = total,
            overdueCount = all.count { it.overdue },
            habitsDue = habitToday,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun toggleTask(instance: TaskInstance) {
        viewModelScope.launch {
            if (instance.recurring && instance.day != null) {
                repo.setOccurrenceCompleted(instance.task.id, instance.day, !instance.done)
            } else {
                repo.setTaskCompleted(instance.task, !instance.done)
                if (!instance.done) {
                    com.dayflow.planner.remind.ReminderScheduler.cancel(scheduler, instance.task.id)
                } else {
                    com.dayflow.planner.remind.ReminderScheduler.schedule(scheduler, instance.task)
                }
            }
        }
    }

    fun toggleHabit(item: HabitToday) {
        viewModelScope.launch {
            repo.setCheckedIn(item.habit.id, LocalDate.now(), !item.checked)
        }
    }
}
