package com.dayflow.planner.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.remind.ReminderScheduler
import com.dayflow.planner.settings.SettingsStore
import com.dayflow.planner.ui.containerFactory
import com.dayflow.planner.ui.theme.DayFlowPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val app: DayFlowApp) : ViewModel() {
    val settings: SettingsStore = app.container.settings
    private val repo = app.container.repository
    private val backup = app.container.backup

    val message = mutableStateOf<String?>(null)

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                app.contentResolver.openOutputStream(uri)?.use { out -> backup.export(out) }
                    ?: error("Could not open file for writing")
            }
            message.value = result.fold(
                { "Backup written ✓" },
                { "Export failed: ${it.message?.take(90)}" },
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    backup.import(input, wipeFirst = true)
                } ?: error("Could not open file")
            }
            message.value = result.fold(
                { if (it.ok) "Restored: ${it.message}" else "Import failed: ${it.message}" },
                { "Import failed: ${it.message?.take(90)}" },
            )
            if (result.isSuccess) ReminderScheduler.resyncAll(app)
        }
    }

    fun wipeAll() {
        viewModelScope.launch {
            runCatching { repo.deleteAllData() }
            ReminderScheduler.cancelAll(app)
            message.value = "All data deleted"
        }
    }

    fun setReminders(enabled: Boolean) {
        settings.setRemindersEnabled(enabled)
        if (enabled) ReminderScheduler.resyncAll(app) else ReminderScheduler.cancelAll(app)
    }
}

@Composable
fun SettingsScreen(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = containerFactory(context) { SettingsViewModel(it) })
    val settings = vm.settings
    val mode by settings.themeMode.collectAsState()
    val accent by settings.accentIndex.collectAsState()
    val reminders by settings.remindersEnabled.collectAsState()
    val showDone by settings.showCompletedInToday.collectAsState()
    var confirmWipe by remember { mutableStateOf(false) }
    var msg by vm.message

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importBackup) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = if (onBack == null) 0.dp else 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        if (msg != null) {
            LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); msg = null }
            Text(
                msg!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    .padding(10.dp),
            )
        }

        Section("Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth(), space = 4.dp) {
                SettingsStore.ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { settings.setThemeMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                        label = {
                            Text(
                                when (m) {
                                    SettingsStore.ThemeMode.SYSTEM -> "System"
                                    SettingsStore.ThemeMode.LIGHT -> "Light"
                                    SettingsStore.ThemeMode.DARK -> "Dark"
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
            Text("Accent colour", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DayFlowPalette.accents.forEachIndexed { i, a ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(a.seed)
                            .then(
                                if (accent == i) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                else Modifier,
                            )
                            .clickable { settings.setAccent(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (accent == i) {
                            Icon(Icons.Rounded.Check, a.name, Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
            }
            Text(
                DayFlowPalette.accents.getOrElse(accent) { DayFlowPalette.accents.first() }.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Behaviour") {
            ToggleRow(
                title = "Task reminders",
                subtitle = "Notify on the day a timed task is due (Android asks for permission once).",
                checked = reminders,
                onChange = { vm.setReminders(it) },
            )
            ToggleRow(
                title = "Show completed on Today",
                subtitle = "Keep checked-off tasks visible on the Today screen.",
                checked = showDone,
                onChange = settings::setShowCompletedInToday,
            )
        }

        Section("Backup") {
            Text(
                "Everything stays on this device. A backup is a single JSON file you choose where to save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                        exportLauncher.launch("dayflow-backup-$stamp.json")
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Export")
                }
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Upload, null, Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Import")
                }
            }
            Text(
                "Importing replaces all current data with the contents of the chosen file.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Danger zone") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Delete all tasks, habits, notes, journal entries and goals.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { confirmWipe = true }) {
                    Text("Delete data", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Section("About") {
            Text("DayFlow Planner · v1.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                "A calm, offline-first day planner. No accounts, no ads, no tracking — your data never leaves this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(80.dp))
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = { Text("All tasks, habits, notes, journal entries and goals will be permanently removed from this device.") },
            confirmButton = {
                Button(onClick = { confirmWipe = false; vm.wipeAll() }) {
                    Text("Delete all data", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
