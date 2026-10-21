package com.enotes.app

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val IMAGE_EDITOR_LOG_TAG = "ENotesImageEditor"
private val imageMarkupColors = listOf(
    0xFF6639E8.toInt(), 0xFF202033.toInt(), 0xFFE53935.toInt(), 0xFFFF8F00.toInt(),
    0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFFFFFFFF.toInt()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ImageEditorDialog(
    uriText: String,
    mimeType: String,
    initialPreview: androidx.compose.ui.graphics.ImageBitmap?,
    onDismiss: () -> Unit,
    onSaved: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var discardConfirmation by remember(uriText) { mutableStateOf(false) }
    var saveError by remember(uriText) { mutableStateOf<String?>(null) }
    var busy by remember(uriText) { mutableStateOf(false) }
    var formatMenu by remember(uriText) { mutableStateOf(false) }
    var saveAsPng by remember(uriText) { mutableStateOf(mimeType.equals("image/png", true)) }
    var selectedTool by remember(uriText) { mutableStateOf("Crop") }
    var markupTool by remember(uriText) { mutableStateOf("Pen") }
    var markupColor by remember(uriText) { mutableIntStateOf(imageMarkupColors.first()) }
    var highlighterTransparency by remember(uriText) { mutableFloatStateOf(.6f) }
    var showOriginal by remember(uriText) { mutableStateOf(false) }
    var textDialog by remember(uriText) { mutableStateOf(false) }
    var annotationText by remember(uriText) { mutableStateOf("") }
    var draftAnnotation by remember(uriText) { mutableStateOf<ImageAnnotation?>(null) }
    var pendingSave by remember(uriText) { mutableStateOf(false) }
    var previewMaxDimension by remember(uriText) { mutableIntStateOf(2048) }
    var previewRetryToken by remember(uriText) { mutableIntStateOf(0) }
    val historyState = remember(uriText) { mutableStateOf(ImageEditHistory()) }
    var history by historyState
    val currentState = history.current

    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingSave = true else saveError = "Storage permission is needed to save an edited image on this Android version."
    }

    val sourceLoad by produceState(
        ImageLoadResult(null, null, false),
        uriText,
        initialPreview,
        previewMaxDimension,
        previewRetryToken,
        mimeType
    ) {
        value = ImageLoadResult(null, null, false)
        var pendingResult: ImageLoadResult? = null
        try {
            val result = withContext(Dispatchers.IO) {
                val uri = Uri.parse(uriText)
                val resolvedMime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: mimeType.ifBlank { "unknown" }
                try {
                    val cached = if (previewRetryToken == 0) {
                        try { initialPreview?.asAndroidBitmap() } catch (_: Exception) { null }
                    } else null
                    val reusable = cached?.takeIf {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                            !it.isRecycled && it.config != Bitmap.Config.HARDWARE &&
                            it.width <= previewMaxDimension && it.height <= previewMaxDimension
                    }
                    val loaded = if (reusable != null) {
                        ImageLoadResult(reusable, null, false)
                    } else {
                        ImageLoadResult(decodeBitmap(context, uri, previewMaxDimension), null, true)
                    }
                    Log.d(IMAGE_EDITOR_LOG_TAG, "Image preview source ready stage=decode width=${loaded.bitmap?.width} height=${loaded.bitmap?.height} mime=$resolvedMime")
                    loaded
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: OutOfMemoryError) {
                    logImageFailure("preview-decode", resolvedMime, error, uri = uri, maxDimension = previewMaxDimension)
                    ImageLoadResult(null, "There was not enough memory to prepare this image. Retry with a smaller preview.", false)
                } catch (error: Exception) {
                    logImageFailure("preview-decode", resolvedMime, error, uri = uri, maxDimension = previewMaxDimension)
                    ImageLoadResult(null, imageLoadErrorMessage(error), false)
                }.also { pendingResult = it }
            }
            value = result
            pendingResult = null
        } finally {
            pendingResult?.takeIf { it.owned }?.bitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }
    val source = sourceLoad.bitmap
    DisposableEffect(source, sourceLoad.owned) {
        val owned = sourceLoad.owned
        onDispose { if (owned) source?.let { if (!it.isRecycled) it.recycle() } }
    }
    val previewResult by produceState<ImagePreviewResult>(initialValue = ImagePreviewResult(null, null, true), key1 = source, key2 = currentState) {
        value = ImagePreviewResult(null, null, true)
        val bitmap = source
        if (bitmap == null) {
            value = ImagePreviewResult(null, null, false)
        } else if (bitmap.isRecycled) {
            value = ImagePreviewResult(null, "The preview image was released. Retry to load it again.", false)
        } else if (!needsImagePreviewRender(currentState)) {
            // Opening the editor with no changes should display the decoded source directly.
            // Rendering an identical second bitmap here doubled the editor's initial bitmap memory.
            value = ImagePreviewResult(bitmap, null, false)
        } else {
            var pendingBitmap: Bitmap? = null
            try {
                val result = withContext(Dispatchers.Default) {
                    try {
                        pendingBitmap = renderEditedBitmap(bitmap, currentState)
                        ImagePreviewResult(pendingBitmap, null, false)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: OutOfMemoryError) {
                        logImageFailure("preview-render", mimeType, error, width = bitmap.width, height = bitmap.height, uri = Uri.parse(uriText))
                        ImagePreviewResult(null, "There was not enough memory to render this edit. Retry with a smaller preview.", false)
                    } catch (error: Exception) {
                        logImageFailure("preview-render", mimeType, error, width = bitmap.width, height = bitmap.height, uri = Uri.parse(uriText))
                        ImagePreviewResult(null, "This edit could not be rendered. Retry the preview or undo the last change.", false)
                    }
                }
                value = result
                pendingBitmap = null
            } catch (cancelled: CancellationException) {
                pendingBitmap?.takeIf { it !== bitmap }?.let { if (!it.isRecycled) it.recycle() }
                throw cancelled
            } finally {
                pendingBitmap?.takeIf { it !== bitmap }?.let { if (!it.isRecycled) it.recycle() }
            }
        }
    }
    val preview = previewResult.bitmap
    DisposableEffect(preview, source) {
        onDispose { preview?.let { if (it !== source && !it.isRecycled) it.recycle() } }
    }

    fun commit(next: ImageEditState) {
        history = recordImageEdit(history, next)
    }
    val latestHistoryCommit by rememberUpdatedState<(ImageEditState) -> Unit>({ next ->
        historyState.value = recordImageEdit(historyState.value, next)
    })
    var sliderStart by remember(uriText) { mutableStateOf<ImageEditState?>(null) }
    fun beginSliderEdit() { if (sliderStart == null) sliderStart = historyState.value.current }
    fun finishSliderEdit() {
        val initial = sliderStart ?: return
        val latest = historyState.value
        if (initial != latest.current) {
            historyState.value = latest.copy(undo = (latest.undo + initial).takeLast(60), redo = emptyList())
        }
        sliderStart = null
    }
    fun commitContinuous(next: ImageEditState) { history = history.copy(current = next) }
    fun requestDismiss() {
        if (currentState == ImageEditState()) onDismiss() else discardConfirmation = true
    }
    fun saveEditedImage() {
        val original = source ?: return
        val exportState = historyState.value.current
        busy = true
        saveError = null
        scope.launch {
            val result: Result<Uri> = withContext(Dispatchers.IO) {
                try {
                    val full = try {
                        decodeBitmap(context, Uri.parse(uriText), 4800)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        original.copy(Bitmap.Config.ARGB_8888, false) ?: throw error
                    }
                    Result.success(try {
                        val output = renderEditedBitmap(full, exportState)
                        try { saveToGallery(context, output, saveAsPng) } finally { if (output !== full) output.recycle() }
                    } finally { if (full !== original && !full.isRecycled) full.recycle() })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: OutOfMemoryError) {
                    logImageFailure("export-render", mimeType, error, width = original.width, height = original.height, uri = Uri.parse(uriText))
                    Result.failure(IOException("The edited image needs more memory to export. Try a smaller image or close other apps and retry.", error))
                } catch (error: Exception) {
                    logImageFailure("export-save", mimeType, error, width = original.width, height = original.height, uri = Uri.parse(uriText))
                    Result.failure(error)
                }
            }
            busy = false
            result.onSuccess { onSaved(it.toString(), if (saveAsPng) "image/png" else "image/jpeg") }
                .onFailure { saveError = it.message ?: "The edited image could not be saved." }
        }
    }

    LaunchedEffect(pendingSave) {
        if (pendingSave) {
            pendingSave = false
            saveEditedImage()
        }
    }
    BackHandler { requestDismiss() }

    Dialog(onDismissRequest = { requestDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    IconButton(onClick = { requestDismiss() }, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.ArrowBack, "Cancel image editing") }
                    Text("Edit photo", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    IconButton(onClick = { history = undoImageEdit(history) }, enabled = history.undo.isNotEmpty(), modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Undo, "Undo edit") }
                    IconButton(onClick = { history = redoImageEdit(history) }, enabled = history.redo.isNotEmpty(), modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Redo, "Redo edit") }
                    Box {
                        TextButton(onClick = { formatMenu = true }, enabled = !busy, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.width(44.dp)) { Text(if (saveAsPng) "PNG" else "JPG", maxLines = 1) }
                        DropdownMenu(expanded = formatMenu, onDismissRequest = { formatMenu = false }) {
                            DropdownMenuItem(text = { Text("Save as PNG") }, onClick = { saveAsPng = true; formatMenu = false })
                            DropdownMenuItem(text = { Text("Save as JPEG") }, onClick = { saveAsPng = false; formatMenu = false })
                        }
                    }
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                            ) {
                                storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                saveEditedImage()
                            }
                        },
                        enabled = source != null && preview != null && !busy,
                        modifier = Modifier.height(44.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp)); Text(if (busy) "Saving" else "Save")
                    }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val shown = if (showOriginal) source else preview
                    val imageBitmap = remember(shown) { shown?.asImageBitmap() }
                    if (imageBitmap != null) {
                        Box(Modifier.fillMaxSize().padding(12.dp)) {
                            Image(imageBitmap, "Image edit preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            ImageMarkupOverlay(
                                bitmap = shown,
                                annotations = if (showOriginal) emptyList() else currentState.annotations,
                                draft = if (showOriginal) null else draftAnnotation,
                                activeTool = if (showOriginal || selectedTool != "Markup") "" else markupTool,
                                activeColor = markupColor,
                                highlighterOpacity = 1f - highlighterTransparency,
                                onDraftChange = { draftAnnotation = it },
                                onDraftFinish = { annotation ->
                                    if (annotation != null) latestHistoryCommit(currentState.copy(annotations = currentState.annotations + annotation))
                                    draftAnnotation = null
                                }
                            )
                        }
                        TextButton(
                            onClick = { showOriginal = !showOriginal },
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .92f), RoundedCornerShape(16.dp))
                        ) { Text(if (showOriginal) "Show edits" else "Before") }
                    } else if (source == null && sourceLoad.error == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(10.dp))
                            Text("Loading image…", color = MaterialTheme.colorScheme.onBackground)
                        }
                    } else if (source == null) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(34.dp))
                            Text("This image could not be opened for editing.", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
                            Text(sourceLoad.error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                if (previewMaxDimension > 1024) previewMaxDimension = 1024 else previewRetryToken++
                            }) { Text(if (previewMaxDimension > 1024) "Retry at lower resolution" else "Retry") }
                        }
                    } else if (previewResult.loading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator()
                            Text("Rendering preview…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (preview == null) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(34.dp))
                            Text(previewResult.error ?: "The image preview is unavailable.", color = MaterialTheme.colorScheme.onBackground)
                            OutlinedButton(onClick = {
                                if (previewMaxDimension > 1024) previewMaxDimension = 1024 else previewRetryToken++
                            }) { Text(if (previewMaxDimension > 1024) "Retry at lower resolution" else "Retry") }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorTool("Crop", Icons.Default.Crop, selectedTool == "Crop") { selectedTool = "Crop" }
                    EditorTool("Rotate", Icons.Default.RotateRight, selectedTool == "Rotate") { selectedTool = "Rotate" }
                    EditorTool("Adjust", Icons.Default.Tune, selectedTool == "Adjust") { selectedTool = "Adjust" }
                    EditorTool("Filters", Icons.Default.Palette, selectedTool == "Filters") { selectedTool = "Filters" }
                    EditorTool("Markup", Icons.Default.Draw, selectedTool == "Markup") { selectedTool = "Markup" }
                    EditorTool("Info", Icons.Default.Info, selectedTool == "Info") { selectedTool = "Info" }
                }

                Column(
                    Modifier.fillMaxWidth().heightIn(min = 116.dp, max = 220.dp).padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
                        .verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (selectedTool) {
                        "Crop" -> {
                            Text("Crop to aspect ratio", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Free", "1:1", "4:3", "3:2", "16:9", "9:16").forEach { ratio ->
                                    FilterChip(selected = currentState.crop == ratio, onClick = { commit(currentState.copy(crop = ratio)) }, label = { Text(ratio) })
                                }
                            }
                            Text("Aspect crops are centered. Choose Free to keep the full image.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        "Rotate" -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { commit(currentState.copy(rotation = (currentState.rotation - 90 + 360) % 360)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.RotateLeft, null); Spacer(Modifier.width(4.dp)); Text("Left") }
                                OutlinedButton(onClick = { commit(currentState.copy(rotation = (currentState.rotation + 90) % 360)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(4.dp)); Text("Right") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(currentState.flipHorizontal, { commit(currentState.copy(flipHorizontal = !currentState.flipHorizontal)) }, label = { Text("Flip horizontal") })
                                FilterChip(currentState.flipVertical, { commit(currentState.copy(flipVertical = !currentState.flipVertical)) }, label = { Text("Flip vertical") })
                            }
                        }
                        "Adjust" -> {
                            EditorSlider("Brightness", currentState.brightness, -60f..60f, "${currentState.brightness.toInt()}", ::beginSliderEdit, ::commitContinuous, { value -> currentState.copy(brightness = value) }, ::finishSliderEdit)
                            EditorSlider("Contrast", currentState.contrast, .5f..1.8f, "${(currentState.contrast * 100).toInt()}%", ::beginSliderEdit, ::commitContinuous, { value -> currentState.copy(contrast = value) }, ::finishSliderEdit)
                            EditorSlider("Saturation", currentState.saturation, 0f..2f, "${(currentState.saturation * 100).toInt()}%", ::beginSliderEdit, ::commitContinuous, { value -> currentState.copy(saturation = value) }, ::finishSliderEdit)
                            EditorSlider("Warmth", currentState.warmth, -1f..1f, "${(currentState.warmth * 100).toInt()}", ::beginSliderEdit, ::commitContinuous, { value -> currentState.copy(warmth = value) }, ::finishSliderEdit)
                            TextButton(onClick = { commit(currentState.copy(brightness = 0f, contrast = 1f, saturation = 1f, warmth = 0f)) }) { Text("Reset adjustments") }
                        }
                        "Filters" -> {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("Original", "Warm", "Cool", "B&W", "Vintage", "Cinematic").forEach { filter ->
                                    FilterChip(currentState.filter == filter, { commit(currentState.copy(filter = filter)) }, label = { Text(filter) })
                                }
                            }
                            EditorSlider("Filter strength", currentState.filterIntensity, 0f..1f, "${(currentState.filterIntensity * 100).toInt()}%", ::beginSliderEdit, ::commitContinuous, { value -> currentState.copy(filterIntensity = value) }, ::finishSliderEdit)
                        }
                        "Markup" -> {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(markupTool == "Pen", { markupTool = "Pen" }, label = { Text("Pen") })
                                FilterChip(markupTool == "Highlighter", { markupTool = "Highlighter" }, label = { Text("Highlighter") })
                                FilterChip(markupTool == "Arrow", { markupTool = "Arrow" }, label = { Text("Arrow") })
                                OutlinedButton(onClick = { textDialog = true }) { Icon(Icons.Default.TextFields, null); Spacer(Modifier.width(4.dp)); Text("Add text") }
                                OutlinedButton(onClick = {
                                    if (currentState.annotations.isNotEmpty()) commit(currentState.copy(annotations = currentState.annotations.dropLast(1)))
                                }, enabled = currentState.annotations.isNotEmpty()) { Icon(Icons.Default.DeleteOutline, "Remove last markup") }
                            }
                            Text("${markupTool} color", style = MaterialTheme.typography.labelLarge)
                            MarkupColorPicker(markupColor) { markupColor = it }
                            if (markupTool == "Highlighter") {
                                Text("Highlighter transparency · ${(highlighterTransparency * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                                Slider(value = highlighterTransparency, onValueChange = { highlighterTransparency = it }, valueRange = .1f..0.9f, steps = 15)
                            }
                            Text("Draw with the pen, broad highlighter, or arrow. Add text and choose its color here. Markup stays editable until Save.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text("Image information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${source?.width ?: "—"} × ${source?.height ?: "—"} px · ${mimeType.ifBlank { "image" }}", style = MaterialTheme.typography.bodyMedium)
                            Text("The original file is preserved. Save creates a new edited image in Pictures/ENotes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { commit(ImageEditState()) }, enabled = currentState != ImageEditState()) { Text("Reset all edits") }
                        }
                    }
                }
            }
        }
    }

    if (busy) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Saving edited image…")
            }
        }
    }
    if (discardConfirmation) AlertDialog(
        onDismissRequest = { discardConfirmation = false },
        title = { Text("Discard image edits?") },
        text = { Text("Your unsaved changes will be discarded. The original image stays unchanged.") },
        confirmButton = { TextButton(onClick = { discardConfirmation = false; onDismiss() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("Keep editing") } }
    )
    if (textDialog) AlertDialog(
        onDismissRequest = { textDialog = false },
        title = { Text("Add text annotation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(annotationText, { annotationText = it }, label = { Text("Text") }, singleLine = true)
                Text("Text color", style = MaterialTheme.typography.labelLarge)
                MarkupColorPicker(markupColor) { markupColor = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cleanText = annotationText.trim()
                if (cleanText.isNotEmpty()) commit(currentState.copy(annotations = currentState.annotations + createImageAnnotation("Text", listOf(ImagePoint(.5f, .5f)), markupColor, cleanText)))
                annotationText = ""; textDialog = false
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { textDialog = false }) { Text("Cancel") } }
    )
    saveError?.let { message -> AlertDialog(onDismissRequest = { saveError = null }, title = { Text("Could not save image") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { saveError = null }) { Text("OK") } }) }
}

