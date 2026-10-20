package com.enotes.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enotes.app.data.Note
import com.enotes.app.data.NotesDatabase
import com.enotes.app.data.NotesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val vm: NotesViewModel by lazy {
        val repo = NotesRepository(NotesDatabase.get(applicationContext).noteDao())
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(c: Class<T>): T = NotesViewModel(repo) as T
        })[NotesViewModel::class.java]
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { NotesHome(vm) } }
    }
}

class NotesViewModel(private val repo: NotesRepository) : ViewModel() {
    val notes = repo.observeNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archived = repo.observeArchived().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trash = repo.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(title: String, body: String, old: Note?) { viewModelScope.launch { repo.save(title, body, old) } }
    fun trash(note: Note) { viewModelScope.launch { repo.trash(note) } }
    fun restore(note: Note) { viewModelScope.launch { repo.restore(note) } }
    fun permanentlyDelete(note: Note) { viewModelScope.launch { repo.permanentlyDelete(note) } }
    fun archive(note: Note) { viewModelScope.launch { repo.archive(note) } }
    fun unarchive(note: Note) { viewModelScope.launch { repo.unarchive(note) } }
    fun pin(note: Note) { viewModelScope.launch { repo.togglePin(note) } }
    fun importBackup(json: String, onResult: (String) -> Unit) { viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching {
            val root = JSONObject(json)
            require(root.optString("format") == "enotes-backup-v1") { "This is not a supported ENotes backup file." }
            val array = root.getJSONArray("notes")
            val restored = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(Note(id = item.getString("id"), title = item.optString("title", "Untitled note"), body = item.optString("body", ""), createdAt = item.optLong("createdAt", System.currentTimeMillis()), updatedAt = item.optLong("updatedAt", System.currentTimeMillis()), isPinned = item.optBoolean("isPinned", false), isArchived = item.optBoolean("isArchived", false), deletedAt = if (item.isNull("deletedAt")) null else item.optLong("deletedAt")))
                }
            }
            require(restored.size <= 100000) { "Backup contains too many notes." }
            repo.importNotes(restored)
            "Restored ${restored.size} notes. Existing notes with matching IDs were updated."
        }.getOrElse { "Could not restore backup: ${it.message ?: "invalid file"}" } }
        onResult(result)
    } }
}

