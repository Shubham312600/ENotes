package com.enotes.app

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** A compact Android-native spectrum picker that follows the active app color scheme. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CustomColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val initialHsv = remember(initialColor) { FloatArray(3).also { AndroidColor.colorToHSV(initialColor, it) } }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialColor) { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember(initialColor) { mutableFloatStateOf(AndroidColor.alpha(initialColor) / 255f) }
    var hex by remember(initialColor) { mutableStateOf(initialColor.toHexColor()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val presets = listOf(
        0xFF6639E8.toInt(), 0xFF202033.toInt(), 0xFFE53935.toInt(), 0xFFFF8F00.toInt(),
        0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF7B1FA2.toInt(),
        0xFFFFFFFF.toInt(), 0xFF795548.toInt()
    )
    val generatedColor = Color.hsv(hue, saturation, value, alpha).toArgb()
    val previewColor = hex.toHexArgbOrNull() ?: generatedColor
    val scheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.92f).heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            color = scheme.surface,
            contentColor = scheme.onSurface,
            tonalElevation = 8.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Custom color", style = MaterialTheme.typography.titleLarge)
                        Text("Choose a color for this tool", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(previewColor)).border(2.dp, scheme.outline, CircleShape))
                }

                TabRow(selectedTabIndex = selectedTab, containerColor = scheme.surface) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Spectrum") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Palettes") })
                }

                if (selectedTab == 0) {
                    ColorSpectrum(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onChange = { s, v -> saturation = s; value = v; hex = Color.hsv(hue, s, v, alpha).toArgb().toHexColor() }
                    )
                    Text("Hue · ${hue.roundToInt()}°", style = MaterialTheme.typography.labelLarge)
                    Slider(value = hue, onValueChange = { hue = it; hex = Color.hsv(it, saturation, value, alpha).toArgb().toHexColor() }, valueRange = 0f..360f)
                    Text("Opacity · ${(alpha * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = alpha, onValueChange = { alpha = it; hex = Color.hsv(hue, saturation, value, it).toArgb().toHexColor() }, valueRange = 0f..1f)
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { entered ->
                            hex = entered.take(9)
                            entered.toHexArgbOrNull()?.let { color ->
                                val hsv = FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
                                hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; alpha = AndroidColor.alpha(color) / 255f
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Hex color") },
                        supportingText = { Text("Enter #RRGGBB or #AARRGGBB") }
                    )
                } else {
                    Text("Preset colors", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        presets.chunked(5).forEach { line ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                line.forEach { preset ->
                                    val selected = previewColor == preset
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).background(Color(preset))
                                            .border(if (selected) 3.dp else 1.dp, if (selected) scheme.primary else scheme.outline, CircleShape)
                                            .clickable {
                                                val hsv = FloatArray(3).also { AndroidColor.colorToHSV(preset, it) }
                                                hue = hsv[0]; saturation = hsv[1]; value = hsv[2]; alpha = AndroidColor.alpha(preset) / 255f; hex = preset.toHexColor()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) { if (selected) Icon(Icons.Default.Check, "Selected color", tint = if (AndroidColor.luminance(preset) > .55) Color.Black else Color.White) }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSelect(previewColor); onDismiss() }) { Text("Use color") }
                }
            }
        }
    }
}

@Composable
private fun ColorSpectrum(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit
) {
    val hueColor = Color.hsv(hue, 1f, 1f)
    val latestChange by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp))
            .pointerInput(hue) {
                detectTapGestures { point ->
                    latestChange((point.x / size.width).coerceIn(0f, 1f), (1f - point.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(hue) {
                detectDragGestures(onDragStart = { point ->
                    latestChange((point.x / size.width).coerceIn(0f, 1f), (1f - point.y / size.height).coerceIn(0f, 1f))
                }, onDrag = { change: PointerInputChange, _ ->
                    change.consume()
                    latestChange((change.position.x / size.width).coerceIn(0f, 1f), (1f - change.position.y / size.height).coerceIn(0f, 1f))
                })
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val marker = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 10.dp.toPx(), center = marker, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = .5f), radius = 12.dp.toPx(), center = marker, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}

private fun Int.toHexColor(): String = "#%08X".format(this)

private fun String.toHexArgbOrNull(): Int? = runCatching {
    val digits = trim().removePrefix("#")
    when (digits.length) {
        6 -> (0xFF000000L or digits.toLong(16)).toInt()
        8 -> digits.toLong(16).toInt()
        else -> null
    }
}.getOrNull()