@Composable
private fun EditorTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MarkupColorPicker(selectedColor: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        imageMarkupColors.forEachIndexed { index, color ->
            val selected = color == selectedColor
            Box(
                Modifier.size(if (selected) 34.dp else 29.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onSelect(color) },
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Default.Check, "Selected markup color ${index + 1}", tint = if (color == 0xFFFFFFFF.toInt() || color == 0xFFFDD835.toInt()) Color.Black else Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onStart: () -> Unit,
    onChange: (ImageEditState) -> Unit,
    toState: (Float) -> ImageEditState,
    onFinish: () -> Unit
) {
    val latestState by rememberUpdatedState(toState)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value.coerceIn(range), onValueChange = { onStart(); onChange(latestState(it)) }, valueRange = range, onValueChangeFinished = onFinish)
    }
}

private data class CanvasImageBounds(val left: Float, val top: Float, val width: Float, val height: Float)

private fun imageBounds(canvasWidth: Float, canvasHeight: Float, imageWidth: Int, imageHeight: Int): CanvasImageBounds {
    if (canvasWidth <= 0f || canvasHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) return CanvasImageBounds(0f, 0f, 0f, 0f)
    val factor = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
    val width = imageWidth * factor
    val height = imageHeight * factor
    return CanvasImageBounds((canvasWidth - width) / 2f, (canvasHeight - height) / 2f, width, height)
}

