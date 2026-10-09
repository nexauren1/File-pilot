package com.nexauren.filepilot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nexauren.filepilot.data.FileCategory
import com.nexauren.filepilot.data.FileEntry
import com.nexauren.filepilot.data.FileRepository
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val ViewerInk = Color(0xFF202033)
private val ViewerMuted = Color(0xFF656579)
private val ViewerPurple = Color(0xFF5A45D6)
private val ViewerPage = Color(0xFFF5F5FA)

private enum class InternalViewerType { PDF, IMAGE, AUDIO, VIDEO, TEXT, ZIP, EXTERNAL }

@Composable
internal fun InternalFileViewerScreen(
    modifier: Modifier,
    entry: FileEntry,
    onOpenExternal: () -> Unit,
) {
    when (viewerType(entry)) {
        InternalViewerType.PDF -> PdfDocumentReader(modifier, entry)
        InternalViewerType.IMAGE -> ImageDocumentReader(modifier, entry)
        InternalViewerType.AUDIO -> AudioDocumentPlayer(modifier, entry)
        InternalViewerType.VIDEO -> VideoDocumentPlayer(modifier, entry)
        InternalViewerType.TEXT -> TextDocumentReader(modifier, entry)
        InternalViewerType.ZIP -> ZipDocumentPreview(modifier, entry, onOpenExternal)
        InternalViewerType.EXTERNAL -> ExternalDocumentCard(modifier, entry, onOpenExternal)
    }
}

private fun viewerType(entry: FileEntry): InternalViewerType {
    val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mime = entry.mimeType.orEmpty().lowercase(Locale.ROOT)
    return when {
        extension == "pdf" || mime == "application/pdf" -> InternalViewerType.PDF
        extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "tif", "tiff") || mime.startsWith("image/") -> InternalViewerType.IMAGE
        extension in setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus", "mid", "midi", "wma") || mime.startsWith("audio/") -> InternalViewerType.AUDIO
        extension in setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v", "mpeg", "mpg") || mime.startsWith("video/") -> InternalViewerType.VIDEO
        extension in setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "yaml", "yml", "ini", "properties", "kt", "java", "py", "js", "css", "sh", "sql") || mime.startsWith("text/") -> InternalViewerType.TEXT
        extension == "zip" -> InternalViewerType.ZIP
        else -> InternalViewerType.EXTERNAL
    }
}

private fun openUri(context: Context, entry: FileEntry): Uri = FileRepository.shareUri(context, entry)

@Composable
private fun ViewerPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().background(ViewerPage).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun PdfDocumentReader(modifier: Modifier, entry: FileEntry) {
    var currentPage by remember(entry.location) { mutableIntStateOf(0) }
    var refresh by remember(entry.location) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val result by produceState<PdfRenderResult?>(
        initialValue = null,
        key1 = entry.location,
        key2 = currentPage,
        key3 = refresh,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { renderPdfPage(context, openUri(context, entry), currentPage) }.getOrNull()
        }
    }
    ViewerPanel(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.pdf_label), color = ViewerMuted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { currentPage = (currentPage - 1).coerceAtLeast(0) }, enabled = currentPage > 0) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(R.string.pdf_previous_page))
                }
                Text(
                    result?.let { context.getString(R.string.pdf_page_count, currentPage + 1, it.pageCount) } ?: "— / —",
                    color = ViewerInk,
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(
                    onClick = { result?.let { currentPage = (currentPage + 1).coerceAtMost(it.pageCount - 1) } },
                    enabled = result != null && currentPage < (result?.pageCount ?: 1) - 1,
                ) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(R.string.pdf_next_page))
                }
            }
        }
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    result != null -> Image(
                        bitmap = result!!.bitmap.asImageBitmap(),
                        contentDescription = "${entry.name}, page ${currentPage + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                    else -> CircularProgressIndicator(color = ViewerPurple)
                }
            }
        }
        Text(entry.name, color = ViewerMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (result == null) {
            TextButton(onClick = { refresh++ }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.viewer_try_again))
            }
        }
    }
}

private data class PdfRenderResult(val bitmap: Bitmap, val pageCount: Int)

private fun renderPdfPage(context: Context, uri: Uri, pageIndex: Int): PdfRenderResult {
    val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: error("PDF could not be opened")
    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            require(renderer.pageCount > 0) { "The PDF has no pages." }
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            val page = renderer.openPage(safeIndex)
            try {
                val scale = minOf(1.65f, 1200f / page.width.coerceAtLeast(1))
                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfRenderResult(bitmap, renderer.pageCount)
            } finally {
                page.close()
            }
        }
    }
}

