package com.enotes.app

import android.os.Bundle
import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
import com.enotes.app.data.NoteFolder
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
            val scheme = if (dark) darkColorScheme(primary = Color(0xFFB9B7FF), secondary = Color(0xFFC6C3FF))
                else lightColorScheme(primary = Color(0xFF5935E8), secondary = Color(0xFF625BCE), background = Color(0xFFFBF9FF), surface = Color.White)
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
    fun save(title: String, body: String, old: Note?, category: String = old?.category ?: "Others", folder: String? = old?.folder, attachmentUri: String? = old?.attachmentUri, priority: String = old?.priority ?: "Medium", reminderAt: Long? = old?.reminderAt, labelColor: String = old?.labelColor ?: "purple", isPinned: Boolean = old?.isPinned ?: false, richContentJson: String = old?.richContentJson ?: "[]") {
        viewModelScope.launch { repo.save(title, body, old, category, folder, attachmentUri, priority, reminderAt, labelColor, isPinned, richContentJson) }
    }
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
                    add(Note(id = item.getString("id"), title = item.optString("title", "Untitled note"), body = item.optString("body", ""), createdAt = item.optLong("createdAt", System.currentTimeMillis()), updatedAt = item.optLong("updatedAt", System.currentTimeMillis()), isPinned = item.optBoolean("isPinned", false), isArchived = item.optBoolean("isArchived", false), deletedAt = if (item.isNull("deletedAt")) null else item.optLong("deletedAt"), category = item.optString("category", "Others"), folder = item.optString("folder").takeIf { it.isNotBlank() && it != "null" }, attachmentUri = item.optString("attachmentUri").takeIf { it.isNotBlank() && it != "null" }, priority = item.optString("priority", "Medium"), reminderAt = if (item.isNull("reminderAt")) null else item.optLong("reminderAt"), labelColor = item.optString("labelColor", "purple"), richContentJson = item.optString("richContentJson", "[]")))
                }
            }
            require(restored.size <= 100000) { "Backup contains too many notes." }
            repo.importNotes(restored)
            "Restored ${restored.size} notes. Existing notes with matching IDs were updated."
        }.getOrElse { "Could not restore backup: ${it.message ?: "invalid file"}" } }
        onResult(result)
    } }
}

