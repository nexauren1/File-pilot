package com.nexauren.filepilot

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.nexauren.filepilot.data.FileEntry
import com.nexauren.filepilot.data.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

private val FeaturePurple = Color(0xFF6D4AE8)
private val FeatureMuted = Color(0xFF6B6680)
private val FeatureSoft = Color(0xFFEFEAFF)

data class TrashItem(
    val id: String,
    val name: String,
    val originalLocation: String,
    val payloadPath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val deletedAt: Long,
    val mimeType: String?,
)

object TrashRepository {
    private const val PREFS = "filepilot_trash_index"
    private const val KEY_ITEMS = "items"
    private fun root(context: Context) = File(context.filesDir, "filepilot_trash")

    fun list(context: Context): List<TrashItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val item = TrashItem(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        originalLocation = o.getString("originalLocation"),
                        payloadPath = o.getString("payloadPath"),
                        isDirectory = o.optBoolean("isDirectory"),
                        sizeBytes = o.optLong("sizeBytes"),
                        deletedAt = o.optLong("deletedAt"),
                        mimeType = o.optString("mimeType").takeUnless { it == "null" || it.isBlank() },
                    )
                    if (File(item.payloadPath).exists()) add(item)
                }
            }.sortedByDescending { it.deletedAt }
        }.getOrDefault(emptyList())
    }

    fun find(context: Context, id: String): TrashItem? = list(context).firstOrNull { it.id == id }

    fun moveToTrash(context: Context, entry: FileEntry): Result<Unit> = runCatching {
        require(entry.location.isNotBlank()) { "Invalid file location." }
        val id = UUID.randomUUID().toString()
        val itemRoot = File(root(context), id)
        check(itemRoot.mkdirs() || itemRoot.isDirectory) { "Trash storage is unavailable." }
        val payload = File(itemRoot, "payload")
        try {
            if (entry.location.startsWith("content://")) {
                copySafDocument(context, Uri.parse(entry.location), payload, entry.isDirectory)
            } else {
                val source = File(entry.location)
                require(source.exists() && source.canRead()) { "Source is unavailable." }
                if (source.isDirectory) source.copyRecursively(payload, overwrite = false)
                else source.copyTo(payload, overwrite = false)
            }
            val item = TrashItem(
                id = id,
                name = entry.name,
                originalLocation = entry.location,
                payloadPath = payload.absolutePath,
                isDirectory = entry.isDirectory,
                sizeBytes = directorySize(payload),
                deletedAt = System.currentTimeMillis(),
                mimeType = entry.mimeType,
            )
            save(context, item)
            val deleted = if (entry.location.startsWith("content://")) {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(entry.location))
            } else {
                val source = File(entry.location)
                if (source.isDirectory) source.deleteRecursively() else source.delete()
            }
            check(deleted) { "A backup was created, but the original could not be removed." }
        } catch (error: Throwable) {
            removeRecord(context, id)
            itemRoot.deleteRecursively()
            throw error
        }
    }

    fun restoreToTree(context: Context, item: TrashItem, destinationTreeUri: Uri): Result<Unit> = runCatching {
        val destination = DocumentFile.fromTreeUri(context, destinationTreeUri)
            ?: error("Destination folder is unavailable.")
        require(destination.isDirectory && destination.canWrite()) { "Destination folder is not writable." }
        val payload = File(item.payloadPath)
        require(payload.exists()) { "The trashed content is missing." }
        val restoredName = uniqueName(destination, item.name)
        try {
            if (item.isDirectory) {
                val newDir = destination.createDirectory(restoredName) ?: error("Could not create the restored folder.")
                copyLocalDirectoryToDocument(context, payload, newDir)
            } else {
                val newFile = destination.createFile(item.mimeType ?: mimeFromName(item.name), restoredName)
                    ?: error("Could not create the restored file.")
                payload.inputStream().use { input ->
                    val output = context.contentResolver.openOutputStream(newFile.uri, "wt")
                        ?: error("Could not write the restored file.")
                    output.use { input.copyTo(it, 32 * 1024) }
                }
            }
        } catch (error: Throwable) {
            destination.findFile(restoredName)?.delete()
            throw error
        }
        removeRecord(context, item.id)
        File(File(item.payloadPath).parentFile, "").deleteRecursively()
    }

    fun deletePermanently(context: Context, id: String): Result<Unit> = runCatching {
        val item = find(context, id) ?: error("Trash item was not found.")
        check(File(item.payloadPath).parentFile?.deleteRecursively() == true) { "Could not remove the trashed item." }
        removeRecord(context, id)
    }

    fun empty(context: Context): Result<Unit> = runCatching {
        val items = list(context)
        items.forEach { item ->
            val folder = File(item.payloadPath).parentFile
            check(folder == null || !folder.exists() || folder.deleteRecursively()) {
                "Could not empty all trash items."
            }
            removeRecord(context, item.id)
        }
        root(context).listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun save(context: Context, item: TrashItem) {
        val items = list(context).filterNot { it.id == item.id } + item
        val array = JSONArray()
        items.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("name", entry.name)
                put("originalLocation", entry.originalLocation)
                put("payloadPath", entry.payloadPath)
                put("isDirectory", entry.isDirectory)
                put("sizeBytes", entry.sizeBytes)
                put("deletedAt", entry.deletedAt)
                put("mimeType", entry.mimeType ?: JSONObject.NULL)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun removeRecord(context: Context, id: String) {
        val remaining = list(context).filterNot { it.id == id }
        val array = JSONArray()
        remaining.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id); put("name", entry.name)
                put("originalLocation", entry.originalLocation); put("payloadPath", entry.payloadPath)
                put("isDirectory", entry.isDirectory); put("sizeBytes", entry.sizeBytes)
                put("deletedAt", entry.deletedAt); put("mimeType", entry.mimeType ?: JSONObject.NULL)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun copySafDocument(context: Context, uri: Uri, destination: File, isDirectory: Boolean) {
        if (!isDirectory) {
            destination.parentFile?.let { check(it.mkdirs() || it.isDirectory) }
            val input = context.contentResolver.openInputStream(uri) ?: error("Could not read the selected file.")
            input.use { source -> destination.outputStream().use { source.copyTo(it, 32 * 1024) } }
            return
        }
        check(destination.mkdirs() || destination.isDirectory) { "Could not create a folder in Trash." }
        val docId = DocumentsContract.getDocumentId(uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                if (idCol < 0 || nameCol < 0) continue
                val childId = cursor.getString(idCol) ?: continue
                val childName = safeName(cursor.getString(nameCol) ?: continue)
                val childMime = if (mimeCol >= 0 && !cursor.isNull(mimeCol)) cursor.getString(mimeCol) else null
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, childId)
                copySafDocument(
                    context, childUri, File(destination, childName),
                    childMime == DocumentsContract.Document.MIME_TYPE_DIR,
                )
            }
        } ?: error("Could not list the folder contents.")
    }

    private fun copyLocalDirectoryToDocument(context: Context, source: File, target: DocumentFile) {
        source.listFiles()?.forEach { child ->
            val name = uniqueName(target, child.name)
            if (child.isDirectory) {
                val newDir = target.createDirectory(name) ?: error("Could not restore a folder.")
                copyLocalDirectoryToDocument(context, child, newDir)
            } else {
                val mime = mimeFromName(child.name)
                val newFile = target.createFile(mime, name) ?: error("Could not restore a file.")
                child.inputStream().use { input ->
                    val output = context.contentResolver.openOutputStream(newFile.uri, "wt")
                        ?: error("Could not write a restored file.")
                    output.use { input.copyTo(it, 32 * 1024) }
                }
            }
        }
    }

    private fun uniqueName(folder: DocumentFile, original: String): String {
        if (folder.findFile(original) == null) return safeName(original)
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        for (n in 1..9999) {
            val candidate = "$stem ($n)$ext"
            if (folder.findFile(candidate) == null) return safeName(candidate)
        }
        error("Could not choose a unique name.")
    }

    private fun safeName(name: String) = name.replace(Regex("[/\\\\\\u0000]"), "_").ifBlank { "restored-file" }
    private fun mimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
    private fun directorySize(file: File): Long {
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySize(it) } ?: 0L
    }
}

