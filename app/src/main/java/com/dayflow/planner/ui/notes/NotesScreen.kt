package com.dayflow.planner.ui.notes

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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.planner.DayFlowApp
import com.dayflow.planner.data.NoteEntity
import com.dayflow.planner.logic.Dates
import com.dayflow.planner.ui.common.EmptyState
import com.dayflow.planner.ui.containerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NotesViewModel(private val app: DayFlowApp) : ViewModel() {

    private val repo = app.container.repository

    enum class Filter { ALL, PINNED, FAVORITES }

    private val filter = MutableStateFlow(Filter.ALL)
    private val query = MutableStateFlow("")

    val notes: StateFlow<List<NoteEntity>> = combine(
        repo.observeNotes(),
        filter,
        query,
    ) { list, f, q ->
        list.asSequence()
            .filter {
                when (f) {
                    Filter.ALL -> true
                    Filter.PINNED -> it.pinned
                    Filter.FAVORITES -> it.favorite
                }
            }
            .filter {
                q.isBlank() ||
                    it.title.contains(q, true) ||
                    it.body.contains(q, true) ||
                    it.tags.any { t -> t.contains(q.removePrefix("#"), true) }
            }
            .sortedWith(compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt })
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(f: Filter) { filter.value = f }
    fun setQuery(q: String) { query.value = q }

    fun togglePin(note: NoteEntity) = viewModelScope.launch { repo.setNotePinned(note.id, !note.pinned) }
    fun toggleFavorite(note: NoteEntity) = viewModelScope.launch { repo.setNoteFavorite(note.id, !note.favorite) }
    fun delete(note: NoteEntity) = viewModelScope.launch { repo.deleteNote(note.id) }

    fun save(note: NoteEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            val title = note.title.trim().ifBlank {
                note.body.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) ?: "Note"
            }
            repo.saveNote(note.copy(title = title))
            onSaved()
        }
    }

    companion object {
        fun dayOf(note: NoteEntity): LocalDate =
            Instant.ofEpochMilli(note.updatedAt.coerceAtLeast(1L))
                .atZone(ZoneId.systemDefault()).toLocalDate()
    }
}

@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: NotesViewModel = viewModel(factory = containerFactory(context) { NotesViewModel(it) })
    val notes by vm.notes.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NotesViewModel.Filter.ALL) }

    LaunchedEffect(query, filter) {
        vm.setQuery(query)
        vm.setFilter(filter)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Notes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                NotesViewModel.Filter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = {
                            Text(
                                when (f) {
                                    NotesViewModel.Filter.ALL -> "All"
                                    NotesViewModel.Filter.PINNED -> "Pinned"
                                    NotesViewModel.Filter.FAVORITES -> "Favorites"
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
            if (notes.isEmpty()) {
                EmptyState(
                    emoji = "📝",
                    title = "No notes",
                    subtitle = "Quick thoughts, ideas, shopping lists — anything that isn't a task.",
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onOpen = { onOpenNote(note.id) },
                            onPin = { vm.togglePin(note) },
                            onFav = { vm.toggleFavorite(note) },
                            onDelete = { vm.delete(note) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onNewNote,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Rounded.Add, "New note") }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onFav: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (note.pinned) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.favorite) {
                    Icon(
                        Icons.Rounded.Star,
                        null,
                        Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { menu = true }, modifier = Modifier.size(26.dp)) {
                    Text("⋮", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (note.pinned) "Unpin" else "Pin") },
                        onClick = { menu = false; onPin() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (note.favorite) "Unfavorite" else "Favorite") },
                        onClick = { menu = false; onFav() },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
            if (note.body.isNotBlank()) {
                Text(
                    note.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                Dates.pretty(NotesViewModel.dayOf(note)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vm: NotesViewModel = viewModel(factory = containerFactory(context) { NotesViewModel(it) })
    val all by vm.notes.collectAsStateWithLifecycle()
    val note = if (noteId == 0L) null else all.firstOrNull { it.id == noteId }

    var title by remember(noteId) { mutableStateOf(note?.title ?: "") }
    var body by remember(noteId) { mutableStateOf(note?.body ?: "") }
    var base by remember(noteId) { mutableStateOf(note ?: NoteEntity(id = noteId)) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { /* start editing right away */ }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == 0L) "New note" else "Note") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.save(base.copy(title = title, body = body), onBack) }) {
                        Icon(Icons.Rounded.Check, "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (noteId != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("Write something…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 10,
                maxLines = 24,
                shape = MaterialTheme.shapes.medium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pinned", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = base.pinned, onCheckedChange = { base = base.copy(pinned = it) })
                Spacer(Modifier.size(12.dp))
                Text("Favorite", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = base.favorite, onCheckedChange = { base = base.copy(favorite = it) })
            }
            TextButton(onClick = { vm.save(base.copy(title = title, body = body), onBack) }) {
                Text("Save note")
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?") },
            text = { Text("“${title.ifBlank { "Untitled" }}” will be removed.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(base); confirmDelete = false; onBack() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