@Composable
private fun ImageMarkupOverlay(
    bitmap: Bitmap?,
    annotations: List<ImageAnnotation>,
    draft: ImageAnnotation?,
    activeTool: String,
    activeColor: Int,
    highlighterOpacity: Float,
    onDraftChange: (ImageAnnotation?) -> Unit,
    onDraftFinish: (ImageAnnotation?) -> Unit
) {
    var canvasSize by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    val latestDraftChange by rememberUpdatedState(onDraftChange)
    val latestDraftFinish by rememberUpdatedState(onDraftFinish)
    Canvas(
        Modifier.fillMaxSize().onSizeChanged { canvasSize = it }
            .pointerInput(activeTool, activeColor, highlighterOpacity, bitmap, canvasSize) {
                if (activeTool != "Pen" && activeTool != "Highlighter" && activeTool != "Arrow") return@pointerInput
                var points = emptyList<ImagePoint>()
                var working: ImageAnnotation? = null
                detectDragGestures(
                    onDragStart = { position ->
                        val bounds = bitmap?.let { imageBounds(canvasSize.width.toFloat(), canvasSize.height.toFloat(), it.width, it.height) }
                        val normalized = bounds?.let { normalizePoint(position, it) }
                        points = normalized?.let(::listOf).orEmpty()
                        working = if (points.isNotEmpty()) createImageAnnotation(activeTool, points, activeColor, highlighterOpacity = highlighterOpacity) else null
                        latestDraftChange(working)
                    },
                    onDrag = { change: PointerInputChange, _ ->
                        change.consume()
                        val bounds = bitmap?.let { imageBounds(canvasSize.width.toFloat(), canvasSize.height.toFloat(), it.width, it.height) }
                        val normalized = bounds?.let { normalizePoint(change.position, it) }
                        if (normalized != null && points.isNotEmpty()) {
                            points = if (activeTool == "Arrow") listOf(points.first(), normalized) else points + normalized
                            working = createImageAnnotation(activeTool, points, activeColor, highlighterOpacity = highlighterOpacity)
                            latestDraftChange(working)
                        }
                    },
                    onDragEnd = { latestDraftFinish(working) },
                    onDragCancel = { latestDraftChange(null) }
                )
            }
    ) {
        val target = bitmap ?: return@Canvas
        val bounds = imageBounds(size.width, size.height, target.width, target.height)
        (annotations + listOfNotNull(draft)).forEach { annotation -> drawMarkup(annotation, bounds) }
    }
}