data class StorageReport(
    val filesScanned: Int,
    val truncated: Boolean,
    val largeFiles: List<FileEntry>,
    val installers: List<FileEntry>,
    val suspiciousFiles: List<FileEntry>,
    val duplicateGroups: List<List<FileEntry>>,
)

object StorageAnalyzer {
    private const val MAX_FILES = 1800
    private const val MAX_HASH_BYTES = 200L * 1024 * 1024
    private const val HASHABLE_FILE = 20L * 1024 * 1024
    private const val LARGE_FILE = 100L * 1024 * 1024

    fun scan(context: Context, rootLocation: String?): StorageReport {
        if (rootLocation.isNullOrBlank()) return StorageReport(0, false, emptyList(), emptyList(), emptyList(), emptyList())
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootLocation to 0)
        val files = mutableListOf<FileEntry>()
        val large = mutableListOf<FileEntry>()
        val installers = mutableListOf<FileEntry>()
        val suspicious = mutableListOf<FileEntry>()
        val hashGroups = linkedMapOf<String, MutableList<FileEntry>>()
        var hashedBytes = 0L
        var visitedFiles = 0
        var truncated = false
        while (queue.isNotEmpty()) {
            if (visitedFiles >= MAX_FILES) { truncated = true; break }
            val (location, depth) = queue.removeFirst()
            val children = FileRepository.listChildren(context, location)
            children.forEach { entry ->
                if (visitedFiles >= MAX_FILES) { truncated = true; return@forEach }
                if (entry.isDirectory) {
                    val normalized = entry.location.replace('\\', '/').lowercase(Locale.ROOT)
                    if (depth < 8 && !normalized.contains("/android/data") && !normalized.contains("/android/obb")) {
                        queue.add(entry.location to depth + 1)
                    }
                } else {
                    visitedFiles++
                    files.add(entry)
                    val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (entry.sizeBytes >= LARGE_FILE) large.add(entry)
                    if (ext in setOf("apk", "apks", "xapk")) installers.add(entry)
                    val doubleExtension = entry.name.lowercase(Locale.ROOT).let {
                        it.contains(".pdf.apk") || it.contains(".jpg.apk") || it.contains(".png.apk") ||
                            it.contains(".mp4.apk") || it.contains(".doc.exe") || it.contains(".pdf.exe")
                    }
                    if (doubleExtension || ext in setOf("exe", "bat", "cmd", "vbs", "scr", "ps1")) suspicious.add(entry)
                    if (entry.sizeBytes in 1..HASHABLE_FILE && hashedBytes + entry.sizeBytes <= MAX_HASH_BYTES) {
                        val digest = runCatching { sha256(context, entry) }.getOrNull()
                        if (digest != null) {
                            hashGroups.getOrPut(digest) { mutableListOf() }.add(entry)
                            hashedBytes += entry.sizeBytes
                        }
                    }
                }
            }
        }
        return StorageReport(
            filesScanned = visitedFiles,
            truncated = truncated,
            largeFiles = large.sortedByDescending { it.sizeBytes }.take(60),
            installers = installers.sortedByDescending { it.lastModified }.take(100),
            suspiciousFiles = suspicious.distinctBy { it.location }.take(100),
            duplicateGroups = hashGroups.values.filter { it.size > 1 }.map { it.toList() }.take(40),
        )
    }

    private fun sha256(context: Context, entry: FileEntry): String {
        val input: InputStream = if (entry.location.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(entry.location)) ?: error("Not readable")
        } else FileInputStream(File(entry.location))
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class ManagedAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val canUninstall: Boolean,
)

