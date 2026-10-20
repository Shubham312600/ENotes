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
