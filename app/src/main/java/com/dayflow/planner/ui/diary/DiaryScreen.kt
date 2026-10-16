package com.dayflow.planner.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.DiaryEntryEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.ui.containerFactory
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val DIARY_MOODS = listOf(
    "😖" to "Rough",
    "🙁" to "Low",
    "😐" to "Okay",
    "🙂" to "Good",
    "😄" to "Great",
)

class DiaryViewModel(app: DayFlowApp) : ViewModel() {
    private val repo = app.container.repository

    private val month = MutableStateFlow(YearMonth.now())

    val monthState: StateFlow<MonthUi> = combine(
        repo.observeDiary(),
        month,
    ) { list, m ->
        MonthUi(
            month = m,
            days = Dates.monthGrid(m.atDay(1)),
            entries = list.associateBy { it.dayEpoch },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthUi())

    fun shift(delta: Long) { month.value = month.value.plusMonths(delta) }

    fun save(day: LocalDate, mood: Int?, text: String) {
        viewModelScope.launch { repo.saveDiary(day, mood, text) }
    }

    fun delete(day: LocalDate) {
        viewModelScope.launch { repo.deleteDiary(day) }
    }

    data class MonthUi(
        val month: YearMonth = YearMonth.now(),
        val days: List<LocalDate> = emptyList(),
        val entries: Map<Long, DiaryEntryEntity> = emptyMap(),
    )
}

@Composable
fun DiaryScreen(
    initialDayEpoch: Long?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: DiaryViewModel = viewModel(factory = containerFactory(context) { DiaryViewModel(it) })
    val ui by vm.monthState.collectAsStateWithLifecycle()
    var editingDay by remember {
        mutableStateOf(initialDayEpoch?.let(Dates::fromEpochDay) ?: LocalDate.now())
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (onBack == null) 16.dp else 4.dp, end = 16.dp, top = 6.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            }
            Text(
                "Journal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        // Month calendar with mood dots ---------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.Rounded.ArrowBackIos, "Previous month") }
            Text(
                Dates.shortMonthLabel(ui.month.year, ui.month.month),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Medium,
            )
            IconButton(
                onClick = { vm.shift(1) },
                enabled = ui.month < YearMonth.now(),
            ) { Icon(Icons.Rounded.ArrowForwardIos, "Next month") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        ui.days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                week.forEach { day ->
                    val entry = ui.entries[Dates.toEpochDay(day)]
                    val inMonth = day.month == ui.month.month
                    val selected = day == editingDay
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primaryContainer
                                    inMonth -> Color.Transparent
                                    else -> Color.Transparent
                                },
                            )
                            .clickable(enabled = inMonth) { editingDay = day },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (!inMonth) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            entry?.mood?.let { m ->
                                DIARY_MOODS.getOrNull(m)?.let { Text(it.first, style = MaterialTheme.typography.labelSmall) }
                            } ?: run {
                                if (entry != null && entry.text.isNotBlank() && inMonth) {
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Day editor -----------------------------------------------------------
        DayEditor(
            day = editingDay,
            entry = ui.entries[Dates.toEpochDay(editingDay)],
            onSave = { mood, text -> vm.save(editingDay, mood, text) },
            onDelete = { vm.delete(editingDay) },
        )
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DayEditor(
    day: LocalDate,
    entry: DiaryEntryEntity?,
    onSave: (Int?, String) -> Unit,
    onDelete: () -> Unit,
) {
    var mood by remember(day) { mutableStateOf(entry?.mood) }
    var text by remember(day) { mutableStateOf(entry?.text ?: "") }
    var dirty by remember(day) { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EditNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    Dates.dayLabel(day),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (entry != null) {
                    TextButton(onClick = onDelete) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                }
            }
            Text("How did it go?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DIARY_MOODS.forEachIndexed { index, (emoji, label) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (mood == index) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                            )
                            .clickable { mood = if (mood == index) null else index; dirty = true }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(emoji, style = MaterialTheme.typography.titleMedium)
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; dirty = true },
                placeholder = { Text("What happened today? Anything worth remembering.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 18,
                shape = MaterialTheme.shapes.medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { onSave(mood, text) },
                    enabled = dirty && (text.isNotBlank() || mood != null),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save entry")
                }
                if (dirty && text.isBlank() && mood == null && entry != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text("Delete entry", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
