package com.dayflow.planner.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.data.HabitEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.HabitSchedule
import com.dayflow.planner.logic.Streaks
import com.dayflow.planner.ui.common.ConfirmDialog
import com.dayflow.planner.ui.containerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: HabitsViewModel = viewModel(factory = containerFactory(context) { HabitsViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val row = state.rows.firstOrNull { it.habit.id == habitId }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var heatmapEnd by remember { mutableStateOf(YearMonth.now()) }

    LaunchedEffect(state.loading, row == null) {
        // If the habit was deleted or archived while open, leave.
        if (!state.loading && row == null && !editing) onBack()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text(row?.habit?.name ?: "Habit", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { editing = true }) { Text("Edit", color = MaterialTheme.colorScheme.primary) }
                row?.let { r ->
                    IconButton(onClick = { vm.setArchived(r.habit, true) }) {
                        Icon(Icons.Rounded.Archive, "Archive habit")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, "Delete habit", tint = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )

        val r = row
        if (r == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (state.loading) "Loading…" else "Habit not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hero row: emoji + streak + today toggle
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color(r.habit.colorArgb).copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(r.habit.emoji, style = MaterialTheme.typography.headlineMedium)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${r.currentStreak}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("day streak", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.currentStreak > 0) {
                                Spacer(Modifier.size(6.dp))
                                Icon(
                                    Icons.Rounded.LocalFireDepartment,
                                    null,
                                    Modifier.size(16.dp),
                                    tint = Color(0xFFE07B39L),
                                )
                            }
                        }
                        Text(
                            "Best: ${r.longestStreak} days · This week ${r.weekDone}/${r.weekDue}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (r.doneToday) Color(r.habit.colorArgb)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable(enabled = r.dueToday) { vm.toggleToday(r) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (r.doneToday) "✓" else if (r.dueToday) "" else "·",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (r.doneToday) Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Heatmap: weeks as columns, Mon..Sun as rows
            Text(
                "History",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Heatmap(
                endMonth = heatmapEnd,
                habit = r.habit,
                checked = r.checked,
                canToggle = { true },
                onToggle = { day -> vm.toggleDay(r.habit.id, day, !r.checked.contains(day)) },
                onMonthShift = { delta -> heatmapEnd = heatmapEnd.plusMonths(delta) },
            )

            if (r.habit.description.isNotBlank()) {
                Text(
                    r.habit.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
        }
    }

    val rEdit = row
    if (editing && rEdit != null) {
        HabitEditDialog(
            initial = rEdit.habit,
            onDismiss = { editing = false },
            onSave = { habit ->
                vm.updateHabit(habit)
                editing = false
            },
        )
    }
    val rr = row
    if (confirmDelete && rr != null) {
        ConfirmDialog(
            title = "Delete habit?",
            message = "“${rr.habit.name}” and its ${rr.checked.size} check-ins will be removed. This cannot be undone.",
            onConfirm = {
                confirmDelete = false
                vm.delete(rr.habit)
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Heatmap(
    endMonth: YearMonth,
    habit: HabitEntity,
    checked: Set<LocalDate>,
    canToggle: (LocalDate) -> Boolean,
    onToggle: (LocalDate) -> Unit,
    onMonthShift: (Long) -> Unit,
) {
    val schedule = HabitSchedule(habit.daysMask)
    val color = Color(habit.colorArgb)
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonthShift(-1) }) { Text("‹") }
            Text(
                endMonth.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + endMonth.year,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(
                onClick = { onMonthShift(1) },
                enabled = endMonth < YearMonth.now(),
            ) { Text("›") }
        }
        // grid: columns = weeks
        val first = endMonth.atDay(1)
        val last = endMonth.atEndOfMonth()
        val gridStart = Dates.weekStart(first)
        val weeks = generateSequence(gridStart) { it.plusWeeks(1) }
            .takeWhile { it <= last }
            .toList()
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.size(2.dp))
            weeks.forEach { weekStart ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0L until 7L).forEach { offset ->
                        val day = weekStart.plusDays(offset)
                        val inMonth = day.month == endMonth.month && day.year == endMonth.year
                        val isDue = schedule.isDue(day)
                        val done = checked.contains(day)
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        done -> color
                                        isDue && inMonth -> color.copy(alpha = 0.16f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                )
                                .clickable(enabled = inMonth && isDue && canToggle(day) && day <= today) {
                                    onToggle(day)
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitEditDialog(
    initial: HabitEntity,
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var color by remember { mutableStateOf(initial.colorArgb) }
    var mask by remember { mutableStateOf(initial.daysMask) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("✅", "🌱", "📚", "💪", "🧘", "💧", "😴", "✍️", "🎸", "🥗").forEach { e ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (emoji == e) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent,
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HABIT_PALETTE.forEach { c ->
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (color == c) Modifier
                                        .padding(0.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier,
                                )
                                .clickable { color = c },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { dow ->
                        val bit = 1 shl (dow.value - 1)
                        val on = mask and bit != 0
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (on) Color(color) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                )
                                .clickable { mask = mask xor bit },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dow.name.take(1),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(name = name.trim().ifBlank { initial.name }, emoji = emoji, colorArgb = color, daysMask = mask)) },
                enabled = name.isNotBlank() && mask != 0,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