private enum class Tab { NOTES, ARCHIVE, TRASH, SETTINGS, MORE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesHome(vm: NotesViewModel, themeMode: String, onThemeModeChange: (String) -> Unit) {
    val context = LocalContext.current
    val dashboardStore = remember { context.getSharedPreferences("enotes_dashboard", android.content.Context.MODE_PRIVATE) }
    var dashboardPreferences by remember { mutableStateOf(DashboardPreferences.decode(dashboardStore.getString("customization", null))) }
    val notes by vm.notes.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.NOTES) }
    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("All Notes") }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var pinnedOnly by remember { mutableStateOf(false) }
    var sortByTitle by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
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
            val providerName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.trim()?.takeIf { name -> name.isNotBlank() && !name.all(Char::isDigit) }
            vm.save(providerName ?: "Imported document", "", null, attachmentUri=uri.toString())
            quickMessage = "File imported as a note attachment. Camera scanning is not implemented."
        }
    }
    var editor by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var pendingEditorMediaKind by remember { mutableStateOf("file") }
    var pickedEditorBlock by remember { mutableStateOf<RichContentBlock?>(null) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    val editorMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull().orEmpty().ifBlank { "${pendingEditorMediaKind.replaceFirstChar { it.uppercase() }} attachment" }
            val kind = when {
                mimeType.startsWith("image/") -> "image"
                mimeType.startsWith("video/") -> "video"
                mimeType.startsWith("audio/") -> "audio"
                else -> pendingEditorMediaKind
            }
            pickedEditorBlock = RichContentBlock(kind = kind, uri = uri.toString(), mimeType = mimeType, title = displayName)
        }
    }
    val source = when(tab) { Tab.NOTES -> notes; Tab.ARCHIVE -> archived; Tab.TRASH -> trash; Tab.SETTINGS, Tab.MORE -> emptyList() }
    val filtered = source.filter {
        (query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) || decodeRichContent(it.richContentJson).any { block -> block.title.contains(query, true) || block.description.contains(query, true) } || (it.folder?.contains(query, true) == true) || it.category.contains(query, true)) &&
        (tab != Tab.NOTES || categoryFilter == "All Notes" || it.category.equals(categoryFilter, true)) &&
        (tab != Tab.NOTES || folderFilter == null || it.folder == folderFilter) &&
        (tab != Tab.NOTES || !pinnedOnly || it.isPinned)
    }
    val shown = if (sortByTitle && tab != Tab.TRASH) filtered.sortedBy { it.title.lowercase() } else filtered
    Scaffold(
        topBar = { if (!editor) TopAppBar(title = {
            if (tab == Tab.NOTES) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    BrandMark(38)
                    Column {
                        Text("ENotes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                        Text("Your ideas, organized", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            } else Column {
                Text(when(tab) { Tab.ARCHIVE -> "Archive"; Tab.TRASH -> "Trash"; Tab.SETTINGS -> "Settings"; Tab.MORE -> "More"; else -> "ENotes" }, fontWeight = FontWeight.Bold)
                Text(when(tab) { Tab.ARCHIVE -> "Archived notes"; Tab.TRASH -> "Recover deleted notes"; Tab.SETTINGS -> "Workspace settings"; Tab.MORE -> "More from ENotes"; else -> "Your ideas, organized" }, style = MaterialTheme.typography.labelMedium)
            }
        }, actions = { if(tab == Tab.NOTES) {
            TextButton(onClick = { quickMessage = "Notes are stored on this device. Cloud sync is not connected." }, contentPadding = PaddingValues(horizontal = 4.dp)) { Icon(Icons.Default.CloudOff, "Local storage", tint = Color(0xFF31865B)); Spacer(Modifier.width(3.dp)); Text("Local", style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = { quickMessage = "Reminders and notifications are not available in this build." }) { Icon(Icons.Default.NotificationsNone, "Notifications") }
            IconButton(onClick = { quickMessage = "ENotes is using local storage. Account sign-in is not configured." }, modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) { Icon(Icons.Default.Person, "Account", tint = Color.White) }
        } }) },
        bottomBar = { if (!editor) NavigationBar {
            NavigationBarItem(tab == Tab.NOTES, { tab = Tab.NOTES; query = "" }, { Icon(Icons.Default.StickyNote2, null) }, label = { Text("Notes", maxLines=1) })
            NavigationBarItem(tab == Tab.ARCHIVE, { tab = Tab.ARCHIVE; query = "" }, { Icon(Icons.Default.Archive, null) }, label = { Text("Archive", maxLines=1) })
            NavigationBarItem(tab == Tab.TRASH, { tab = Tab.TRASH; query = "" }, { Icon(Icons.Default.DeleteOutline, null) }, label = { Text("Trash", maxLines=1) })
            NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS; query = "" }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings", maxLines=1) })
            NavigationBarItem(tab == Tab.MORE, { tab = Tab.MORE; query = "" }, { Icon(Icons.Default.Apps, null) }, label = { Text("More", maxLines=1) })
        } },
        floatingActionButton = { if (!editor && tab == Tab.NOTES) Box { FloatingActionButton(onClick = { fabMenu = true }, shape=CircleShape, containerColor=Color(0xFF5935E8), contentColor=Color.White) { Icon(Icons.Default.Add, "Create") }; DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
            DropdownMenuItem(text = { Text("New note") }, leadingIcon = { Icon(Icons.Default.NoteAdd, null) }, onClick = { fabMenu=false; editing=null; editor=true })
            DropdownMenuItem(text = { Text("New folder") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }, onClick = { fabMenu=false; createFolder=true })
        } } }
    ) { inset ->
        if (editor) {
            NoteEditorScreen(
                modifier = Modifier.fillMaxSize().padding(inset),
                note = editing,
                folders = folders,
                pickedBlock = pickedEditorBlock,
                onConsumePickedBlock = { pickedEditorBlock = null },
                onPickMedia = { kind ->
                    pendingEditorMediaKind = kind
                    editorMediaLauncher.launch(when (kind) {
                        "image" -> arrayOf("image/*")
                        "video" -> arrayOf("video/*")
                        "audio" -> arrayOf("audio/*")
                        else -> arrayOf("*/*")
                    })
                },
                onOpenAttachment = { uriText ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = android.net.Uri.parse(uriText); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
                        .onFailure { quickMessage = "No app could open this attachment." }
                },
                onSave = { draft ->
                    vm.save(draft.title, draft.body, editing, draft.category, draft.folder, draft.attachmentUri, draft.priority, draft.reminderAt, draft.labelColor, draft.pinned, draft.richContentJson)
                    editor = false
                },
                onUnavailable = { quickMessage = it },
                onDismiss = { editor = false }
            )
        } else if (tab == Tab.SETTINGS) {
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
                Text("Export preserves note IDs, text, organization, reminder and priority metadata, timestamps, and lifecycle state. Import merges by ID; matching notes are replaced.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    val allNotes = (notes + archived + trash).distinctBy { it.id }.sortedByDescending { it.updatedAt }
                    val array = JSONArray()
                    allNotes.forEach { note -> array.put(JSONObject().apply {
                        put("id", note.id); put("title", note.title); put("body", note.body)
                        put("createdAt", note.createdAt); put("updatedAt", note.updatedAt)
                        put("isPinned", note.isPinned); put("isArchived", note.isArchived)
                        put("category", note.category); put("folder", note.folder ?: JSONObject.NULL)
                        put("deletedAt", note.deletedAt ?: JSONObject.NULL)
                        put("priority", note.priority); put("reminderAt", note.reminderAt ?: JSONObject.NULL); put("labelColor", note.labelColor)
                        put("attachmentUri", note.attachmentUri ?: JSONObject.NULL); put("richContentJson", note.richContentJson)
                    }) }
                    val backup = JSONObject().apply { put("format", "enotes-backup-v1"); put("exportedAt", System.currentTimeMillis()); put("notes", array) }.toString(2)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_SUBJECT, "ENotes backup.json"); putExtra(Intent.EXTRA_TEXT, backup) }
                    context.startActivity(Intent.createChooser(sendIntent, "Save ENotes backup"))
                    backupMessage = "Choose an app to save or send your JSON backup."
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Export backup") }
                OutlinedButton(onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Import / restore backup") }
                if (backupMessage.isNotBlank()) Text(backupMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        } else if (tab == Tab.NOTES) {
            HomeDashboard(
                modifier = Modifier.fillMaxSize().padding(inset),
                notes = shown,
                allNotes = notes,
                folders = folders,
                dashboardPreferences = dashboardPreferences,
                onDashboardPreferencesChange = { next ->
                    dashboardPreferences = next
                    dashboardStore.edit().putString("customization", next.encode()).apply()
                },
                query = query,
                onQueryChange = { query = it },
                categoryFilter = categoryFilter,
                folderFilter = folderFilter,
                pinnedOnly = pinnedOnly,
                onFilterChange = { showFilterDialog = true },
                onCategorySelected = { categoryFilter = it; folderFilter = null },
                onFolderSelected = { folderFilter = it; categoryFilter = "All Notes" },
                onViewAll = { query = ""; categoryFilter = "All Notes"; folderFilter = null; pinnedOnly = false },
                onCreateNote = { editing = null; editor = true },
                onCreateFolder = { createFolder = true },
                onScanDocument = { importDocumentLauncher.launch(arrayOf("image/*", "application/pdf")) },
                onUnavailable = { quickMessage = it },
                onOpenNote = { editing = it; editor = true },
                onShareNote = { note ->
                    val textToShare = note.title + if (note.body.isBlank()) "" else "\n\n" + note.body
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "ENotes note" })
                        putExtra(Intent.EXTRA_TEXT, textToShare)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share note"))
                },
                onOpenAttachment = { note ->
                    note.attachmentUri?.let { uriText ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(uriText)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }.onFailure { quickMessage = "No app could open this attachment." }
                    }
                },
                onPin = vm::pin,
                onArchive = vm::archive,
                onTrash = vm::trash
            )
        } else if (tab == Tab.MORE) {
            MoreScreen(
                modifier = Modifier.fillMaxSize().padding(inset),
                onCreateNote = { editing = null; editor = true },
                onCreateFolder = { createFolder = true },
                onImportDocument = { importDocumentLauncher.launch(arrayOf("image/*", "application/pdf")) },
                onOpenSettings = { tab = Tab.SETTINGS },
                onUnavailable = { quickMessage = it }
            )
        } else {
            CollectionScreen(
                modifier = Modifier.fillMaxSize().padding(inset),
                tab = tab,
                notes = shown,
                query = query,
                onQueryChange = { query = it },
                sortByTitle = sortByTitle,
                onToggleSort = { sortByTitle = !sortByTitle },
                onOpenNote = { editing = it; editor = true },
                onShareNote = { note ->
                    val textToShare = note.title + if (note.body.isBlank()) "" else "\n\n" + note.body
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "ENotes note" })
                        putExtra(Intent.EXTRA_TEXT, textToShare)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share note"))
                },
                onOpenAttachment = { note ->
                    note.attachmentUri?.let { uriText ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(uriText)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }.onFailure { quickMessage = "No app could open this attachment." }
                    }
                },
                onArchive = vm::archive,
                onUnarchive = vm::unarchive,
                onTrash = vm::trash,
                onRestore = vm::restore,
                onDelete = { deleteTarget = it }
            )
        }
    }
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter notes") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (listOf("All Notes", "Personal", "Study", "Work", "Others")).forEach { value ->
                        Row(Modifier.fillMaxWidth().clickable { categoryFilter = value; folderFilter = null }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = categoryFilter == value && folderFilter == null, onClick = { categoryFilter = value; folderFilter = null })
                            Text(value)
                        }
                    }
                    if (folders.isNotEmpty()) {
                        Text("Folders", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        folders.forEach { folder ->
                            Row(Modifier.fillMaxWidth().clickable { folderFilter = folder.name; categoryFilter = "All Notes" }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = folderFilter == folder.name, onClick = { folderFilter = folder.name; categoryFilter = "All Notes" })
                                Text(folder.name)
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { pinnedOnly = !pinnedOnly }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = pinnedOnly, onCheckedChange = { pinnedOnly = it })
                        Text("Pinned notes only")
                    }
                    if (tab != Tab.TRASH) {
                        Row(Modifier.fillMaxWidth().clickable { sortByTitle = !sortByTitle }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = sortByTitle, onCheckedChange = { sortByTitle = it })
                            Text("Sort by title A–Z")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilterDialog = false }) { Text("Done") } },
            dismissButton = {
                TextButton(onClick = { categoryFilter = "All Notes"; folderFilter = null; pinnedOnly = false; sortByTitle = false; showFilterDialog = false }) { Text("Clear filters") }
            }
        )
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
private fun BrandMark(size: Int) {
    Box(Modifier.size(size.dp).background(Color(0xFF5935E8), RoundedCornerShape(11.dp)).padding(5.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.StickyNote2, null, tint = Color.White, modifier = Modifier.fillMaxSize())
        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).size((size / 2.7f).dp).background(Color(0xFF5935E8), CircleShape).padding(1.dp))
    }
}