private fun installedApps(context: Context): List<ManagedAppInfo> {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return apps.map { info ->
        val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(info.packageName)
        val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        ManagedAppInfo(info.packageName, label, system, !system && info.packageName != context.packageName)
    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

@Composable
fun ToolsScreen(
    modifier: Modifier,
    onOpenTrash: () -> Unit,
    onOpenCleaner: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.toolbox_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.toolbox_subtitle), color = FeatureMuted)
        FeatureCard(Icons.Outlined.DeleteSweep, R.string.tool_trash_title, R.string.tool_trash_body, onOpenTrash)
        FeatureCard(Icons.Outlined.Storage, R.string.tool_cleaner_title, R.string.tool_cleaner_body, onOpenCleaner)
        FeatureCard(Icons.Outlined.Apps, R.string.tool_apps_title, R.string.tool_apps_body, onOpenApps)
        FeatureCard(Icons.Outlined.Shield, R.string.tool_security_title, R.string.tool_security_body, onOpenSecurity)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, description: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(FeatureSoft), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = FeaturePurple)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = FeatureMuted)
            }
        }
    }
}

@Composable
fun TrashScreen(
    modifier: Modifier,
    items: List<TrashItem>,
    onRestore: (String) -> Unit,
    onDeletePermanently: (String) -> Unit,
    onEmpty: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<TrashItem?>(null) }
    var showEmptyConfirm by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.trash_description), color = FeatureMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showEmptyConfirm = true }, enabled = items.isNotEmpty()) {
                Text(stringResource(R.string.trash_empty_action))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) {
            EmptyFeatureState(Icons.Outlined.DeleteSweep, R.string.trash_empty_title, R.string.trash_empty_body)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (item.isDirectory) Icons.Outlined.Folder else Icons.Outlined.DeleteSweep, contentDescription = null, tint = FeaturePurple)
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatFeatureBytes(item.sizeBytes) + " · " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.deletedAt)), color = FeatureMuted, fontSize = 12.sp)
                                }
                            }
                            Text(stringResource(R.string.trash_original_location, item.originalLocation), color = FeatureMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRestore(item.id) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.trash_restore)) }
                                TextButton(onClick = { pendingDelete = item }) { Text(stringResource(R.string.trash_delete_permanently)) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.trash_delete_confirm_title)) },
            text = { Text(stringResource(R.string.trash_delete_confirm_body, pendingDelete?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    val item = pendingDelete
                    pendingDelete = null
                    item?.let { onDeletePermanently(it.id) }
                }) { Text(stringResource(R.string.trash_delete_permanently), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showEmptyConfirm = false; onEmpty() }) { Text(stringResource(R.string.trash_empty_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun CleanerScreen(modifier: Modifier, rootLocation: String?, onRequestAccess: () -> Unit, onMoveToTrash: (FileEntry) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<StorageReport?>(null) }
    var scanning by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cleaner_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.cleaner_description), color = FeatureMuted)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cleaner_safe_note), color = FeatureMuted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    if (rootLocation == null) onRequestAccess()
                    else scope.launch {
                        scanning = true
                        report = withContext(Dispatchers.IO) { StorageAnalyzer.scan(context, rootLocation) }
                        scanning = false
                    }
                }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (scanning) R.string.scan_in_progress else R.string.cleaner_scan))
                }
                if (report != null) {
                    Text(stringResource(R.string.scan_summary, report!!.filesScanned), fontWeight = FontWeight.SemiBold)
                    if (report!!.truncated) Text(stringResource(R.string.scan_limit_note), color = FeatureMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val snapshot = report
        if (snapshot != null) {
            FeatureSectionTitle(R.string.large_files_title, snapshot.largeFiles.size)
            if (snapshot.largeFiles.isEmpty()) Text(stringResource(R.string.no_candidates), color = FeatureMuted)
            snapshot.largeFiles.forEach { entry -> ReviewFileRow(entry, R.string.cleaner_large_file_hint, onMoveToTrash) }
            Divider()
            FeatureSectionTitle(R.string.installers_title, snapshot.installers.size)
            if (snapshot.installers.isEmpty()) Text(stringResource(R.string.no_candidates), color = FeatureMuted)
            snapshot.installers.forEach { entry -> ReviewFileRow(entry, R.string.cleaner_installer_hint, onMoveToTrash) }
            Divider()
            FeatureSectionTitle(R.string.duplicates_title, snapshot.duplicateGroups.sumOf { it.size })
            if (snapshot.duplicateGroups.isEmpty()) Text(stringResource(R.string.no_candidates), color = FeatureMuted)
            snapshot.duplicateGroups.forEachIndexed { index, group ->
                Text(stringResource(R.string.duplicate_group_label, index + 1, group.size), fontWeight = FontWeight.Medium)
                group.forEach { entry -> ReviewFileRow(entry, R.string.duplicate_exact_match, onMoveToTrash) }
            }
        }
    }
}

