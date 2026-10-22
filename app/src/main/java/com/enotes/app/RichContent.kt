package com.enotes.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A saved content block embedded in a note's editing canvas. */
internal data class RichContentBlock(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val uri: String? = null,
    val mimeType: String = "",
    val title: String = "",
    val description: String = "",
    val rows: List<List<String>> = emptyList(),
    val skin: String = "plain",
    val tasks: List<ChecklistTask> = emptyList(),
    val checklistStyle: String = "square",
    val textStyles: List<NoteTextStyleRange> = emptyList(),
    val textAlignment: String = "start",
    val textLineSpacing: Float = 1.25f
)

internal data class ChecklistTask(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val checked: Boolean = false
)

internal data class NoteTextStyleRange(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val fontSizeSp: Float? = null,
    val textColor: Int? = null,
    val highlightColor: Int? = null
)

internal fun applyNoteTextStyle(
    textLength: Int,
    ranges: List<NoteTextStyleRange>,
    start: Int,
    end: Int,
    transform: (NoteTextStyleRange) -> NoteTextStyleRange
): List<NoteTextStyleRange> {
    val left = start.coerceIn(0, textLength)
    val right = end.coerceIn(0, textLength)
    if (left >= right) return ranges
    val existing = ranges.mapNotNull { range ->
        val from = range.start.coerceIn(0, textLength)
        val to = range.end.coerceIn(from, textLength)
        range.takeIf { from < to }?.copy(start = from, end = to)
    }
    val boundaries = (existing.flatMap { listOf(it.start, it.end) } + listOf(left, right)).distinct().sorted()
    val segments = boundaries.zipWithNext().mapNotNull { (from, to) ->
        if (from >= to) return@mapNotNull null
        val old = existing.firstOrNull { it.start <= from && it.end >= to }
        val base = old?.copy(start = from, end = to) ?: NoteTextStyleRange(from, to)
        val next = if (from >= left && to <= right) transform(base).copy(start = from, end = to) else base
        if (next == NoteTextStyleRange(from, to)) null else next
    }
    return buildList {
        segments.forEach { segment ->
            val previous = lastOrNull()
            if (previous != null && previous.end == segment.start && previous.copy(start = segment.start, end = segment.end) == segment) {
                this[lastIndex] = previous.copy(end = segment.end)
            } else add(segment)
        }
    }
}

/** Keeps saved style ranges anchored to the same text when an editor callback changes the body. */
internal fun remapNoteTextStylesForTextEdit(
    oldText: String,
    newText: String,
    styles: List<NoteTextStyleRange>
): List<NoteTextStyleRange> {
    if (oldText == newText || styles.isEmpty()) return styles

    var editStart = 0
    val sharedLength = minOf(oldText.length, newText.length)
    while (editStart < sharedLength && oldText[editStart] == newText[editStart]) editStart++

    var oldEditEnd = oldText.length
    var newEditEnd = newText.length
    while (oldEditEnd > editStart && newEditEnd > editStart && oldText[oldEditEnd - 1] == newText[newEditEnd - 1]) {
        oldEditEnd--
        newEditEnd--
    }

    val insertedLength = newEditEnd - editStart
    val delta = insertedLength - (oldEditEnd - editStart)
    val inherited = styles.firstOrNull { it.start <= editStart && it.end > editStart }
        ?: styles.firstOrNull { it.start < oldEditEnd && it.end > editStart }
        ?: styles.firstOrNull { it.start < editStart && it.end >= editStart }
    val result = buildList {
        styles.forEach { style ->
            val start = style.start.coerceIn(0, oldText.length)
            val end = style.end.coerceIn(start, oldText.length)
            if (start >= end) return@forEach
            if (start < editStart) {
                val leftEnd = minOf(end, editStart)
                if (start < leftEnd) add(style.copy(start = start, end = leftEnd))
            }
            if (end > oldEditEnd) {
                val rightStart = maxOf(start, oldEditEnd) + delta
                val rightEnd = end + delta
                if (rightStart < rightEnd) add(style.copy(start = rightStart, end = rightEnd))
            }
        }
        if (insertedLength > 0 && inherited != null) {
            add(inherited.copy(start = editStart, end = newEditEnd))
        }
    }.sortedBy { it.start }

    return buildList {
        result.forEach { range ->
            val previous = lastOrNull()
            if (previous != null && previous.end == range.start && previous.copy(start = range.start, end = range.end) == range) {
                this[lastIndex] = previous.copy(end = range.end)
            } else add(range)
        }
    }
}

