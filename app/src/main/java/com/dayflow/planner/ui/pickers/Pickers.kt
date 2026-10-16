package com.dayflow.planner.ui.pickers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import com.dayflow.planner.logic.Times

/** Material3 date picker wrapped in a dialog; LocalDate <-> UTC millis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(
    title: String,
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis =
        initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) { Text("Choose") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
            )
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.padding(top = 8.dp)) {
                TimePicker(state = state)
                Row(Modifier.padding(top = 8.dp)) {
                    Text(
                        Times.label(state.hour * 60 + state.minute),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Convenience: now() local date (kept here so screens don't import java.time twice). */
fun todayLocal(): LocalDate = LocalDate.now()

/** minutes-from-midnight for a wall clock time. */
fun minutesOf(time: LocalTime): Int = time.hour * 60 + time.minute
