package com.dayflow.planner.ui.habits

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.dayflow.planner.logic.HabitSchedule
import com.dayflow.planner.ui.common.EmptyState
import com.dayflow.planner.ui.common.ProgressRing
import com.dayflow.planner.ui.containerFactory
import java.time.DayOfWeek

@Composable
fun HabitsScreen(
    onOpenHabit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: HabitsViewModel = viewModel(factory = containerFactory(context) { HabitsViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showLibrary by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp),
        ) {
            Text(
                "Habits",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showLibrary = true }) {
                Icon(Icons.Rounded.Add, "Add habit", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (state.rows.isEmpty() && !state.loading) {
            EmptyState(
                emoji = "🌱",
                title = "No habits yet",
                subtitle = "Small steps, every day. Pick one from the library to start.",
                action = {
                    FilterChip(selected = false, onClick = { showLibrary = true }, label = { Text("Browse library") })
                },
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.rows, key = { it.habit.id }) { row ->
                    HabitCard(
                        row = row,
                        onToggle = { vm.toggleToday(row) },
                        onOpen = { onOpenHabit(row.habit.id) },
                    )
                }
                if (state.archived.isNotEmpty()) {
                    item(key = "archived") {
                        Text(
                            "Archived: ${state.archived.joinToString { it.name }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showLibrary) {
        HabitLibrarySheet(
            onDismiss = { showLibrary = false },
            onPick = {
                vm.addPreset(it)
                showLibrary = false
            },
            onCustom = { name, emoji, mask ->
                vm.addCustom(name, emoji, HABIT_PALETTE[0], mask)
                showLibrary = false
            },
        )
    }
}

val HABIT_PALETTE = listOf(
    0xFF5B5BD6L, 0xFF0E8A7BL, 0xFFC9760AL, 0xFFC2416DL, 0xFF3E7C4FL, 0xFF2C6FBFL, 0xFFD64550L,
)

@Composable
private fun HabitCard(row: HabitRow, onToggle: () -> Unit, onOpen: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(row.habit.colorArgb).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(row.habit.emoji, style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.habit.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val freq = if (row.habit.daysMask == HabitSchedule.DAILY_MASK) "Every day"
                else DayOfWeek.entries
                    .filter { (row.habit.daysMask shr (it.value - 1)) and 1 == 1 }
                    .joinToString(", ") { d -> d.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    .ifBlank { "No days" }
                Text(
                    "$freq · week ${row.weekDone}/${row.weekDue}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.currentStreak > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            null,
                            modifier = Modifier.size(13.dp),
                            tint = Color(0xFFE07B39L),
                        )
                        Spacer(Modifier.size(3.dp))
                        Text(
                            "${row.currentStreak} day streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            ProgressRing(
                fraction = if (row.dueToday && row.doneToday) 1f else row.weekDone.toFloat() / row.weekDue.coerceAtLeast(1),
                label = "${row.weekDone}/${row.weekDue}",
                size = 44,
                color = Color(row.habit.colorArgb),
            )
            Checkbox(
                checked = row.dueToday && row.doneToday,
                enabled = row.dueToday,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitLibrarySheet(
    onDismiss: () -> Unit,
    onPick: (HabitPreset) -> Unit,
    onCustom: (name: String, emoji: String, mask: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🌱") }
    var mask by remember { mutableStateOf(HabitSchedule.DAILY_MASK) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                if (customMode) "Create a habit" else "Habit library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            if (!customMode) {
                LazyColumn(
                    modifier = Modifier.height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    items(HabitPresets.ALL, key = { it.name }) { p ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().clickable { onPick(p) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(p.emoji, style = MaterialTheme.typography.titleMedium)
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        p.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Rounded.Add, "Add", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item(key = "custom") {
                        FilterChip(
                            selected = false,
                            onClick = { customMode = true },
                            label = { Text("Create custom habit…") },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Habit name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Emoji", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("✅", "🌱", "📚", "💪", "🧘", "💧", "😴", "✍️", "🎸", "🥗").forEach { e ->
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (emoji == e) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Days", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { dow ->
                        val bit = 1 shl (dow.value - 1)
                        val on = mask and bit != 0
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                )
                                .clickable { mask = mask xor bit },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                dow.name.take(1),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(
                        onClick = { onCustom(name, emoji, mask) },
                        enabled = name.isNotBlank() && mask != 0,
                    ) { Text("Create") }
                    androidx.compose.material3.TextButton(onClick = { customMode = false }) {
                        Text("Back")
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
