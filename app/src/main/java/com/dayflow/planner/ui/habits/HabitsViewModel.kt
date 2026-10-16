package com.dayflow.planner.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.HabitEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.HabitSchedule
import com.dayflow.planner.logic.Streaks
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitRow(
    val habit: HabitEntity,
    val schedule: HabitSchedule,
    val checked: Set<LocalDate>,
    val currentStreak: Int,
    val longestStreak: Int,
    val dueToday: Boolean,
    val doneToday: Boolean,
    val weekDone: Int,
    val weekDue: Int,
)

data class HabitsUiState(
    val rows: List<HabitRow> = emptyList(),
    val archived: List<HabitEntity> = emptyList(),
    val loading: Boolean = true,
)

class HabitsViewModel(app: DayFlowApp) : ViewModel() {

    private val repo = app.container.repository
    private val today = LocalDate.now()

    val state: StateFlow<HabitsUiState> = combine(
        repo.observeAllHabits(),
        repo.observeAllCheckIns(),
    ) { habits, checkIns ->
        val byHabit = checkIns.groupBy { it.habitId }
        val active = habits.filter { !it.archived }
        val rows = active.map { h ->
            val schedule = HabitSchedule(h.daysMask)
            val checked = (byHabit[h.id] ?: emptyList())
                .map { Dates.fromEpochDay(it.dayEpoch) }
                .toSet()
            val weekStart = Dates.weekStart(today)
            val (wd, wu) = Streaks.weekProgress(checked, schedule, weekStart)
            HabitRow(
                habit = h,
                schedule = schedule,
                checked = checked,
                currentStreak = Streaks.currentStreak(checked, schedule, today),
                longestStreak = Streaks.longestStreak(checked, schedule),
                dueToday = schedule.isDue(today),
                doneToday = checked.contains(today),
                weekDone = wd,
                weekDue = wu,
            )
        }.sortedByDescending { it.currentStreak }
        HabitsUiState(
            rows = rows,
            archived = habits.filter { it.archived },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun toggleToday(row: HabitRow) {
        viewModelScope.launch { repo.setCheckedIn(row.habit.id, today, !row.doneToday) }
    }

    fun toggleDay(habitId: Long, day: LocalDate, checked: Boolean) {
        viewModelScope.launch { repo.setCheckedIn(habitId, day, checked) }
    }

    fun addPreset(preset: HabitPreset) {
        viewModelScope.launch {
            repo.saveHabit(
                HabitEntity(
                    name = preset.name,
                    description = preset.description,
                    emoji = preset.emoji,
                    daysMask = preset.daysMask,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun addCustom(name: String, emoji: String, colorArgb: Long, daysMask: Int) {
        val clean = name.trim().take(60)
        if (clean.isEmpty() || daysMask == 0) return
        viewModelScope.launch {
            repo.saveHabit(
                HabitEntity(
                    name = clean,
                    emoji = emoji.ifBlank { "✅" },
                    colorArgb = colorArgb,
                    daysMask = daysMask,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updateHabit(habit: HabitEntity) {
        viewModelScope.launch { repo.saveHabit(habit) }
    }

    fun setArchived(habit: HabitEntity, archived: Boolean) {
        viewModelScope.launch { repo.saveHabit(habit.copy(archived = archived)) }
    }

    fun delete(habit: HabitEntity) {
        viewModelScope.launch { repo.deleteHabit(habit.id) }
    }
}
