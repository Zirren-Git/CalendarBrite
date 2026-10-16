package com.dayflow.planner.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.HabitEntity
import com.dayflow.planner.data.Priorities
import com.dayflow.planner.data.TaskEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.HabitSchedule
import com.dayflow.planner.ui.containerFactory
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val loading: Boolean = true,
    val weekly: List<Int> = List(8) { 0 },          // completions per week, oldest → newest
    val heat: Map<Long, Int> = emptyMap(),           // epochDay → completions (last ~26 weeks)
    val openTasks: Int = 0,
    val openRecurring: Int = 0,
    val doneAllTime: Int = 0,
    val activeHabits: Int = 0,
    val habitCompletionLast7: Float = 0f,             // 0..1 across due habit-days
    val avgMood: Int? = null,
    val moodCount: Int = 0,
    val perPriority: Map<Int, Int> = emptyMap(),       // open tasks by priority
    val streakLeader: String? = null,
)

class StatsViewModel(app: DayFlowApp) : ViewModel() {

    private val repo = app.container.repository
    private val today = LocalDate.now()
    private val from = today.minusWeeks(8).let { Dates.weekStart(it) }
    private val fromEpoch = Dates.toEpochDay(from)

    val state: StateFlow<StatsUiState> = combine(
        repo.observeTasks(),
        repo.observeCompletionsSince(fromEpoch),
        repo.observeAllHabits(),
        repo.observeAllCheckIns(),
        repo.observeDiary(),
    ) { tasks, completions, habits, checkIns, diary ->
        val allCompletions = mutableListOf<Long>()
        tasks.filter { it.completedAt != null }.forEach { t ->
            val day = t.completedAt!!.let { ms ->
                java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            }
            if (day >= fromEpoch) allCompletions += day
        }
        completions.forEach { c -> allCompletions += c.dayEpoch }
        checkIns.forEach { c -> if (c.dayEpoch >= fromEpoch) allCompletions += c.dayEpoch }

        val byDay = allCompletions.groupingBy { it }.eachCount()
        val weeks = (0L until 8L).map { w ->
            val ws = from.plusWeeks(w)
            (0L until 7L).sumOf { d -> byDay[Dates.toEpochDay(ws.plusDays(d))] ?: 0 }
        }

        val heat = byDay.filterKeys { it >= from.minusDays(7).toEpochDay() }

        val activeHabits = habits.filter { !it.archived }
        var due7 = 0
        var done7 = 0
        activeHabits.forEach { h ->
            val sched = HabitSchedule(h.daysMask)
            (0L until 7L).forEach { off ->
                val day = today.minusDays(off)
                if (sched.isDue(day)) {
                    due7++
                    if (checkIns.any { it.habitId == h.id && it.dayEpoch == day.toEpochDay() }) done7++
                }
            }
        }
        val moods = diary.mapNotNull { it.mood }
        val open = tasks.filter { it.completedAt == null && it.recurrence == "NONE" }
        val bestStreak = activeHabits
            .map { h ->
                val sched = HabitSchedule(h.daysMask)
                val checked = checkIns.filter { it.habitId == h.id }.map { Dates.fromEpochDay(it.dayEpoch) }.toSet()
                com.dayflow.planner.logic.Streaks.longestStreak(checked, sched) to h.name
            }
            .maxByOrNull { it.first }

        StatsUiState(
            loading = false,
            weekly = weeks,
            heat = heat,
            openTasks = open.size,
            openRecurring = tasks.count { it.recurrence != "NONE" },
            doneAllTime = tasks.count { it.completedAt != null } + completions.size,
            activeHabits = activeHabits.size,
            habitCompletionLast7 = if (due7 == 0) 0f else done7.toFloat() / due7,
            avgMood = if (moods.isEmpty()) null else (moods.sum() + moods.size / 2) / moods.size,
            moodCount = moods.size,
            perPriority = open.groupingBy { it.priority }.eachCount(),
            streakLeader = bestStreak?.takeIf { it.first > 0 }?.let { "${it.second} · ${it.first}d best" },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: StatsViewModel = viewModel(factory = containerFactory(context) { StatsViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Statistics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryTile("Open tasks", state.openTasks.toString(), Modifier.weight(1f))
            SummaryTile("Recurring", state.openRecurring.toString(), Modifier.weight(1f))
            SummaryTile("Done all-time", state.doneAllTime.toString(), Modifier.weight(1f))
        }

        Card("Last 8 weeks") {
            WeeklyBars(values = state.weekly, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("8 wks ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("this week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card("Consistency — last two months") {
            HeatGrid(heat = state.heat, color = MaterialTheme.colorScheme.primary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(title = "Habits", modifier = Modifier.weight(1f)) {
                Text(
                    "${(state.habitCompletionLast7 * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "due days done this week · ${state.activeHabits} active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.streakLeader?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("🔥 $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(title = "Mood", modifier = Modifier.weight(1f)) {
                val mood = state.avgMood
                Text(
                    mood?.let { com.dayflow.planner.ui.diary.DIARY_MOODS[it].first } ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (state.moodCount > 0) "avg over ${state.moodCount} entries" else "no journal entries yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card("Open tasks by priority") {
            listOf(Priorities.HIGH, Priorities.MEDIUM, Priorities.LOW).forEach { p ->
                val count = state.perPriority[p] ?: 0
                val max = (state.perPriority.values.maxOrNull() ?: 1).coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        Priorities.label(p).padEnd(7),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(count.toFloat() / max)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(priorityTint(p)),
                        )
                    }
                    Text("$count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.height(70.dp))
    }
}

private fun priorityTint(p: Int): Color = when (p) {
    Priorities.HIGH -> Color(0xFFD64550)
    Priorities.MEDIUM -> Color(0xFFC9760A)
    else -> Color(0xFF5B5BD6)
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Card(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun WeeklyBars(values: List<Int>, color: Color) {
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(90.dp),
    ) {
        val n = values.size
        val gap = size.width / (n * 2f - 1)
        values.forEachIndexed { i, v ->
            val h = (size.height - 14.dp.toPx()) * (v.toFloat() / max)
            drawRoundRect(
                color = if (v == 0) color.copy(alpha = 0.15f) else color,
                topLeft = Offset(i * 2 * gap, size.height - h.coerceAtLeast(3.dp.toPx())),
                size = Size(gap, h.coerceAtLeast(3.dp.toPx())),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }
    }
}

@Composable
private fun HeatGrid(heat: Map<Long, Int>, color: Color) {
    val today = LocalDate.now()
    val weeks = 9
    val gridStart = Dates.weekStart(today.minusWeeks((weeks - 1).toLong()))
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (0 until weeks).forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (0L until 7L).forEach { d ->
                    val day = gridStart.plusWeeks(w.toLong()).plusDays(d)
                    val v = heat[day.toEpochDay()] ?: 0
                    val level = when {
                        day.isAfter(today) -> -1
                        v == 0 -> 0
                        v <= 1 -> 1
                        v <= 3 -> 2
                        v <= 5 -> 3
                        else -> 4
                    }
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (level) {
                                    -1 -> Color.Transparent
                                    0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else -> color.copy(alpha = 0.25f + level * 0.19f)
                                },
                            ),
                    )
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(0.5f, 0.25f, 0.44f, 0.63f, 0.82f).forEachIndexed { i, a ->
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (i == 0) MaterialTheme.colorScheme.surfaceVariant else color.copy(alpha = a)),
            )
        }
        Text("more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
