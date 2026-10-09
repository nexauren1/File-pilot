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
    private const val RECURSIVE_CACHE_TTL_MS = 20_000L

    private data class RecursiveCache(
        val rootLocation: String,
        val maxDepth: Int,
        val maxFiles: Int,
        val createdAt: Long,
        val entries: List<FileEntry>,
    )

    @Volatile
    private var recursiveCache: RecursiveCache? = null

    /** Clear the short-lived file index after an operation changes storage. */
    fun invalidateCache() {
        recursiveCache = null
    }

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
                listSafChildren(context, Uri.parse(location))
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

    /**
     * Returns files from nested folders without returning directory rows.
     * Used for category views such as Videos and Images, which should behave as collections.
     * The traversal is bounded to avoid scanning a whole device indefinitely.
     */
    fun listFilesRecursively(
        context: Context,
        rootLocation: String,
        maxFiles: Int = 2500,
        maxDepth: Int = 8,
    ): List<FileEntry> {
        val now = System.currentTimeMillis()
        val cached = recursiveCache
        if (
            cached != null &&
            cached.rootLocation == rootLocation &&
            cached.maxDepth == maxDepth &&
            cached.maxFiles >= maxFiles &&
            now - cached.createdAt < RECURSIVE_CACHE_TTL_MS
        ) {
            return cached.entries.take(maxFiles)
        }

        val queue = ArrayDeque<Pair<String, Int>>()
        val visitedDirectories = HashSet<String>()
        val files = mutableListOf<FileEntry>()
        queue.add(rootLocation to 0)
        while (queue.isNotEmpty() && files.size < maxFiles) {
            val (location, depth) = queue.removeFirst()
            if (!visitedDirectories.add(location)) continue
            val children = listChildren(context, location)
            for (entry in children) {
                if (files.size >= maxFiles) break
                if (entry.isDirectory) {
                    val normalized = entry.location.replace('\\', '/').lowercase()
                    val restricted = normalized.contains("/android/data") ||
                        normalized.contains("/android/obb") ||
                        normalized.contains("/.thumbnails")
                    if (depth < maxDepth && !restricted) queue.add(entry.location to depth + 1)
                } else {
                    files.add(entry)
                }
            }
        }
        val sorted = files.sortedWith(compareBy<FileEntry> { it.name.lowercase() }.thenBy { it.location })
        recursiveCache = RecursiveCache(rootLocation, maxDepth, maxFiles, System.currentTimeMillis(), sorted)
        return sorted
    }

    fun rename(context: Context, entry: FileEntry, newName: String): Result<Unit> = runCatching {
        require(newName.isNotBlank() && newName == newName.trim()) { "Invalid name." }
        require(!newName.contains('/') && !newName.contains('\\')) { "Invalid characters." }
        val ok = if (entry.location.startsWith("content://")) {
            val uri = Uri.parse(entry.location)
            DocumentsContract.renameDocument(context.contentResolver, uri, newName) != null
        } else {
            val old = File(entry.location)
            val parent = old.parentFile ?: error("Folder unavailable.")
            old.renameTo(File(parent, newName))
        }
        check(ok) { "Rename was rejected." }
        invalidateCache()
    }

    fun delete(context: Context, entry: FileEntry): Result<Unit> = runCatching {
        val ok = if (entry.location.startsWith("content://")) {
            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(entry.location))
        } else {
            val file = File(entry.location)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        check(ok) { "Delete was rejected." }
        invalidateCache()
    }

    fun shareUri(context: Context, entry: FileEntry): Uri =
        if (entry.location.startsWith("content://")) Uri.parse(entry.location)
        else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(entry.location))

    private fun documentFromUri(context: Context, uri: Uri): DocumentFile? {
        val isChildDocument = uri.pathSegments.contains("document")
        return if (DocumentsContract.isTreeUri(uri) && !isChildDocument) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }

    private fun listSafChildren(context: Context, directoryUri: Uri): List<FileEntry> {
        val pathSegments = directoryUri.pathSegments
        val documentId = if (pathSegments.contains("document")) {
            DocumentsContract.getDocumentId(directoryUri)
        } else {
            DocumentsContract.getTreeDocumentId(directoryUri)
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = mutableListOf<FileEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                try {
                    if (idColumn < 0 || nameColumn < 0) continue
                    val documentIdValue = cursor.getString(idColumn) ?: continue
                    val name = cursor.getString(nameColumn) ?: continue
                    val mime = if (mimeColumn >= 0 && !cursor.isNull(mimeColumn)) cursor.getString(mimeColumn) else null
                    val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    val size = if (!isDirectory && sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                        cursor.getLong(sizeColumn).coerceAtLeast(0L)
                    } else 0L
                    val modified = if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) {
                        cursor.getLong(modifiedColumn)
                    } else 0L
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentIdValue)
                    result.add(
                        FileEntry(
                            childUri.toString(),
                            name,
                            isDirectory,
                            size,
                            modified,
                            if (isDirectory) null else mime ?: mimeFromName(name),
                        ),
                    )
                } catch (_: Exception) {
                    // Ignore a single provider row that is malformed or no longer accessible.
                }
            }
        }
        return result.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun createDirectory(context: Context, location: String, name: String): Result<Unit> = runCatching {
        require(name.isNotBlank() && name == name.trim()) { "Invalid folder name." }
        require(!name.contains('/') && !name.contains('\\')) { "Invalid characters." }
        val created = if (location.startsWith("content://")) {
            val parentUri = Uri.parse(location)
            val parentDocumentUri = if (parentUri.pathSegments.contains("document")) {
                DocumentsContract.buildDocumentUriUsingTree(parentUri, DocumentsContract.getDocumentId(parentUri))
            } else {
                parentUri
            }
            DocumentsContract.createDocument(
                context.contentResolver,
                parentDocumentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            ) != null
        } else {
            val parent = File(location)
            require(parent.isDirectory && parent.canWrite()) { "Folder is not writable." }
            File(parent, name).mkdir()
        }
        check(created) { "Folder could not be created." }
        invalidateCache()
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
            invalidateCache()
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
