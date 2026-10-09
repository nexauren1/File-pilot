package com.nexauren.filepilot

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.nexauren.filepilot.data.FileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.min

/**
 * Built-in viewers for common local file types. Unsupported formats intentionally fall back
 * to Android's app chooser instead of pretending that FilePilot can render every format.
 */
class FileViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "File" }
        val mime = intent.getStringExtra(EXTRA_MIME)
        if (uri == null) {
            finish()
            return
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = ViewerPurple)) {
                FileViewerScreen(
                    uri = uri,
                    fileName = name,
                    mimeType = mime,
                    onBack = { finish() },
                    onOpenExternally = { openExternally(uri, name, mime) },
                )
            }
        }
    }

    private fun openExternally(uri: Uri, name: String, mime: String?) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, name)) }
    }

    companion object {
        const val EXTRA_URI = "com.nexauren.filepilot.viewer.URI"
        const val EXTRA_NAME = "com.nexauren.filepilot.viewer.NAME"
        const val EXTRA_MIME = "com.nexauren.filepilot.viewer.MIME"
    }
}

private val ViewerPurple = androidx.compose.ui.graphics.Color(0xFF5A45D6)
private val ViewerBackground = androidx.compose.ui.graphics.Color(0xFFF5F5FA)

private enum class ViewerKind { PDF, IMAGE, VIDEO, AUDIO, TEXT, EXTERNAL }

private fun viewerKind(fileName: String, mimeType: String?): ViewerKind {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        extension == "pdf" || mimeType.equals("application/pdf", ignoreCase = true) -> ViewerKind.PDF
        extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "tif", "tiff") || mimeType?.startsWith("image/") == true -> ViewerKind.IMAGE
        extension in setOf("mp4", "mkv", "mov", "webm", "avi", "3gp", "m4v", "mpeg", "mpg") || mimeType?.startsWith("video/") == true -> ViewerKind.VIDEO
        extension in setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus", "mid", "midi") || mimeType?.startsWith("audio/") == true -> ViewerKind.AUDIO
        extension in setOf("txt", "md", "csv", "log", "json", "xml", "html", "htm", "yaml", "yml", "ini", "conf", "properties") || mimeType?.startsWith("text/") == true -> ViewerKind.TEXT
        else -> ViewerKind.EXTERNAL
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileViewerScreen(
    uri: Uri,
    fileName: String,
    mimeType: String?,
    onBack: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    val kind = remember(fileName, mimeType) { viewerKind(fileName, mimeType) }
    Scaffold(
        containerColor = ViewerBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (kind) {
                                ViewerKind.PDF -> "PDF"
                                ViewerKind.IMAGE -> "Image"
                                ViewerKind.VIDEO -> "Video"
                                ViewerKind.AUDIO -> "Audio"
                                ViewerKind.TEXT -> "Text file"
                                ViewerKind.EXTERNAL -> "File preview"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenExternally) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open with another app")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(ViewerBackground)) {
            when (kind) {
                ViewerKind.PDF -> PdfViewer(uri)
                ViewerKind.IMAGE -> ImageViewer(uri, fileName)
                ViewerKind.VIDEO -> VideoViewer(uri)
                ViewerKind.AUDIO -> AudioViewer(uri, fileName)
                ViewerKind.TEXT -> TextViewer(uri)
                ViewerKind.EXTERNAL -> Column(
                    Modifier.fillMaxSize().padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = ViewerPurple, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(fileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.viewer_unsupported), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onOpenExternally) { Text(androidx.compose.ui.res.stringResource(R.string.viewer_open_with)) }
                }
            }
        }
    }
}

@Composable
private fun ImageViewer(uri: Uri, fileName: String) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { decodePreviewBitmap(context, uri) }
        failed = bitmap == null
    }
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else if (failed) {
            Text(androidx.compose.ui.res.stringResource(R.string.viewer_image_error), textAlign = TextAlign.Center)
        } else {
            CircularProgressIndicator(color = ViewerPurple)
        }
    }
}

private fun decodePreviewBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 2200) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}

@Composable
private fun PdfViewer(uri: Uri) {
    val context = LocalContext.current
    var pageCount by remember(uri) { mutableIntStateOf(-1) }
    LaunchedEffect(uri) {
        pageCount = withContext(Dispatchers.IO) { readPdfPageCount(context, uri) }
    }
    when {
        pageCount < 0 -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = ViewerPurple)
            Spacer(Modifier.height(12.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.viewer_pdf_loading))
        }
        pageCount == 0 -> Text(
            androidx.compose.ui.res.stringResource(R.string.viewer_pdf_error),
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            textAlign = TextAlign.Center,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pageCount, key = { it }) { index -> PdfPage(uri, index) }
        }
    }
}

private fun readPdfPageCount(context: android.content.Context, uri: Uri): Int {
    return runCatching {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
        descriptor.use { pfd -> PdfRenderer(pfd).use { renderer -> renderer.pageCount } }
    }.getOrDefault(0)
}