private fun normalizePoint(position: Offset, bounds: CanvasImageBounds): ImagePoint? {
    if (bounds.width <= 0f || bounds.height <= 0f || position.x !in bounds.left..(bounds.left + bounds.width) || position.y !in bounds.top..(bounds.top + bounds.height)) return null
    return ImagePoint(((position.x - bounds.left) / bounds.width).coerceIn(0f, 1f), ((position.y - bounds.top) / bounds.height).coerceIn(0f, 1f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarkup(annotation: ImageAnnotation, bounds: CanvasImageBounds) {
    val points = annotation.points
    if (points.isEmpty()) return
    val color = Color(annotation.color)
    val stroke = (annotation.width * min(bounds.width, bounds.height)).coerceAtLeast(2f)
    fun x(point: ImagePoint) = bounds.left + point.x * bounds.width
    fun y(point: ImagePoint) = bounds.top + point.y * bounds.height
    when (annotation.kind) {
        "pen", "highlighter" -> {
            if (points.size == 1) drawCircle(color, stroke / 2f, Offset(x(points[0]), y(points[0])))
            else {
                val path = Path().apply { moveTo(x(points.first()), y(points.first())); points.drop(1).forEach { lineTo(x(it), y(it)) } }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        "arrow" -> if (points.size >= 2) {
            val start = Offset(x(points.first()), y(points.first()))
            val end = Offset(x(points.last()), y(points.last()))
            drawLine(color, start, end, stroke, cap = StrokeCap.Round)
            val angle = atan2(end.y - start.y, end.x - start.x)
            val head = stroke * 4f
            val left = Offset(end.x - head * cos(angle - .55f), end.y - head * sin(angle - .55f))
            val right = Offset(end.x - head * cos(angle + .55f), end.y - head * sin(angle + .55f))
            drawLine(color, end, left, stroke, cap = StrokeCap.Round)
            drawLine(color, end, right, stroke, cap = StrokeCap.Round)
        }
        "text" -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = annotation.color; textSize = min(bounds.width, bounds.height) * .07f; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
            drawContext.canvas.nativeCanvas.drawText(annotation.text, x(points.first()), y(points.first()), paint)
        }
    }
}

private data class ImageLoadResult(val bitmap: Bitmap?, val error: String?, val owned: Boolean)
private data class ImagePreviewResult(val bitmap: Bitmap?, val error: String?, val loading: Boolean)

private fun imageLoadErrorMessage(error: Exception): String = when (error) {
    is SecurityException -> "Access to this image has expired. Select the image again and retry."
    is FileNotFoundException -> "This image is no longer available. Select it again and retry."
    is IOException -> error.message?.takeIf { it.startsWith("This image") || it == "The source image is unavailable." }
        ?: "This image is damaged or could not be read."
    is IllegalArgumentException -> "This image format is not supported or the file is damaged."
    else -> "This image could not be decoded by this device."
}

internal fun logImageFailure(
    stage: String,
    mime: String,
    error: Throwable,
    width: Int? = null,
    height: Int? = null,
    uri: Uri? = null,
    maxDimension: Int? = null
) {
    val rawDetails = error.message.orEmpty()
    val uriText = uri?.toString()?.takeIf { it.isNotEmpty() }
    val details = (if (uriText == null) rawDetails else rawDetails.replace(uriText, "<content-uri>")).take(240)
    Log.e(
        IMAGE_EDITOR_LOG_TAG,
        "Image operation failed stage=$stage width=${width ?: "unknown"} height=${height ?: "unknown"} " +
            "maxDimension=${maxDimension ?: "n/a"} mime=$mime error=${error.javaClass.simpleName} details=$details"
    )
}

internal fun decodeBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap {
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "unknown"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val originalWidth = info.size.width
            val originalHeight = info.size.height
            Log.d(IMAGE_EDITOR_LOG_TAG, "Image dimensions stage=decoder-bounds width=$originalWidth height=$originalHeight maxDimension=$maxDimension mime=$mime")
            val target = fitImageWithinMaxDimension(originalWidth, originalHeight, maxDimension)
                ?: throw IOException("This image has invalid dimensions.")
            decoder.setTargetSize(target.width, target.height)
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("The source image is unavailable.")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("This image is damaged or its format is not supported on this Android version.")
    Log.d(IMAGE_EDITOR_LOG_TAG, "Image dimensions stage=decoder-bounds width=${bounds.outWidth} height=${bounds.outHeight} maxDimension=$maxDimension mime=$mime")
    val sample = imageDecodeSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        })
    } ?: throw FileNotFoundException("The source image is unavailable.")
    if (decoded.width <= 0 || decoded.height <= 0 || decoded.isRecycled) {
        if (!decoded.isRecycled) decoded.recycle()
        throw IOException("This image is damaged or its format is not supported on this Android version.")
    }
    val orientation = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (error: Exception) {
        logImageFailure("exif-orientation", mime, error, width = bounds.outWidth, height = bounds.outHeight, uri = uri)
        ExifInterface.ORIENTATION_NORMAL
    }
    return applyExifOrientation(decoded, orientation)
}

private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val transform = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> transform.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { transform.setRotate(90f); transform.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> transform.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { transform.setRotate(270f); transform.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> transform.setRotate(270f)
        else -> return bitmap
    }
    return try {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true).also { oriented ->
            if (oriented !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        }
    } catch (error: Exception) {
        if (!bitmap.isRecycled) bitmap.recycle()
        throw error
    } catch (error: OutOfMemoryError) {
        if (!bitmap.isRecycled) bitmap.recycle()
        throw error
    }
}

private fun renderEditedBitmap(source: Bitmap, state: ImageEditState): Bitmap {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) throw IOException("The image preview is no longer available.")
    val transform = Matrix().apply {
        if (state.rotation % 360 != 0) postRotate(state.rotation.toFloat())
        if (state.flipHorizontal || state.flipVertical) postScale(if (state.flipHorizontal) -1f else 1f, if (state.flipVertical) -1f else 1f)
    }
    var transformed = if (state.rotation % 360 != 0 || state.flipHorizontal || state.flipVertical) {
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, transform, true)
    } else source
    try {
        val crop = cropBounds(transformed.width, transformed.height, cropAspect(state.crop))
        if (crop.left != 0 || crop.top != 0 || crop.right != transformed.width || crop.bottom != transformed.height) {
            val cropped = Bitmap.createBitmap(transformed, crop.left, crop.top, crop.width, crop.height)
            if (transformed !== source && transformed !== cropped && !transformed.isRecycled) transformed.recycle()
            transformed = cropped
        }
        val output = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
        var keepOutput = false
        try {
            val canvas = android.graphics.Canvas(output)
            val matrix = adjustedColorMatrix(state)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
            canvas.drawBitmap(transformed, 0f, 0f, paint)
            renderAnnotations(canvas, output.width, output.height, state.annotations)
            keepOutput = true
            return output
        } finally {
            if (!keepOutput && !output.isRecycled) output.recycle()
        }
    } finally {
        if (transformed !== source && !transformed.isRecycled) transformed.recycle()
    }
}

