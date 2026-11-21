package com.enotes.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import java.io.FileNotFoundException
import java.net.URI
import java.util.Locale

/** Chooses a useful MIME type when a document provider reports a generic type or none at all. */
internal fun attachmentMimeType(uriText: String, providerMimeType: String?): String {
    val providerType = providerMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf(String::isNotBlank)
    val extension = runCatching {
        URI(uriText).path.orEmpty().substringAfterLast('/').substringAfterLast('.').lowercase(Locale.ROOT)
    }.getOrDefault("")
    val extensionType = attachmentMimeTypesByExtension[extension]

    return when {
        providerType == null -> extensionType ?: "application/octet-stream"
        providerType in genericAttachmentMimeTypes -> extensionType ?: providerType
        else -> providerType
    }
}

private val genericAttachmentMimeTypes = setOf("application/octet-stream", "binary/octet-stream")

private val attachmentMimeTypesByExtension = mapOf(
    "pdf" to "application/pdf",
    "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
    "gif" to "image/gif", "bmp" to "image/bmp", "heic" to "image/heic", "heif" to "image/heif",
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "wav" to "audio/wav",
    "ogg" to "audio/ogg", "oga" to "audio/ogg", "opus" to "audio/opus", "flac" to "audio/flac",
    "amr" to "audio/amr", "mp4" to "video/mp4", "3gp" to "video/3gpp", "webm" to "video/webm",
    "mkv" to "video/x-matroska", "mov" to "video/quicktime"
)

/** Opens a saved SAF attachment with its actual type and a temporary read grant. Returns an error for UI display, or null when launched. */
internal fun openAttachment(context: Context, uriText: String): String? {
    if (uriText.isBlank()) return "This note doesn't have an attachment to open."
    val uri = runCatching { Uri.parse(uriText) }.getOrNull()
        ?: return "This attachment reference is invalid. Reattach the file and try again."
    if (uri.scheme.isNullOrBlank()) return "This attachment reference is invalid. Reattach the file and try again."
    if (uri.scheme.equals("file", ignoreCase = true)) {
        return "This attachment uses an old file path. Reattach it from the file picker to open it."
    }

    if (uri.scheme.equals("content", ignoreCase = true)) {
        try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return "This attachment is no longer available. Reattach the file and try again."
            descriptor.close()
        } catch (_: SecurityException) {
            return "ENotes can no longer access this attachment. Reattach the file and try again."
        } catch (_: FileNotFoundException) {
            return "This attachment is missing. Reattach the file and try again."
        } catch (_: Exception) {
            return "ENotes couldn't read this attachment. Check that the file is available, then try again."
        }
    }

    val mimeType = attachmentMimeType(
        uriText = uriText,
        providerMimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    )
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (context.packageManager.resolveActivity(viewIntent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
        return "No installed app can open this attachment type ($mimeType)."
    }
    return try {
        context.startActivity(Intent.createChooser(viewIntent, "Open attachment"))
        null
    } catch (_: ActivityNotFoundException) {
        "No installed app can open this attachment type ($mimeType)."
    } catch (_: SecurityException) {
        "Android couldn't grant access to this attachment. Reattach the file and try again."
    } catch (_: Exception) {
        "The attachment couldn't be opened. Check that the file is available and try again."
    }
}
