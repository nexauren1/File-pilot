package com.nexauren.filepilot.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File

object FileRepository {
    private const val PREFS = "filepilot_local"
    private const val KEY_TREE_URI = "selected_tree_uri"

    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val write = Build.VERSION.SDK_INT > Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            read && write
        }
    }

    fun storageRoot(): String = Environment.getExternalStorageDirectory().absolutePath

    fun initialLocation(context: Context): String? {
        if (hasStorageAccess(context)) return storageRoot()
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TREE_URI, null)
    }

    fun saveSelectedTree(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun listChildren(context: Context, location: String): List<FileEntry> {
        return try {
            if (location.startsWith("content://")) {
                val directory = documentFromUri(context, Uri.parse(location))
                if (directory?.isDirectory != true) {
                    emptyList()
                } else {
                    directory.listFiles().mapNotNull { child ->
                        try {
                            val name = child.name ?: return@mapNotNull null
                            FileEntry(child.uri.toString(), name, child.isDirectory, child.length().coerceAtLeast(0L), child.lastModified(), child.type ?: mimeFromName(name))
                        } catch (_: Exception) {
                            null
                        }
                    }.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
                }
            } else {
                val directory = File(location)
                if (!directory.isDirectory || !directory.canRead()) {
                    emptyList()
                } else {
                    directory.listFiles()?.filter { it.canRead() }?.map { file ->
                        FileEntry(file.absolutePath, file.name, file.isDirectory, if (file.isFile) file.length().coerceAtLeast(0L) else 0L, file.lastModified(), if (file.isFile) mimeFromName(file.name) else null)
                    }?.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun rename(context: Context, entry: FileEntry, newName: String): Result<Unit> = runCatching {
        require(newName.isNotBlank() && newName == newName.trim()) { "Invalid name." }
        require(!newName.contains('/') && !newName.contains('\\')) { "Invalid characters." }
        val ok = if (entry.location.startsWith("content://")) {
            documentFromUri(context, Uri.parse(entry.location))?.renameTo(newName) == true
        } else {
            val old = File(entry.location)
            val parent = old.parentFile ?: error("Folder unavailable.")
            old.renameTo(File(parent, newName))
        }
        check(ok) { "Rename was rejected." }
    }

    fun delete(context: Context, entry: FileEntry): Result<Unit> = runCatching {
        val ok = if (entry.location.startsWith("content://")) {
            documentFromUri(context, Uri.parse(entry.location))?.delete() == true
        } else {
            val file = File(entry.location)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        check(ok) { "Delete was rejected." }
    }

    fun shareUri(context: Context, entry: FileEntry): Uri =
        if (entry.location.startsWith("content://")) Uri.parse(entry.location)
        else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(entry.location))

    private fun documentFromUri(context: Context, uri: Uri): DocumentFile? =
        if (DocumentsContract.isTreeUri(uri)) DocumentFile.fromTreeUri(context, uri)
        else DocumentFile.fromSingleUri(context, uri)

    private fun mimeFromName(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
