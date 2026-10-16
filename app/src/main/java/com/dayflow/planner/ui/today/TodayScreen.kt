package com.dayflow.planner.ui.today

import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.ui.common.EmptyState
import com.dayflow.planner.ui.common.ProgressRing
import com.dayflow.planner.ui.common.SectionHeader
import com.dayflow.planner.ui.common.TaskRow
import com.dayflow.planner.ui.containerFactory

@Composable
fun TodayScreen(
    onOpenTasks: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenHabit: (Long) -> Unit,
    onQuickAdd: () -> Unit,
    onOpenDiary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: TodayViewModel = viewModel(factory = containerFactory(androidx.compose.ui.platform.LocalContext.current) { TodayViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()

    val hour = java.time.LocalTime.now().hour
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    Dates.greeting(hour),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    Dates.dayLabel(state.today),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val pct = if (state.totalCount == 0) 0f else state.doneCount.toFloat() / state.totalCount
                    ProgressRing(
                        fraction = pct,
                        label = "${(pct * 100).toInt()}%",
                        sublabel = "${state.doneCount}/${state.totalCount} today",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryTile("Tasks open", (state.totalCount - state.doneCount).toString())
                        SummaryTile("Overdue", state.overdueCount.toString())
                        SummaryTile("Habits today", "${state.habitsDue.count { !it.checked }}")
                    }
                }
            }
        }

        item {
            SectionHeader(
                "Today's tasks",
                trailing = {
                    Text(
                        "See all →",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable { onOpenTasks() },
                    )
                },
            )
        }
        if (state.tasks.isEmpty() && state.loaded) {
            item {
                EmptyState(
                    emoji = "🌤️",
                    title = "Nothing due today",
                    subtitle = "Enjoy the calm — or plan ahead with the + button.",
                )
            }
        } else {
            items(state.tasks, key = { it.task.id }) { instance ->
                TaskRow(
                    instance = instance,
                    onToggle = { vm.toggleTask(instance) },
                    onOpen = { onOpenTask(instance.task.id) },
                )
            }
        }

        item {
            SectionHeader(
                "Habits due today",
                trailing = if (state.habitsDue.isNotEmpty()) {
                    {
                        Text(
                            "${state.habitsDue.count { it.checked }}/${state.habitsDue.size} done",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                } else null,
            )
        }
        if (state.habitsDue.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🌱",
                    title = "No habits scheduled today",
                    subtitle = "Add one from the habit library to start a streak.",
                )
            }
        } else {
            items(state.habitsDue, key = { it.habit.id }) { habit ->
                HabitTodayRow(
                    item = habit,
                    onToggle = { vm.toggleHabit(habit) },
                    onOpen = { onOpenHabit(habit.habit.id) },
                )
            }
        }

        item {
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HabitTodayRow(
    item: HabitToday,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val habit = item.habit
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.medium,
            )
            .clickable { onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(
                    androidx.compose.ui.graphics.Color(habit.colorArgb.toInt()),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(habit.emoji, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (item.streak > 0) {
                Text(
                    "🔥 ${item.streak} day streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        androidx.compose.material3.Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
    }
}

