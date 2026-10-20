package com.enotes.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enotes.app.data.Note
import com.enotes.app.data.NoteFolder
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private data class NoteLabelColor(val key: String, val label: String, val color: Color)

private val noteLabelColors = listOf(
    NoteLabelColor("purple", "Purple", Color(0xFF7650E8)),
    NoteLabelColor("blue", "Blue", Color(0xFF448EEA)),
    NoteLabelColor("green", "Green", Color(0xFF40A878)),
    NoteLabelColor("yellow", "Yellow", Color(0xFFE7B52D)),
    NoteLabelColor("orange", "Orange", Color(0xFFEA8D3C)),
    NoteLabelColor("pink", "Pink", Color(0xFFE56C9B)),
    NoteLabelColor("gray", "Gray", Color(0xFF81899A))
)

internal fun noteLabelColor(key: String): Color = noteLabelColors.firstOrNull { it.key == key }?.color ?: noteLabelColors.first().color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditorDialog(
    note: Note?,
    folders: List<NoteFolder>,
    attachmentUri: String?,
    onAttachmentChange: (String?) -> Unit,
    onPickAttachment: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    onSave: (title: String, body: String, category: String, folder: String?, attachmentUri: String?, priority: String, reminderAt: Long?, labelColor: String, pinned: Boolean) -> Unit,
    onUnavailable: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember(note?.id) { mutableStateOf(note?.title.orEmpty()) }
    var body by remember(note?.id) { mutableStateOf(TextFieldValue(note?.body.orEmpty())) }
    var category by remember(note?.id) { mutableStateOf(note?.category ?: "Others") }
    var folder by remember(note?.id) { mutableStateOf(note?.folder) }
    var priority by remember(note?.id) { mutableStateOf(note?.priority ?: "Medium") }
    var reminderAt by remember(note?.id) { mutableStateOf(note?.reminderAt) }
    var labelColor by remember(note?.id) { mutableStateOf(note?.labelColor ?: "purple") }
    var pinned by remember(note?.id) { mutableStateOf(note?.isPinned ?: false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var discardConfirmation by remember { mutableStateOf(false) }
    var attachmentConfirmation by remember { mutableStateOf(false) }
    var showAttachmentReplaceConfirmation by remember { mutableStateOf(false) }
    val undoStack = remember(note?.id) { mutableStateListOf<TextFieldValue>() }
    val original = remember(note?.id) {
        listOf(note?.title.orEmpty(), note?.body.orEmpty(), note?.category ?: "Others", note?.folder.orEmpty(), note?.attachmentUri.orEmpty(), note?.priority ?: "Medium", note?.reminderAt?.toString().orEmpty(), note?.labelColor ?: "purple", note?.isPinned?.toString() ?: "false")
    }
    val hasChanges = listOf(title, body.text, category, folder.orEmpty(), attachmentUri.orEmpty(), priority, reminderAt?.toString().orEmpty(), labelColor, pinned.toString()) != original
    val wordCount = body.text.trim().split(Regex("\\s+")).count(String::isNotBlank)
    val maxTitleLength = maxOf(100, note?.title?.length ?: 0)

    fun requestDismiss() {
        if (hasChanges) discardConfirmation = true else onDismiss()
    }

    fun applyBodyEdit(result: TextEditResult) {
        undoStack.add(body)
        if (undoStack.size > 30) undoStack.removeAt(0)
        body = TextFieldValue(result.text, TextRange(result.selectionStart, result.selectionEnd))
    }

    fun wrap(prefix: String, suffix: String = prefix) {
        applyBodyEdit(wrapTextSelection(body.text, body.selection.start, body.selection.end, prefix, suffix))
    }

    fun prefixLine(prefix: String) {
        applyBodyEdit(prefixCurrentLine(body.text, body.selection.start, prefix))
    }

    Dialog(onDismissRequest = ::requestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier.fillMaxWidth().widthIn(max = 620.dp).fillMaxHeight(0.94f).padding(horizontal = 10.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFF))
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).background(Color(0xFFF0EAFE), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.NoteAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(if (note == null) "New note" else "Edit note", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = ::requestDismiss) { Icon(Icons.Default.Close, "Close editor", tint = MaterialTheme.colorScheme.primary) }
                }

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(maxTitleLength) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title") },
                        leadingIcon = { Icon(Icons.Default.Title, null) },
                        supportingText = { Text("${title.length}/100", Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Column(Modifier.fillMaxWidth().border(1.dp, Color(0xFFE1DDF0), RoundedCornerShape(16.dp))) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            EditorTool(Icons.Default.FormatBold, "Bold") { wrap("**") }
                            EditorTool(Icons.Default.FormatItalic, "Italic") { wrap("*") }
                            EditorTool(Icons.Default.FormatUnderlined, "Underline") { wrap("__") }
                            VerticalDivider(Modifier.height(26.dp).padding(horizontal = 2.dp))
                            EditorTool(Icons.Default.CheckBox, "Checklist") { prefixLine("- [ ] ") }
                            EditorTool(Icons.Default.FormatListBulleted, "Bulleted list") { prefixLine("- ") }
                            EditorTool(Icons.Default.FormatListNumbered, "Numbered list") { prefixLine("1. ") }
                            Spacer(Modifier.weight(1f))
                            EditorTool(Icons.Default.Undo, "Undo") {
                                if (undoStack.isNotEmpty()) body = undoStack.removeAt(undoStack.lastIndex)
                            }
                        }
                        HorizontalDivider(color = Color(0xFFECE9F5))
                        BasicTextField(
                            value = body,
                            onValueChange = { newValue ->
                                if (newValue.text != body.text) {
                                    undoStack.add(body)
                                    if (undoStack.size > 30) undoStack.removeAt(0)
                                }
                                body = newValue
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 155.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box {
                                    if (body.text.isEmpty()) Text("Write something about your note…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                                    inner()
                                }
                            }
                        )
                        Text("$wordCount words · ${body.text.length} characters", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    }
                    Text("Formatting shortcuts add Markdown markers to your note text.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)) {
                                Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                    Text("Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(categoryMenu, { categoryMenu = false }) {
                                dashboardCategories.drop(1).forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { category = value; categoryMenu = false }) }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { folderMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                    Text("Folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(folder ?: "None", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(folderMenu, { folderMenu = false }) {
                                DropdownMenuItem(text = { Text("None") }, onClick = { folder = null; folderMenu = false })
                                folders.forEach { value -> DropdownMenuItem(text = { Text(value.name) }, onClick = { folder = value.name; folderMenu = false }) }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(7.dp))
                            Text("Attachment${if (attachmentUri == null) "" else " (1)"}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { if (attachmentUri == null) onPickAttachment() else showAttachmentReplaceConfirmation = true }) {
                                Icon(Icons.Default.Add, if (attachmentUri == null) "Add attachment" else "Replace attachment", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (attachmentUri != null) {
                            val attachmentName = remember(attachmentUri) {
                                runCatching {
                                    context.contentResolver.query(Uri.parse(attachmentUri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                                        if (cursor.moveToFirst()) cursor.getString(0) else null
                                    }
                                }.getOrNull().orEmpty().ifBlank { "Attached document" }
                            }
                            Card(shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFE8E5F4)), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBFAFF))) {
                                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 3.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFFE05B67))
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(attachmentName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text("Attachment linked to this note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onOpenAttachment(attachmentUri) }) { Icon(Icons.Default.OpenInNew, "Open attachment") }
                                    IconButton(onClick = { attachmentConfirmation = true }) { Icon(Icons.Default.Close, "Remove attachment link") }
                                }
                            }
                        } else {
                            OutlinedButton(onClick = onPickAttachment, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add a PDF or image")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val initial = Calendar.getInstance().apply { reminderAt?.let { timeInMillis = it } }
                            DatePickerDialog(context, { _, year, month, day ->
                                val selected = Calendar.getInstance().apply {
                                    timeInMillis = reminderAt ?: System.currentTimeMillis()
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, day)
                                }
                                TimePickerDialog(context, { _, hour, minute ->
                                    selected.set(Calendar.HOUR_OF_DAY, hour)
                                    selected.set(Calendar.MINUTE, minute)
                                    selected.set(Calendar.SECOND, 0)
                                    selected.set(Calendar.MILLISECOND, 0)
                                    reminderAt = selected.timeInMillis
                                }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), false).show()
                            }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text("Reminder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(reminderAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: "Add date and time", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (reminderAt != null) IconButton(onClick = { reminderAt = null }) { Icon(Icons.Default.Close, "Clear reminder") }
                        else Icon(Icons.Default.ArrowDropDown, null)
                    }
                    Text("Reminder time is saved with this note. Notification delivery is not configured in this build.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { priorityMenu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFE7A923))
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                    Text("Priority", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(priority, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(priorityMenu, { priorityMenu = false }) {
                                listOf("Low", "Medium", "High").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { priority = value; priorityMenu = false }) }
                            }
                        }
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFF8F6FC), border = BorderStroke(1.dp, Color(0xFFEAE7F2))) {
                            Row(Modifier.weight(1f).padding(start = 10.dp, end = 5.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                                    Text("Pin to top", style = MaterialTheme.typography.labelSmall)
                                    Text(if (pinned) "Pinned" else "Keep handy", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(pinned, { pinned = it })
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Color / Label", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            noteLabelColors.forEach { option ->
                                val selected = labelColor == option.key
                                Box(
                                    Modifier.size(if (selected) 34.dp else 30.dp).background(option.color, CircleShape)
                                        .then(if (selected) Modifier.border(3.dp, Color.White, CircleShape).border(2.dp, option.color, CircleShape) else Modifier)
                                        .clickable { labelColor = option.key }
                                        .semantics { contentDescription = "Set note color to ${option.label}" },
                                    contentAlignment = Alignment.Center
                                ) { if (selected) Icon(Icons.Default.Check, option.label, tint = Color.White, modifier = Modifier.size(17.dp)) }
                            }
                        }
                    }

                    Column(Modifier.fillMaxWidth().background(Color(0xFFF8F6FF), RoundedCornerShape(18.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(7.dp))
                            Text("AI Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onUnavailable("AI improvements need a configured secure provider. Your note was not sent or changed.") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Improve with AI", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { onUnavailable("AI summaries need a configured secure provider. Your note was not sent or changed.") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                                Icon(Icons.Default.Summarize, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Summarize", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFECE9F5))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::requestDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { onSave(title, body.text, category, folder, attachmentUri, priority, reminderAt, labelColor, pinned) },
                        shape = RoundedCornerShape(50.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (note == null) "Save note" else "Save changes")
                    }
                }
            }
        }
    }

    if (discardConfirmation) AlertDialog(
        onDismissRequest = { discardConfirmation = false },
        title = { Text("Discard unsaved changes?") },
        text = { Text("Your changes have not been saved.") },
        confirmButton = { TextButton(onClick = { discardConfirmation = false; onDismiss() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("Keep editing") } }
    )
    if (attachmentConfirmation) AlertDialog(
        onDismissRequest = { attachmentConfirmation = false },
        title = { Text("Remove attachment link?") },
        text = { Text("The file stays on your device, but it will no longer be attached to this note.") },
        confirmButton = { TextButton(onClick = { onAttachmentChange(null); attachmentConfirmation = false }) { Text("Remove link") } },
        dismissButton = { TextButton(onClick = { attachmentConfirmation = false }) { Text("Cancel") } }
    )
    if (showAttachmentReplaceConfirmation) AlertDialog(
        onDismissRequest = { showAttachmentReplaceConfirmation = false },
        title = { Text("Replace attachment?") },
        text = { Text("This note supports one attachment. The current file remains on your device, but its link will be replaced.") },
        confirmButton = { TextButton(onClick = { showAttachmentReplaceConfirmation = false; onPickAttachment() }) { Text("Choose file") } },
        dismissButton = { TextButton(onClick = { showAttachmentReplaceConfirmation = false }) { Text("Cancel") } }
    )
}
@Composable
private fun EditorTool(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(39.dp)) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

