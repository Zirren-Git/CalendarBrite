package com.dayflow.planner.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.SubtaskEntity
import com.dayflow.planner.data.TaskEntity
import com.dayflow.planner.data.TaskSnapshot
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.TaskInstance
import com.dayflow.planner.logic.TaskSelector
import com.dayflow.planner.remind.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TasksView(val label: String) {
    TODAY("Today"),
    UPCOMING("Upcoming"),
    ALL("All"),
    COMPLETED("Done"),
}

data class TasksUiState(
    val view: TasksView = TasksView.TODAY,
    val query: String = "",
    val tagFilter: String? = null,
    val priorityFilter: Int? = null,
    val items: List<TaskInstance> = emptyList(),
    val upcomingGroups: List<Pair<LocalDate, List<TaskInstance>>> = emptyList(),
    val allTags: List<String> = emptyList(),
)

private data class TaskBundle(
    val tasks: List<TaskEntity>,
    val completions: List<com.dayflow.planner.data.TaskCompletionEntity>,
    val subtasks: List<SubtaskEntity>,
)

class TasksViewModel(private val app: DayFlowApp) : ViewModel() {

    private val repo = app.container.repository

    private val view = MutableStateFlow(TasksView.TODAY)
    private val query = MutableStateFlow("")
    private val tagFilter = MutableStateFlow<String?>(null)
    private val priorityFilter = MutableStateFlow<Int?>(null)

    private val _undo = MutableStateFlow<TaskSnapshot?>(null)
    /** Last deleted task (kept for a snackbar Undo action). */
    val pendingUndo: StateFlow<TaskSnapshot?> = _undo

    private val bundle: StateFlow<TaskBundle> = combine(
        repo.observeTasks(),
        repo.observeCompletionsSince(LocalDate.now().minusDays(400).toEpochDay()),
        repo.observeSubtasks(),
    ) { t, c, s -> TaskBundle(t, c, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskBundle(emptyList(), emptyList(), emptyList()))

    val state: StateFlow<TasksUiState> = combine(view, query, tagFilter, priorityFilter, bundle) { v, q, tag, pr, b ->
        val compByTask = b.completions.groupBy { it.taskId }.mapValues { e -> e.value.map { it.dayEpoch }.toSet() }
        val subByTask = b.subtasks.groupBy { it.taskId }
            .mapValues { e -> e.value.count { s -> s.done } to e.value.size }
        val tags = b.tasks.flatMap { it.tags }.map { it.lowercase() }.distinct().sorted()

        fun keep(i: TaskInstance) = TaskSelector.matches(i, q, tag, pr)

        val today = LocalDate.now()
        when (v) {
            TasksView.TODAY -> {
                val items = TaskSelector.forDay(b.tasks, compByTask, today, subByTask).filter(::keep)
                TasksUiState(v, q, tag, pr, items, emptyList(), tags)
            }

            TasksView.UPCOMING -> {
                val groups = TaskSelector.forRange(b.tasks, compByTask, today, 7, subByTask)
                    .toSortedMap()
                    .mapNotNull { (day, list) ->
                        val kept = list.filter(::keep)
                        if (kept.isEmpty()) null else day to kept
                    }
                TasksUiState(v, q, tag, pr, emptyList(), groups, tags)
            }

            TasksView.ALL -> {
                val items = TaskSelector.asList(b.tasks, subByTask).filter {
                    TaskSelector.matches(it, q, tag, pr)
                }
                TasksUiState(v, q, tag, pr, items, emptyList(), tags)
            }

            TasksView.COMPLETED -> {
                val items = TaskSelector.completedList(b.tasks, subByTask).filter {
                    TaskSelector.matches(it, q, tag, pr)
                }
                TasksUiState(v, q, tag, pr, items, emptyList(), tags)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setView(v: TasksView) { view.value = v }
    fun setQuery(q: String) { query.value = q }
    fun toggleTagFilter(tag: String?) { tagFilter.value = if (tagFilter.value == tag) null else tag }
    fun togglePriorityFilter(p: Int?) { priorityFilter.value = if (priorityFilter.value == p) null else p }
    fun clearUndo() { _undo.value = null }

    fun toggleTask(instance: TaskInstance) {
        viewModelScope.launch {
            if (instance.recurring && instance.day != null) {
                repo.setOccurrenceCompleted(instance.task.id, instance.day, !instance.done)
            } else {
                repo.setTaskCompleted(instance.task, !instance.done)
                if (instance.done) {
                    // re-opening a completed task restores its reminder if still due
                    ReminderScheduler.schedule(app, instance.task)
                } else {
                    ReminderScheduler.cancel(app, instance.task.id)
                }
            }
        }
    }

    fun deleteTask(instance: TaskInstance) {
        viewModelScope.launch {
            val snapshot = repo.snapshotTask(instance.task.id)
            repo.deleteTask(instance.task.id)
            ReminderScheduler.cancel(app, instance.task.id)
            _undo.value = snapshot
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            _undo.value?.let { repo.restoreTask(it) }
            _undo.value = null
        }
    }

    fun duplicateTask(instance: TaskInstance) {
        viewModelScope.launch { repo.duplicateTask(instance.task.id) }
    }

    fun moveTask(instance: TaskInstance, day: LocalDate) {
        viewModelScope.launch {
            if (instance.recurring) {
                repo.saveTask(instance.task.copy(anchorDay = Dates.toEpochDay(day), dueDay = Dates.toEpochDay(day)))
            } else {
                repo.moveTaskToDay(instance.task.id, day)
                repo.loadTask(instance.task.id)?.let { ReminderScheduler.schedule(app, it) }
            }
        }
    }
}
