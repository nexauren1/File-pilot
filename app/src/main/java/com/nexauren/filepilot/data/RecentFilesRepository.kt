package com.nexauren.filepilot.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import java.io.File

/**
 * Stores only the locations of recently opened files locally. It does not store or upload file contents.
 */
object RecentFilesRepository {
    private const val PREFS = "filepilot_local"
    private const val KEY_RECENTS = "recently_opened_locations"
    private const val MAX_RECENTS = 30

    fun recordOpen(context: Context, entry: FileEntry) {
        if (entry.isDirectory) return
        val recent = locations(context).toMutableList()
        recent.remove(entry.location)
        recent.add(0, entry.location)
        val json = JSONArray()
        recent.take(MAX_RECENTS).forEach(json::put)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENTS, json.toString())
            .apply()
    }

    fun list(context: Context): List<FileEntry> {
        val current = locations(context)
        val valid = mutableListOf<String>()
        val entries = current.mapNotNull { location ->
            runCatching {
                val entry = if (location.startsWith("content://")) {
                    val doc = DocumentFile.fromSingleUri(context, Uri.parse(location))
                        ?: return@runCatching null
                    if (!doc.exists() || doc.isDirectory) return@runCatching null
                    val name = doc.name ?: return@runCatching null
                    FileEntry(location, name, false, doc.length().coerceAtLeast(0L), doc.lastModified(), doc.type)
                } else {
                    val file = File(location)
                    if (!file.exists() || !file.isFile || !file.canRead()) return@runCatching null
                    FileEntry(
                        file.absolutePath,
                        file.name,
                        false,
                        file.length().coerceAtLeast(0L),
                        file.lastModified(),
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()),
                    )
                }
                valid.add(location)
                entry
            }.getOrNull()
        }
        if (valid != current) {
            val json = JSONArray()
            valid.forEach(json::put)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENTS, json.toString())
                .apply()
        }
        return entries
    }

    private fun locations(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENTS, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            List(json.length()) { json.getString(it) }.distinct().take(MAX_RECENTS)
        }.getOrDefault(emptyList())
    }
}
