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
}

