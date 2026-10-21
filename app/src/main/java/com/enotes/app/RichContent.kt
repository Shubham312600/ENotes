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

internal data class ChecklistPrefixEdit(
    val text: String,
    val selection: Int,
    val styleRanges: List<NoteTextStyleRange>
)

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
    val result = StringBuilder(text)
    val prefixes = lines.mapIndexed { index, _ -> if (numbered) "${index + 1}. " else "• " }
    val insertions = lines.zip(prefixes)
    insertions.asReversed().forEach { (line, prefix) -> result.insert(line, prefix) }
    val shiftedSelection = end + insertions.filter { (line, _) -> line <= end }.sumOf { (_, prefix) -> prefix.length }
    val shiftedStyles = styles.map { style ->
        val startShift = insertions.filter { (line, _) -> line <= style.start }.sumOf { (_, prefix) -> prefix.length }
        val endShift = insertions.filter { (line, _) -> line < style.end }.sumOf { (_, prefix) -> prefix.length }
        style.copy(start = style.start + startShift, end = style.end + endShift)
    }
    return ChecklistPrefixEdit(result.toString(), shiftedSelection, shiftedStyles)
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