private enum class Tab { NOTES, ARCHIVE, TRASH, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesHome(vm: NotesViewModel) {
    val context = LocalContext.current
    val notes by vm.notes.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.NOTES) }
    var query by remember { mutableStateOf("") }
    var sortByTitle by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf("") }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                val json = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to open file") } }
                json.onSuccess { vm.importBackup(it) { backupMessage = it } }.onFailure { backupMessage = "Could not read backup: ${it.message}" }
            }
        }
    }
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    val source = when(tab) { Tab.NOTES -> notes; Tab.ARCHIVE -> archived; Tab.TRASH -> trash; Tab.SETTINGS -> emptyList() }
    val filtered = source.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
    val shown = if (sortByTitle && tab != Tab.TRASH) filtered.sortedBy { it.title.lowercase() } else filtered
    Scaffold(
        topBar = { TopAppBar(title = { Column {
            Text("ENotes", fontWeight = FontWeight.Bold)
            Text(when(tab) { Tab.NOTES -> "Your ideas, organized"; Tab.ARCHIVE -> "Archived notes"; Tab.TRASH -> "Recover deleted notes"; Tab.SETTINGS -> "Workspace settings" }, style = MaterialTheme.typography.labelMedium)
        } }) },
        bottomBar = { NavigationBar {
            NavigationBarItem(tab == Tab.NOTES, { tab = Tab.NOTES; query = "" }, { Icon(Icons.Default.StickyNote2, null) }, label = { Text("Notes") })
            NavigationBarItem(tab == Tab.ARCHIVE, { tab = Tab.ARCHIVE; query = "" }, { Icon(Icons.Default.Archive, null) }, label = { Text("Archive") })
            NavigationBarItem(tab == Tab.TRASH, { tab = Tab.TRASH; query = "" }, { Icon(Icons.Default.DeleteOutline, null) }, label = { Text("Trash") })
            NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS; query = "" }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
        } },
        floatingActionButton = { if (tab == Tab.NOTES) FloatingActionButton(onClick = { editing = null; editor = true }) { Icon(Icons.Default.Add, "Create note") } }
    ) { inset ->
        if (tab == Tab.SETTINGS) {
            Column(Modifier.fillMaxSize().padding(inset).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                SettingRow("Storage", "Notes are saved locally on this device.")
                SettingRow("Account and sync", "Not connected in this build.")
                SettingRow("Google Drive", "Not connected; Drive changes will require confirmation.")
                SettingRow("AI assistant", "Requires a configured provider. No API key is embedded.")
                SettingRow("App lock", "PIN and biometric lock are not implemented yet.")
                SettingRow("Appearance", "System theme is currently used.")
                SettingRow("Backup and restore", "Create an importable JSON backup or restore one from this device. Backups stay under your control.")
                HorizontalDivider()
                Text("Backup and restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Export preserves note IDs, text, timestamps, pin/archive status, and trash state. Import merges by ID; matching notes are replaced.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    val allNotes = (notes + archived + trash).distinctBy { it.id }.sortedByDescending { it.updatedAt }
                    val array = JSONArray()
                    allNotes.forEach { note -> array.put(JSONObject().apply {
                        put("id", note.id); put("title", note.title); put("body", note.body)
                        put("createdAt", note.createdAt); put("updatedAt", note.updatedAt)
                        put("isPinned", note.isPinned); put("isArchived", note.isArchived)
                        put("deletedAt", note.deletedAt ?: JSONObject.NULL)
                    }) }
                    val backup = JSONObject().apply { put("format", "enotes-backup-v1"); put("exportedAt", System.currentTimeMillis()); put("notes", array) }.toString(2)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_SUBJECT, "ENotes backup.json"); putExtra(Intent.EXTRA_TEXT, backup) }
                    context.startActivity(Intent.createChooser(sendIntent, "Save ENotes backup"))
                    backupMessage = "Choose an app to save or send your JSON backup."
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Export backup") }
                OutlinedButton(onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Import / restore backup") }
                if (backupMessage.isNotBlank()) Text(backupMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(inset).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), label = { Text("Search notes") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } })
                    Box {
                        IconButton(onClick = { showSortMenu = true }, enabled = tab != Tab.TRASH) { Icon(Icons.Default.Sort, "Sort notes") }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("Recently updated") }, leadingIcon = { Icon(Icons.Default.Schedule, null) }, onClick = { sortByTitle = false; showSortMenu = false })
                            DropdownMenuItem(text = { Text("Title A–Z") }, leadingIcon = { Icon(Icons.Default.SortByAlpha, null) }, onClick = { sortByTitle = true; showSortMenu = false })
                        }
                    }
                }
                if (shown.isNotEmpty()) Text("${shown.size} " + when (tab) { Tab.NOTES -> if (shown.size == 1) "note" else "notes"; Tab.ARCHIVE -> "archived"; Tab.TRASH -> "in trash"; Tab.SETTINGS -> "" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (shown.isEmpty()) {
                    Text(when(tab) { Tab.NOTES -> if(query.isBlank()) "Start with one small idea." else "No matching notes"; Tab.ARCHIVE -> "Nothing archived yet."; Tab.TRASH -> "Trash is empty."; Tab.SETTINGS -> "" }, style = MaterialTheme.typography.titleLarge)
                    if(tab == Tab.NOTES && query.isBlank()) Button(onClick = { editing = null; editor = true }) { Text("Create your first note") }
                    if(tab == Tab.TRASH && query.isBlank()) Text("Deleted notes stay here until permanently deleted.")
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.id }) { note ->
                        Card(Modifier.fillMaxWidth().clickable { if(tab != Tab.TRASH) { editing = note; editor = true } }) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(note.title.ifBlank { "Untitled note" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (note.isPinned && tab == Tab.NOTES) Icon(Icons.Default.PushPin, contentDescription = "Pinned note", tint = MaterialTheme.colorScheme.primary)
                                }
                                if (note.body.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(note.body, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Spacer(Modifier.height(8.dp))
                                Text("Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    when(tab) {
                                        Tab.NOTES -> {
                                            IconButton(onClick = {
                                                val textToShare = note.title + if (note.body.isBlank()) "" else "\n\n" + note.body
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "ENotes note" })
                                                    putExtra(Intent.EXTRA_TEXT, textToShare)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share note"))
                                            }) { Icon(Icons.Default.Share, "Share note") }
                                            IconButton(onClick = { vm.pin(note) }) { Icon(Icons.Default.PushPin, if(note.isPinned) "Unpin" else "Pin") }
                                            IconButton(onClick = { vm.archive(note) }) { Icon(Icons.Default.Archive, "Archive note") }
                                            IconButton(onClick = { vm.trash(note) }) { Icon(Icons.Default.DeleteOutline, "Move to trash") }
                                        }
                                        Tab.ARCHIVE -> {
                                            IconButton(onClick = { vm.unarchive(note) }) { Icon(Icons.Default.Unarchive, "Restore from archive") }
                                            IconButton(onClick = { vm.trash(note) }) { Icon(Icons.Default.DeleteOutline, "Move to trash") }
                                        }
                                        Tab.TRASH -> {
                                            IconButton(onClick = { vm.restore(note) }) { Icon(Icons.Default.Restore, "Restore note") }
                                            IconButton(onClick = { deleteTarget = note }) { Icon(Icons.Default.DeleteForever, "Delete permanently") }
                                        }
                                        Tab.SETTINGS -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if(editor) {
        var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
        var body by remember(editing?.id) { mutableStateOf(editing?.body.orEmpty()) }
        AlertDialog(onDismissRequest = { editor = false }, title = { Text(if(editing == null) "New note" else "Edit note") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Write something…") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Text(body.trim().split(Regex("\\s+")).count { it.isNotBlank() }.toString() + " words · " + body.length + " characters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { Button(onClick = { vm.save(title, body, editing); editor = false }) { Text("Save note") } },
            dismissButton = { TextButton(onClick = { editor = false }) { Text("Cancel") } })
    }
    deleteTarget?.let { note ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete permanently?") },
            text = { Text("This note will be permanently deleted from this device. This cannot be undone.") },
            confirmButton = { Button(onClick = { vm.permanentlyDelete(note); deleteTarget = null }) { Text("Delete permanently") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } })
    }
}

@Composable
private fun SettingRow(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}