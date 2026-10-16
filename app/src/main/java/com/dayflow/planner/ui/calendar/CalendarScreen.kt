package com.dayflow.planner.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.logic.Times
import com.dayflow.planner.ui.common.EmptyState
import com.dayflow.planner.ui.common.SectionHeader
import com.dayflow.planner.ui.containerFactory
import java.time.LocalDate

@Composable
fun CalendarScreen(
    focusEpoch: Long? = null,
    onOpenTask: (Long) -> Unit,
    onNewTask: (Long) -> Unit,
    onOpenDiary: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: CalendarViewModel = viewModel(factory = containerFactory(context) { CalendarViewModel(it) })
    val state by vm.state.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val focus by vm.focusDay.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(focusEpoch) {
        if (focusEpoch != null && focusEpoch >= 0) {
            vm.focusOn(java.time.LocalDate.ofEpochDay(focusEpoch))
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header -------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp),
        ) {
            IconButton(onClick = { vm.shift(-1) }) {
                Icon(Icons.Rounded.ArrowBackIos, "Previous", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                vm.rangeLabel(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            FilterChip(
                selected = false,
                onClick = vm::today,
                label = { Text("Today", style = MaterialTheme.typography.labelMedium) },
            )
            IconButton(onClick = { vm.shift(1) }) {
                Icon(Icons.Rounded.ArrowForwardIos, "Next", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            CalendarView.entries.forEach { v ->
                FilterChip(
                    selected = view == v,
                    onClick = { vm.setView(v) },
                    label = {
                        Text(
                            when (v) {
                                CalendarView.MONTH -> "Month"
                                CalendarView.WEEK -> "Week"
                                CalendarView.DAY -> "Agenda"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val days = vm.visibleDays()
        when (view) {
            CalendarView.MONTH -> MonthGrid(
                focus = focus,
                days = days,
                events = state.eventsByDay,
                onPick = vm::pickDay,
            )
            CalendarView.WEEK -> WeekStrip(
                focus = focus,
                days = days.take(7),
                events = state.eventsByDay,
                onPick = vm::pickDay,
            )
            CalendarView.DAY -> DayAgenda(
                days = days,
                selected = focus,
                events = state.eventsByDay,
                vm = vm,
                onOpenTask = onOpenTask,
                onNewTask = onNewTask,
                onOpenDiary = onOpenDiary,
                onOpenNote = onOpenNote,
            )
        }
    }
}

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun MonthGrid(
    focus: LocalDate,
    days: List<LocalDate>,
    events: Map<Long, List<DayEvent>>,
    onPick: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
            WEEKDAYS.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        inMonth = day.month == focus.month,
                        isToday = day == LocalDate.now(),
                        isSelected = day == focus,
                        events = events[Dates.toEpochDay(day)] ?: emptyList(),
                        onClick = { onPick(day) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<DayEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (inMonth) 1f else 0.38f
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(6.dp)) {
            Box(
                Modifier
                    .size(24.dp)
                    .then(
                        if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
            }
            Spacer(Modifier.height(2.dp))
            events.take(2).forEach { ev ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(ev.colorArgb).copy(alpha = contentAlpha)),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        ev.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f * contentAlpha),
                    )
                }
            }
            if (events.size > 2) {
                Text(
                    "+${events.size - 2}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                )
            }
        }
    }
}

@Composable
private fun WeekStrip(
    focus: LocalDate,
    days: List<LocalDate>,
    events: Map<Long, List<DayEvent>>,
    onPick: (LocalDate) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            val evs = events[Dates.toEpochDay(day)] ?: emptyList()
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (day == focus) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    )
                    .clickable { onPick(day) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    day.dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                )
                evs.take(3).forEach { ev ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(ev.colorArgb)),
                    )
                }
                if (evs.size > 3) {
                    Text("+${evs.size - 3}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DayAgenda(
    days: List<LocalDate>,
    selected: LocalDate,
    events: Map<Long, List<DayEvent>>,
    vm: CalendarViewModel,
    onOpenTask: (Long) -> Unit,
    onNewTask: (Long) -> Unit,
    onOpenDiary: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // week selector row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                days.forEach { day ->
                    val count = (events[Dates.toEpochDay(day)] ?: emptyList()).size
                    val isSel = day == selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            )
                            .clickable { vm.pickDay(day) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            day.dayOfWeek.name.take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(minOf(count, 3)) {
                                Box(
                                    Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
            Text(
                Dates.dayLabel(selected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
            )
            val evs = events[Dates.toEpochDay(selected)] ?: emptyList()
            if (evs.isEmpty()) {
                EmptyState(
                    emoji = "🌤️",
                    title = "Nothing scheduled",
                    subtitle = "A clear day — or a good time to plan one.",
                    action = {
                        FilterChip(selected = false, onClick = { onNewTask(Dates.toEpochDay(selected)) }, label = { Text("Add a task") })
                    },
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(evs, key = { "${it::class.simpleName}-${eventKey(it)}" }) { ev ->
                        AgendaRow(
                            ev = ev,
                            onToggleTask = { vm.toggleTask((ev as DayEvent.TaskEv).instance, !(ev).instance.done) },
                            onToggleHabit = { vm.toggleHabit((ev as DayEvent.HabitEv).habit.id, (ev).day, !(ev).done) },
                            onOpenTask = { onOpenTask((ev as DayEvent.TaskEv).instance.task.id) },
                            onOpenNote = { onOpenNote((ev as DayEvent.NoteEv).note.id) },
                            onOpenDiary = { onOpenDiary(Dates.toEpochDay(selected)) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onNewTask(Dates.toEpochDay(selected)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Rounded.Add, "New task")
        }
    }
}

private fun eventKey(ev: DayEvent): Long = when (ev) {
    is DayEvent.TaskEv -> ev.instance.task.id
    is DayEvent.HabitEv -> 1_000_000L + ev.habit.id
    is DayEvent.NoteEv -> 2_000_000L + ev.note.id
    is DayEvent.DiaryEv -> 3_000_000L + ev.entry.dayEpoch
}

@Composable
private fun AgendaRow(
    ev: DayEvent,
    onToggleTask: () -> Unit,
    onToggleHabit: () -> Unit,
    onOpenTask: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenDiary: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = when (ev) {
            is DayEvent.TaskEv -> onOpenTask
            is DayEvent.NoteEv -> onOpenNote
            is DayEvent.DiaryEv -> onOpenDiary
            is DayEvent.HabitEv -> null
        } ?: {},
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (ev) {
                is DayEvent.TaskEv -> {
                    Checkbox(
                        checked = ev.instance.done,
                        onCheckedChange = { onToggleTask() },
                        modifier = Modifier.size(34.dp),
                    )
                }
                is DayEvent.HabitEv -> {
                    Icon(
                        if (ev.done) Icons.Rounded.CheckCircle else Icons.Rounded.StarBorder,
                        contentDescription = if (ev.done) "Undo" else "Check in",
                        tint = Color(ev.colorArgb),
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { onToggleHabit() },
                    )
                }
                is DayEvent.NoteEv -> Icon(
                    Icons.Rounded.Description,
                    null,
                    tint = Color(ev.colorArgb),
                    modifier = Modifier.size(22.dp),
                )
                is DayEvent.DiaryEv -> Icon(
                    Icons.Rounded.EditNote,
                    null,
                    tint = Color(ev.colorArgb),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    ev.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (ev is DayEvent.TaskEv && ev.instance.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else MaterialTheme.colorScheme.onSurface,
                )
                val sub = when (ev) {
                    is DayEvent.TaskEv -> listOfNotNull(
                        ev.instance.task.dueTime?.let(Times::label),
                        if (ev.instance.recurring) "repeats" else null,
                    ).joinToString(" · ").ifBlank { null }
                    is DayEvent.HabitEv -> if (ev.done) "done today" else "tap to check in"
                    is DayEvent.NoteEv -> "note"
                    is DayEvent.DiaryEv -> "journal"
                }
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (ev is DayEvent.TaskEv) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(ev.colorArgb)),
                )
            }
        }
    }
}
