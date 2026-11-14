package dev.trywonuf1.echoframe.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/**
 * Imports a user-selected TTF/OTF into app-private storage.
 * Android's Typeface loader performs the actual font parsing.
 */
object FontManager {
    private const val FONT_FILE = "echoframe_custom_font"

    fun importFont(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }.orEmpty()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension == "ttf" || extension == "otf") { "Choose a .ttf or .otf font file." }

        val destination = File(context.filesDir, FONT_FILE)
        val temporary = File(context.filesDir, "$FONT_FILE.tmp")
        resolver.openInputStream(uri)?.use { input ->
            temporary.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Unable to read the selected font.")
        val parsed = runCatching { Typeface.createFromFile(temporary) }.getOrElse {
            temporary.delete()
            throw IllegalArgumentException("This font file is invalid or unsupported.")
        }
        if (parsed == Typeface.DEFAULT) {
            temporary.delete()
            throw IllegalArgumentException("This font file could not be loaded.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        context.getSharedPreferences("echoframe_settings", Context.MODE_PRIVATE)
            .edit().putString("custom_font_name", displayName.ifBlank { "Custom font" }).apply()
        return displayName.ifBlank { "Custom font" }
    }

    fun loadTypeface(context: Context): Typeface? {
        val file = File(context.filesDir, FONT_FILE)
        return if (file.isFile && file.length() > 0L) {
            runCatching { Typeface.createFromFile(file) }.getOrNull()
        } else null
    }

    fun displayName(context: Context): String =
        context.getSharedPreferences("echoframe_settings", Context.MODE_PRIVATE)
            .getString("custom_font_name", null) ?: "System default"
}
