package com.enotes.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import com.enotes.app.data.Note
import com.enotes.app.data.NoteFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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

private data class EditorPalette(
    val background: Color,
    val panel: Color,
    val line: Color,
    val purple: Color,
    val blue: Color,
    val gold: Color,
    val text: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val placeholder: Color,
    val canvas: Color,
    val field: Color,
    val dialog: Color,
    val mediaCanvas: Color,
    val audioSurface: Color,
    val cell: Color,
    val selectedCell: Color,
    val cellLine: Color
) {
    companion object {
        val Dark = EditorPalette(
            background = Color(0xFF03050C), panel = Color(0xFF090E19), line = Color(0xFF27283A),
            purple = Color(0xFFD65BFF), blue = Color(0xFF48A5FF), gold = Color(0xFFFFD45B),
            text = Color(0xFFF4F3FA), secondaryText = Color(0xFFCBD1E0), mutedText = Color(0xFF9EA6BA),
            placeholder = Color(0xFF777F95), canvas = Color(0xFF050914), field = Color(0xFF080D17),
            dialog = Color(0xFF101625), mediaCanvas = Color(0xFF02040A), audioSurface = Color(0xFF081426),
            cell = Color(0xFF0D1420), selectedCell = Color(0xFF21172C), cellLine = Color(0xFF343B4C)
        )
        val Light = EditorPalette(
            background = Color(0xFFFBF9FF), panel = Color.White, line = Color(0xFFE4E1EE),
            purple = Color(0xFF5935E8), blue = Color(0xFF2876E8), gold = Color(0xFF9A6900),
            text = Color(0xFF202033), secondaryText = Color(0xFF4D4B5B), mutedText = Color(0xFF777486),
            placeholder = Color(0xFF858294), canvas = Color.White, field = Color(0xFFFCFBFF),
            dialog = Color.White, mediaCanvas = Color(0xFFF3F2F8), audioSurface = Color(0xFFF3F5FB),
            cell = Color.White, selectedCell = Color(0xFFF0EAFE), cellLine = Color(0xFFE4E1EE)
        )
    }
}