internal data class ChecklistPrefixEdit(
    val text: String,
    val selection: Int,
    val styleRanges: List<NoteTextStyleRange>
)

private data class ChecklistPrefixChange(val start: Int, val oldLength: Int, val replacement: String)

internal fun insertChecklistPrefix(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    numbered: Boolean,
    styles: List<NoteTextStyleRange> = emptyList()
): ChecklistPrefixEdit {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val firstLine = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (start == 0) 0 else it + 1 }
    val lastSelection = if (end > start) (end - 1).coerceAtLeast(start) else start
    val finalLineEnd = text.indexOf('\n', lastSelection).let { if (it < 0) text.length else it }
    val lines = buildList {
        add(firstLine)
        var cursor = text.indexOf('\n', firstLine)
        while (cursor >= 0 && cursor < finalLineEnd) {
            val nextStart = cursor + 1
            if (nextStart <= finalLineEnd) add(nextStart)
            cursor = text.indexOf('\n', nextStart)
        }
    }
    val previousLineNumber = if (firstLine == 0) null else {
        val previousEnd = firstLine - 1
        val previousStart = text.lastIndexOf('\n', (previousEnd - 1).coerceAtLeast(0)).let { if (previousEnd == 0 || it < 0) 0 else it + 1 }
        Regex("^[\\t ]*(\\d+)[.)][\\t ]+").find(text.substring(previousStart, previousEnd))?.groupValues?.get(1)?.toIntOrNull()
    }
    var nextNumber = (previousLineNumber ?: 0) + 1
    val edits = lines.map { line ->
        val lineEnd = text.indexOf('\n', line).let { if (it < 0) text.length else it }
        val value = text.substring(line, lineEnd)
        val indentation = value.takeWhile { it == ' ' || it == '\t' }
        val oldMarker = Regex("^(?:\\d+[.)]|[•*-])(?:[\\t ]+)?").find(value.drop(indentation.length))?.value.orEmpty()
        val replacement = if (numbered) "${nextNumber++}. " else "• "
        ChecklistPrefixChange(line + indentation.length, oldMarker.length, replacement)
    }
    val result = StringBuilder(text)
    edits.asReversed().forEach { edit ->
        result.replace(edit.start, edit.start + edit.oldLength, edit.replacement)
    }
    fun shiftedOffset(offset: Int, afterInsertAtSamePosition: Boolean): Int {
        var delta = 0
        edits.forEach { edit ->
            val oldEnd = edit.start + edit.oldLength
            if (offset < edit.start || (offset == edit.start && !afterInsertAtSamePosition && edit.oldLength == 0)) return offset + delta
            if (offset <= oldEnd) return edit.start + delta + edit.replacement.length
            delta += edit.replacement.length - edit.oldLength
        }
        return offset + delta
    }
    val shiftedSelection = shiftedOffset(end, afterInsertAtSamePosition = true)
    val shiftedStyles = styles.map { style ->
        style.copy(
            start = shiftedOffset(style.start, afterInsertAtSamePosition = true),
            end = shiftedOffset(style.end, afterInsertAtSamePosition = false)
        ).takeIf { it.start < it.end }
    }.filterNotNull()
    return ChecklistPrefixEdit(result.toString(), shiftedSelection, shiftedStyles)
}

/** Adds the next numbered marker when Enter creates a new line in a numbered list. */
internal fun continueNumberedListOnNewline(
    oldText: String,
    newText: String,
    cursor: Int,
    styles: List<NoteTextStyleRange> = emptyList()
): ChecklistPrefixEdit? {
    val insertion = cursor.coerceIn(0, newText.length) - 1
    if (insertion < 0 || newText.getOrNull(insertion) != '\n' || newText.length != oldText.length + 1) return null
    if (newText.removeRange(insertion, insertion + 1) != oldText) return null
    val previousStart = newText.lastIndexOf('\n', (insertion - 1).coerceAtLeast(0)).let { if (insertion == 0 || it < 0) 0 else it + 1 }
    val previousLine = newText.substring(previousStart, insertion)
    val match = Regex("^([\\t ]*)(\\d+)[.)][\\t ]+(.*)$").matchEntire(previousLine) ?: return null
    if (match.groupValues[3].isBlank()) return null
    val nextMarker = "${match.groupValues[2].toIntOrNull()?.plus(1) ?: return null}. "
    val lineStart = insertion + 1
    val updated = newText.replaceRange(lineStart, lineStart, nextMarker)
    val shiftedStyles = styles.map { style ->
        style.copy(
            start = if (style.start >= lineStart) style.start + nextMarker.length else style.start,
            end = if (style.end > lineStart) style.end + nextMarker.length else style.end
        )
    }
    return ChecklistPrefixEdit(updated, cursor + nextMarker.length, shiftedStyles)
}

