package com.enotes.app

import kotlin.math.max
import kotlin.math.roundToInt

internal data class ImageDimensions(val width: Int, val height: Int)

/** Returns dimensions that fit inside [maxDimension] without changing the image's aspect ratio. */
internal fun fitImageWithinMaxDimension(width: Int, height: Int, maxDimension: Int): ImageDimensions? {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return null
    val scale = minOf(1.0, maxDimension.toDouble() / maxOf(width, height))
    return ImageDimensions(
        (width * scale).roundToInt().coerceIn(1, maxDimension),
        (height * scale).roundToInt().coerceIn(1, maxDimension)
    )
}

/** BitmapFactory requires a power-of-two sample size on the Android versions supported here. */
internal fun imageDecodeSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sample = 1
    while (width.toLong() / sample > maxDimension || height.toLong() / sample > maxDimension) {
        if (sample > Int.MAX_VALUE / 2) break
        sample *= 2
    }
    return sample
}

internal fun needsImagePreviewRender(state: ImageEditState): Boolean = state != ImageEditState()

internal data class PanSize(val width: Float, val height: Float)
internal data class PanOffset(val x: Float, val y: Float)
internal data class PanBounds(val x: Float, val y: Float)

internal fun viewerPanBounds(viewport: PanSize, image: PanSize, scale: Float): PanBounds {
    if (viewport.width <= 0f || viewport.height <= 0f || image.width <= 0f || image.height <= 0f) {
        return PanBounds(0f, 0f)
    }
    val fit = minOf(viewport.width / image.width, viewport.height / image.height)
    return PanBounds(
        max((image.width * fit * scale - viewport.width) / 2f, 0f),
        max((image.height * fit * scale - viewport.height) / 2f, 0f)
    )
}

internal fun clampViewerPan(offset: PanOffset, viewport: PanSize, image: PanSize, scale: Float): PanOffset {
    val bounds = viewerPanBounds(viewport, image, scale.coerceAtLeast(1f))
    fun clamp(value: Float, bound: Float) = if (bound <= 0f) 0f else value.coerceIn(-bound, bound)
    return PanOffset(clamp(offset.x, bounds.x), clamp(offset.y, bounds.y))
}

internal data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Normalized free-crop coordinates, independent of preview size and source resolution. */
internal data class NormalizedCropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal val fullImageCropRect = NormalizedCropRect(0f, 0f, 1f, 1f)

internal enum class CropEdge { Left, Top, Right, Bottom }

/** Moves one or more crop edges while keeping a usable rectangle inside the image. */
internal fun moveFreeCropEdges(
    rect: NormalizedCropRect,
    edges: Set<CropEdge>,
    deltaX: Float,
    deltaY: Float,
    minimumSize: Float = .04f
): NormalizedCropRect {
    val minSize = minimumSize.coerceIn(.001f, 1f)
    var left = rect.left.coerceIn(0f, 1f - minSize)
    var top = rect.top.coerceIn(0f, 1f - minSize)
    var right = rect.right.coerceIn(left + minSize, 1f)
    var bottom = rect.bottom.coerceIn(top + minSize, 1f)
    if (CropEdge.Left in edges) left = (left + deltaX).coerceIn(0f, right - minSize)
    if (CropEdge.Right in edges) right = (right + deltaX).coerceIn(left + minSize, 1f)
    if (CropEdge.Top in edges) top = (top + deltaY).coerceIn(0f, bottom - minSize)
    if (CropEdge.Bottom in edges) bottom = (bottom + deltaY).coerceIn(top + minSize, 1f)
    return NormalizedCropRect(left, top, right, bottom)
}

internal fun normalizedCropBounds(width: Int, height: Int, rect: NormalizedCropRect): PixelRect {
    if (width <= 0 || height <= 0) return PixelRect(0, 0, width.coerceAtLeast(0), height.coerceAtLeast(0))
    val left = (rect.left.coerceIn(0f, 1f) * width).roundToInt().coerceIn(0, width - 1)
    val top = (rect.top.coerceIn(0f, 1f) * height).roundToInt().coerceIn(0, height - 1)
    val right = (rect.right.coerceIn(0f, 1f) * width).roundToInt().coerceIn(left + 1, width)
    val bottom = (rect.bottom.coerceIn(0f, 1f) * height).roundToInt().coerceIn(top + 1, height)
    return PixelRect(left, top, right, bottom)
}

internal fun cropBounds(width: Int, height: Int, aspect: Float?): PixelRect {
    if (width <= 0 || height <= 0 || aspect == null || aspect <= 0f || !aspect.isFinite()) {
        return PixelRect(0, 0, width.coerceAtLeast(0), height.coerceAtLeast(0))
    }
    val sourceAspect = width.toFloat() / height
    return if (sourceAspect > aspect) {
        val cropWidth = (height * aspect).toInt().coerceIn(1, width)
        val left = (width - cropWidth) / 2
        PixelRect(left, 0, left + cropWidth, height)
    } else {
        val cropHeight = (width / aspect).toInt().coerceIn(1, height)
        val top = (height - cropHeight) / 2
        PixelRect(0, top, width, top + cropHeight)
    }
}

internal fun cropAspect(value: String): Float? = when (value) {
    "1:1" -> 1f
    "4:3" -> 4f / 3f
    "3:2" -> 3f / 2f
    "16:9" -> 16f / 9f
    "9:16" -> 9f / 16f
    else -> null
}

internal data class ImagePoint(val x: Float, val y: Float)
internal data class ImageAnnotation(
    val kind: String,
    val points: List<ImagePoint>,
    val text: String = "",
    val color: Int = 0xFF6639E8.toInt(),
    val width: Float = 0.008f
)

internal fun createImageAnnotation(
    tool: String,
    points: List<ImagePoint>,
    color: Int,
    text: String = "",
    highlighterOpacity: Float = .4f
): ImageAnnotation = when (tool) {
    "Arrow" -> ImageAnnotation("arrow", points, color = color)
    "Highlighter" -> ImageAnnotation(
        "highlighter",
        points,
        color = (color and 0x00FFFFFF) or ((highlighterOpacity.coerceIn(.08f, .9f) * 255).roundToInt() shl 24),
        width = .045f
    )
    "Text" -> ImageAnnotation("text", points, text = text, color = color, width = .07f)
    else -> ImageAnnotation("pen", points, color = color)
}

internal data class ImageEditState(
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: String = "Free",
    val freeCrop: NormalizedCropRect? = null,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val filter: String = "Original",
    val filterIntensity: Float = 1f,
    val annotations: List<ImageAnnotation> = emptyList()
)

internal data class ImageEditHistory(
    val current: ImageEditState = ImageEditState(),
    val undo: List<ImageEditState> = emptyList(),
    val redo: List<ImageEditState> = emptyList()
)

internal fun recordImageEdit(history: ImageEditHistory, next: ImageEditState): ImageEditHistory {
    if (next == history.current) return history
    return history.copy(current = next, undo = (history.undo + history.current).takeLast(60), redo = emptyList())
}

internal fun undoImageEdit(history: ImageEditHistory): ImageEditHistory {
    val previous = history.undo.lastOrNull() ?: return history
    return history.copy(current = previous, undo = history.undo.dropLast(1), redo = history.redo + history.current)
}

internal fun redoImageEdit(history: ImageEditHistory): ImageEditHistory {
    val next = history.redo.lastOrNull() ?: return history
    return history.copy(current = next, undo = (history.undo + history.current).takeLast(60), redo = history.redo.dropLast(1))
}

