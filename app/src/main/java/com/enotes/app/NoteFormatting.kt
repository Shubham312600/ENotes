package com.enotes.app

internal data class TextEditResult(val text: String, val selectionStart: Int, val selectionEnd: Int)

internal fun wrapTextSelection(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    prefix: String,
    suffix: String
): TextEditResult {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val selected = text.substring(start, end)
    val result = text.replaceRange(start, end, prefix + selected + suffix)
    return if (selected.isEmpty()) {
        TextEditResult(result, start + prefix.length, start + prefix.length)
    } else {
        TextEditResult(result, start, start + prefix.length + selected.length + suffix.length)
    }
}
internal fun prefixCurrentLine(text: String, selectionStart: Int, prefix: String): TextEditResult {
    val cursor = selectionStart.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (cursor == 0 || it < 0) 0 else it + 1 }
    val result = text.replaceRange(lineStart, lineStart, prefix)
    return TextEditResult(result, cursor + prefix.length, cursor + prefix.length)
}