@Composable
private fun HomeDashboard(
    modifier: Modifier, notes: List<Note>, allNotes: List<Note>, folders: List<NoteFolder>,
    dashboardPreferences: DashboardPreferences, onDashboardPreferencesChange: (DashboardPreferences) -> Unit,
    query: String, onQueryChange: (String) -> Unit, categoryFilter: String, folderFilter: String?,
    pinnedOnly: Boolean, onFilterChange: () -> Unit, onCategorySelected: (String) -> Unit,
    onFolderSelected: (String?) -> Unit, onViewAll: () -> Unit, onCreateNote: () -> Unit,
    onCreateFolder: () -> Unit, onScanDocument: () -> Unit, onUnavailable: (String) -> Unit,
    onOpenNote: (Note) -> Unit, onShareNote: (Note) -> Unit, onOpenAttachment: (Note) -> Unit,
    onPin: (Note) -> Unit, onArchive: (Note) -> Unit, onTrash: (Note) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var editingActions by remember { mutableStateOf(false) }
    var editingQuickAccess by remember { mutableStateOf(false) }
    val actions = listOf(
        HomeAction("new_note", "New Note", "Create a note", Icons.Default.NoteAdd, Color(0xFF6338E8), onCreateNote),
        HomeAction("new_folder", "New Folder", "Organize notes", Icons.Default.CreateNewFolder, Color(0xFF2876E8), onCreateFolder),
        HomeAction("scan_document", "Scan Document", "Choose PDF or image", Icons.Default.DocumentScanner, Color(0xFF1D9C68), onScanDocument),
        HomeAction("voice_note", "Voice Note", "Unavailable", Icons.Default.Mic, Color(0xFFE34C76), { onUnavailable("Voice recording is not available in this build. No audio was recorded.") }),
        HomeAction("ai_assistant", "AI Assistant", "Not configured", Icons.Default.AutoAwesome, Color(0xFF7047E8), { onUnavailable("AI Assistant needs a configured secure provider. No AI request was sent.") })
    )
    val quickAccessOptions = buildList {
        val icons = mapOf("All Notes" to Icons.Default.Layers, "Personal" to Icons.Default.Person, "Study" to Icons.Default.School, "Work" to Icons.Default.Work, "Others" to Icons.Default.MoreHoriz)
        dashboardCategories.forEach { category ->
            val count = if (category == "All Notes") allNotes.size else allNotes.count { it.category.equals(category, true) }
            add(DashboardOption("category:$category", category, count.toString(), icons[category] ?: Icons.Default.Label, if (category == "All Notes") Color(0xFF5935E8) else Color(0xFF475477), selected = folderFilter == null && categoryFilter == category))
        }
        folders.forEach { folder ->
            add(DashboardOption("folder:${folder.name}", folder.name, allNotes.count { it.folder == folder.name }.toString(), Icons.Default.Folder, Color(0xFF2876E8), selected = folderFilter == folder.name))
        }
        actions.forEach { action ->
            add(DashboardOption("action:${action.id}", action.title, action.subtitle, action.icon, action.tint))
        }
    }
    val actionOptions = actions.map { DashboardOption(it.id, it.title, it.subtitle, it.icon, it.tint) }
    val quickAccessActionIds = dashboardPreferences.quickAccessOrder.filter { it.startsWith("action:") }.toSet()
    LazyColumn(modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "search") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f),
                    placeholder = { Text("Search notes and folders…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true, shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search notes") },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Clear search") } }
                )
                Surface(Modifier.size(52.dp).clickable(onClick = onFilterChange), shape = RoundedCornerShape(18.dp), color = Color(0xFFF0EAFE), contentColor = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Tune, contentDescription = "Filter notes", modifier = Modifier.size(25.dp))
                        if (pinnedOnly || categoryFilter != "All Notes" || folderFilter != null) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }
            }
        }
        item(key = "actions") { QuickActionsSection(actions, dashboardPreferences) { editingActions = true } }
        item(key = "quick-access") {
            QuickAccessSection(quickAccessOptions, dashboardPreferences, onEdit = { editingQuickAccess = true }) { id ->
                when {
                    id.startsWith("category:") -> onCategorySelected(id.removePrefix("category:"))
                    id.startsWith("folder:") -> onFolderSelected(id.removePrefix("folder:"))
                    id.startsWith("action:") -> actions.firstOrNull { it.id == id.removePrefix("action:") }?.onClick?.invoke()
                }
            }
        }
        item(key = "recent-heading") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(9.dp))
                Text("Recent Notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onViewAll, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("View All"); Icon(Icons.Default.ChevronRight, null) }
            }
        }
        if (notes.isEmpty()) item(key = "empty-state") {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NoteAdd, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                Text(if (query.isBlank() && categoryFilter == "All Notes" && folderFilter == null && !pinnedOnly) "Start with one small idea." else "No matching notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (query.isBlank()) OutlinedButton(onClick = onCreateNote) { Text("Create your first note") }
            }
        } else items(notes, key = { it.id }) { note ->
            NoteCard(
                note = note,
                tab = Tab.NOTES,
                onOpen = { onOpenNote(note) },
                onCategory = { onCategorySelected(note.category) },
                onFolder = { note.folder?.let(onFolderSelected) },
                onShare = { onShareNote(note) },
                onOpenAttachment = { onOpenAttachment(note) },
                onPin = { onPin(note) },
                onArchive = { onArchive(note) },
                onTrash = { onTrash(note) }
            )
        }
        item(key = "footer") { DashboardFooter() }
    }
    if (editingActions) CustomizeDashboardDialog(
        title = "Edit Quick Actions",
        description = "Drag to reorder. Hide actions you do not use, pin favorites to the front, or add an action to Quick Access.",
        options = actionOptions,
        order = dashboardPreferences.actionOrder,
        hidden = dashboardPreferences.hiddenActions,
        pinned = dashboardPreferences.pinnedActions,
        quickAccessIds = quickAccessActionIds,
        onVisibilityChange = { id, visible ->
            val order = if (visible && id !in dashboardPreferences.actionOrder) dashboardPreferences.actionOrder + id else dashboardPreferences.actionOrder
            onDashboardPreferencesChange(dashboardPreferences.copy(actionOrder = order, hiddenActions = if (visible) dashboardPreferences.hiddenActions - id else dashboardPreferences.hiddenActions + id))
        },
        onMove = { id, direction -> onDashboardPreferencesChange(dashboardPreferences.copy(actionOrder = moveDashboardItem(dashboardPreferences.actionOrder, id, direction))) },
        onPinChange = { id, pin -> onDashboardPreferencesChange(dashboardPreferences.copy(pinnedActions = if (pin) dashboardPreferences.pinnedActions + id else dashboardPreferences.pinnedActions - id)) },
        onQuickAccessChange = { id, add ->
            val accessId = "action:$id"
            onDashboardPreferencesChange(dashboardPreferences.copy(
                quickAccessOrder = if (add && accessId !in dashboardPreferences.quickAccessOrder) dashboardPreferences.quickAccessOrder + accessId else if (!add) dashboardPreferences.quickAccessOrder - accessId else dashboardPreferences.quickAccessOrder,
                hiddenQuickAccess = dashboardPreferences.hiddenQuickAccess - accessId
            ))
        },
        onDismiss = { editingActions = false }
    )
    if (editingQuickAccess) CustomizeDashboardDialog(
        title = "Edit Quick Access",
        description = "Drag to reorder categories, folders and action shortcuts. Turn Show off to hide an option, or pin it to the front.",
        options = quickAccessOptions,
        order = dashboardPreferences.quickAccessOrder,
        hidden = dashboardPreferences.hiddenQuickAccess,
        pinned = dashboardPreferences.pinnedQuickAccess,
        onVisibilityChange = { id, visible ->
            val order = if (visible && id !in dashboardPreferences.quickAccessOrder) dashboardPreferences.quickAccessOrder + id else dashboardPreferences.quickAccessOrder
            onDashboardPreferencesChange(dashboardPreferences.copy(quickAccessOrder = order, hiddenQuickAccess = if (visible) dashboardPreferences.hiddenQuickAccess - id else dashboardPreferences.hiddenQuickAccess + id))
        },
        onMove = { id, direction -> onDashboardPreferencesChange(dashboardPreferences.copy(quickAccessOrder = moveDashboardItem(dashboardPreferences.quickAccessOrder, id, direction))) },
        onPinChange = { id, pin -> onDashboardPreferencesChange(dashboardPreferences.copy(pinnedQuickAccess = if (pin) dashboardPreferences.pinnedQuickAccess + id else dashboardPreferences.pinnedQuickAccess - id)) },
        onDismiss = { editingQuickAccess = false }
    )
}