@Composable
fun SecurityScreen(modifier: Modifier, rootLocation: String?, onRequestAccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<StorageReport?>(null) }
    var scanning by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.security_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = FeaturePurple, modifier = Modifier.size(34.dp))
                Text(stringResource(R.string.security_description), color = FeatureMuted)
                Text(stringResource(R.string.security_not_antivirus), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Button(onClick = {
                    if (rootLocation == null) onRequestAccess()
                    else scope.launch {
                        scanning = true
                        report = withContext(Dispatchers.IO) { StorageAnalyzer.scan(context, rootLocation) }
                        scanning = false
                    }
                }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (scanning) R.string.scan_in_progress else R.string.security_scan))
                }
            }
        }
        report?.let { result ->
            Text(stringResource(R.string.security_results, result.filesScanned), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (result.suspiciousFiles.isEmpty() && result.installers.isEmpty()) {
                EmptyFeatureState(Icons.Outlined.Shield, R.string.security_no_flags_title, R.string.security_no_flags_body)
            } else {
                Text(stringResource(R.string.security_risky_extensions), color = FeatureMuted)
                (result.suspiciousFiles + result.installers).distinctBy { it.location }.take(100).forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color(0xFFB7791F))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.location, color = FeatureMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (result.truncated) Text(stringResource(R.string.scan_limit_note), color = FeatureMuted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent("android.settings.SECURITY_SETTINGS"))
                }
            }) { Text(stringResource(R.string.security_open_settings)) }
        }
    }
}

