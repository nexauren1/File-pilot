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
import java.io.FileInputStream
import java.io.InputStream

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

    fun createDirectory(context: Context, location: String, name: String): Result<Unit> = runCatching {
        require(name.isNotBlank() && name == name.trim()) { "Invalid folder name." }
        require(!name.contains('/') && !name.contains('\\')) { "Invalid characters." }
        val created = if (location.startsWith("content://")) {
            documentFromUri(context, Uri.parse(location))?.createDirectory(name) != null
        } else {
            val parent = File(location)
            require(parent.isDirectory && parent.canWrite()) { "Folder is not writable." }
            File(parent, name).mkdir()
        }
        check(created) { "Folder could not be created." }
    }

    fun copyToTree(context: Context, entry: FileEntry, destinationTreeUri: Uri): Result<Unit> = runCatching {
        require(!entry.isDirectory) { "Folder copying is not supported by this operation." }
        val destination = DocumentFile.fromTreeUri(context, destinationTreeUri)
            ?: error("Destination folder is unavailable.")
        require(destination.isDirectory && destination.canWrite()) { "Destination folder is not writable." }

        val outputName = uniqueDocumentName(destination, entry.name)
        val target = destination.createFile(entry.mimeType ?: "application/octet-stream", outputName)
            ?: error("Could not create the destination file.")
        try {
            openEntryInputStream(context, entry).use { source ->
                val output = context.contentResolver.openOutputStream(target.uri, "wt")
                    ?: error("Could not open the destination file.")
                output.use { sink -> source.copyTo(sink, 32 * 1024) }
                Unit
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun openEntryInputStream(context: Context, entry: FileEntry): InputStream {
        return if (entry.location.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(entry.location))
                ?: error("File could not be opened.")
        } else {
            val file = File(entry.location)
            require(file.isFile && file.canRead()) { "File is not readable." }
            FileInputStream(file)
        }
    }

    private fun uniqueDocumentName(destination: DocumentFile, original: String): String {
        if (destination.findFile(original) == null) return original
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val extension = if (dot > 0) original.substring(dot) else ""
        for (number in 1..9999) {
            val candidate = stem + " (" + number + ")" + extension
            if (destination.findFile(candidate) == null) return candidate
        }
        error("Could not choose a unique filename.")
    }

    private fun mimeFromName(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