@Composable
private fun NoteCard(
    note: Note, tab: Tab, onOpen: (() -> Unit)?, onCategory: (() -> Unit)?, onFolder: (() -> Unit)?,
    onShare: () -> Unit, onOpenAttachment: () -> Unit, onPin: () -> Unit = {}, onArchive: () -> Unit = {},
    onUnarchive: () -> Unit = {}, onTrash: () -> Unit = {}, onRestore: () -> Unit = {}, onDelete: () -> Unit = {}
) {
    var menuExpanded by remember(note.id) { mutableStateOf(false) }
    val date = remember(note.updatedAt) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt)) }
    Card(Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFEAE8F2)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
            val noteTint = noteLabelColor(note.labelColor)
            Box(Modifier.size(54.dp).background(noteTint.copy(alpha = 0.12f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(if (note.attachmentUri != null) Icons.Default.InsertDriveFile else Icons.Default.Description, null, tint = noteTint, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(note.body.ifBlank { if (note.attachmentUri != null) "Attached document" else "No additional text" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(if (onCategory != null) Modifier.clickable(onClick = onCategory) else Modifier, color = noteTint.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
                        Text(note.category, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = noteTint)
                    }
                    if (note.folder != null && onFolder != null) Surface(Modifier.clickable(onClick = onFolder), color = Color(0xFFF5F4FA), shape = RoundedCornerShape(50)) {
                        Text(note.folder, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (note.attachmentUri != null) Icon(Icons.Default.AttachFile, "Has attachment", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    if (note.priority != "Medium") Text(note.priority, style = MaterialTheme.typography.labelSmall, color = if (note.priority == "High") Color(0xFFB34B28) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                    Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (note.reminderAt != null) {
                        Icon(Icons.Default.NotificationsActive, "Reminder saved", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(note.reminderAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "More note actions") }
                DropdownMenu(menuExpanded, { menuExpanded = false }) {
                    if (onOpen != null) DropdownMenuItem(text = { Text("Open / edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuExpanded = false; onOpen() })
                    if (note.attachmentUri != null) DropdownMenuItem(text = { Text("Open attachment") }, leadingIcon = { Icon(Icons.Default.AttachFile, null) }, onClick = { menuExpanded = false; onOpenAttachment() })
                    DropdownMenuItem(text = { Text("Share note") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menuExpanded = false; onShare() })
                    when (tab) {
                        Tab.NOTES -> {
                            DropdownMenuItem(text = { Text(if (note.isPinned) "Unpin note" else "Pin note") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menuExpanded = false; onPin() })
                            DropdownMenuItem(text = { Text("Archive note") }, leadingIcon = { Icon(Icons.Default.Archive, null) }, onClick = { menuExpanded = false; onArchive() })
                            DropdownMenuItem(text = { Text("Move to trash") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { menuExpanded = false; onTrash() })
                        }
                        Tab.ARCHIVE -> {
                            DropdownMenuItem(text = { Text("Restore from archive") }, leadingIcon = { Icon(Icons.Default.Unarchive, null) }, onClick = { menuExpanded = false; onUnarchive() })
                            DropdownMenuItem(text = { Text("Move to trash") }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { menuExpanded = false; onTrash() })
                        }
                        Tab.TRASH -> {
                            DropdownMenuItem(text = { Text("Restore note") }, leadingIcon = { Icon(Icons.Default.Restore, null) }, onClick = { menuExpanded = false; onRestore() })
                            DropdownMenuItem(text = { Text("Delete permanently") }, leadingIcon = { Icon(Icons.Default.DeleteForever, null) }, onClick = { menuExpanded = false; onDelete() })
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardFooter() {
    val footerAccent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val wave = Path().apply {
                moveTo(0f, size.height)
                cubicTo(size.width * 0.22f, size.height * 0.15f, size.width * 0.34f, size.height * 0.2f, size.width * 0.52f, size.height * 0.72f)
                cubicTo(size.width * 0.7f, size.height * 1.2f, size.width * 0.84f, size.height * 0.1f, size.width, size.height * 0.55f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(wave, footerAccent.copy(alpha = 0.08f))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 520.dp
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (compact) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FooterBenefit(Icons.Default.Star, "Save Time", "Get to what you need faster", Modifier.weight(1f))
                            FooterBenefit(Icons.Default.Folder, "Stay Organized", "Keep notes neat and sorted", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FooterBenefit(Icons.Default.Bolt, "Boost Productivity", "Tools at your fingertips", Modifier.weight(1f))
                            FooterBenefit(Icons.Default.Favorite, "Simple & Easy", "A smooth experience", Modifier.weight(1f))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FooterBenefit(Icons.Default.Star, "Save Time", "Get to what you need faster", Modifier.weight(1f))
                            FooterBenefit(Icons.Default.Folder, "Stay Organized", "Keep notes neat and sorted", Modifier.weight(1f))
                            FooterBenefit(Icons.Default.Bolt, "Boost Productivity", "Tools at your fingertips", Modifier.weight(1f))
                            FooterBenefit(Icons.Default.Favorite, "Simple & Easy", "A smooth experience", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        Text("More Control  •  More Focus  •  More You", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text("Better Notes  ✦  Brighter Ideas", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.width(62.dp).height(2.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(50)))
    }
}

@Composable
private fun FooterBenefit(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MoreScreen(modifier: Modifier, onCreateNote: () -> Unit, onCreateFolder: () -> Unit, onImportDocument: () -> Unit, onOpenSettings: () -> Unit, onUnavailable: (String) -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("More from ENotes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Shortcuts to your local notes and device features.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoreAction(Icons.Default.NoteAdd, "Create a note", "Write a new idea", onCreateNote)
        MoreAction(Icons.Default.CreateNewFolder, "Create a folder", "Organize your notes", onCreateFolder)
        MoreAction(Icons.Default.DocumentScanner, "Import a document", "Attach a PDF or image to a note", onImportDocument)
        MoreAction(Icons.Default.Settings, "Settings and backup", "Appearance, export, and restore", onOpenSettings)
        MoreAction(Icons.Default.Mic, "Voice note", "Voice recording is not available", { onUnavailable("Voice recording is not available in this build. No audio was recorded.") })
        MoreAction(Icons.Default.AutoAwesome, "AI assistant", "Requires a secure provider", { onUnavailable("AI Assistant needs a configured secure provider. No AI request was sent.") })
    }
}

@Composable
private fun MoreAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFEAE8F2)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun CollectionScreen(
    modifier: Modifier, tab: Tab, notes: List<Note>, query: String, onQueryChange: (String) -> Unit,
    sortByTitle: Boolean, onToggleSort: () -> Unit, onOpenNote: (Note) -> Unit, onShareNote: (Note) -> Unit,
    onOpenAttachment: (Note) -> Unit, onArchive: (Note) -> Unit, onUnarchive: (Note) -> Unit,
    onTrash: (Note) -> Unit, onRestore: (Note) -> Unit, onDelete: (Note) -> Unit
) {
    val title = if (tab == Tab.ARCHIVE) "Archived notes" else "Trash"
    var sortMenu by remember { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "collection-search") {
            OutlinedTextField(query, onQueryChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search notes") }, singleLine = true, shape = RoundedCornerShape(22.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        item(key = "collection-title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${notes.size} ${if (tab == Tab.ARCHIVE) "archived" else "in trash"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { sortMenu = true }, enabled = tab != Tab.TRASH) { Icon(Icons.Default.Sort, "Sort notes") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        DropdownMenuItem(text = { Text(if (sortByTitle) "Recently updated" else "Title A–Z") }, onClick = { sortMenu = false; onToggleSort() })
                    }
                }
            }
        }
        if (notes.isEmpty()) item(key = "collection-empty") {
            Text(if (tab == Tab.ARCHIVE) "Nothing archived yet." else "Trash is empty.", style = MaterialTheme.typography.titleMedium)
        } else items(notes, key = { it.id }) { note ->
            NoteCard(
                note, tab, if (tab == Tab.TRASH) null else ({ onOpenNote(note) }), null, null,
                { onShareNote(note) }, { onOpenAttachment(note) }, onArchive = { onArchive(note) },
                onUnarchive = { onUnarchive(note) }, onTrash = { onTrash(note) }, onRestore = { onRestore(note) }, onDelete = { onDelete(note) }
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