internal fun setChecklistTaskChecked(tasks: List<ChecklistTask>, taskId: String, checked: Boolean): List<ChecklistTask> =
    tasks.map { task -> if (task.id == taskId) task.copy(checked = checked) else task }

internal fun clearCompletedChecklistTasks(tasks: List<ChecklistTask>): List<ChecklistTask> = tasks.filterNot { it.checked }

/** Pads imported or edited sheet rows to a consistent width for the editor grid. */
internal fun editableSheetRows(rows: List<List<String>>): List<List<String>> {
    val columns = maxOf(1, rows.maxOfOrNull { it.size } ?: 3)
    val sourceRows = rows.ifEmpty { List(4) { List(columns) { "" } } }
    return sourceRows.map { row -> row + List((columns - row.size).coerceAtLeast(0)) { "" } }
}

internal fun appendSheetRow(rows: List<List<String>>): List<List<String>> {
    val columns = maxOf(1, rows.maxOfOrNull { it.size } ?: 3)
    return rows + listOf(List(columns) { "" })
}

/** Removes the selected row while retaining one editable row in the sheet. */
internal fun deleteSheetRow(rows: List<List<String>>, selectedRow: Int): List<List<String>> {
    if (rows.size <= 1) return rows
    val indexToRemove = selectedRow.coerceIn(0, rows.lastIndex)
    return rows.filterIndexed { index, _ -> index != indexToRemove }
}

/** Writes a sheet as RFC 4180-style CSV so commas, quotes, and line breaks remain in their cells. */
internal fun encodeSheetCsv(rows: List<List<String>>): String = rows.joinToString("\r\n") { row ->
    row.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" }
}

/** Parses CSV cells including escaped quotes and quoted newlines, with editor-sized import limits. */
internal fun parseSheetCsv(source: String): List<List<String>> {
    val text = source.removePrefix("\uFEFF")
    if (text.isBlank()) return emptyList()
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = 0
    var rowHasContent = false
    fun finishCell() {
        require(row.size < 12) { "A sheet can contain up to 12 columns." }
        row += cell.toString()
        cell.setLength(0)
        rowHasContent = true
    }
    fun finishRow() {
        finishCell()
        require(rows.size < 100) { "A sheet can contain up to 100 rows." }
        rows += row.toList()
        row.clear()
        rowHasContent = false
    }
    while (index < text.length) {
        val char = text[index]
        if (quoted) {
            rowHasContent = true
            if (char == '"' && text.getOrNull(index + 1) == '"') {
                cell.append('"')
                index += 2
                continue
            }
            if (char == '"') quoted = false else cell.append(char)
        } else {
            when (char) {
                '"' -> {
                    rowHasContent = true
                    if (cell.isEmpty()) quoted = true else cell.append(char)
                }
                ',' -> finishCell()
                '\n' -> finishRow()
                '\r' -> {
                    finishRow()
                    if (text.getOrNull(index + 1) == '\n') index++
                }
                else -> { cell.append(char); rowHasContent = true }
            }
        }
        index++
    }
    require(!quoted) { "The CSV file ends inside a quoted cell." }
    if (cell.isNotEmpty() || row.isNotEmpty() || rowHasContent) finishRow()
    require(rows.isNotEmpty()) { "The selected CSV file has no sheet data." }
    val width = rows.maxOf { it.size }
    return rows.map { values -> values + List((width - values.size).coerceAtLeast(0)) { "" } }
}

internal fun sheetCsvFilename(title: String): String {
    val safe = title.trim().replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().take(60).ifBlank { "Sheet" }
    return if (safe.endsWith(".csv", ignoreCase = true)) safe else "$safe.csv"
}