@Composable
private fun ImageDocumentReader(modifier: Modifier, entry: FileEntry) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = entry.location) {
        value = withContext(Dispatchers.IO) {
            runCatching { readThumbnail(context, openUri(context, entry), maxSide = 2600) }.getOrNull()
        }
    }
    var scale by remember(entry.location) { mutableFloatStateOf(1f) }
    var offsetX by remember(entry.location) { mutableFloatStateOf(0f) }
    var offsetY by remember(entry.location) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 6f)
        scale = next
        if (next <= 1.01f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }
    ViewerPanel(modifier) {
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181821)),
            shape = RoundedCornerShape(22.dp),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .transformable(transformState)
                    .pointerInput(entry.location, scale) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = ViewerInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.image_zoom_hint), color = ViewerMuted, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) { Text(stringResource(R.string.image_reset_zoom)) }
        }
    }
}

private fun readThumbnail(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

@Composable
private fun AudioDocumentPlayer(modifier: Modifier, entry: FileEntry) {
    val context = LocalContext.current
    var player by remember(entry.location) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(entry.location) { mutableStateOf(false) }
    var playing by remember(entry.location) { mutableStateOf(false) }
    var failed by remember(entry.location) { mutableStateOf(false) }
    var duration by remember(entry.location) { mutableIntStateOf(1) }
    var position by remember(entry.location) { mutableIntStateOf(0) }
    var seeking by remember(entry.location) { mutableStateOf(false) }

    DisposableEffect(entry.location) {
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setOnPreparedListener {
            duration = it.duration.coerceAtLeast(1)
            prepared = true
        }
        mediaPlayer.setOnCompletionListener {
            playing = false
            position = 0
            runCatching { it.seekTo(0) }
        }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            failed = true
            playing = false
            true
        }
        runCatching {
            mediaPlayer.setDataSource(context, openUri(context, entry))
            mediaPlayer.prepareAsync()
        }.onFailure { failed = true }
        onDispose {
            runCatching { mediaPlayer.stop() }
            mediaPlayer.release()
            player = null
        }
    }

    LaunchedEffect(prepared, player, seeking) {
        val activePlayer = player
        if (prepared && activePlayer != null) {
            while (isActive) {
                if (!seeking) position = runCatching { activePlayer.currentPosition }.getOrDefault(position).coerceIn(0, duration)
                delay(300)
            }
        }
    }

    ViewerPanel(modifier) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    Modifier.size(172.dp).clip(RoundedCornerShape(42.dp)).background(Color(0xFFEAE7FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = ViewerPurple, modifier = Modifier.size(86.dp))
                }
                Text(entry.name, color = ViewerInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    stringResource(when { failed -> R.string.audio_failed; prepared -> R.string.audio_ready; else -> R.string.audio_preparing }),
                    color = ViewerMuted,
                )
                if (!prepared && !failed) CircularProgressIndicator(color = ViewerPurple)
            }
        }
        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = { position = it.roundToInt(); seeking = true },
            onValueChangeFinished = {
                runCatching { player?.seekTo(position) }
                seeking = false
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = prepared && !failed,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMediaTime(position), style = MaterialTheme.typography.labelSmall, color = ViewerMuted)
            Text(formatMediaTime(duration), style = MaterialTheme.typography.labelSmall, color = ViewerMuted)
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = {
                position = (position - 10_000).coerceAtLeast(0)
                runCatching { player?.seekTo(position) }
            }, enabled = prepared && !failed) {
                Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(R.string.audio_back_10))
            }
            IconButton(
                onClick = {
                    val active = player ?: return@IconButton
                    runCatching {
                        if (active.isPlaying) {
                            active.pause()
                            playing = false
                        } else {
                            active.start()
                            playing = true
                        }
                    }.onFailure { failed = true }
                },
                enabled = prepared && !failed,
                modifier = Modifier.size(76.dp).clip(CircleShape).background(ViewerPurple),
            ) {
                Icon(
                    if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.audio_pause else R.string.audio_play),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = {
                position = (position + 10_000).coerceAtMost(duration)
                runCatching { player?.seekTo(position) }
            }, enabled = prepared && !failed) {
                Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(R.string.audio_forward_10))
            }
        }
    }
}

