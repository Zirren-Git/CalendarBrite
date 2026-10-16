package com.dayflow.planner.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.GoalEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.ui.common.EmptyState
import com.dayflow.planner.ui.containerFactory
import com.dayflow.planner.ui.pickers.DayPickerDialog
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalRow(
    val goal: GoalEntity,
    val daysLeft: Int?,
    val overdue: Boolean,
) {
    val statusLabel: String
        get() = when {
            goal.done -> "Done"
            daysLeft == null -> "No deadline"
            overdue -> "Overdue by ${-daysLeft}d"
            else -> "$daysLeft days left"
        }
}

class GoalsViewModel(app: DayFlowApp) : ViewModel() {

    private val repo = app.container.repository
    private val today = LocalDate.now()

    val rows: StateFlow<List<GoalRow>> = repo.observeGoals()
        .map { goals ->
            goals.map { g ->
                val left = g.deadlineDay?.let { (it - Dates.toEpochDay(today)).toInt() }
                GoalRow(
                    goal = g,
                    daysLeft = left,
                    overdue = left != null && left < 0 && !g.done,
                )
            }.sortedWith(
                compareBy<GoalRow> { it.goal.done }
                    .thenBy { r -> r.daysLeft ?: Int.MAX_VALUE },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(goal: GoalEntity) = viewModelScope.launch { repo.saveGoal(goal) }
    fun setProgress(goal: GoalEntity, progress: Int) =
        viewModelScope.launch { repo.setGoalProgress(goal.id, progress.coerceIn(0, 100)) }

    fun toggleDone(goal: GoalEntity) = viewModelScope.launch {
        repo.saveGoal(goal.copy(done = !goal.done, progress = if (!goal.done) 100 else goal.progress))
    }

    fun delete(goal: GoalEntity) = viewModelScope.launch { repo.deleteGoal(goal.id) }
}

@Composable
fun GoalsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: GoalsViewModel = viewModel(factory = containerFactory(context) { GoalsViewModel(it) })
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<GoalEntity?>(null) }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Goals",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
            )
            if (rows.isEmpty()) {
                EmptyState(
                    emoji = "🎯",
                    title = "No goals yet",
                    subtitle = "Set a destination and track the walk there.",
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows, key = { it.goal.id }) { row ->
                        GoalCard(
                            row = row,
                            onEdit = { editing = row.goal },
                            onToggleDone = { vm.toggleDone(row.goal) },
                            onDelete = { confirmDelete = row.goal },
                            onProgress = { v -> vm.setProgress(row.goal, v) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editing = GoalEntity(id = 0, title = "") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Rounded.Add, "New goal") }
    }

    editing?.let { goal ->
        GoalEditorDialog(
            initial = goal,
            onDismiss = { editing = null },
            onSave = {
                vm.save(it)
                editing = null
            },
        )
    }
    confirmDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete goal?") },
            text = { Text("“${g.title}” will be removed.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(g); confirmDelete = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GoalCard(
    row: GoalRow,
    onEdit: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
    onProgress: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var localProgress by remember(row.goal.progress) { mutableStateOf(row.goal.progress.toFloat()) }
    LaunchedEffect(row.goal.progress) { if (!dragging) localProgress = row.goal.progress.toFloat() }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (row.goal.done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable(onClick = onToggleDone),
                    contentAlignment = Alignment.Center,
                ) {
                    if (row.goal.done) {
                        Icon(Icons.Rounded.Check, "Mark not done", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        row.goal.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (row.goal.done) TextDecoration.LineThrough else null,
                    )
                    if (row.goal.description.isNotBlank()) {
                        Text(
                            row.goal.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Delete, "Delete goal", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            if (!row.goal.done) {
                Slider(
                    value = localProgress,
                    onValueChange = { localProgress = it },
                    onValueChangeFinished = {
                        dragging = false
                        onProgress(localProgress.toInt())
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${row.goal.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun GoalEditorDialog(
    initial: GoalEntity,
    onDismiss: () -> Unit,
    onSave: (GoalEntity) -> Unit,
) {
    var title by remember { mutableStateOf(initial.title) }
    var description by remember { mutableStateOf(initial.description) }
    var deadline by remember { mutableStateOf(initial.deadlineDay?.let(Dates::fromEpochDay)) }
    var progress by remember { mutableStateOf(initial.progress.toFloat()) }
    var done by remember { mutableStateOf(initial.done) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Goal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Why it matters (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        deadline?.let { "By " + Dates.pretty(it) } ?: "No deadline",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { showPicker = true }) { Text(if (deadline == null) "Set" else "Change") }
                    if (deadline != null) {
                        IconButton(onClick = { deadline = null }, modifier = Modifier.size(26.dp)) {
                            Text("✕", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (!done) {
                    Text("Progress: ${progress.toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(value = progress, onValueChange = { progress = it }, valueRange = 0f..100f)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Already done", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = done, onCheckedChange = { done = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim().ifBlank { "Untitled goal" },
                            description = description.trim(),
                            deadlineDay = deadline?.let(Dates::toEpochDay),
                            progress = if (done) 100 else progress.toInt(),
                            done = done,
                        ),
                    )
                },
            ) { Text(if (initial.id == 0L) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showPicker) {
        DayPickerDialog(
            title = "Target date",
            initial = deadline ?: LocalDate.now().plusMonths(1),
            onDismiss = { showPicker = false },
            onPick = {
                deadline = it
                showPicker = false
            },
        )
    }
}