private val LocalEditorPalette = staticCompositionLocalOf { EditorPalette.Dark }
private val editorBg: Color @Composable get() = LocalEditorPalette.current.background
private val editorPanel: Color @Composable get() = LocalEditorPalette.current.panel
private val editorLine: Color @Composable get() = LocalEditorPalette.current.line
private val editorPurple: Color @Composable get() = LocalEditorPalette.current.purple
private val editorBlue: Color @Composable get() = LocalEditorPalette.current.blue
private val editorGold: Color @Composable get() = LocalEditorPalette.current.gold
private val editorText: Color @Composable get() = LocalEditorPalette.current.text
private val editorSecondary: Color @Composable get() = LocalEditorPalette.current.secondaryText
private val editorMuted: Color @Composable get() = LocalEditorPalette.current.mutedText
private val editorPlaceholder: Color @Composable get() = LocalEditorPalette.current.placeholder
private val editorCanvas: Color @Composable get() = LocalEditorPalette.current.canvas
private val editorField: Color @Composable get() = LocalEditorPalette.current.field
private val editorDialog: Color @Composable get() = LocalEditorPalette.current.dialog
private val editorMediaCanvas: Color @Composable get() = LocalEditorPalette.current.mediaCanvas
private val editorAudioSurface: Color @Composable get() = LocalEditorPalette.current.audioSurface
private val editorCell: Color @Composable get() = LocalEditorPalette.current.cell
private val editorSelectedCell: Color @Composable get() = LocalEditorPalette.current.selectedCell
private val editorCellLine: Color @Composable get() = LocalEditorPalette.current.cellLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorScreen(
    modifier: Modifier = Modifier,
    note: Note?,
    darkTheme: Boolean,
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
    val initialBlocks = remember(note?.id) {
        val saved = decodeRichContent(note?.richContentJson)
        val content = saved.toMutableList()
        val legacyUri = note?.attachmentUri
        if (!legacyUri.isNullOrBlank() && content.none { it.uri == legacyUri }) {
            content.add(0, legacyAttachmentBlock(context, legacyUri))
        }
        content
    }
    val savedBodyFormat = remember(note?.id) { initialBlocks.firstOrNull { it.kind == "body-formatting" } }
    var bodyStyles by remember(note?.id) { mutableStateOf(savedBodyFormat?.textStyles.orEmpty()) }
    var bodyAlignment by remember(note?.id) { mutableStateOf(savedBodyFormat?.textAlignment ?: "start") }
    var bodyLineSpacing by remember(note?.id) { mutableFloatStateOf(savedBodyFormat?.textLineSpacing ?: 1.25f) }
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note?.id) {
        val text = note?.body.orEmpty()
        mutableStateOf(TextFieldValue(styledNoteBody(text, bodyStyles), TextRange(text.length)))
    }
    var category by remember(note?.id) { mutableStateOf(note?.category ?: "Others") }
    var folder by remember(note?.id) { mutableStateOf(note?.folder) }
    var priority by remember(note?.id) { mutableStateOf(note?.priority ?: "Medium") }
    var reminderAt by remember(note?.id) { mutableStateOf(note?.reminderAt) }
    var labelColor by remember(note?.id) { mutableStateOf(note?.labelColor ?: "purple") }
    var pinned by remember(note?.id) { mutableStateOf(note?.isPinned ?: false) }
    var blocks by remember(note?.id) { mutableStateOf<List<RichContentBlock>>(initialBlocks) }
    var categoryMenu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var discardConfirmation by remember { mutableStateOf(false) }
    var reminderMessage by remember { mutableStateOf(false) }
    var skinTarget by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var textToolsOpen by remember(note?.id) { mutableStateOf(false) }
    var formattingSelection by remember(note?.id) { mutableStateOf(TextRange.Zero) }
    var restoreBodyFocus by remember(note?.id) { mutableStateOf(false) }

    LaunchedEffect(textToolsOpen, restoreBodyFocus) {
        if (!textToolsOpen && restoreBodyFocus) {
            bodyFocus.requestFocus()
            keyboard?.show()
            restoreBodyFocus = false
        }
    }

    LaunchedEffect(pickedBlock?.id) {
        if (pickedBlock != null && blocks.none { it.id == pickedBlock.id }) {
            blocks = blocks + pickedBlock
            onConsumePickedBlock()
        }
    }

    fun updateBlock(id: String, transform: (RichContentBlock) -> RichContentBlock) {
        blocks = blocks.map { if (it.id == id) transform(it) else it }
    }

    fun syncBodyFormatBlock() {
        val without = blocks.filterNot { it.kind == "body-formatting" }
        blocks = if (bodyStyles.isEmpty() && bodyAlignment == "start" && bodyLineSpacing == 1.25f) {
            without
        } else {
            without + RichContentBlock(
                id = savedBodyFormat?.id ?: "body-formatting-${note?.id ?: "new"}",
                kind = "body-formatting",
                textStyles = bodyStyles,
                textAlignment = bodyAlignment,
                textLineSpacing = bodyLineSpacing
            )
        }
    }

    fun updateBody(
        next: TextFieldValue,
        recordUndo: Boolean = true,
        styleRanges: List<NoteTextStyleRange>? = null
    ) {
        val contentChanged = next.text != body.text
        if (recordUndo && contentChanged) {
            undoStack.add(body)
            if (undoStack.size > 60) undoStack.removeAt(0)
            redoStack.clear()
        }
        if (next.text != body.text) formattingSelection = TextRange.Zero
        if (next.selection.min < next.selection.max) formattingSelection = next.selection
        bodyStyles = styleRanges ?: remapNoteTextStylesForTextEdit(body.text, next.text, bodyStyles)
        // Keep the IME's selection and composing range from BasicTextField's latest value.
        // Clearing composition during deletion interrupts the active input connection and
        // can make the IME restore composing text after a formatted edit.
        body = restyleNoteBodyValue(next, bodyStyles)
        syncBodyFormatBlock()
    }

    fun applyBodyStyle(transform: (NoteTextStyleRange) -> NoteTextStyleRange) {
        val selection = formattingSelection.takeIf { it.min < it.max } ?: body.selection
        if (selection.min >= selection.max) return
        undoStack.add(body)
        if (undoStack.size > 60) undoStack.removeAt(0)
        redoStack.clear()
        bodyStyles = applyNoteTextStyle(body.text.length, bodyStyles, selection.min, selection.max, transform)
        body = TextFieldValue(styledNoteBody(body.text, bodyStyles), selection)
        formattingSelection = selection
        syncBodyFormatBlock()
    }

    fun applyListPrefix(numbered: Boolean) {
        val selection = formattingSelection.takeIf { it.min < it.max } ?: body.selection
        val edit = insertChecklistPrefix(body.text, selection.min, selection.max, numbered, bodyStyles)
        updateBody(TextFieldValue(edit.text, TextRange(edit.selection)), styleRanges = edit.styleRanges)
    }

    fun changeBody(result: TextEditResult) {
        updateBody(TextFieldValue(result.text, TextRange(result.selectionStart, result.selectionEnd)))
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

    val palette = if (darkTheme) EditorPalette.Dark else EditorPalette.Light
    CompositionLocalProvider(LocalEditorPalette provides palette) {
      MaterialTheme(colorScheme = if (darkTheme) darkColorScheme(
        primary = palette.purple, secondary = palette.blue, background = palette.background, surface = palette.panel,
        onPrimary = Color.White, onBackground = palette.text, onSurface = palette.text,
        onSurfaceVariant = palette.secondaryText, outline = palette.line
      ) else lightColorScheme(
        primary = palette.purple, secondary = palette.blue, background = palette.background, surface = palette.panel,
        onPrimary = Color.White, onBackground = palette.text, onSurface = palette.text,
        onSurfaceVariant = palette.secondaryText, outline = palette.line
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
                        val restored = undoStack.removeAt(undoStack.lastIndex)
                        updateBody(restored, recordUndo = false, styleRanges = noteTextStylesFromAnnotatedString(restored.annotatedString))
                    }
                }, enabled = undoStack.isNotEmpty())
                EditorTopButton(Icons.Default.Redo, "Redo", editorBlue, onClick = {
                    if (redoStack.isNotEmpty()) {
                        undoStack.add(body)
                        val restored = redoStack.removeAt(redoStack.lastIndex)
                        updateBody(restored, recordUndo = false, styleRanges = noteTextStylesFromAnnotatedString(restored.annotatedString))
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
                    Icon(Icons.Default.CalendarMonth, null, tint = editorMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note?.updatedAt ?: System.currentTimeMillis())), color = editorSecondary, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Text("• ${body.text.length} characters", color = editorBlue, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text("${body.text.trim().split(Regex("\\s+")).count(String::isNotBlank)} words", color = editorMuted, style = MaterialTheme.typography.labelSmall)
                }

                Column(
                    Modifier.fillMaxWidth().heightIn(min = if (blocks.isEmpty()) 300.dp else 112.dp).border(1.dp, editorLine, RoundedCornerShape(18.dp)).background(editorCanvas, RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BasicTextField(
                        value = body,
                        onValueChange = { next ->
                            val continued = continueNumberedListOnNewline(body.text, next.text, next.selection.start, bodyStyles)
                            if (continued == null) updateBody(next)
                            else updateBody(TextFieldValue(continued.text, TextRange(continued.selection)), styleRanges = continued.styleRanges)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (blocks.isEmpty()) 180.dp else 56.dp).focusRequester(bodyFocus),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = editorText,
                            lineHeight = (20f * bodyLineSpacing).sp,
                            textAlign = when (bodyAlignment) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start }
                        ),
                        cursorBrush = SolidColor(editorPurple),
                        decorationBox = { inner -> Box {
                            if (body.text.isBlank()) Text("Write something about your note…", color = editorPlaceholder, style = MaterialTheme.typography.bodyLarge)
                            inner()
                        } }
                    )
                    blocks.filterNot { it.kind == "body-formatting" }.forEach { block ->
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
                        Text("Use the toolbar below to add text, images, video, audio, or a sheet.", color = editorMuted, style = MaterialTheme.typography.bodySmall)
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
                        Text("Pin to top", Modifier.weight(1f).padding(start = 8.dp), color = editorText, style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = pinned, onCheckedChange = { pinned = it })
                    }
                }
                Column(Modifier.fillMaxWidth().border(1.dp, editorLine, RoundedCornerShape(15.dp)).background(editorPanel, RoundedCornerShape(15.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { showDetails = !showDetails }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, null, tint = editorPurple)
                        Text("Color / Label", Modifier.weight(1f).padding(start = 8.dp), color = editorText, fontWeight = FontWeight.SemiBold)
                        Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = editorMuted)
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
                Modifier.fillMaxWidth().border(BorderStroke(1.dp, editorLine), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(editorCanvas, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(horizontal = 3.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorToolbarItem(Icons.Default.TextFields, "Text", editorPurple) {
                    if (body.selection.min < body.selection.max) formattingSelection = body.selection
                    textToolsOpen = true
                    keyboard?.hide()
                }
                EditorToolbarItem(Icons.Default.CheckBox, "Checklist", editorBlue) {
                    val checklist = RichContentBlock(kind = "checklist", title = "Checklist", tasks = listOf(ChecklistTask()))
                    val firstAttachment = blocks.indexOfFirst { it.kind != "checklist" && it.kind != "body-formatting" }
                    blocks = if (firstAttachment < 0) blocks + checklist else blocks.toMutableList().apply { add(firstAttachment, checklist) }
                }
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

        if (textToolsOpen) {
            TextFormattingDialog(
                selectionExists = formattingSelection.min < formattingSelection.max || body.selection.min < body.selection.max,
                alignment = bodyAlignment,
                lineSpacing = bodyLineSpacing,
                onStyle = { style -> applyBodyStyle { old -> when (style) {
                    "bold" -> old.copy(bold = !old.bold)
                    "italic" -> old.copy(italic = !old.italic)
                    "underline" -> old.copy(underline = !old.underline)
                    else -> old.copy(strike = !old.strike)
                } } },
                onFontSize = { size -> applyBodyStyle { it.copy(fontSizeSp = size) } },
                onTextColor = { color -> applyBodyStyle { it.copy(textColor = color) } },
                onHighlight = { color -> applyBodyStyle { it.copy(highlightColor = color) } },
                onAlignment = { bodyAlignment = it; syncBodyFormatBlock() },
                onList = ::applyListPrefix,
                onLineSpacing = { bodyLineSpacing = it; syncBodyFormatBlock() },
                onClear = {
                    val selected = formattingSelection.takeIf { it.min < it.max } ?: body.selection
                    if (selected.min < selected.max) {
                        undoStack.add(body)
                        if (undoStack.size > 60) undoStack.removeAt(0)
                        redoStack.clear()
                        bodyStyles = applyNoteTextStyle(body.text.length, bodyStyles, selected.min, selected.max) { NoteTextStyleRange(it.start, it.end) }
                        body = TextFieldValue(styledNoteBody(body.text, bodyStyles), selected)
                        formattingSelection = selected
                        syncBodyFormatBlock()
                    }
                },
                onDone = { restoreBodyFocus = true; textToolsOpen = false }
            )
        }

        if (skinTarget != null) {
            val target = skinTarget
            AlertDialog(
                onDismissRequest = { skinTarget = null },
                containerColor = editorDialog,
                title = { Text("Choose sheet skin", color = editorText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("plain" to "Midnight", "floral" to "Blossom", "leaves" to "Sage", "warm" to "Coral", "sea" to "Ocean").forEach { (key, name) ->
                            Row(Modifier.fillMaxWidth().clickable {
                                target?.let { id -> updateBlock(id) { it.copy(skin = key) } }
                                skinTarget = null
                            }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(24.dp).background(skinColor(key), RoundedCornerShape(6.dp)).border(1.dp, editorPurple.copy(alpha = .7f), RoundedCornerShape(6.dp)))
                                Text(name, Modifier.padding(start = 10.dp), color = editorText)
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
            containerColor = editorDialog,
            title = { Text("Discard unsaved changes?", color = editorText) },
            text = { Text("Your changes have not been saved.", color = editorSecondary) },
            confirmButton = { TextButton(onClick = { discardConfirmation = false; onDismiss() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("Keep editing") } }
        )
        if (reminderMessage) AlertDialog(
            onDismissRequest = { reminderMessage = false },
            containerColor = editorDialog,
            title = { Text("Reminder saved", color = editorText) },
            text = { Text("The reminder time is stored with the note. Notification delivery is not enabled in this build.", color = editorSecondary) },
            confirmButton = { TextButton(onClick = { reminderMessage = false }) { Text("OK") } }
        )
      }
    }
}

@Composable
private fun TextFormattingDialog(
    selectionExists: Boolean,
    alignment: String,
    lineSpacing: Float,
    onStyle: (String) -> Unit,
    onFontSize: (Float) -> Unit,
    onTextColor: (Int) -> Unit,
    onHighlight: (Int?) -> Unit,
    onAlignment: (String) -> Unit,
    onList: (Boolean) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    var fontSize by remember { mutableFloatStateOf(18f) }
    var showTextColorPicker by remember { mutableStateOf(false) }
    var showHighlightColorPicker by remember { mutableStateOf(false) }
    val colors = listOf(0xFF202033.toInt(), 0xFFD32F2F.toInt(), 0xFFEF6C00.toInt(), 0xFF388E3C.toInt(), 0xFF1976D2.toInt(), 0xFF7B1FA2.toInt())
    val highlights = listOf(0xFFFFF176.toInt(), 0xFFFFCC80.toInt(), 0xFFA5D6A7.toInt(), 0xFF90CAF9.toInt(), 0xFFE1BEE7.toInt(), 0xFFF8BBD0.toInt())
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Text formatting", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (selectionExists) "Formatting applies to the selected note text." else "Select a word or sentence in the note first, then choose its formatting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Font size · ${fontSize.toInt()} sp", style = MaterialTheme.typography.labelLarge)
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..32f, steps = 9, enabled = selectionExists)
                OutlinedButton(onClick = { onFontSize(fontSize) }, enabled = selectionExists, modifier = Modifier.fillMaxWidth()) { Text("Apply font size") }

                Text("Style", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("B" to "bold", "I" to "italic", "U" to "underline", "S" to "strike").forEach { (label, value) ->
                        FilterChip(
                            selected = false,
                            onClick = { onStyle(value) },
                            enabled = selectionExists,
                            label = { Text(label, fontWeight = if (value == "bold") FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                Text("Text color", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    colors.forEach { color ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(enabled = selectionExists) { onTextColor(color) })
                    }
                    IconButton(onClick = { showTextColorPicker = true }, enabled = selectionExists, modifier = Modifier.size(34.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)) {
                        Icon(Icons.Default.Add, "Choose a custom text color", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Text("Highlight", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    highlights.forEach { color ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(enabled = selectionExists) { onHighlight(color) })
                    }
                    TextButton(onClick = { onHighlight(null) }, enabled = selectionExists) { Text("Clear") }
                    IconButton(onClick = { showHighlightColorPicker = true }, enabled = selectionExists, modifier = Modifier.size(34.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)) {
                        Icon(Icons.Default.Add, "Choose a custom highlight color", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Text("Alignment · applies to the note text", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("start" to "Left", "center" to "Center", "end" to "Right").forEach { (value, label) ->
                        FilterChip(selected = alignment == value, onClick = { onAlignment(value) }, label = { Text(label) })
                    }
                }

                Text("Lists", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onList(false) }, modifier = Modifier.weight(1f)) { Text("•  Bullets") }
                    OutlinedButton(onClick = { onList(true) }, modifier = Modifier.weight(1f)) { Text("1.  Numbered") }
                }

                var spacing by remember(lineSpacing) { mutableFloatStateOf(lineSpacing) }
                Text("Line spacing · ${"%.2f".format(spacing)}×", style = MaterialTheme.typography.labelLarge)
                Slider(value = spacing, onValueChange = { spacing = it; onLineSpacing(it) }, valueRange = .9f..2f, steps = 10)
                OutlinedButton(onClick = onClear, enabled = selectionExists, modifier = Modifier.fillMaxWidth()) { Text("Clear formatting from selection") }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } }
    )
    if (showTextColorPicker) CustomColorPickerDialog(
        initialColor = colors.first(),
        onDismiss = { showTextColorPicker = false },
        onSelect = onTextColor
    )
    if (showHighlightColorPicker) CustomColorPickerDialog(
        initialColor = highlights.first(),
        onDismiss = { showHighlightColorPicker = false },
        onSelect = { onHighlight(it) }
    )
}

internal fun restyleNoteBodyValue(value: TextFieldValue, ranges: List<NoteTextStyleRange>): TextFieldValue =
    value.copy(annotatedString = styledNoteBody(value.text, ranges))

private fun styledNoteBody(text: String, ranges: List<NoteTextStyleRange>) = buildAnnotatedString {
    append(text)
    ranges.forEach { range ->
        val start = range.start.coerceIn(0, text.length)
        val end = range.end.coerceIn(start, text.length)
        if (start >= end) return@forEach
        val decorations = buildList {
            if (range.underline) add(TextDecoration.Underline)
            if (range.strike) add(TextDecoration.LineThrough)
        }
        addStyle(
            SpanStyle(
                fontWeight = if (range.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (range.italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                textDecoration = decorations.takeIf { it.isNotEmpty() }?.let(TextDecoration::combine),
                fontSize = range.fontSizeSp?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                color = range.textColor?.let(::Color) ?: Color.Unspecified,
                background = range.highlightColor?.let(::Color) ?: Color.Unspecified
            ),
            start,
            end
        )
    }
}

private fun noteTextStylesFromAnnotatedString(text: androidx.compose.ui.text.AnnotatedString): List<NoteTextStyleRange> =
    text.spanStyles.mapNotNull { span ->
        if (span.start >= span.end) return@mapNotNull null
        val style = span.item
        NoteTextStyleRange(
            start = span.start,
            end = span.end,
            bold = (style.fontWeight?.weight ?: 0) >= FontWeight.SemiBold.weight,
            italic = style.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic,
            underline = style.textDecoration?.contains(TextDecoration.Underline) == true,
            strike = style.textDecoration?.contains(TextDecoration.LineThrough) == true,
            fontSizeSp = style.fontSize.takeIf { it != androidx.compose.ui.unit.TextUnit.Unspecified }?.value,
            textColor = style.color.takeIf { it != Color.Unspecified }?.toArgb(),
            highlightColor = style.background.takeIf { it != Color.Unspecified }?.toArgb()
        )
    }.sortedBy { it.start }

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
        Text(label, color = editorText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun EditorMetaButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), color = editorPanel, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, editorLine)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = editorPurple, modifier = Modifier.size(19.dp))
            Column(Modifier.weight(1f).padding(start = 7.dp)) {
                Text(label, color = editorMuted, style = MaterialTheme.typography.labelSmall)
                Text(value, color = editorText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ArrowDropDown, null, tint = editorMuted, modifier = Modifier.size(19.dp))
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestBlock by rememberUpdatedState(block)
    val latestOnChange by rememberUpdatedState(onChange)
    val importSheetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val importedRows = withContext(Dispatchers.IO) {
                    val csv = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("The selected file could not be opened.")
                    parseSheetCsv(csv)
                }
                latestOnChange(latestBlock.copy(rows = importedRows))
                Toast.makeText(context, "Sheet imported", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "Could not import this CSV file", Toast.LENGTH_LONG).show()
            }
        }
    }
    val exportSheetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            try {
                val rows = editableSheetRows(latestBlock.rows)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(encodeSheetCsv(rows))
                    } ?: error("The CSV file could not be created.")
                }
                Toast.makeText(context, "Sheet exported", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "Could not export this sheet", Toast.LENGTH_LONG).show()
            }
        }
    }
    val bg = skinColor(block.skin)
    var viewerOpen by remember(block.id) { mutableStateOf(false) }
    var editorOpen by remember(block.id) { mutableStateOf(false) }
    var confirmDetach by remember(block.id) { mutableStateOf(false) }
    var imagePreview by remember(block.id, block.uri) { mutableStateOf<ImageBitmap?>(null) }
    Column(Modifier.fillMaxWidth().border(1.dp, editorLine, RoundedCornerShape(16.dp)).background(bg, RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(when (block.kind) {
                "image" -> Icons.Default.Image
                "video" -> Icons.Default.Videocam
                "audio" -> Icons.Default.GraphicEq
                "sheet" -> Icons.Default.TableChart
                "checklist" -> Icons.Default.Checklist
                else -> Icons.Default.InsertDriveFile
            }, null, tint = if (block.kind == "video") editorBlue else editorPurple)
            Text(when (block.kind) { "image" -> "Image"; "video" -> "Video"; "audio" -> "Audio"; "sheet" -> "Sheet"; "checklist" -> "Checklist"; else -> "Attachment" }, Modifier.weight(1f).padding(start = 8.dp), color = editorText, fontWeight = FontWeight.SemiBold)
            if (block.kind == "sheet") {
                IconButton(onClick = { importSheetLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.FileUpload, "Import sheet from CSV", tint = editorPurple, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { exportSheetLauncher.launch(sheetCsvFilename(block.title)) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.FileDownload, "Export sheet as CSV", tint = editorPurple, modifier = Modifier.size(18.dp))
                }
                TextButton(onClick = onSkin, contentPadding = PaddingValues(horizontal = 6.dp)) { Icon(Icons.Default.Palette, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Skins") }
            }
            if (block.uri != null && block.kind == "image") {
                TextButton(onClick = { editorOpen = true }, contentPadding = PaddingValues(horizontal = 5.dp), modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Default.Edit, "Edit image", tint = editorPurple, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Edit", color = editorPurple, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { viewerOpen = true }, contentPadding = PaddingValues(horizontal = 5.dp), modifier = Modifier.height(40.dp)) {
                    Icon(Icons.Default.Visibility, "View image", tint = editorPurple, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("View", color = editorPurple, style = MaterialTheme.typography.labelLarge)
                }
            } else if (block.uri != null && block.kind == "video") IconButton(onClick = { viewerOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.OpenInFull, "View video", tint = editorBlue, modifier = Modifier.size(18.dp))
            } else if (block.uri != null) IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.OpenInNew, "Open media", tint = editorBlue, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { if (block.kind == "image") confirmDetach = true else onRemove() }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, "Remove content", tint = editorMuted, modifier = Modifier.size(18.dp)) }
        }

        if (block.kind == "checklist") {
            ChecklistContent(block, onChange)
        } else {
            if (block.kind == "image" && block.uri != null) EmbeddedImage(block.uri, onOpen = { viewerOpen = true }, onImageLoaded = { imagePreview = it })
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
                Text("${block.mimeType.ifBlank { block.kind }} · linked from this device", color = editorMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (viewerOpen && block.uri != null && block.kind in setOf("image", "video")) {
        MediaViewerDialog(block, onDismiss = { viewerOpen = false })
    }
    if (editorOpen && block.kind == "image" && block.uri != null) {
        ImageEditorDialog(
            uriText = block.uri,
            mimeType = block.mimeType,
            initialPreview = imagePreview,
            onDismiss = { editorOpen = false },
            onSaved = { uri, mime ->
                onChange(block.copy(uri = uri, mimeType = mime))
                editorOpen = false
            }
        )
    }
    if (confirmDetach) {
        AlertDialog(
            onDismissRequest = { confirmDetach = false },
            title = { Text("Remove image from note?") },
            text = { Text("This detaches the image from this note. The original file on your device will not be deleted.") },
            confirmButton = { TextButton(onClick = { confirmDetach = false; onRemove() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDetach = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ChecklistContent(block: RichContentBlock, onChange: (RichContentBlock) -> Unit) {
    var newTask by remember(block.id) { mutableStateOf("") }
    val completed = block.tasks.count { it.checked }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$completed of ${block.tasks.size} completed", Modifier.weight(1f), color = editorMuted, style = MaterialTheme.typography.labelMedium)
            FilterChip(selected = block.checklistStyle == "square", onClick = { onChange(block.copy(checklistStyle = "square")) }, label = { Text("Square") })
            Spacer(Modifier.width(5.dp))
            FilterChip(selected = block.checklistStyle == "circle", onClick = { onChange(block.copy(checklistStyle = "circle")) }, label = { Text("Circle") })
        }
        block.tasks.forEachIndexed { index, task ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (block.checklistStyle == "circle") {
                    IconButton(onClick = {
                        onChange(block.copy(tasks = setChecklistTaskChecked(block.tasks, task.id, !task.checked)))
                    }, modifier = Modifier.size(42.dp)) {
                        Icon(if (task.checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, if (task.checked) "Mark ${task.text} incomplete" else "Mark ${task.text} complete", tint = if (task.checked) editorPurple else editorMuted)
                    }
                } else {
                    Checkbox(
                        checked = task.checked,
                        onCheckedChange = { checked -> onChange(block.copy(tasks = setChecklistTaskChecked(block.tasks, task.id, checked))) },
                        colors = CheckboxDefaults.colors(checkedColor = editorPurple, uncheckedColor = editorMuted)
                    )
                }
                BasicTextField(
                    value = task.text,
                    onValueChange = { value -> onChange(block.copy(tasks = block.tasks.mapIndexed { i, item -> if (i == index) item.copy(text = value) else item })) },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = editorText,
                        textDecoration = if (task.checked) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    cursorBrush = SolidColor(editorPurple),
                    decorationBox = { inner -> Box {
                        if (task.text.isBlank()) Text("Task ${index + 1}", color = editorPlaceholder, style = MaterialTheme.typography.bodyMedium)
                        inner()
                    } }
                )
                IconButton(onClick = {
                    if (index > 0) onChange(block.copy(tasks = block.tasks.toMutableList().apply { add(index - 1, removeAt(index)) }))
                }, enabled = index > 0, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Move task up", tint = editorMuted) }
                IconButton(onClick = {
                    if (index < block.tasks.lastIndex) onChange(block.copy(tasks = block.tasks.toMutableList().apply { add(index + 1, removeAt(index)) }))
                }, enabled = index < block.tasks.lastIndex, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Move task down", tint = editorMuted) }
                IconButton(onClick = { onChange(block.copy(tasks = block.tasks.filterNot { it.id == task.id })) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Delete task ${task.text.ifBlank { index + 1 }}", tint = editorMuted, modifier = Modifier.size(19.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTask,
                onValueChange = { newTask = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Add a new item…") },
                colors = editorFieldColors()
            )
            IconButton(onClick = {
                val clean = newTask.trim()
                if (clean.isNotEmpty()) {
                    onChange(block.copy(tasks = block.tasks + ChecklistTask(text = clean)))
                    newTask = ""
                }
            }, enabled = newTask.isNotBlank(), modifier = Modifier.size(46.dp)) { Icon(Icons.Default.Add, "Add checklist task", tint = editorPurple) }
        }
        if (completed > 0) {
            TextButton(onClick = { onChange(block.copy(tasks = clearCompletedChecklistTasks(block.tasks))) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Clear completed")
            }
        }
    }
}

@Composable
private fun SheetGrid(block: RichContentBlock, onChange: (RichContentBlock) -> Unit, onPaste: () -> Unit) {
    var selectedColumn by remember(block.id) { mutableIntStateOf(0) }
    var selectedRow by remember(block.id) { mutableIntStateOf(0) }
    val rows = editableSheetRows(block.rows)
    val columns = rows.firstOrNull()?.size ?: 1
    val activeColumn = selectedColumn.coerceIn(0, columns - 1)
    val activeRow = selectedRow.coerceIn(0, rows.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onPaste, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Paste") }
            OutlinedButton(onClick = {
                onChange(block.copy(rows = appendSheetRow(rows)))
                selectedRow = rows.size
            }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Text("Row") }
            OutlinedButton(onClick = {
                onChange(block.copy(rows = rows.map { it + "" }))
                selectedColumn = columns
            }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.ViewColumn, null, modifier = Modifier.size(16.dp)); Text("Add column") }
            IconButton(
                onClick = {
                if (columns > 1) {
                    val remove = activeColumn
                    onChange(block.copy(rows = rows.map { row -> row.filterIndexed { index, _ -> index != remove } }))
                    selectedColumn = (remove - 1).coerceAtLeast(0)
                }
            }, enabled = columns > 1, modifier = Modifier.size(40.dp)
            ) { Icon(Icons.Default.DeleteOutline, "Delete selected column", tint = if (columns > 1) editorPurple else editorMuted, modifier = Modifier.size(19.dp)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(columns) { index ->
                FilterChip(selected = activeColumn == index, onClick = { selectedColumn = index }, label = { Text("Column ${index + 1}") })
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rows.indices.forEach { index ->
                    FilterChip(selected = activeRow == index, onClick = { selectedRow = index }, label = { Text("Row ${index + 1}") })
                }
            }
            IconButton(
                onClick = {
                    onChange(block.copy(rows = deleteSheetRow(rows, activeRow)))
                    selectedRow = (activeRow - 1).coerceAtLeast(0)
                },
                enabled = rows.size > 1,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, "Delete selected row", tint = if (rows.size > 1) editorPurple else editorMuted, modifier = Modifier.size(19.dp))
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
                            modifier = Modifier.width(112.dp).heightIn(min = 40.dp).background(if (activeColumn == columnIndex || activeRow == rowIndex) editorSelectedCell else editorCell).border(1.dp, if (activeColumn == columnIndex || activeRow == rowIndex) editorPurple.copy(alpha = .5f) else editorCellLine).padding(horizontal = 7.dp, vertical = 8.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = editorText),
                            cursorBrush = SolidColor(editorPurple),
                            decorationBox = { inner -> Box { if (value.isBlank()) Text(if (rowIndex == 0) "Header" else "Cell", color = editorMuted, style = MaterialTheme.typography.labelSmall); inner() } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddedImage(uriText: String, onOpen: () -> Unit, onImageLoaded: (ImageBitmap?) -> Unit) {
    val context = LocalContext.current
    val image by produceImage(uriText, context)
    LaunchedEffect(image) { onImageLoaded(image) }
    if (image != null) {
        Image(image!!, contentDescription = "View embedded image", modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(10.dp)).background(editorMediaCanvas).clickable(onClick = onOpen), contentScale = ContentScale.Fit)
    } else {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(10.dp)).background(editorCanvas).clickable(onClick = onOpen), contentAlignment = Alignment.Center) { Icon(Icons.Default.Image, "Image preview unavailable; open viewer", tint = editorPurple, modifier = Modifier.size(36.dp)) }
    }
}

@Composable
private fun produceImage(uriText: String, context: Context, maxDimension: Int = 1800): State<ImageBitmap?> {
    return produceState<ImageBitmap?>(initialValue = null, key1 = uriText, key2 = maxDimension) {
        var pendingBitmap: Bitmap? = null
        try {
            val image = withContext(Dispatchers.IO) {
                val decoded = decodeBitmap(context, Uri.parse(uriText), maxDimension)
                pendingBitmap = decoded
                decoded.asImageBitmap()
            }
            value = image
            pendingBitmap = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: OutOfMemoryError) {
            val uri = Uri.parse(uriText)
            val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null } ?: "unknown"
            logImageFailure("embedded-preview", mime, error, uri = uri, maxDimension = maxDimension)
            value = null
        } catch (error: Exception) {
            val uri = Uri.parse(uriText)
            val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null } ?: "unknown"
            logImageFailure("embedded-preview", mime, error, uri = uri, maxDimension = maxDimension)
            value = null
        } finally {
            pendingBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }
}

@Composable
private fun EmbeddedVideo(uriText: String) {
    val ratio = rememberVideoAspectRatio(uriText)
    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(Color.Black),
        factory = { ctx -> createVideoView(ctx, uriText, autoPlay = false) }
    )
}

@Composable
private fun rememberVideoAspectRatio(uriText: String): Float {
    val context = LocalContext.current
    val ratio by produceState(initialValue = 16f / 9f, key1 = uriText) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, Uri.parse(uriText))
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                normalizedVideoAspectRatio(width, height, rotation)
            }.getOrDefault(16f / 9f).also { runCatching { retriever.release() } }
        }
    }
    return ratio
}

internal fun normalizedVideoAspectRatio(width: Float?, height: Float?, rotationDegrees: Int): Float {
    if (width == null || height == null || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
        return 16f / 9f
    }
    val rotation = ((rotationDegrees % 360) + 360) % 360
    val aspect = if (rotation == 90 || rotation == 270) height / width else width / height
    return aspect.coerceIn(0.25f, 4f)
}

private fun createVideoView(context: Context, uriText: String, autoPlay: Boolean): VideoView {
    val video = VideoView(context)
    video.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    video.setMediaController(MediaController(context).apply { setAnchorView(video) })
    video.setOnPreparedListener { if (autoPlay) video.start() }
    video.setVideoURI(Uri.parse(uriText))
    return video
}

@Composable
private fun MediaViewerDialog(block: RichContentBlock, onDismiss: () -> Unit) {
    val uriText = block.uri ?: return
    val ratio = if (block.kind == "video") rememberVideoAspectRatio(uriText) else 1f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(editorBg)) {
            if (block.kind == "image") {
                val context = LocalContext.current
                val image by produceImage(uriText, context, maxDimension = 3200)
                if (image != null) {
                    var scale by remember(uriText) { mutableFloatStateOf(1f) }
                    var translationX by remember(uriText) { mutableFloatStateOf(0f) }
                    var translationY by remember(uriText) { mutableFloatStateOf(0f) }
                    var viewport by remember(uriText) { mutableStateOf(IntSize.Zero) }
                    val currentImage = image!!
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
                        val nextPan = if (nextScale <= 1.001f) PanOffset(0f, 0f) else clampViewerPan(
                            PanOffset(translationX + panChange.x, translationY + panChange.y),
                            PanSize(viewport.width.toFloat(), viewport.height.toFloat()),
                            PanSize(currentImage.width.toFloat(), currentImage.height.toFloat()),
                            nextScale
                        )
                        scale = nextScale
                        translationX = nextPan.x
                        translationY = nextPan.y
                    }
                    Box(
                        Modifier.fillMaxSize().padding(20.dp)
                            .onSizeChanged { viewport = it }
                            .pointerInput(uriText, viewport, scale) {
                                detectTapGestures(onDoubleTap = { tap ->
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        translationX = 0f
                                        translationY = 0f
                                    } else {
                                        val target = 2.5f
                                        val candidate = PanOffset(
                                            -(tap.x - viewport.width / 2f) * (target - 1f),
                                            -(tap.y - viewport.height / 2f) * (target - 1f)
                                        )
                                        val clamped = clampViewerPan(
                                            candidate,
                                            PanSize(viewport.width.toFloat(), viewport.height.toFloat()),
                                            PanSize(currentImage.width.toFloat(), currentImage.height.toFloat()),
                                            target
                                        )
                                        scale = target
                                        translationX = clamped.x
                                        translationY = clamped.y
                                    }
                                })
                            }
                            .transformable(transformState),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            currentImage,
                            contentDescription = "Full-screen image. Pinch to zoom, drag to pan, double-tap to zoom.",
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.translationX = translationX
                                this.translationY = translationY
                            },
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Text("This image could not be previewed on this device.", Modifier.align(Alignment.Center).padding(24.dp), color = editorText)
                }
            } else {
                val availableWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp)
                val availableHeight = (maxHeight - 72.dp).coerceAtLeast(1.dp)
                val videoWidth = minOf(availableWidth, availableHeight * ratio)
                val videoHeight = videoWidth / ratio
                AndroidView(
                    modifier = Modifier.size(videoWidth, videoHeight).align(Alignment.Center).background(Color.Black),
                    factory = { context -> createVideoView(context, uriText, autoPlay = true) }
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(48.dp).background(editorPanel.copy(alpha = .9f), CircleShape)) {
                Icon(Icons.Default.Close, "Close media viewer", tint = editorText)
            }
        }
    }
}

@Composable
private fun EmbeddedAudio(uriText: String, title: String) {
    val context = LocalContext.current
    var player by remember(uriText) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(uriText) { mutableStateOf(false) }
    var loading by remember(uriText) { mutableStateOf(false) }
    var failed by remember(uriText) { mutableStateOf(false) }
    DisposableEffect(uriText) { onDispose { runCatching { player?.release() }; player = null } }
    Row(Modifier.fillMaxWidth().background(editorAudioSurface, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Text(title.ifBlank { "Audio attachment" }, color = editorText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(when { loading -> "Loading audio…"; failed -> "This audio could not be played here."; playing -> "Playing"; else -> "Tap play to listen" }, color = editorMuted, style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.Default.GraphicEq, null, tint = editorPurple)
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = editorText, unfocusedTextColor = editorText,
    focusedBorderColor = editorPurple.copy(alpha = .75f), unfocusedBorderColor = editorLine,
    focusedLabelColor = editorPurple, unfocusedLabelColor = editorMuted, cursorColor = editorPurple,
    focusedContainerColor = editorField, unfocusedContainerColor = editorField
)

private fun noteLabelColorOptions(): List<Pair<String, Color>> = listOf(
    "purple" to Color(0xFF7650E8), "blue" to Color(0xFF448EEA), "green" to Color(0xFF40A878),
    "yellow" to Color(0xFFE7B52D), "orange" to Color(0xFFEA8D3C), "pink" to Color(0xFFE56C9B), "gray" to Color(0xFF81899A)
)

@Composable
private fun skinColor(skin: String): Color {
    return if (LocalEditorPalette.current == EditorPalette.Dark) when (skin) {
        "floral" -> Color(0xFF241626)
        "leaves" -> Color(0xFF12211D)
        "warm" -> Color(0xFF251A18)
        "sea" -> Color(0xFF101C2C)
        else -> Color(0xFF080D19)
    } else when (skin) {
        "floral" -> Color(0xFFFFF5FA)
        "leaves" -> Color(0xFFF2FAF4)
        "warm" -> Color(0xFFFFF7F0)
        "sea" -> Color(0xFFF0F8FF)
        else -> Color(0xFFF8F6FD)
    }
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


