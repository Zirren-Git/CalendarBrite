package com.dayflow.planner.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.DiaryEntryEntity
import com.dayflow.planner.data.HabitEntity
import com.dayflow.planner.data.NoteEntity
import com.dayflow.planner.data.Priorities
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.HabitSchedule
import com.dayflow.planner.logic.TaskInstance
import com.dayflow.planner.logic.TaskSelector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CalendarView { MONTH, WEEK, DAY }

/** five-flow combine payload (kotlinx combine supports at most 5 typed flows) */
data class Quint(
    val vis: Pair<CalendarView, LocalDate>,
    val tasks: List<com.dayflow.planner.data.TaskEntity>,
    val completions: List<com.dayflow.planner.data.TaskCompletionEntity>,
    val habits: List<HabitEntity>,
)

/** One visual item inside a day cell / agenda row. */
sealed interface DayEvent {
    val label: String
    val colorArgb: Long

    data class TaskEv(
        val instance: TaskInstance,
        override val label: String,
        override val colorArgb: Long,
    ) : DayEvent

    data class HabitEv(
        val habit: HabitEntity,
        val day: LocalDate,
        val done: Boolean,
        override val label: String,
        override val colorArgb: Long,
    ) : DayEvent

    data class NoteEv(
        val note: NoteEntity,
        override val label: String,
        override val colorArgb: Long,
    ) : DayEvent

    data class DiaryEv(
        val entry: DiaryEntryEntity,
        override val label: String,
        override val colorArgb: Long,
    ) : DayEvent
}

data class CalendarUiState(
    val ready: Boolean = false,
    val eventsByDay: Map<Long, List<DayEvent>> = emptyMap(),
    val todayEpoch: Long = Dates.toEpochDay(LocalDate.now()),
)

class CalendarViewModel(private val app: DayFlowApp) : ViewModel() {

    private val repo = app.container.repository

    val view = MutableStateFlow(CalendarView.MONTH)
    val focusDay = MutableStateFlow(LocalDate.now())

    data class Visible(val view: CalendarView, val focus: LocalDate)

    val state: StateFlow<CalendarUiState> = combine(
        combine(
            view,
            focusDay,
            repo.observeTasks(),
            repo.observeCompletionsSince(EARLY),
            repo.observeActiveHabits(),
        ) { v, f, tasks, completions, habits ->
            Quint(v to f, tasks, completions, habits)
        },
        repo.observeCheckInsSince(EARLY),
        repo.observeNotes(),
        repo.observeDiary(),
    ) { q, checkIns, notes, diary ->
        val (vis, tasks, completions, habits) = q
        val days = visibleDays(vis.first, vis.second)
        val dayEPOCHs = days.map(Dates::toEpochDay)
        val completionsByTask = completions.groupBy { it.taskId }.mapValues { (_, rows) -> rows.map { it.dayEpoch }.toSet() }
        val checkSet = checkIns.map { it.habitId to it.dayEpoch }.toSet()
        val perDay = dayEPOCHs.mapNotNull { epoch ->
            val day = Dates.fromEpochDay(epoch)
            val evs = mutableListOf<DayEvent>()
            TaskSelector.forDay(tasks, completionsByTask, day).forEach { inst ->
                evs += DayEvent.TaskEv(inst, inst.task.title, priorityArgb(inst.task.priority))
            }
            habits.forEach { h ->
                if (HabitSchedule(h.daysMask).isDue(day)) {
                    evs += DayEvent.HabitEv(
                        habit = h,
                        day = day,
                        done = (h.id to epoch) in checkSet,
                        label = h.emoji + " " + h.name,
                        colorArgb = h.colorArgb,
                    )
                }
            }
            notes.filter { note ->
                note.updatedAt > 0 &&
                    Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault())
                        .toLocalDate().toEpochDay() == epoch
            }.forEach { n ->
                evs += DayEvent.NoteEv(n, if (n.title.isBlank()) "Note" else n.title, 0xFF9A6BD0L)
            }
            diary.firstOrNull { it.dayEpoch == epoch }?.let { d ->
                if (d.text.isNotBlank() || d.mood != null) {
                    val label = d.text.trim().lineSequence().first().take(40)
                    evs += DayEvent.DiaryEv(d, if (label.isBlank()) "Journal" else label, 0xFFC9760AL)
                }
            }
            if (evs.isEmpty()) null else epoch to evs
        }.toMap()

        CalendarUiState(ready = true, eventsByDay = perDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun setView(v: CalendarView) { view.value = v }

    /** Tapping a day in month/week view drills into it. */
    fun pickDay(day: LocalDate) {
        if (view.value == CalendarView.DAY) focusDay.value = day else {
            focusDay.value = day
            view.value = CalendarView.DAY
        }
    }

    fun shift(delta: Long) {
        focusDay.value = when (view.value) {
            CalendarView.MONTH -> focusDay.value.plusMonths(delta)
            CalendarView.WEEK -> focusDay.value.plusWeeks(delta)
            CalendarView.DAY -> focusDay.value.plusDays(delta)
        }
    }

    fun today() { focusDay.value = LocalDate.now() }

    /** Open straight into the agenda of [day] (deep link / notification tap). */
    fun focusOn(day: LocalDate) {
        focusDay.value = day
        view.value = CalendarView.DAY
    }

    fun toggleTask(inst: TaskInstance, done: Boolean) {
        viewModelScope.launch {
            if (inst.recurring && inst.day != null) {
                repo.setOccurrenceCompleted(inst.task.id, inst.day, done)
            } else {
                repo.setTaskCompleted(inst.task, done)
            }
        }
    }

    fun toggleHabit(habitId: Long, day: LocalDate, checked: Boolean) {
        viewModelScope.launch { repo.setCheckedIn(habitId, day, checked) }
    }

    fun rangeLabel(): String = when (view.value) {
        CalendarView.MONTH -> Dates.shortMonthLabel(focusDay.value.year, focusDay.value.month)
        CalendarView.WEEK -> {
            val start = Dates.weekStart(focusDay.value)
            "${start.format(Dates.iso)} – ${start.plusDays(6).format(Dates.iso)}"
        }
        CalendarView.DAY -> focusDay.value.format(Dates.iso)
    }

    /** Which calendar days the current page renders. */
    fun visibleDays(): List<LocalDate> = visibleDays(view.value, focusDay.value)

    private fun visibleDays(v: CalendarView, f: LocalDate): List<LocalDate> = when (v) {
        CalendarView.MONTH -> Dates.monthGrid(f)
        CalendarView.WEEK -> {
            val s = Dates.weekStart(f)
            (0L until 7L).map { s.plusDays(it) }
        }
        CalendarView.DAY -> {
            val s = Dates.weekStart(f)
            (0L until 7L).map { s.plusDays(it) }
        }
    }

    companion object {
        private val EARLY: Long = LocalDate.of(2015, 1, 1).toEpochDay()

        fun priorityArgb(p: Int): Long = when (p) {
            Priorities.HIGH -> 0xFFD64550L
            Priorities.MEDIUM -> 0xFFC9760AL
            else -> 0xFF5B5BD6L
        }
    }
}
