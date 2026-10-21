package com.enotes.app

import kotlin.math.max

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

internal data class ImageEditState(
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: String = "Free",
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
