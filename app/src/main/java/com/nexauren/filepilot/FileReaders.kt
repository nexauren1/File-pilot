package com.nexauren.filepilot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.nexauren.filepilot.data.FileCategory
import com.nexauren.filepilot.data.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import android.graphics.Bitmap as AndroidBitmap
import android.view.ViewGroup

private enum class ReaderKind { PDF, IMAGE, VIDEO, AUDIO, TEXT, EXTERNAL }

internal fun supportsInternalReader(entry: FileEntry): Boolean {
    if (entry.isDirectory) return false
    return readerKind(entry) != ReaderKind.EXTERNAL
}

private fun readerKind(entry: FileEntry): ReaderKind {
    val extension = entry.name.substringAfterLast('.', "").lowercase()
    return when {
        extension == "pdf" -> ReaderKind.PDF
        entry.category == FileCategory.IMAGES -> ReaderKind.IMAGE
        entry.category == FileCategory.VIDEOS -> ReaderKind.VIDEO
        entry.category == FileCategory.AUDIO -> ReaderKind.AUDIO
        extension in setOf("txt", "md", "csv", "tsv", "json", "xml", "html", "htm", "log", "ini", "yaml", "yml", "properties", "kt", "java", "py", "js", "css") -> ReaderKind.TEXT
        else -> ReaderKind.EXTERNAL
    }
}

@Composable
internal fun InternalFileReaderScreen(
    entry: FileEntry,
    modifier: Modifier = Modifier,
    onOpenExternal: () -> Unit,
) {
    when (readerKind(entry)) {
        ReaderKind.PDF -> PdfReaderScreen(entry, modifier)
        ReaderKind.IMAGE -> ImageReaderScreen(entry, modifier)
        ReaderKind.VIDEO -> VideoReaderScreen(entry, modifier)
        ReaderKind.AUDIO -> AudioReaderScreen(entry, modifier)
        ReaderKind.TEXT -> TextReaderScreen(entry, modifier)
        ReaderKind.EXTERNAL -> UnsupportedReaderScreen(entry, modifier, onOpenExternal)
    }
}

@Composable
private fun PdfReaderScreen(entry: FileEntry, modifier: Modifier) {
    val context = LocalContext.current
    var renderer by remember(entry.location) { mutableStateOf<PdfRenderer?>(null) }
    var error by remember(entry.location) { mutableStateOf(false) }
    LaunchedEffect(entry.location) {
        val result = withContext(Dispatchers.IO) { runCatching { openPdfRenderer(context, entry) } }
        renderer = result.getOrNull()
        error = result.isFailure
    }
    DisposableEffect(entry.location, renderer) {
        val owned = renderer
        onDispose { runCatching { owned?.close() } }
    }

    when {
        error -> ReaderErrorState(
            modifier = modifier,
            icon = Icons.Outlined.Description,
            title = context.getString(R.string.reader_pdf_error_title),
            body = context.getString(R.string.reader_pdf_error_body),
        )
        renderer == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> {
            val pdf = renderer ?: return
            Column(modifier.fillMaxSize().background(Color(0xFFEDECF3))) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.reader_pdf_title), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.reader_pdf_page_count, pdf.pageCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.reader_pdf_zoom_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(pdf.pageCount, key = { it }) { pageIndex ->
                        PdfPageCard(renderer = pdf, pageIndex = pageIndex)
                    }
                }
            }
        }
    }
}

private fun openPdfRenderer(context: Context, entry: FileEntry): PdfRenderer {
    val descriptor = if (entry.location.startsWith("content://")) {
        context.contentResolver.openFileDescriptor(Uri.parse(entry.location), "r")
    } else {
        ParcelFileDescriptor.open(File(entry.location), ParcelFileDescriptor.MODE_READ_ONLY)
    } ?: error("Could not open this PDF file.")
    return try {
        PdfRenderer(descriptor)
    } catch (error: Exception) {
        descriptor.close()
        throw error
    }
}

