package com.dayflow.planner.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.data.Priorities
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.Recurrence
import com.dayflow.planner.logic.Times
import com.dayflow.planner.ui.common.ConfirmDialog
import com.dayflow.planner.ui.common.PriorityDot
import com.dayflow.planner.ui.containerFactory
import com.dayflow.planner.ui.pickers.DayPickerDialog
import com.dayflow.planner.ui.pickers.TimePickerDialog
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    taskId: Long,
    presetDay: Long?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: TaskEditorViewModel = viewModel(
        factory = containerFactory(context) { TaskEditorViewModel(it, taskId, presetDay) },
        key = "task-editor-$taskId",
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var showDayPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var subtaskInput by remember { mutableStateOf("") }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }
    val err = state.error
    if (err != null) {
        LaunchedEffect(err) { /* shown inline below */ }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = {
                Text(if (taskId == 0L) "New task" else "Edit task", fontWeight = FontWeight.SemiBold)
            },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (taskId != 0L) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete task",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = vm::save) {
                    Icon(Icons.Rounded.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (err != null) {
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = state.task.title,
                onValueChange = vm::setTitle,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What needs doing?") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = state.task.notes,
                onValueChange = vm::setNotes,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Notes (optional)") },
                minLines = 2,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
            )

            // Due date + time -------------------------------------------------
            LabeledGroup("Due") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.task.dueDay != null,
                        onClick = { showDayPicker = true },
                        label = {
                            Text(
                                state.task.dueDay
                                    ?.let { Dates.pretty(Dates.fromEpochDay(it)) }
                                    ?: "Pick date",
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Today, null, Modifier.size(16.dp)) },
                        trailingIcon = if (state.task.dueDay != null) {
                            {
                                IconButton(onClick = { vm.setDueDay(null) }, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Rounded.Close, "Clear date", modifier = Modifier.size(14.dp))
                                }
                            }
                        } else null,
                    )
                    FilterChip(
                        selected = state.task.dueTime != null,
                        onClick = { showTimePicker = true },
                        label = { Text(state.task.dueTime?.let(Times::label) ?: "Time") },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp)) },
                        trailingIcon = if (state.task.dueTime != null) {
                            {
                                IconButton(onClick = { vm.setDueTime(null) }, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Rounded.Close, "Clear time", modifier = Modifier.size(14.dp))
                                }
                            }
                        } else null,
                    )
                }
            }

            // Priority ----------------------------------------------------------
            LabeledGroup("Priority") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth(), space = 4.dp) {
                    Priorities.LABELS.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = state.task.priority == index,
                            onClick = { vm.setPriority(index) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                            icon = { PriorityDot(index) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            // Recurrence ---------------------------------------------------------
            LabeledGroup("Repeats") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Recurrence.entries.forEach { r ->
                        FilterChip(
                            selected = state.task.recurrence == r.name,
                            onClick = {
                                vm.setRecurrence(r)
                                if (r != Recurrence.NONE) vm.setAnchor(LocalDate.now())
                            },
                            label = {
                                Text(
                                    when (r) {
                                        Recurrence.NONE -> "Never"
                                        Recurrence.DAILY -> "Daily"
                                        Recurrence.WEEKLY -> "Weekly"
                                        Recurrence.MONTHLY -> "Monthly"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
                if (state.task.recurrence != Recurrence.NONE.name) {
                    Text(
                        "Starts " + Dates.dayLabel(Dates.fromEpochDay(state.task.anchorDay)) +
                            (if (state.task.dueDay != null) " — tap the due date to change the start day" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Tags -----------------------------------------------------------------
            LabeledGroup("Tags") {
                if (state.task.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.task.tags.forEach { tag ->
                            FilterChip(
                                selected = true,
                                onClick = { vm.removeTag(tag) },
                                label = { Text("#$tag", style = MaterialTheme.typography.labelMedium) },
                                trailingIcon = { Icon(Icons.Rounded.Close, "Remove tag", Modifier.size(14.dp)) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a tag and press +") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                vm.addTag(tagInput)
                                tagInput = ""
                            },
                        ) { Icon(Icons.Rounded.Add, "Add tag") }
                    },
                    shape = MaterialTheme.shapes.medium,
                )
            }

            // Checklist ------------------------------------------------------------
            LabeledGroup("Checklist (${state.subtasks.count { it.done }}/${state.subtasks.size})") {
                state.subtasks.forEachIndexed { index, sub ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = sub.done,
                            onCheckedChange = { vm.toggleSubtask(index) },
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            sub.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { vm.deleteSubtask(index) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.Close, "Remove item", modifier = Modifier.size(15.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = subtaskInput,
                    onValueChange = { subtaskInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a check item and press +") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                vm.addSubtask(subtaskInput)
                                subtaskInput = ""
                            },
                        ) { Icon(Icons.Rounded.Add, "Add check item") }
                    },
                    shape = MaterialTheme.shapes.medium,
                )
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = vm::save, modifier = Modifier.fillMaxWidth()) {
                Text(if (taskId == 0L) "Create task" else "Save changes")
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showDayPicker) {
        DayPickerDialog(
            title = "Due date",
            initial = state.task.dueDay?.let(Dates::fromEpochDay) ?: LocalDate.now(),
            onDismiss = { showDayPicker = false },
            onPick = {
                vm.setDueDay(it)
                if (state.task.recurrence != Recurrence.NONE.name) vm.setAnchor(it)
                showDayPicker = false
            },
        )
    }
    if (showTimePicker) {
        TimePickerDialog(
            title = "Due time",
            initialMinutes = state.task.dueTime ?: 9 * 60,
            onDismiss = { showTimePicker = false },
            onPick = {
                vm.setDueTime(it)
                showTimePicker = false
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete task?",
            message = "“${state.task.title}” and its checklist will be removed.",
            onConfirm = {
                confirmDelete = false
                vm.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun LabeledGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}


