package com.enotes.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardAndEditorTest {
    @Test
    fun movingDashboardItemPreservesEveryEntryAndChangesOrder() {
        assertEquals(listOf("b", "a", "c"), moveDashboardItem(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("a", "c", "b"), moveDashboardItem(listOf("a", "b", "c"), "b", 1))
        assertEquals(listOf("a", "b", "c"), moveDashboardItem(listOf("a", "b", "c"), "missing", 1))
        assertEquals(emptyList<String>(), moveDashboardItem(emptyList(), "missing", 1))
    }

    @Test
    fun formattingWrapsSelectedTextAndKeepsSelectionAroundIt() {
        assertEquals(TextEditResult("A **note** here", 2, 10), wrapTextSelection("A note here", 2, 6, "**", "**"))
    }

    @Test
    fun formattingPlacesCursorInsideEmptyMarkers() {
        assertEquals(TextEditResult("Hello ****", 8, 8), wrapTextSelection("Hello ", 6, 6, "**", "**"))
    }

    @Test
    fun listMarkerStartsAtTheCurrentLineAndKeepsCursorAfterMarker() {
        assertEquals(TextEditResult("First\n- second", 11, 11), prefixCurrentLine("First\nsecond", 9, "- "))
    }

    @Test
    fun videoAspectRatioUsesPortraitLandscapeAndRotationMetadata() {
        assertEquals(9f / 16f, normalizedVideoAspectRatio(1080f, 1920f, 0), 0.001f)
        assertEquals(16f / 9f, normalizedVideoAspectRatio(1080f, 1920f, 90), 0.001f)
        assertEquals(16f / 9f, normalizedVideoAspectRatio(null, 1920f, 0), 0.001f)
    }

    @Test
    fun sheetRowsCanBeSelectedAndDeletedWithoutChangingOtherRows() {
        val rows = listOf(
            listOf("Header 1", "Header 2"),
            listOf("A1", "B1"),
            listOf("A2", "B2"),
            listOf("A3", "B3")
        )

        assertEquals(listOf(rows[0], rows[1], rows[3]), deleteSheetRow(rows, 2))
        assertEquals(rows.drop(1), deleteSheetRow(rows, -1))
    }

    @Test
    fun sheetKeepsAtLeastOneRowAndNewRowsMatchCurrentColumnCount() {
        val rows = listOf(listOf("Header 1", "Header 2"))

        assertEquals(rows, deleteSheetRow(rows, 0))
        assertEquals(rows + listOf(listOf("", "")), appendSheetRow(rows))
    }

    @Test
    fun viewerPanIsLimitedToTheScaledImageBounds() {
        val viewport = PanSize(100f, 200f)
        val image = PanSize(400f, 200f)

        assertEquals(PanOffset(100f, 0f), clampViewerPan(PanOffset(500f, 30f), viewport, image, 3f))
        assertEquals(PanOffset(-100f, 0f), clampViewerPan(PanOffset(-500f, -30f), viewport, image, 3f))
        assertEquals(PanOffset(0f, 0f), clampViewerPan(PanOffset(12f, -5f), viewport, image, 1f))
    }

    @Test
    fun imageCropPresetsKeepTheirAspectAndStayInsideTheSource() {
        val square = cropBounds(1600, 900, cropAspect("1:1"))
        assertEquals(900, square.width)
        assertEquals(900, square.height)
        assertEquals(350, square.left)
        assertEquals(PixelRect(0, 0, 1600, 900), cropBounds(1600, 900, null))

        val portrait = cropBounds(1600, 900, cropAspect("9:16"))
        assertEquals(506, portrait.width)
        assertEquals(900, portrait.height)
        assertEquals(547, portrait.left)
        assertEquals(9f / 16f, portrait.width.toFloat() / portrait.height, .002f)
    }

    @Test
    fun imageEditHistoryUndoesAndRedoesWholeSettings() {
        val initial = ImageEditHistory()
        val adjusted = recordImageEdit(initial, initial.current.copy(brightness = 18f))
        val rotated = recordImageEdit(adjusted, adjusted.current.copy(rotation = 90))

        assertEquals(90, rotated.current.rotation)
        assertEquals(18f, undoImageEdit(rotated).current.brightness)
        assertEquals(90, redoImageEdit(undoImageEdit(rotated)).current.rotation)
        assertEquals(initial, recordImageEdit(initial, initial.current))
    }
}

