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
}