internal fun encodeRichContent(blocks: List<RichContentBlock>): String = JSONArray().apply {
    blocks.forEach { block ->
        put(JSONObject().apply {
            put("id", block.id)
            put("kind", block.kind)
            put("uri", block.uri ?: JSONObject.NULL)
            put("mimeType", block.mimeType)
            put("title", block.title)
            put("description", block.description)
            put("skin", block.skin)
            put("checklistStyle", block.checklistStyle)
            put("textAlignment", block.textAlignment)
            put("textLineSpacing", block.textLineSpacing.toDouble())
            put("tasks", JSONArray().apply {
                block.tasks.forEach { task ->
                    put(JSONObject().apply {
                        put("id", task.id)
                        put("text", task.text)
                        put("checked", task.checked)
                    })
                }
            })
            put("textStyles", JSONArray().apply {
                block.textStyles.forEach { style ->
                    put(JSONObject().apply {
                        put("start", style.start)
                        put("end", style.end)
                        put("bold", style.bold)
                        put("italic", style.italic)
                        put("underline", style.underline)
                        put("strike", style.strike)
                        put("fontSizeSp", style.fontSizeSp ?: JSONObject.NULL)
                        put("textColor", style.textColor ?: JSONObject.NULL)
                        put("highlightColor", style.highlightColor ?: JSONObject.NULL)
                    })
                }
            })
            put("rows", JSONArray().apply {
                block.rows.forEach { row -> put(JSONArray(row)) }
            })
        })
    }
}.toString()

internal fun decodeRichContent(value: String?): List<RichContentBlock> = runCatching {
    val array = JSONArray(value.orEmpty().ifBlank { "[]" })
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val rowsJson = item.optJSONArray("rows") ?: JSONArray()
            val rows = buildList {
                for (rowIndex in 0 until rowsJson.length()) {
                    val rowJson = rowsJson.optJSONArray(rowIndex) ?: continue
                    add(buildList {
                        for (cellIndex in 0 until rowJson.length()) add(rowJson.optString(cellIndex, ""))
                    })
                }
            }
            val tasksJson = item.optJSONArray("tasks") ?: JSONArray()
            val tasks = buildList {
                for (taskIndex in 0 until tasksJson.length()) {
                    val task = tasksJson.optJSONObject(taskIndex) ?: continue
                    add(ChecklistTask(
                        id = task.optString("id").ifBlank { UUID.randomUUID().toString() },
                        text = task.optString("text"),
                        checked = task.optBoolean("checked", false)
                    ))
                }
            }
            val stylesJson = item.optJSONArray("textStyles") ?: JSONArray()
            val textStyles = buildList {
                for (styleIndex in 0 until stylesJson.length()) {
                    val style = stylesJson.optJSONObject(styleIndex) ?: continue
                    val start = style.optInt("start", 0).coerceAtLeast(0)
                    val end = style.optInt("end", start).coerceAtLeast(start)
                    if (end <= start) continue
                    add(NoteTextStyleRange(
                        start = start,
                        end = end,
                        bold = style.optBoolean("bold", false),
                        italic = style.optBoolean("italic", false),
                        underline = style.optBoolean("underline", false),
                        strike = style.optBoolean("strike", false),
                        fontSizeSp = if (style.isNull("fontSizeSp")) null else style.optDouble("fontSizeSp").toFloat(),
                        textColor = if (style.isNull("textColor")) null else style.optInt("textColor"),
                        highlightColor = if (style.isNull("highlightColor")) null else style.optInt("highlightColor")
                    ))
                }
            }
            add(
                RichContentBlock(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    kind = item.optString("kind", "file"),
                    uri = if (item.isNull("uri")) null else item.optString("uri").takeIf(String::isNotBlank),
                    mimeType = item.optString("mimeType"),
                    title = item.optString("title"),
                    description = item.optString("description"),
                    rows = rows,
                    skin = item.optString("skin", "plain"),
                    tasks = tasks,
                    checklistStyle = item.optString("checklistStyle", "square"),
                    textStyles = textStyles,
                    textAlignment = item.optString("textAlignment", "start"),
                    textLineSpacing = item.optDouble("textLineSpacing", 1.25).toFloat().coerceIn(.9f, 2f)
                )
            )
        }
    }
}.getOrDefault(emptyList())
