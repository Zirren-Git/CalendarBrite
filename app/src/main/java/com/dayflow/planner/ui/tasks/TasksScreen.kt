package com.dayflow.planner.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.data.Priorities
import java.time.LocalDate
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.TaskInstance
import com.dayflow.planner.ui.common.ConfirmDialog
import com.dayflow.planner.ui.common.EmptyState
import com.dayflow.planner.ui.common.TaskRow
import com.dayflow.planner.ui.containerFactory
import com.dayflow.planner.ui.pickers.DayPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    initialView: TasksView = TasksView.TODAY,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: TasksViewModel = viewModel(
        factory = containerFactory(LocalContext.current) { TasksViewModel(it) },
    )
    LaunchedEffect(initialView) { vm.setView(initialView) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pendingUndo by vm.pendingUndo.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var menuFor by remember { mutableStateOf<TaskInstance?>(null) }
    var moveFor by remember { mutableStateOf<TaskInstance?>(null) }
    var deleteFor by remember { mutableStateOf<TaskInstance?>(null) }

    LaunchedEffect(pendingUndo) {
        val undo = pendingUndo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Task deleted", "Undo")
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearUndo()
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                space = 4.dp,
            ) {
                TasksView.entries.forEachIndexed { index, v ->
                    SegmentedButton(
                        selected = state.view == v,
                        onClick = { vm.setView(v) },
                        shape = SegmentedButtonDefaults.itemShape(index, TasksView.entries.size),
                        label = { Text(v.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Search tasks, notes, #tags") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                shape = MaterialTheme.shapes.large,
            )

            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Priorities.LABELS.forEachIndexed { p, label ->
                    FilterChip(
                        selected = state.priorityFilter == p,
                        onClick = { vm.togglePriorityFilter(p) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
                state.allTags.forEach { tag ->
                    FilterChip(
                        selected = state.tagFilter?.lowercase() == tag,
                        onClick = { vm.toggleTagFilter(tag) },
                        label = { Text("#$tag", style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            val empty = state.items.isEmpty() && state.upcomingGroups.isEmpty()
            LazyColumn(Modifier.fillMaxSize()) {
                if (empty) {
                    item {
                        EmptyState(
                            emoji = when (state.view) {
                                TasksView.COMPLETED -> "🏁"
                                TasksView.UPCOMING -> "🔭"
                                TasksView.ALL -> "🗂️"
                                else -> "🌤️"
                            },
                            title = when (state.view) {
                                TasksView.COMPLETED -> "No completed tasks yet"
                                TasksView.UPCOMING -> "The week ahead is clear"
                                TasksView.ALL -> "No tasks yet"
                                else -> "Nothing due today"
                            },
                            subtitle = if (state.query.isNotBlank() || state.tagFilter != null || state.priorityFilter != null) {
                                "No results for the current search/filters."
                            } else {
                                "Tap the + button to add your first task."
                            },
                        )
                    }
                }

                if (state.view == TasksView.UPCOMING) {
                    state.upcomingGroups.forEach { (day, list) ->
                        item(key = "group-${day.toEpochDay()}") {
                            Text(
                                Dates.pretty(day),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
                            )
                        }
                        items(list, key = { "u${it.task.id}-${day.toEpochDay()}" }) { instance ->
                            TaskRow(
                                instance = instance,
                                onToggle = { vm.toggleTask(instance) },
                                onOpen = { onOpenTask(instance.task.id) },
                                onDelete = { vm.deleteTask(instance) },
                            )
                        }
                    }
                } else {
                    items(state.items, key = { "${state.view}-${it.task.id}-${it.day?.toEpochDay()}" }) { instance ->
                        TaskRow(
                            instance = instance,
                            onToggle = { vm.toggleTask(instance) },
                            onOpen = { onOpenTask(instance.task.id) },
                            onDelete = { vm.deleteTask(instance) },
                            onMenu = { menuFor = instance },
                        )
                    }
                }
                item { Spacer(Modifier.height(140.dp)) }
            }
        }

        // context menu
        val menu = menuFor
        if (menu != null) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuFor = null },
                modifier = Modifier.widthIn(min = 220.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = { onOpenTask(menu.task.id); menuFor = null },
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                    onClick = { vm.duplicateTask(menu); menuFor = null },
                )
                DropdownMenuItem(
                    text = { Text("Move to another day…") },
                    leadingIcon = { Icon(Icons.Rounded.EventAvailable, null) },
                    onClick = { moveFor = menu; menuFor = null },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { deleteFor = menu; menuFor = null },
                )
            }
        }

        moveFor?.let { target ->
            DayPickerDialog(
                title = "Move “${target.task.title}”",
                initial = target.day ?: LocalDate.now(),
                onDismiss = { moveFor = null },
                onPick = { day ->
                    vm.moveTask(target, day)
                    moveFor = null
                },
            )
        }

        deleteFor?.let { target ->
            ConfirmDialog(
                title = "Delete task?",
                message = "“${target.task.title}” and its checklist will be deleted. You can undo right after.",
                onConfirm = {
                    vm.deleteTask(target)
                    deleteFor = null
                },
                onDismiss = { deleteFor = null },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}