@Composable
private fun PdfPageCard(renderer: PdfRenderer, pageIndex: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = renderer, key2 = pageIndex) {
        value = withContext(Dispatchers.IO) {
            runCatching { renderPdfPage(renderer, pageIndex, 1200) }.getOrNull()
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        val pageBitmap = bitmap
        if (pageBitmap == null) {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column {
                Image(
                    bitmap = pageBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.reader_pdf_page_description, pageIndex + 1),
                    modifier = Modifier.fillMaxWidth().padding(3.dp),
                )
                Text(
                    stringResource(R.string.reader_pdf_page_label, pageIndex + 1),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun renderPdfPage(renderer: PdfRenderer, pageIndex: Int, maxWidth: Int): Bitmap {
    val page = renderer.openPage(pageIndex)
    return try {
        val ratio = maxWidth.toFloat() / page.width.coerceAtLeast(1)
        val targetWidth = (page.width * ratio).toInt().coerceIn(1, maxWidth)
        val targetHeight = (page.height * ratio).toInt().coerceIn(1, 2400)
        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    } finally {
        page.close()
    }
}

@Composable
private fun ImageReaderScreen(entry: FileEntry, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = entry.location) {
        value = loadImage(context, entry)
    }
    var scale by remember(entry.location) { mutableFloatStateOf(1f) }
    var offsetX by remember(entry.location) { mutableFloatStateOf(0f) }
    var offsetY by remember(entry.location) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        if (newScale <= 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF17171D)),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = entry.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                    .transformable(transformState),
            )
        }
        TextButton(
            onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color.White)
            Text(stringResource(R.string.reader_reset_zoom), color = Color.White)
        }
    }
}

private suspend fun loadImage(context: Context, entry: FileEntry): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val stream = openEntryStream(context, entry)
        stream.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}

@Composable
private fun VideoReaderScreen(entry: FileEntry, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(entry.location) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(entryUri(entry)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Column(
        modifier.fillMaxSize().background(Color(0xFF15151C)),
        verticalArrangement = Arrangement.Center,
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = true
                    controllerAutoShow = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            },
            update = { view -> if (view.player !== player) view.player = player },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Text(
            entry.name,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudioReaderScreen(entry: FileEntry, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember(entry.location) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(entryUri(entry)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    var position by remember(entry.location) { mutableLongStateOf(0L) }
    var duration by remember(entry.location) { mutableLongStateOf(0L) }
    var playing by remember(entry.location) { mutableStateOf(true) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            playing = player.isPlaying
            delay(350)
        }
    }
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(190.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(42.dp)).background(Color(0xFFEAE7FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color(0xFF5141C2), modifier = Modifier.size(84.dp))
        }
        Text(
            entry.name,
            modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(stringResource(R.string.reader_audio_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = { fraction ->
                if (duration > 0L) player.seekTo((duration * fraction).toLong())
            },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlaybackTime(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatPlaybackTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            IconButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                Icon(Icons.Outlined.SkipPrevious, contentDescription = stringResource(R.string.reader_skip_back), modifier = Modifier.size(30.dp))
            }
            IconButton(
                onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                    playing = player.isPlaying
                },
                modifier = Modifier.size(68.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF5141C2)),
            ) {
                Icon(
                    if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.reader_pause else R.string.reader_play),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
            IconButton(onClick = { player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration)) }) {
                Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(R.string.reader_skip_forward), modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { player.seekTo(0L); player.play() }) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text(stringResource(R.string.reader_restart), modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun TextReaderScreen(entry: FileEntry, modifier: Modifier) {
    val context = LocalContext.current
    var content by remember(entry.location) { mutableStateOf<String?>(null) }
    var tooLarge by remember(entry.location) { mutableStateOf(false) }
    LaunchedEffect(entry.location) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                openEntryStream(context, entry).use { stream ->
                    val bytes = stream.readNBytes(1_500_001)
                    if (bytes.size > 1_500_000) {
                        tooLarge = true
                        null
                    } else {
                        bytes.toString(Charsets.UTF_8)
                    }
                }
            }
        }.getOrNull()
        content = result
    }
    when {
        tooLarge -> ReaderErrorState(
            modifier,
            Icons.Outlined.Description,
            stringResource(R.string.reader_text_too_large_title),
            stringResource(R.string.reader_text_too_large_body),
        )
        content == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> Column(modifier.fillMaxSize().background(Color.White)) {
            Text(
                stringResource(R.string.reader_text_preview),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    content.orEmpty(),
                    modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun UnsupportedReaderScreen(entry: FileEntry, modifier: Modifier, onOpenExternal: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.reader_external_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            stringResource(R.string.reader_external_body, entry.name),
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Text(stringResource(R.string.reader_open_with))
        }
    }
}

@Composable
private fun ReaderErrorState(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun openEntryStream(context: Context, entry: FileEntry): InputStream {
    return if (entry.location.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(entry.location)) ?: error("File is not readable")
    } else {
        FileInputStream(File(entry.location))
    }
}

private fun entryUri(entry: FileEntry): Uri =
    if (entry.location.startsWith("content://")) Uri.parse(entry.location) else Uri.fromFile(File(entry.location))

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return String.format(
        java.util.Locale.getDefault(),
        "%02d:%02d",
        totalSeconds / 60L,
        totalSeconds % 60L,
    )
}