@Composable
fun AppManagerScreen(modifier: Modifier, onOpenInfo: (String) -> Unit, onUninstall: (String) -> Unit) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf<List<ManagedAppInfo>>(emptyList()) }
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { installedApps(context) }
        loading = false
    }
    val filtered = allApps.filter {
        (showSystem || !it.isSystem) &&
            (it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true))
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.apps_description), color = FeatureMuted)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.apps_search_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
            Text(stringResource(R.string.apps_show_system))
            Spacer(Modifier.weight(1f))
            Text(if (loading) "…" else filtered.size.toString(), color = FeatureMuted)
        }
        if (loading) {
            Text(stringResource(R.string.scan_in_progress), color = FeatureMuted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Apps, contentDescription = null, tint = FeaturePurple)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, color = FeatureMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onOpenInfo(app.packageName) }) {
                                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(stringResource(R.string.apps_open_info))
                                }
                                if (app.canUninstall) {
                                    TextButton(onClick = { onUninstall(app.packageName) }) { Text(stringResource(R.string.apps_uninstall), color = MaterialTheme.colorScheme.error) }
                                } else {
                                    Text(stringResource(if (app.isSystem) R.string.apps_system_label else R.string.apps_protected_label), color = FeatureMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureSectionTitle(titleRes: Int, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(count.toString(), color = FeatureMuted)
    }
}

@Composable
private fun ReviewFileRow(entry: FileEntry, hintRes: Int, onMoveToTrash: (FileEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Folder, contentDescription = null, tint = FeaturePurple)
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 6.dp)) {
            Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatFeatureBytes(entry.sizeBytes) + " · " + stringResource(hintRes), color = FeatureMuted, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { onMoveToTrash(entry) }) { Text(stringResource(R.string.send_to_trash)) }
    }
    Divider(color = Color(0xFFF0F2F6), thickness = 0.7.dp)
}

@Composable
private fun EmptyFeatureState(icon: androidx.compose.ui.graphics.vector.ImageVector, titleRes: Int, bodyRes: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = Color(0xFF9AA6B8))
        Text(stringResource(titleRes), fontWeight = FontWeight.SemiBold)
        Text(stringResource(bodyRes), color = FeatureMuted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatFeatureBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var index = -1
    do { size /= 1024.0; index++ } while (size >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", size, units[index])
}