@Composable
private fun VideoDocumentPlayer(modifier: Modifier, entry: FileEntry) {
    val context = LocalContext.current
    var videoView by remember(entry.location) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(entry.location) {
        onDispose { runCatching { videoView?.stopPlayback() } }
    }
    Column(
        modifier = modifier.fillMaxSize().background(Color(0xFF16161F)).padding(12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)),
            factory = { viewContext ->
                VideoView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    videoView = this
                    setMediaController(MediaController(viewContext).also { it.setAnchorView(this) })
                    setVideoURI(openUri(context, entry))
                    setOnPreparedListener { media -> media.isLooping = false }
                }
            },
            update = { videoView = it },
        )
        Text(
            entry.name,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(stringResource(R.string.video_playback_hint), color = Color(0xFFCBCBD7), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TextDocumentReader(modifier: Modifier, entry: FileEntry) {
    val context = LocalContext.current
    val content by produceState<String?>(initialValue = null, key1 = entry.location) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(openUri(context, entry))?.use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        buildString {
                            val buffer = CharArray(8192)
                            val limit = 1_000_000
                            var count = reader.read(buffer)
                            while (count >= 0 && length < limit) {
                                append(buffer, 0, count.coerceAtMost(limit - length))
                                count = if (length < limit) reader.read(buffer) else -1
                            }
                            if (count >= 0) append("\n\n" + context.getString(R.string.text_preview_shortened))
                        }
                    }
                } ?: error("File unavailable.")
            }.getOrNull()
        }
    }
    Column(
        modifier = modifier.fillMaxSize().background(ViewerPage).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.text_preview_title), color = ViewerMuted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
        ) {
            when {
                content != null -> Text(
                    content!!,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    color = ViewerInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ViewerPurple)
                }
            }
        }
        Text(entry.name, color = ViewerMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ZipDocumentPreview(modifier: Modifier, entry: FileEntry, onOpenExternal: () -> Unit) {
    val context = LocalContext.current
    val fileNames by produceState<List<String>?>(initialValue = null, key1 = entry.location) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ZipInputStream(context.contentResolver.openInputStream(openUri(context, entry)) ?: error("Archive unavailable."))
                    .use { zip ->
                        buildList {
                            var item = zip.nextEntry
                            while (item != null && size < 250) {
                                add((if (item.isDirectory) "📁 " else "📄 ") + item.name)
                                zip.closeEntry()
                                item = zip.nextEntry
                            }
                        }
                    }
            }.getOrNull()
        }
    }
    ViewerPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Archive, contentDescription = null, tint = ViewerPurple, modifier = Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = ViewerInk, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.zip_preview_subtitle), color = ViewerMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            if (fileNames == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ViewerPurple) }
            } else if (fileNames!!.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.zip_preview_empty), color = ViewerMuted) }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
                    fileNames!!.forEach { name ->
                        Text(name, color = ViewerInk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 5.dp))
                    }
                }
            }
        }
        Button(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.FileOpen, contentDescription = null)
            Text(stringResource(R.string.open_with_another_app), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ExternalDocumentCard(modifier: Modifier, entry: FileEntry, onOpenExternal: () -> Unit) {
    val icon = when (entry.category) {
        FileCategory.AUDIO -> Icons.Outlined.MusicNote
        FileCategory.VIDEOS -> Icons.Outlined.VideoLibrary
        FileCategory.IMAGES -> Icons.Outlined.Image
        FileCategory.ARCHIVES -> Icons.Outlined.Archive
        else -> Icons.Outlined.Description
    }
    Column(
        modifier = modifier.fillMaxSize().background(ViewerPage).padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFFEAE7FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ViewerPurple, modifier = Modifier.size(52.dp))
        }
        Text(entry.name, modifier = Modifier.fillMaxWidth().padding(top = 22.dp), color = ViewerInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            entry.mimeType ?: "Unknown file type",
            modifier = Modifier.padding(top = 8.dp),
            color = ViewerMuted,
            textAlign = TextAlign.Center,
        )
        Text(
            formatMediaBytes(entry.sizeBytes),
            modifier = Modifier.padding(top = 4.dp),
            color = ViewerMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ViewerPurple, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.external_viewer_title), color = ViewerInk, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.external_viewer_body), color = ViewerMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Icon(Icons.Outlined.FileOpen, contentDescription = null)
            Text(stringResource(R.string.open_with_another_app), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun formatMediaTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1000)
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun formatMediaBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    var value = bytes.toDouble()
    val units = arrayOf("KB", "MB", "GB", "TB")
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}
