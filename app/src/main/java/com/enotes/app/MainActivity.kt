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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
        setContent {
            var themeMode by remember {
                mutableStateOf(getSharedPreferences("enotes_preferences", MODE_PRIVATE).getString("theme_mode", "system") ?: "system")
            }
            val dark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val scheme = if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFB9B7FF), secondary = androidx.compose.ui.graphics.Color(0xFFC6C3FF))
                else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF4F46C8), secondary = androidx.compose.ui.graphics.Color(0xFF625BCE))
            MaterialTheme(colorScheme = scheme) {
                NotesHome(vm, themeMode) { mode ->
                    themeMode = mode
                    getSharedPreferences("enotes_preferences", MODE_PRIVATE).edit().putString("theme_mode", mode).apply()
                }
            }
        }
    }
}

class NotesViewModel(private val repo: NotesRepository) : ViewModel() {
    val notes = repo.observeNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archived = repo.observeArchived().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trash = repo.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folders = repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(title: String, body: String, old: Note?, category: String = old?.category ?: "Others", folder: String? = old?.folder, attachmentUri: String? = old?.attachmentUri) { viewModelScope.launch { repo.save(title, body, old, category, folder, attachmentUri) } }
    fun createFolder(name: String) { viewModelScope.launch { repo.createFolder(name) } }
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
                    add(Note(id = item.getString("id"), title = item.optString("title", "Untitled note"), body = item.optString("body", ""), createdAt = item.optLong("createdAt", System.currentTimeMillis()), updatedAt = item.optLong("updatedAt", System.currentTimeMillis()), isPinned = item.optBoolean("isPinned", false), isArchived = item.optBoolean("isArchived", false), deletedAt = if (item.isNull("deletedAt")) null else item.optLong("deletedAt"), category = item.optString("category", "Others"), folder = item.optString("folder").takeIf { it.isNotBlank() }))
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
private fun NotesHome(vm: NotesViewModel, themeMode: String, onThemeModeChange: (String) -> Unit) {
    val context = LocalContext.current
    val notes by vm.notes.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.NOTES) }
    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("All Notes") }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var sortByTitle by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf("") }
    var quickMessage by remember { mutableStateOf<String?>(null) }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                val json = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to open file") } }
                json.onSuccess { vm.importBackup(it) { backupMessage = it } }.onFailure { backupMessage = "Could not read backup: ${it.message}" }
            }
        }
    }
    val importDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.save("${uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Imported document"}", "Document attached. Open this note to review its attachment.", null, attachmentUri=uri.toString())
            quickMessage = "File imported as a note attachment. Camera scanning is not implemented."
        }
    }
    var editor by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    val source = when(tab) { Tab.NOTES -> notes; Tab.ARCHIVE -> archived; Tab.TRASH -> trash; Tab.SETTINGS -> emptyList() }
    val filtered = source.filter {
        (query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) || (it.folder?.contains(query, true) == true) || it.category.contains(query, true)) &&
        (tab != Tab.NOTES || categoryFilter == "All Notes" || it.category.equals(categoryFilter, true)) &&
        (tab != Tab.NOTES || folderFilter == null || it.folder == folderFilter)
    }
    val shown = if (sortByTitle && tab != Tab.TRASH) filtered.sortedBy { it.title.lowercase() } else filtered
    Scaffold(
        topBar = { TopAppBar(title = { Column {
            Text("ENotes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(when(tab) { Tab.NOTES -> "Your ideas, organized"; Tab.ARCHIVE -> "Archived notes"; Tab.TRASH -> "Recover deleted notes"; Tab.SETTINGS -> "Workspace settings" }, style = MaterialTheme.typography.labelMedium)
        } }, actions = { if(tab == Tab.NOTES) {
            TextButton(onClick = { quickMessage = "Notes are stored on this device. Cloud sync is not connected." }) { Icon(Icons.Default.CloudDone, null, tint = Color(0xFF31865B)); Spacer(Modifier.width(4.dp)); Text("On device", style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = { quickMessage = "Reminders and notifications are not available in this build." }) { Icon(Icons.Default.NotificationsNone, "Notifications") }
            IconButton(onClick = { quickMessage = "ENotes is using local storage. Account sign-in is not configured." }) { Icon(Icons.Default.AccountCircle, "Account") }
        } }) },
        bottomBar = { NavigationBar {
            NavigationBarItem(tab == Tab.NOTES, { tab = Tab.NOTES; query = "" }, { Icon(Icons.Default.StickyNote2, null) }, label = { Text("Notes") })
            NavigationBarItem(tab == Tab.ARCHIVE, { tab = Tab.ARCHIVE; query = "" }, { Icon(Icons.Default.Archive, null) }, label = { Text("Archive") })
            NavigationBarItem(tab == Tab.TRASH, { tab = Tab.TRASH; query = "" }, { Icon(Icons.Default.DeleteOutline, null) }, label = { Text("Trash") })
            NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS; query = "" }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
        } },
        floatingActionButton = { if (tab == Tab.NOTES) Box { FloatingActionButton(onClick = { fabMenu = true }) { Icon(Icons.Default.Add, "Create") }; DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
            DropdownMenuItem(text = { Text("New note") }, leadingIcon = { Icon(Icons.Default.NoteAdd, null) }, onClick = { fabMenu=false; editing=null; editor=true })
            DropdownMenuItem(text = { Text("New folder") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { fabMenu=false; createFolder=true })
        } } }
    ) { inset ->
        if (tab == Tab.SETTINGS) {
            Column(Modifier.fillMaxSize().padding(inset).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                SettingRow("Storage", "Notes are saved locally on this device.")
                SettingRow("Account and sync", "Not connected in this build.")
                SettingRow("Google Drive", "Not connected; Drive changes will require confirmation.")
                SettingRow("AI assistant", "Requires a configured provider. No API key is embedded.")
                SettingRow("App lock", "PIN and biometric lock are not implemented yet.")
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Choose how ENotes looks on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = themeMode == option.first,
                            onClick = { onThemeModeChange(option.first) },
                            shape = SegmentedButtonDefaults.itemShape(index, 3)
                        ) { Text(option.second) }
                    }
                }
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
                        put("category", note.category); put("folder", note.folder ?: JSONObject.NULL)
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
            Column(Modifier.fillMaxSize().padding(inset).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == Tab.NOTES) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search notes and folders…") }, singleLine = true, shape = MaterialTheme.shapes.large, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, trailingIcon = { Row { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") }; Box { IconButton(onClick={showSortMenu=true}) { Icon(Icons.Default.Tune, "Sort notes") }; DropdownMenu(expanded=showSortMenu,onDismissRequest={showSortMenu=false}) { DropdownMenuItem(text={Text("Recently updated")},leadingIcon={Icon(Icons.Default.Schedule,null)},onClick={ sortByTitle=false; showSortMenu=false }); DropdownMenuItem(text={Text("Title A–Z")},leadingIcon={Icon(Icons.Default.SortByAlpha,null)},onClick={ sortByTitle=true; showSortMenu=false }) } } } })
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                        items(listOf("New Note", "New Folder", "Scan Document", "Voice Note", "AI Assistant")) { action ->
                            val tint = when(action) { "Scan Document" -> Color(0xFF21A36B); "Voice Note" -> Color(0xFFE84E79); else -> MaterialTheme.colorScheme.primary }
                            val icon = when(action) { "New Note" -> Icons.Default.NoteAdd; "New Folder" -> Icons.Default.CreateNewFolder; "Scan Document" -> Icons.Default.DocumentScanner; "Voice Note" -> Icons.Default.Mic; else -> Icons.Default.AutoAwesome }
                            Card(Modifier.width(112.dp).height(106.dp).clickable {
                                when(action) {
                                    "New Note" -> { editing=null; editor=true }
                                    "New Folder" -> createFolder=true
                                    "Scan Document" -> importDocumentLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    "Voice Note" -> quickMessage="Voice recording is not available in this build. No audio was recorded."
                                    else -> quickMessage="AI Assistant needs a configured secure provider. No AI request was sent."
                                }
                            }, colors = CardDefaults.cardColors(containerColor = tint.copy(alpha=.09f)), shape = MaterialTheme.shapes.large) {
                                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, contentDescription=null, tint=tint); Spacer(Modifier.height(6.dp)); Text(action, style=MaterialTheme.typography.labelMedium, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=TextOverflow.Ellipsis)
                                    Text(when(action) { "New Note" -> "Write an idea"; "New Folder" -> "Organize notes"; "Scan Document" -> "Choose PDF or image"; "Voice Note" -> "Unavailable"; else -> "Not configured" }, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Bolt, null, tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text("Quick Access", fontWeight=FontWeight.SemiBold); Text("Jump to your categories", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick={ folderFilter=null; categoryFilter="All Notes" }) { Icon(Icons.Default.ChevronRight, "Clear category filter") } }
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        val categories = listOf("All Notes", "Personal", "Study", "Work", "Others")
                        items(categories) { category ->
                            val count=if(category=="All Notes") notes.size else notes.count { it.category.equals(category,true) }
                            FilterChip(selected=categoryFilter==category && folderFilter==null, onClick={ categoryFilter=category; folderFilter=null }, label={ Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(category); Text(count.toString(), style=MaterialTheme.typography.labelSmall) } }, leadingIcon={ Icon(when(category) { "Personal" -> Icons.Default.Person; "Study" -> Icons.Default.School; "Work" -> Icons.Default.Work; "Others" -> Icons.Default.MoreHoriz; else -> Icons.Default.Layers }, null) })
                        }
                        items(folders, key={it.name}) { folder ->
                            val count=notes.count { it.folder==folder.name }
                            FilterChip(selected=folderFilter==folder.name, onClick={ folderFilter=folder.name; categoryFilter="All Notes" }, label={ Text("${folder.name} · $count") }, leadingIcon={ Icon(Icons.Default.Folder, null) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, tint=MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(if(folderFilter!=null) folderFilter!! else if(categoryFilter=="All Notes") "Recent Notes" else "$categoryFilter Notes", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f)); TextButton(onClick={ categoryFilter="All Notes"; folderFilter=null }) { Text("View All"); Icon(Icons.Default.ChevronRight,null) } }
                } else {
                    OutlinedTextField(value=query, onValueChange={query=it}, modifier=Modifier.fillMaxWidth(), label={Text("Search notes")}, singleLine=true, leadingIcon={Icon(Icons.Default.Search,null)})
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Text(when(tab) { Tab.ARCHIVE -> "Archived notes"; Tab.TRASH -> "Trash"; else -> "" }, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold, modifier=Modifier.weight(1f)); Box { IconButton(onClick={showSortMenu=true}, enabled=tab!=Tab.TRASH) { Icon(Icons.Default.Sort,"Sort notes") }; DropdownMenu(expanded=showSortMenu,onDismissRequest={showSortMenu=false}) { DropdownMenuItem(text={Text("Recently updated")},onClick={ sortByTitle=false; showSortMenu=false }); DropdownMenuItem(text={Text("Title A–Z")},onClick={ sortByTitle=true; showSortMenu=false }) } } }
                }
                if (shown.isNotEmpty()) Text("${shown.size} " + when (tab) { Tab.NOTES -> if (shown.size == 1) "note" else "notes"; Tab.ARCHIVE -> "archived"; Tab.TRASH -> "in trash"; Tab.SETTINGS -> "" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (shown.isEmpty()) {
                    Text(when(tab) { Tab.NOTES -> if(query.isBlank()) "Start with one small idea." else "No matching notes"; Tab.ARCHIVE -> "Nothing archived yet."; Tab.TRASH -> "Trash is empty."; Tab.SETTINGS -> "" }, style = MaterialTheme.typography.titleLarge)
                    if(tab == Tab.NOTES && query.isBlank()) Button(onClick = { editing = null; editor = true }) { Text("Create your first note") }
                    if(tab == Tab.TRASH && query.isBlank()) Text("Deleted notes stay here until permanently deleted.")
                } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding=PaddingValues(bottom=16.dp)) {
                    items(shown, key = { it.id }) { note ->
                        Card(Modifier.fillMaxWidth().clickable { if(tab != Tab.TRASH) { editing = note; editor = true } }, shape=MaterialTheme.shapes.large, colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(note.title.ifBlank { "Untitled note" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (note.isPinned && tab == Tab.NOTES) Icon(Icons.Default.PushPin, contentDescription = "Pinned note", tint = MaterialTheme.colorScheme.primary)
                                }
                                if (note.body.isNotBlank()) { Spacer(Modifier.height(5.dp)); Text(note.body, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.CenterVertically) { SuggestionChip(onClick={categoryFilter=note.category;folderFilter=null}, label={Text(note.category)}); note.folder?.let { value -> SuggestionChip(onClick={folderFilter=value;categoryFilter="All Notes"},label={Text(value)}) }; if(note.attachmentUri!=null) Text("Attachment", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary) }
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
        var category by remember(editing?.id) { mutableStateOf(editing?.category ?: "Others") }
        var folder by remember(editing?.id) { mutableStateOf(editing?.folder) }
        var categoryMenu by remember { mutableStateOf(false) }
        var folderMenu by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { editor = false }, title = { Text(if(editing == null) "New note" else "Edit note") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Write something…") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Box { OutlinedButton(onClick={categoryMenu=true}) { Text("Category: $category") }; DropdownMenu(expanded=categoryMenu,onDismissRequest={categoryMenu=false}) { listOf("Personal","Study","Work","Others").forEach { value -> DropdownMenuItem(text={Text(value)},onClick={ category=value; categoryMenu=false }) } } }
                Box { OutlinedButton(onClick={folderMenu=true}) { Text("Folder: ${folder ?: "None"}") }; DropdownMenu(expanded=folderMenu,onDismissRequest={folderMenu=false}) { DropdownMenuItem(text={Text("No folder")},onClick={ folder=null; folderMenu=false }); folders.forEach { value -> DropdownMenuItem(text={Text(value.name)},onClick={ folder=value.name; folderMenu=false }) } } }
                editing?.attachmentUri?.let { uriText -> OutlinedButton(onClick={ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).apply { data=android.net.Uri.parse(uriText); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }.onFailure { quickMessage="No app could open this attachment." } }) { Icon(Icons.Default.AttachFile,null); Spacer(Modifier.width(6.dp)); Text("Open attachment") } }
                Text(body.trim().split(Regex("\\s+")).count { it.isNotBlank() }.toString() + " words · " + body.length + " characters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } },
            confirmButton = { Button(onClick = { vm.save(title, body, editing, category, folder); editor = false }) { Text("Save note") } },
            dismissButton = { TextButton(onClick = { editor = false }) { Text("Cancel") } })
    }
    if(createFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest={createFolder=false}, title={Text("New folder")}, text={OutlinedTextField(name,{name=it},label={Text("Folder name")},singleLine=true)},
            confirmButton={Button(onClick={if(name.isNotBlank()) { vm.createFolder(name); createFolder=false } }) { Text("Create") }}, dismissButton={TextButton(onClick={createFolder=false}) { Text("Cancel") }})
    }
    quickMessage?.let { message -> AlertDialog(onDismissRequest={quickMessage=null}, title={Text("ENotes")}, text={Text(message)}, confirmButton={TextButton(onClick={quickMessage=null}) { Text("OK") }}) }
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
