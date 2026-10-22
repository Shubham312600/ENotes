package com.enotes.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

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
    fun selectedNoteFormattingCanBeCombinedAndClearedByRange() {
        val bold = listOf(NoteTextStyleRange(0, 5, bold = true))
        val combined = applyNoteTextStyle(5, bold, 2, 4) { it.copy(italic = true) }
        assertEquals(
            listOf(
                NoteTextStyleRange(0, 2, bold = true),
                NoteTextStyleRange(2, 4, bold = true, italic = true),
                NoteTextStyleRange(4, 5, bold = true)
            ),
            combined
        )
        assertEquals(
            listOf(NoteTextStyleRange(0, 2, bold = true), NoteTextStyleRange(4, 5, bold = true)),
            applyNoteTextStyle(5, combined, 2, 4) { NoteTextStyleRange(it.start, it.end) }
        )
    }

    @Test
    fun selectedTextCanCarrySizeColorAndHighlightAndClearOnlyThatSelection() {
        val styled = applyNoteTextStyle(10, emptyList(), 2, 7) {
            it.copy(fontSizeSp = 24f, textColor = 0xFF1976D2.toInt(), highlightColor = 0xFFFFF176.toInt())
        }

        assertEquals(listOf(NoteTextStyleRange(2, 7, fontSizeSp = 24f, textColor = 0xFF1976D2.toInt(), highlightColor = 0xFFFFF176.toInt())), styled)
        assertEquals(emptyList<NoteTextStyleRange>(), applyNoteTextStyle(10, styled, 2, 7) { NoteTextStyleRange(it.start, it.end) })
    }

    @Test
    fun noteTextStylesSurvivePlainEditorCallbacksAndTrackNearbyTextEdits() {
        val styles = listOf(NoteTextStyleRange(0, 5, bold = true, textColor = 0xFF1976D2.toInt()))

        assertEquals(styles, remapNoteTextStylesForTextEdit("Hello! world", "Hello! world", styles))
        assertEquals(
            listOf(NoteTextStyleRange(0, 6, bold = true, textColor = 0xFF1976D2.toInt())),
            remapNoteTextStylesForTextEdit("Hello world", "Hello! world", styles)
        )
        assertEquals(
            listOf(NoteTextStyleRange(10, 15, bold = true, textColor = 0xFF1976D2.toInt())),
            remapNoteTextStylesForTextEdit("Hello world", "New Hello world", listOf(NoteTextStyleRange(6, 11, bold = true, textColor = 0xFF1976D2.toInt())))
        )
    }

    @Test
    fun deletingCharactersAndWholeFormattedWordsRemapsOnlyRemainingText() {
        val style = NoteTextStyleRange(0, 9, bold = true, textColor = 0xFF1976D2.toInt())
        assertEquals(
            listOf(style.copy(end = 8)),
            remapNoteTextStylesForTextEdit("formatted", "formtted", listOf(style))
        )
        assertEquals(
            emptyList<NoteTextStyleRange>(),
            remapNoteTextStylesForTextEdit("bold word here", " here", listOf(NoteTextStyleRange(0, 9, bold = true)))
        )
    }

    @Test
    fun formattedDeletionKeepsTheImeCompositionAndDeletesOnlyTheRequestedCharacter() {
        val boldHello = listOf(NoteTextStyleRange(0, 5, bold = true))
        val formatted = restyleNoteBodyValue(TextFieldValue("Hello world", TextRange(11)), boldHello)
        val afterOneBackspace = TextFieldValue("Hello worl", TextRange(10), TextRange(6, 10))
        val restyled = restyleNoteBodyValue(afterOneBackspace, boldHello)

        assertEquals("Hello world", formatted.text)
        assertEquals("Hello worl", restyled.text)
        assertEquals(TextRange(10), restyled.selection)
        assertEquals(TextRange(6, 10), restyled.composition)
        assertTrue(restyled.annotatedString.spanStyles.any { it.start == 0 && it.end == 5 })

        val underlineTheRest = applyNoteTextStyle(restyled.text.length, boldHello, 6, 10) { it.copy(underline = true) }
        val afterSecondBackspace = TextFieldValue("Hello wor", TextRange(9), TextRange(6, 9))
        val remappedStyles = remapNoteTextStylesForTextEdit(restyled.text, afterSecondBackspace.text, underlineTheRest)
        val restyledAgain = restyleNoteBodyValue(afterSecondBackspace, remappedStyles)

        assertEquals("Hello wor", restyledAgain.text)
        assertEquals(TextRange(9), restyledAgain.selection)
        assertEquals(TextRange(6, 9), restyledAgain.composition)
        assertTrue(restyledAgain.annotatedString.spanStyles.any { it.start == 0 && it.end == 5 && it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
        assertTrue(restyledAgain.annotatedString.spanStyles.any { it.start == 6 && it.end == 9 && it.item.textDecoration?.contains(androidx.compose.ui.text.style.TextDecoration.Underline) == true })
    }

    @Test
    fun attachmentMimeTypeUsesProviderTypeAndFallsBackToKnownExtensions() {
        assertEquals("application/pdf", attachmentMimeType("content://docs/report.pdf", "application/pdf"))
        assertEquals("image/jpeg", attachmentMimeType("content://docs/photo.jpg", "application/octet-stream"))
        assertEquals("audio/mp4", attachmentMimeType("content://docs/voice.m4a", null))
        assertEquals("application/octet-stream", attachmentMimeType("content://docs/archive.unknown", null))
    }

    @Test
    fun numberedListPrefixesEachSelectedLineAndMovesExistingStyleRanges() {
        val edit = insertChecklistPrefix(
            text = "one\ntwo\nthree",
            selectionStart = 0,
            selectionEnd = 13,
            numbered = true,
            styles = listOf(NoteTextStyleRange(0, 3, bold = true))
        )

        assertEquals("1. one\n2. two\n3. three", edit.text)
        assertEquals(22, edit.selection)
        assertEquals(listOf(NoteTextStyleRange(3, 6, bold = true)), edit.styleRanges)
    }

    @Test
    fun numberedListContinuesFromPreviousLineAndReplacesExistingMarkers() {
        val continued = insertChecklistPrefix("1. first\nsecond", 9, 9, numbered = true)
        assertEquals("1. first\n2. second", continued.text)

        val renumbered = insertChecklistPrefix("1. first\n1. second\n• third", 0, 25, numbered = true)
        assertEquals("1. first\n2. second\n3. third", renumbered.text)
    }

    @Test
    fun pressingEnterContinuesNumberedListAndKeepsFormattingOffsets() {
        val result = continueNumberedListOnNewline(
            oldText = "1. first",
            newText = "1. first\n",
            cursor = 9,
            styles = listOf(NoteTextStyleRange(3, 8, bold = true))
        )

        assertEquals(ChecklistPrefixEdit("1. first\n2. ", 12, listOf(NoteTextStyleRange(3, 8, bold = true))), result)
    }

    @Test
    fun checklistRowsCanBeCheckedUncheckedAndCleared() {
        val tasks = listOf(
            ChecklistTask(id = "one", text = "First"),
            ChecklistTask(id = "two", text = "Second", checked = true)
        )

        assertEquals(tasks[0].copy(checked = true), setChecklistTaskChecked(tasks, "one", true).first())
        assertEquals(tasks[1].copy(checked = false), setChecklistTaskChecked(tasks, "two", false).last())
        assertEquals(listOf(tasks[0]), clearCompletedChecklistTasks(tasks))
        assertEquals(tasks, setChecklistTaskChecked(tasks, "missing", true))
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
    fun photoMarkupToolsKeepTheirChosenColorAndHighlighterIsTranslucentAndWide() {
        val points = listOf(ImagePoint(.1f, .2f), ImagePoint(.8f, .7f))
        val blue = 0xFF1E88E5.toInt()

        assertEquals(ImageAnnotation("pen", points, color = blue), createImageAnnotation("Pen", points, blue))
        assertEquals(ImageAnnotation("arrow", points, color = blue), createImageAnnotation("Arrow", points, blue))
        assertEquals(ImageAnnotation("highlighter", points, color = 0x661E88E5.toInt(), width = .045f), createImageAnnotation("Highlighter", points, blue))
        assertEquals(0x331E88E5.toInt(), createImageAnnotation("Highlighter", points, blue, highlighterOpacity = .2f).color)
        assertEquals(0xCC1E88E5.toInt(), createImageAnnotation("Highlighter", points, blue, highlighterOpacity = .8f).color)
        assertEquals(ImageAnnotation("text", listOf(ImagePoint(.5f, .5f)), text = "Marked", color = blue, width = .07f), createImageAnnotation("Text", listOf(ImagePoint(.5f, .5f)), blue, "Marked"))
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
    fun freeCropEdgesStayInsideImageAndMapToSourcePixels() {
        val initial = NormalizedCropRect(.1f, .2f, .9f, .8f)
        assertEquals(
            NormalizedCropRect(.3f, .2f, .9f, .8f),
            moveFreeCropEdges(initial, setOf(CropEdge.Left), .2f, 0f)
        )
        assertEquals(
            NormalizedCropRect(0f, 0f, .9f, .8f),
            moveFreeCropEdges(initial, setOf(CropEdge.Left, CropEdge.Top), -1f, -1f)
        )
        assertEquals(PixelRect(10, 20, 60, 60), normalizedCropBounds(100, 80, NormalizedCropRect(.1f, .25f, .6f, .75f)))
    }

    @Test
    fun sheetCsvRoundTripsCommasQuotesAndLineBreaks() {
        val rows = listOf(
            listOf("Name", "Notes"),
            listOf("A, B", "She said \"hello\""),
            listOf("multi\nline", "plain"),
            listOf("", "")
        )
        assertEquals(rows, parseSheetCsv(encodeSheetCsv(rows)))
        assertEquals("Plan _ Q4.csv", sheetCsvFilename("Plan / Q4"))
    }

    @Test
    fun imagePreviewSizingPreservesAspectAndBoundsLargeSources() {
        assertEquals(ImageDimensions(2048, 1365), fitImageWithinMaxDimension(12000, 8000, 2048))
        assertEquals(ImageDimensions(2048, 9), fitImageWithinMaxDimension(20000, 90, 2048))
        assertEquals(ImageDimensions(640, 480), fitImageWithinMaxDimension(640, 480, 2048))
        assertNull(fitImageWithinMaxDimension(0, 10, 2048))
    }

    @Test
    fun legacyImageDecoderSamplingHandlesLargeAndExtremeDimensions() {
        assertEquals(8, imageDecodeSampleSize(12000, 8000, 2048))
        assertEquals(16, imageDecodeSampleSize(20000, 90, 2048))
        assertEquals(1, imageDecodeSampleSize(640, 480, 2048))
        assertTrue(imageDecodeSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 2048) > 0)
    }

    @Test
    fun untouchedPhotoUsesItsDecodedSourceAsPreviewWithoutAllocatingAnotherBitmap() {
        assertEquals(false, needsImagePreviewRender(ImageEditState()))
        assertEquals(true, needsImagePreviewRender(ImageEditState().copy(rotation = 90)))
        assertEquals(true, needsImagePreviewRender(ImageEditState().copy(brightness = 1f)))
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


