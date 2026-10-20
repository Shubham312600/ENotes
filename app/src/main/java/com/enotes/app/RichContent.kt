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
    val skin: String = "plain"
)

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
            add(
                RichContentBlock(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    kind = item.optString("kind", "file"),
                    uri = if (item.isNull("uri")) null else item.optString("uri").takeIf(String::isNotBlank),
                    mimeType = item.optString("mimeType"),
                    title = item.optString("title"),
                    description = item.optString("description"),
                    rows = rows,
                    skin = item.optString("skin", "plain")
                )
            )
        }
    }
}.getOrDefault(emptyList())
