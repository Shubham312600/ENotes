package com.enotes.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import com.enotes.app.data.Note
import com.enotes.app.data.NoteFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

internal data class EditorSaveData(
    val title: String,
    val body: String,
    val category: String,
    val folder: String?,
    val attachmentUri: String?,
    val priority: String,
    val reminderAt: Long?,
    val labelColor: String,
    val pinned: Boolean,
    val richContentJson: String
)

private val editorBg = Color(0xFF03050C)
private val editorPanel = Color(0xFF090E19)
private val editorLine = Color(0xFF27283A)
private val editorPurple = Color(0xFFD65BFF)
private val editorBlue = Color(0xFF48A5FF)
private val editorGold = Color(0xFFFFD45B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorScreen(
    modifier: Modifier = Modifier,
    note: Note?,
    folders: List<NoteFolder>,
    pickedBlock: RichContentBlock?,
    onConsumePickedBlock: () -> Unit,
    onPickMedia: (String) -> Unit,
    onOpenAttachment: (String) -> Unit,
    onSave: (EditorSaveData) -> Unit,
    onUnavailable: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val bodyFocus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val undoStack = remember(note?.id) { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember(note?.id) { mutableStateListOf<TextFieldValue>() }
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note?.id) { mutableStateOf(TextFieldValue(note?.body.orEmpty())) }
    var category by remember(note?.id) { mutableStateOf(note?.category ?: "Others") }
    var folder by remember(note?.id) { mutableStateOf(note?.folder) }
    var priority by remember(note?.id) { mutableStateOf(note?.priority ?: "Medium") }
    var reminderAt by remember(note?.id) { mutableStateOf(note?.reminderAt) }
    var labelColor by remember(note?.id) { mutableStateOf(note?.labelColor ?: "purple") }
    var pinned by remember(note?.id) { mutableStateOf(note?.isPinned ?: false) }
    var blocks by remember(note?.id) {
        val saved = decodeRichContent(note?.richContentJson)
        val content = saved.toMutableList()
        val legacyUri = note?.attachmentUri
        if (!legacyUri.isNullOrBlank() && content.none { it.uri == legacyUri }) {
            content.add(0, legacyAttachmentBlock(context, legacyUri))
        }
        mutableStateOf<List<RichContentBlock>>(content)
    }
    var categoryMenu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var discardConfirmation by remember { mutableStateOf(false) }
    var reminderMessage by remember { mutableStateOf(false) }
    var skinTarget by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(pickedBlock?.id) {
        if (pickedBlock != null && blocks.none { it.id == pickedBlock.id }) {
            blocks = blocks + pickedBlock
            onConsumePickedBlock()
        }
    }

    fun updateBlock(id: String, transform: (RichContentBlock) -> RichContentBlock) {
        blocks = blocks.map { if (it.id == id) transform(it) else it }
    }

    fun changeBody(result: TextEditResult) {
        undoStack.add(body)
        if (undoStack.size > 60) undoStack.removeAt(0)
        redoStack.clear()
        body = TextFieldValue(result.text, TextRange(result.selectionStart, result.selectionEnd))
    }

    fun addText(text: String) {
        val start = body.selection.start.coerceIn(0, body.text.length)
        val end = body.selection.end.coerceIn(start, body.text.length)
        val updated = body.text.replaceRange(start, end, text)
        changeBody(TextEditResult(updated, start + text.length, start + text.length))
    }

    fun requestDismiss() {
        val savedBlocks = encodeRichContent(blocks)
        val originalBlocks = encodeRichContent(note?.let { savedNote ->
            val saved = decodeRichContent(savedNote.richContentJson)
            if (!savedNote.attachmentUri.isNullOrBlank() && saved.none { block -> block.uri == savedNote.attachmentUri }) {
                listOf(legacyAttachmentBlock(context, savedNote.attachmentUri)) + saved
            } else saved
        } ?: emptyList())
        val changed = title != note?.title.orEmpty() || body.text != note?.body.orEmpty() || category != (note?.category ?: "Others") || folder != note?.folder || priority != (note?.priority ?: "Medium") || reminderAt != note?.reminderAt || labelColor != (note?.labelColor ?: "purple") || pinned != (note?.isPinned ?: false) || savedBlocks != originalBlocks
        if (changed) discardConfirmation = true else onDismiss()
    }

    BackHandler(enabled = true, onBack = ::requestDismiss)

    MaterialTheme(colorScheme = darkColorScheme(
        primary = editorPurple, secondary = editorBlue, background = editorBg, surface = editorPanel,
        onPrimary = Color.White, onBackground = Color(0xFFF4F3FA), onSurface = Color(0xFFF4F3FA),
        onSurfaceVariant = Color(0xFF9EA6BA), outline = Color(0xFF55536B)
    )) {
        Column(modifier.fillMaxSize().imePadding().background(editorBg)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EditorTopButton(Icons.Default.ArrowBack, "Back", editorPurple, onClick = ::requestDismiss)
                Spacer(Modifier.weight(1f))
                EditorTopButton(Icons.Default.Undo, "Undo", editorPurple, onClick = {
                    if (undoStack.isNotEmpty()) {
                        redoStack.add(body)
                        body = undoStack.removeAt(undoStack.lastIndex)
                    }
                }, enabled = undoStack.isNotEmpty())
                EditorTopButton(Icons.Default.Redo, "Redo", editorBlue, onClick = {
                    if (redoStack.isNotEmpty()) {
                        undoStack.add(body)
                        body = redoStack.removeAt(redoStack.lastIndex)
                    }
                }, enabled = redoStack.isNotEmpty())
                EditorTopButton(Icons.Default.Done, "Save note", editorGold) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    val legacy = blocks.firstOrNull { it.uri != null }?.uri
                    onSave(EditorSaveData(title, body.text, category, folder, legacy, priority, reminderAt, labelColor, pinned, encodeRichContent(blocks)))
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= 150) title = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = editorPurple, fontSize = 32.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(editorPurple),
                        decorationBox = { inner -> Box { if (title.isBlank()) Text("Title", color = editorPurple, fontSize = 32.sp, fontWeight = FontWeight.Bold); inner() } }
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(58.dp).height(3.dp).background(editorPurple, RoundedCornerShape(10.dp)))
                }
                Row(
                    Modifier.fillMaxWidth().border(1.dp, editorLine, RoundedCornerShape(14.dp)).background(editorPanel, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = Color(0xFF8F94AC), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note?.updatedAt ?: System.currentTimeMillis())), color = Color(0xFFBBC2D4), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("• ${body.text.length} characters", color = editorBlue, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text("${body.text.trim().split(Regex("\\s+")).count(String::isNotBlank)} words", color = Color(0xFF8F94AC), style = MaterialTheme.typography.labelSmall)
                }

                Column(
                    Modifier.fillMaxWidth().heightIn(min = 300.dp).border(1.dp, editorLine, RoundedCornerShape(18.dp)).background(Color(0xFF050914), RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BasicTextField(
                        value = body,
                        onValueChange = { next ->
                            undoStack.add(body)
                            if (undoStack.size > 60) undoStack.removeAt(0)
                            redoStack.clear()
                            body = next
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).focusRequester(bodyFocus),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFE7EAF3), lineHeight = 25.sp),
                        cursorBrush = SolidColor(editorPurple),
                        decorationBox = { inner -> Box {
                            if (body.text.isBlank()) Text("Write something about your note…", color = Color(0xFF777F95), style = MaterialTheme.typography.bodyLarge)
                            inner()
                        } }
                    )
                    blocks.forEach { block ->
                        key(block.id) {
                            RichContentCard(
                                block = block,
                                onChange = { changed -> updateBlock(block.id) { changed } },
                                onOpen = { block.uri?.let(onOpenAttachment) },
                                onRemove = { blocks = blocks.filterNot { it.id == block.id } },
                                onSkin = { skinTarget = block.id },
                                onPaste = {
                                    val text = clipboard.getText()?.text.orEmpty()
                                    if (text.isNotBlank()) {
                                        val pasted = parsePastedTable(text)
                                        updateBlock(block.id) { it.copy(rows = pasted) }
                                    }
                                }
                            )
                        }
                    }
                    if (blocks.isEmpty() && body.text.isBlank()) {
                        Text("Use the toolbar below to add text, images, video, audio, or a sheet.", color = Color(0xFF646D84), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        EditorMetaButton(Icons.Default.Label, "Category", category, Modifier.fillMaxWidth()) { categoryMenu = true }
                        DropdownMenu(categoryMenu, { categoryMenu = false }) {
                            listOf("Personal", "Study", "Work", "Others").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { category = value; categoryMenu = false }) }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        EditorMetaButton(Icons.Default.Folder, "Folder", folder ?: "None", Modifier.fillMaxWidth()) { folderMenu = true }
                        DropdownMenu(folderMenu, { folderMenu = false }) {
                            DropdownMenuItem(text = { Text("None") }, onClick = { folder = null; folderMenu = false })
                            folders.forEach { value -> DropdownMenuItem(text = { Text(value.name) }, onClick = { folder = value.name; folderMenu = false }) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EditorMetaButton(Icons.Default.NotificationsActive, "Reminder", reminderAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: "Add date / time", Modifier.weight(1.2f)) {
                        val initial = Calendar.getInstance().apply { reminderAt?.let { timeInMillis = it } }
                        DatePickerDialog(context, { _, year, month, day ->
                            val selected = Calendar.getInstance().apply {
                                timeInMillis = reminderAt ?: System.currentTimeMillis()
                                set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                            }
                            TimePickerDialog(context, { _, hour, minute ->
                                selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute); selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0)
                                reminderAt = selected.timeInMillis
                                reminderMessage = true
                            }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), false).show()
                        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                    }
                    Box(Modifier.weight(0.8f)) {
                        EditorMetaButton(Icons.Default.Star, "Priority", priority, Modifier.fillMaxWidth()) { priorityMenu = true }
                        DropdownMenu(priorityMenu, { priorityMenu = false }) {
                            listOf("Low", "Medium", "High").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { priority = value; priorityMenu = false }) }
                        }
                    }
                }
                Surface(Modifier.fillMaxWidth(), color = editorPanel, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, editorLine)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, null, tint = editorPurple, modifier = Modifier.size(19.dp))
                        Text("Pin to top", Modifier.weight(1f).padding(start = 8.dp), color = Color(0xFFE4E6EE), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = pinned, onCheckedChange = { pinned = it })
                    }
                }
                Column(Modifier.fillMaxWidth().border(1.dp, editorLine, RoundedCornerShape(15.dp)).background(editorPanel, RoundedCornerShape(15.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { showDetails = !showDetails }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = editorPurple)
                        Text("Color / Label", Modifier.weight(1f).padding(start = 8.dp), color = Color(0xFFE4E6EE), fontWeight = FontWeight.SemiBold)
                        Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color(0xFF9EA6BA))
                    }
                    if (showDetails) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            noteLabelColorOptions().forEach { (key, color) ->
                                Box(Modifier.size(if (labelColor == key) 33.dp else 28.dp).background(color, CircleShape)
                                    .then(if (labelColor == key) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { labelColor = key }, contentAlignment = Alignment.Center) {
                                    if (labelColor == key) Icon(Icons.Default.Check, "Selected label color", tint = Color.White, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onUnavailable("AI improvements need a configured secure provider. Your note was not sent or changed.") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(5.dp)); Text("Improve with AI", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            OutlinedButton(onClick = { onUnavailable("AI summaries need a configured secure provider. Your note was not sent or changed.") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Summarize, null); Spacer(Modifier.width(5.dp)); Text("Summarize", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(
                Modifier.fillMaxWidth().border(BorderStroke(1.dp, editorLine), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color(0xFF050914), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(horizontal = 3.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorToolbarItem(Icons.Default.TextFields, "Text", editorPurple) {
                    bodyFocus.requestFocus(); keyboard?.show()
                }
                EditorToolbarItem(Icons.Default.CheckBox, "Checklist", editorBlue) { addText(if (body.text.isBlank()) "☐ " else "\n☐ ") }
                EditorToolbarItem(Icons.Default.Alarm, "Reminder", editorPurple) {
                    val initial = Calendar.getInstance().apply { reminderAt?.let { timeInMillis = it } }
                    DatePickerDialog(context, { _, year, month, day ->
                        val selected = Calendar.getInstance().apply { timeInMillis = reminderAt ?: System.currentTimeMillis(); set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day) }
                        TimePickerDialog(context, { _, hour, minute -> selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute); reminderAt = selected.timeInMillis; reminderMessage = true }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), false).show()
                    }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                }
                EditorToolbarItem(Icons.Default.Image, "Image", editorBlue) { onPickMedia("image") }
                EditorToolbarItem(Icons.Default.GraphicEq, "Audio", editorPurple) { onPickMedia("audio") }
                Box {
                    EditorToolbarItem(Icons.Default.MoreVert, "More", editorBlue) { moreMenu = true }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(text = { Text("Insert sheet") }, leadingIcon = { Icon(Icons.Default.TableChart, null, tint = editorPurple) }, onClick = {
                            val rows = List(4) { List(3) { "" } }
                            blocks = blocks + RichContentBlock(kind = "sheet", title = "Sheet", rows = rows)
                            moreMenu = false
                        })
                        DropdownMenuItem(text = { Text("Add video") }, leadingIcon = { Icon(Icons.Default.Videocam, null, tint = editorBlue) }, onClick = { moreMenu = false; onPickMedia("video") })
                        DropdownMenuItem(text = { Text("Skins") }, leadingIcon = { Icon(Icons.Default.Palette, null, tint = editorPurple) }, onClick = {
                            moreMenu = false
                            skinTarget = blocks.lastOrNull { it.kind == "sheet" }?.id
                            if (skinTarget == null) onUnavailable("Insert a sheet first to choose a sheet skin.")
                        })
                    }
                }
            }
        }

        if (skinTarget != null) {
            val target = skinTarget
            AlertDialog(
                onDismissRequest = { skinTarget = null },
                containerColor = Color(0xFF101625),
                title = { Text("Choose sheet skin", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("plain" to "Midnight", "floral" to "Blossom", "leaves" to "Sage", "warm" to "Coral", "sea" to "Ocean").forEach { (key, name) ->
                            Row(Modifier.fillMaxWidth().clickable {
                                target?.let { id -> updateBlock(id) { it.copy(skin = key) } }
                                skinTarget = null
                            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(24.dp).background(skinColor(key), RoundedCornerShape(6.dp)).border(1.dp, editorPurple.copy(alpha = .7f), RoundedCornerShape(6.dp)))
                                Text(name, Modifier.padding(start = 10.dp), color = Color(0xFFEAECF3))
                                Spacer(Modifier.weight(1f))
                                if (target?.let { id -> blocks.firstOrNull { it.id == id }?.skin == key } == true) Icon(Icons.Default.Check, null, tint = editorPurple)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { skinTarget = null }) { Text("Done") } }
            )
        }
        if (discardConfirmation) AlertDialog(
            onDismissRequest = { discardConfirmation = false },
            containerColor = Color(0xFF101625),
            title = { Text("Discard unsaved changes?", color = Color.White) },
            text = { Text("Your changes have not been saved.", color = Color(0xFFCBD1E0)) },
            confirmButton = { TextButton(onClick = { discardConfirmation = false; onDismiss() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("Keep editing") } }
        )
        if (reminderMessage) AlertDialog(
            onDismissRequest = { reminderMessage = false },
            containerColor = Color(0xFF101625),
            title = { Text("Reminder saved", color = Color.White) },
            text = { Text("The reminder time is stored with the note. Notification delivery is not enabled in this build.", color = Color(0xFFCBD1E0)) },
            confirmButton = { TextButton(onClick = { reminderMessage = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun EditorTopButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp).border(1.dp, tint.copy(alpha = if (enabled) .48f else .18f), RoundedCornerShape(12.dp))
    ) { Icon(icon, label, tint = tint.copy(alpha = if (enabled) 1f else .3f)) }
}

@Composable
private fun EditorToolbarItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(Modifier.width(55.dp).clickable(onClick = onClick).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(25.dp))
        Text(label, color = Color(0xFFE3E5ED), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun EditorMetaButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), color = editorPanel, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, editorLine)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = editorPurple, modifier = Modifier.size(19.dp))
            Column(Modifier.weight(1f).padding(start = 7.dp)) {
                Text(label, color = Color(0xFF9EA6BA), style = MaterialTheme.typography.labelSmall)
                Text(value, color = Color(0xFFE5E7EF), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF9EA6BA), modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun RichContentCard(
    block: RichContentBlock,
    onChange: (RichContentBlock) -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onSkin: () -> Unit,
    onPaste: () -> Unit
) {
    val bg = skinColor(block.skin)
    Column(Modifier.fillMaxWidth().border(1.dp, editorLine, RoundedCornerShape(16.dp)).background(bg, RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(when (block.kind) {
                "image" -> Icons.Default.Image
                "video" -> Icons.Default.Videocam
                "audio" -> Icons.Default.GraphicEq
                "sheet" -> Icons.Default.TableChart
                else -> Icons.Default.InsertDriveFile
            }, null, tint = if (block.kind == "video") editorBlue else editorPurple)
            Text(when (block.kind) { "image" -> "Image"; "video" -> "Video"; "audio" -> "Audio"; "sheet" -> "Sheet"; else -> "Attachment" }, Modifier.weight(1f).padding(start = 8.dp), color = Color(0xFFE7EAF3), fontWeight = FontWeight.SemiBold)
            if (block.kind == "sheet") TextButton(onClick = onSkin, contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Default.Palette, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Skins") }
            if (block.uri != null) IconButton(onClick = onOpen, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.OpenInNew, "Open media", tint = editorBlue, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Remove content", tint = Color(0xFFAAB1C1), modifier = Modifier.size(18.dp)) }
        }

        if (block.kind == "image" && block.uri != null) EmbeddedImage(block.uri)
        if (block.kind == "video" && block.uri != null) EmbeddedVideo(block.uri)
        if (block.kind == "audio" && block.uri != null) EmbeddedAudio(block.uri, block.title)

        OutlinedTextField(
            value = block.title,
            onValueChange = { onChange(block.copy(title = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (block.kind == "sheet") "Sheet title" else "Title") },
            colors = editorFieldColors()
        )
        OutlinedTextField(
            value = block.description,
            onValueChange = { onChange(block.copy(description = it)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
            maxLines = 3,
            label = { Text("Description") },
            colors = editorFieldColors()
        )

        if (block.kind == "sheet") {
            SheetGrid(block = block, onChange = onChange, onPaste = onPaste)
        } else if (block.uri != null) {
            Text("${block.mimeType.ifBlank { block.kind }} · linked from this device", color = Color(0xFF9EA6BA), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SheetGrid(block: RichContentBlock, onChange: (RichContentBlock) -> Unit, onPaste: () -> Unit) {
    var selectedColumn by remember(block.id) { mutableIntStateOf(0) }
    val columns = maxOf(1, block.rows.maxOfOrNull { it.size } ?: 1)
    val rows = if (block.rows.isEmpty()) List(4) { List(3) { "" } } else block.rows.map { row -> row + List((columns - row.size).coerceAtLeast(0)) { "" } }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onPaste, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Paste") }
            OutlinedButton(onClick = { onChange(block.copy(rows = rows + listOf(List(columns) { "" }))) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Text("Row") }
            OutlinedButton(onClick = {
                onChange(block.copy(rows = rows.map { it + "" }))
                selectedColumn = columns
            }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.ViewColumn, null, modifier = Modifier.size(16.dp)); Text("Add column") }
            OutlinedButton(onClick = {
                if (columns > 1) {
                    val remove = selectedColumn.coerceIn(0, columns - 1)
                    onChange(block.copy(rows = rows.map { row -> row.filterIndexed { index, _ -> index != remove } }))
                    selectedColumn = (remove - 1).coerceAtLeast(0)
                }
            }, enabled = columns > 1, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp)); Text("Delete column") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(columns) { index ->
                FilterChip(selected = selectedColumn == index, onClick = { selectedColumn = index }, label = { Text("Column ${index + 1}") })
            }
        }
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    row.forEachIndexed { columnIndex, value ->
                        BasicTextField(
                            value = value,
                            onValueChange = { updated ->
                                val changed = rows.mapIndexed { ri, cells ->
                                    cells.mapIndexed { ci, cell -> if (ri == rowIndex && ci == columnIndex) updated else cell }
                                }
                                onChange(block.copy(rows = changed))
                            },
                            modifier = Modifier.width(112.dp).heightIn(min = 40.dp).background(if (selectedColumn == columnIndex) Color(0xFF21172C) else Color(0xFF0D1420)).border(1.dp, if (selectedColumn == columnIndex) editorPurple.copy(alpha = .5f) else Color(0xFF343B4C)).padding(horizontal = 7.dp, vertical = 8.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE8EAF2)),
                            cursorBrush = SolidColor(editorPurple),
                            decorationBox = { inner -> Box { if (value.isBlank()) Text(if (rowIndex == 0) "Header" else "Cell", color = Color(0xFF687188), style = MaterialTheme.typography.labelSmall); inner() } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddedImage(uriText: String) {
    val context = LocalContext.current
    val image by produceImage(uriText, context)
    if (image != null) {
        Image(image!!, contentDescription = "Embedded image", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).background(Color(0xFF02040A), RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
    } else {
        Box(Modifier.fillMaxWidth().height(96.dp).background(Color(0xFF050914), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, "Image preview", tint = editorPurple, modifier = Modifier.size(36.dp)) }
    }
}

@Composable
private fun produceImage(uriText: String, context: Context): State<ImageBitmap?> {
    return produceState<ImageBitmap?>(initialValue = null, key1 = uriText) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriText)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
                val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
                bitmap?.asImageBitmap()
            }.getOrNull()
        }
    }
}

@Composable
private fun EmbeddedVideo(uriText: String) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(205.dp).background(Color.Black, RoundedCornerShape(12.dp)),
        factory = { ctx ->
            VideoView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setVideoURI(Uri.parse(uriText))
                val controller = MediaController(ctx)
                controller.setAnchorView(this)
                setMediaController(controller)
            }
        }
    )
}

@Composable
private fun EmbeddedAudio(uriText: String, title: String) {
    val context = LocalContext.current
    var player by remember(uriText) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(uriText) { mutableStateOf(false) }
    var loading by remember(uriText) { mutableStateOf(false) }
    var failed by remember(uriText) { mutableStateOf(false) }
    DisposableEffect(uriText) { onDispose { runCatching { player?.release() }; player = null } }
    Row(Modifier.fillMaxWidth().background(Color(0xFF081426), RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) {
                player?.pause(); playing = false
            } else if (player != null) {
                runCatching { player?.start(); playing = true }.onFailure { failed = true }
            } else {
                loading = true; failed = false
                runCatching {
                    MediaPlayer().also { media ->
                        player = media
                        media.setDataSource(context, Uri.parse(uriText))
                        media.setOnPreparedListener { prepared -> loading = false; playing = true; prepared.start() }
                        media.setOnCompletionListener { playing = false }
                        media.setOnErrorListener { _, _, _ -> loading = false; failed = true; playing = false; true }
                        media.prepareAsync()
                    }
                }.onFailure { loading = false; failed = true }
            }
        }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause audio" else "Play audio", tint = editorBlue, modifier = Modifier.size(30.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { "Audio attachment" }, color = Color(0xFFE8EBF4), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(when { loading -> "Loading audio…"; failed -> "This audio could not be played here."; playing -> "Playing"; else -> "Tap play to listen" }, color = Color(0xFF9EA6BA), style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.Default.GraphicEq, null, tint = editorPurple)
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFFE7EAF3), unfocusedTextColor = Color(0xFFE7EAF3),
    focusedBorderColor = editorPurple.copy(alpha = .75f), unfocusedBorderColor = editorLine,
    focusedLabelColor = editorPurple, unfocusedLabelColor = Color(0xFF929AAF), cursorColor = editorPurple,
    focusedContainerColor = Color(0xFF080D17), unfocusedContainerColor = Color(0xFF080D17)
)

private fun noteLabelColorOptions(): List<Pair<String, Color>> = listOf(
    "purple" to Color(0xFF7650E8), "blue" to Color(0xFF448EEA), "green" to Color(0xFF40A878),
    "yellow" to Color(0xFFE7B52D), "orange" to Color(0xFFEA8D3C), "pink" to Color(0xFFE56C9B), "gray" to Color(0xFF81899A)
)

private fun skinColor(skin: String): Color = when (skin) {
    "floral" -> Color(0xFF241626)
    "leaves" -> Color(0xFF12211D)
    "warm" -> Color(0xFF251A18)
    "sea" -> Color(0xFF101C2C)
    else -> Color(0xFF080D19)
}

private fun queryDisplayName(context: Context, uriText: String): String {
    return runCatching {
        context.contentResolver.query(Uri.parse(uriText), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty().ifBlank { "Attached file" }
}

private fun legacyAttachmentBlock(context: Context, uriText: String): RichContentBlock {
    val mime = runCatching { context.contentResolver.getType(Uri.parse(uriText)).orEmpty() }.getOrDefault("")
    val kind = when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "video"
        mime.startsWith("audio/") -> "audio"
        else -> "file"
    }
    return RichContentBlock(id = "legacy-$uriText", kind = kind, uri = uriText, mimeType = mime, title = queryDisplayName(context, uriText))
}

private fun parsePastedTable(text: String): List<List<String>> {
    val rows = text.trimEnd().split(Regex("\\r?\\n")).map { it.split('\t') }
    val width = rows.maxOfOrNull { it.size }?.coerceAtMost(12) ?: 1
    return rows.take(100).map { row -> row.take(12) + List((width - row.size).coerceAtLeast(0)) { "" } }
}