private fun adjustedColorMatrix(state: ImageEditState): ColorMatrix {
    val strength = state.filterIntensity.coerceIn(0f, 1f)
    val filterSaturation = when (state.filter) { "Vintage" -> .72f; "Cinematic" -> 1.08f; "B&W" -> 0f; else -> 1f }
    val saturation = state.saturation * (1f + (filterSaturation - 1f) * strength)
    val sat = ColorMatrix().apply { setSaturation(saturation.coerceIn(0f, 3f)) }.array
    val filterContrast = when (state.filter) { "Cinematic" -> 1.16f; "Vintage" -> .92f; else -> 1f }
    val contrast = state.contrast * (1f + (filterContrast - 1f) * strength)
    val brightness = state.brightness + when (state.filter) { "Vintage" -> 5f * strength; "Cinematic" -> -3f * strength; else -> 0f }
    val warmth = state.warmth + when (state.filter) { "Warm" -> .45f * strength; "Cool" -> -.45f * strength; "Vintage" -> .2f * strength; else -> 0f }
    for (row in 0..2) {
        val index = row * 5
        for (column in 0..3) sat[index + column] *= contrast
        sat[index + 4] = sat[index + 4] * contrast + (1f - contrast) * 128f + brightness
    }
    sat[4] += 24f * warmth
    sat[9] += 5f * warmth
    sat[14] -= 24f * warmth
    return ColorMatrix(sat)
}