@Composable
private fun PdfPage(uri: Uri, index: Int) {
    val context = LocalContext.current
    var bitmap by remember(uri, index) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri, index) { mutableStateOf(false) }
    LaunchedEffect(uri, index) {
        bitmap = withContext(Dispatchers.IO) { renderPdfPage(context, uri, index) }
        failed = bitmap == null
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = "PDF page ${index + 1}",
                modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(10.dp)),
                contentScale = ContentScale.FillWidth,
            )
        } else if (failed) {
            Text(androidx.compose.ui.res.stringResource(R.string.viewer_pdf_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), color = ViewerPurple)
        }
        Text(" ${index + 1} ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
    }
}

private fun renderPdfPage(context: android.content.Context, uri: Uri, index: Int): Bitmap? {
    return runCatching {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (index !in 0 until renderer.pageCount) return null
                renderer.openPage(index).use { page ->
                    val scale = min(2f, min(1200f / page.width.coerceAtLeast(1), 1600f / page.height.coerceAtLeast(1)))
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }.getOrNull()
}

@Composable
private fun VideoViewer(uri: Uri) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).alignInVideoCenter(),
        factory = { viewContext ->
            VideoView(viewContext).apply {
                setBackgroundColor(AndroidColor.BLACK)
                val controller = MediaController(viewContext)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(uri)
                requestFocus()
            }
        },
    )
}

private fun Modifier.alignInVideoCenter(): Modifier = this.padding(12.dp)

@Composable
private fun AudioViewer(uri: Uri, fileName: String) {
    val context = LocalContext.current
    val player = remember(uri) { MediaPlayer() }
    var prepared by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    var failed by remember(uri) { mutableStateOf(false) }
    var duration by remember(uri) { mutableIntStateOf(0) }
    var position by remember(uri) { mutableIntStateOf(0) }

    DisposableEffect(player, uri) {
        player.setOnPreparedListener { media ->
            duration = media.duration.coerceAtLeast(0)
            prepared = true
        }
        player.setOnCompletionListener {
            playing = false
            position = duration
        }
        player.setOnErrorListener { _, _, _ ->
            failed = true
            playing = false
            true
        }
        try {
            player.setDataSource(context, uri)
            player.prepareAsync()
        } catch (_: Exception) {
            failed = true
        }
        onDispose {
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
    }

    LaunchedEffect(player, prepared) {
        while (prepared) {
            if (playing) position = runCatching { player.currentPosition.coerceAtLeast(0) }.getOrDefault(position)
            delay(400)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(132.dp).background(ViewerPurple.copy(alpha = 0.11f), RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = ViewerPurple, modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(fileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        when {
            failed -> Text(androidx.compose.ui.res.stringResource(R.string.viewer_audio_error), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            !prepared -> {
                CircularProgressIndicator(color = ViewerPurple)
                Spacer(Modifier.height(8.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.viewer_audio_loading))
            }
            else -> {
                Slider(
                    value = if (duration > 0) position.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { fraction ->
                        position = (fraction * duration).toInt().coerceIn(0, duration)
                        runCatching { player.seekTo(position) }
                    },
                    enabled = duration > 0,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlayerTime(position), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(formatPlayerTime(duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    runCatching {
                        if (playing) player.pause() else player.start()
                        playing = !playing
                    }.onFailure { failed = true }
                }) {
                    Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(androidx.compose.ui.res.stringResource(if (playing) R.string.viewer_pause else R.string.viewer_play))
                }
            }
        }
    }
}

private fun formatPlayerTime(milliseconds: Int): String {
    val seconds = (milliseconds.coerceAtLeast(0) / 1000)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun TextViewer(uri: Uri) {
    val context = LocalContext.current
    var text by remember(uri) { mutableStateOf<String?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var truncated by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val result = withContext(Dispatchers.IO) { readTextPreview(context, uri) }
        text = result.first
        truncated = result.second
        failed = result.first == null
    }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        if (text == null && !failed) {
            CircularProgressIndicator(color = ViewerPurple)
            Spacer(Modifier.height(12.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.viewer_text_loading))
        } else if (failed) {
            Text(androidx.compose.ui.res.stringResource(R.string.viewer_text_error), color = MaterialTheme.colorScheme.error)
        } else {
            if (truncated) {
                Text(androidx.compose.ui.res.stringResource(R.string.viewer_text_truncated), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp)).padding(14.dp)) {
                item {
                    Text(
                        text = text.orEmpty(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

private fun readTextPreview(context: android.content.Context, uri: Uri): Pair<String?, Boolean> {
    return runCatching {
        val stream = context.contentResolver.openInputStream(uri) ?: return null to false
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (total < 1_048_576) {
                val allowed = min(buffer.size, 1_048_576 - total)
                val count = input.read(buffer, 0, allowed)
                if (count <= 0) break
                output.write(buffer, 0, count)
                total += count
            }
            val truncated = total >= 1_048_576 && input.read() >= 0
            String(output.toByteArray(), StandardCharsets.UTF_8) to truncated
        }
    }.getOrElse { null to false }
}
