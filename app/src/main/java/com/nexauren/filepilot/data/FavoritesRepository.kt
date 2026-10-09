package com.nexauren.filepilot.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Lightweight favourites stored locally. Only file locations are stored; file contents are never copied.
 */
object FavoritesRepository {
    private const val PREFS = "filepilot_local"
    private const val KEY_FAVORITES = "favorite_locations"

    fun isFavorite(context: Context, entry: FileEntry): Boolean =
        favorites(context).contains(entry.location)

    fun toggle(context: Context, entry: FileEntry): Boolean {
        val values = favorites(context).toMutableSet()
        val nowFavorite = if (values.contains(entry.location)) {
            values.remove(entry.location)
            false
        } else {
            values.add(entry.location)
            true
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_FAVORITES, values)
            .apply()
        return nowFavorite
    }

    fun list(context: Context): List<FileEntry> {
        val current = favorites(context)
        val valid = mutableSetOf<String>()
        val entries = current.mapNotNull { location ->
            runCatching {
                val entry = if (location.startsWith("content://")) {
                    val doc = DocumentFile.fromSingleUri(context, Uri.parse(location)) ?: return@runCatching null
                    if (!doc.exists()) return@runCatching null
                    val name = doc.name ?: return@runCatching null
                    FileEntry(
                        location = location,
                        name = name,
                        isDirectory = doc.isDirectory,
                        sizeBytes = doc.length().coerceAtLeast(0L),
                        modifiedAt = doc.lastModified(),
                        mimeType = doc.type,
                    )
                } else {
                    val file = File(location)
                    if (!file.exists() || !file.canRead()) return@runCatching null
                    FileEntry(
                        location = file.absolutePath,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        sizeBytes = if (file.isFile) file.length().coerceAtLeast(0L) else 0L,
                        modifiedAt = file.lastModified(),
                        mimeType = if (file.isFile) android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(file.extension.lowercase()) else null,
                    )
                }
                valid.add(location)
                entry
            }.getOrNull()
        }.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        if (valid != current) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_FAVORITES, valid)
                .apply()
        }
        return entries
    }

    private fun favorites(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITES, emptySet())
            ?.toSet()
            ?: emptySet()
}