private fun renderAnnotations(canvas: android.graphics.Canvas, width: Int, height: Int, annotations: List<ImageAnnotation>) {
    val shortSide = min(width, height).toFloat()
    annotations.forEach { annotation ->
        val color = annotation.color
        val stroke = (annotation.width * shortSide).coerceAtLeast(3f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val points = annotation.points
        if (points.isEmpty()) return@forEach
        fun px(p: ImagePoint) = p.x.coerceIn(0f, 1f) * width
        fun py(p: ImagePoint) = p.y.coerceIn(0f, 1f) * height
        when (annotation.kind) {
            "pen", "highlighter" -> {
                if (points.size == 1) canvas.drawCircle(px(points[0]), py(points[0]), stroke / 2f, paint)
                else {
                    val path = android.graphics.Path().apply { moveTo(px(points.first()), py(points.first())); points.drop(1).forEach { lineTo(px(it), py(it)) } }
                    canvas.drawPath(path, paint)
                }
            }
            "arrow" -> if (points.size >= 2) {
                val start = points.first(); val end = points.last()
                canvas.drawLine(px(start), py(start), px(end), py(end), paint)
                val angle = atan2(py(end) - py(start), px(end) - px(start))
                val head = stroke * 4f
                canvas.drawLine(px(end), py(end), px(end) - head * cos(angle - .55f), py(end) - head * sin(angle - .55f), paint)
                canvas.drawLine(px(end), py(end), px(end) - head * cos(angle + .55f), py(end) - head * sin(angle + .55f), paint)
            }
            "text" -> {
                paint.style = Paint.Style.FILL
                paint.textSize = shortSide * .07f
                paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                canvas.drawText(annotation.text, px(points.first()), py(points.first()), paint)
            }
        }
    }
}

private fun saveToGallery(context: Context, bitmap: Bitmap, png: Boolean): Uri {
    val mime = if (png) "image/png" else "image/jpeg"
    val extension = if (png) "png" else "jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "ENotes_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension")
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ENotes")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val outputUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("The device did not allow the edited image to be created.")
    try {
        val stream = context.contentResolver.openOutputStream(outputUri, "w") ?: throw IOException("The edited image could not be opened for writing.")
        stream.use {
            val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            if (!bitmap.compress(format, if (png) 100 else 94, it)) throw IOException("The edited image could not be encoded.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.update(outputUri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return outputUri
    } catch (error: Throwable) {
        runCatching { context.contentResolver.delete(outputUri, null, null) }
        throw error
    }
}

