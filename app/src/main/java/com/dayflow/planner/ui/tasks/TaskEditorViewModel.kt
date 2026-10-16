package com.dayflow.planner.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.Priorities
import com.dayflow.planner.data.SubtaskEntity
import com.dayflow.planner.data.TaskEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.Recurrence
import com.dayflow.planner.remind.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskEditorViewModel(
    private val app: DayFlowApp,
    private val taskId: Long,
    presetDay: Long?,
) : ViewModel() {

    private val repo = app.container.repository

    data class EditorState(
        val task: TaskEntity,
        val subtasks: List<SubtaskEntity> = emptyList(),
        val loaded: Boolean = false,
        val saved: Boolean = false,
        val deleted: Boolean = false,
        val error: String? = null,
    )

    private val today = LocalDate.now()

    private val _state = MutableStateFlow(
        EditorState(
            task = TaskEntity(
                id = taskId,
                title = "",
                dueDay = presetDay ?: (if (taskId == 0L) Dates.toEpochDay(today) else null),
                anchorDay = Dates.toEpochDay(today),
            ),
        ),
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        if (taskId != 0L) {
            viewModelScope.launch {
                val existing = repo.loadTask(taskId)
                val subs = if (existing != null) repo.loadSubtasks(taskId) else emptyList()
                _state.update {
                    it.copy(
                        task = existing ?: it.task,
                        subtasks = subs,
                        loaded = true,
                    )
                }
            }
        } else {
            _state.update { it.copy(loaded = true) }
        }
    }

    fun setTitle(v: String) = edit { it.copy(title = v) }
    fun setNotes(v: String) = edit { it.copy(notes = v) }
    fun setPriority(v: Int) = edit { it.copy(priority = v) }
    fun setRecurrence(v: Recurrence) = edit { it.copy(recurrence = v.name) }
    fun setDueDay(v: LocalDate?) = edit { it.copy(dueDay = v?.let(Dates::toEpochDay)) }
    fun setDueTime(v: Int?) = edit { it.copy(dueTime = v) }

    fun setAnchor(v: LocalDate) = edit { it.copy(anchorDay = Dates.toEpochDay(v)) }

    fun addTag(raw: String) {
        val tag = raw.trim().lowercase().removePrefix("#").take(24)
        if (tag.isEmpty()) return
        edit { t ->
            if (t.tags.any { it.equals(tag, true) } || t.tags.size >= 8) t
            else t.copy(tags = t.tags + tag)
        }
    }

    fun removeTag(tag: String) = edit { t -> t.copy(tags = t.tags - tag) }

    fun addSubtask(title: String) {
        val clean = title.trim().take(160)
        if (clean.isEmpty()) return
        _state.update {
            it.copy(
                subtasks = it.subtasks + SubtaskEntity(
                    taskId = it.task.id,
                    title = clean,
                    position = it.subtasks.size,
                ),
            )
        }
    }

    fun toggleSubtask(index: Int) = _state.update { st ->
        val list = st.subtasks.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(done = !list[index].done)
        }
        st.copy(subtasks = list)
    }

    fun renameSubtask(index: Int, title: String) = _state.update { st ->
        val list = st.subtasks.toMutableList()
        if (index in list.indices) list[index] = list[index].copy(title = title)
        st.copy(subtasks = list)
    }

    fun deleteSubtask(index: Int) = _state.update { st ->
        st.copy(subtasks = st.subtasks.filterIndexed { i, _ -> i != index })
    }

    fun save() {
        viewModelScope.launch {
            val cur = _state.value
            val title = cur.task.title.trim()
            if (title.isEmpty()) {
                _state.update { it.copy(error = "A task needs a title") }
                return@launch
            }
            var task = cur.task.copy(title = title)
            // recurring templates always carry an anchor day
            if (task.recurrence != Recurrence.NONE.name && task.anchorDay == 0L) {
                task = task.copy(anchorDay = task.dueDay ?: Dates.toEpochDay(today))
            }
            val savedId = repo.saveTask(task)
            if (savedId > 0) {
                repo.replaceSubtasks(savedId, cur.subtasks)
            }
            val stored = repo.loadTask(savedId) ?: task.copy(id = savedId)
            ReminderScheduler.schedule(app, stored)
            _state.update { it.copy(saved = true, error = null) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            if (taskId != 0L) {
                repo.deleteTask(taskId)
                ReminderScheduler.cancel(app, taskId)
            }
            _state.update { it.copy(deleted = true) }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private fun edit(block: (TaskEntity) -> TaskEntity) {
        _state.update { it.copy(task = block(it.task)) }
    }
}
