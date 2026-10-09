package com.nexauren.filepilot

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.nexauren.filepilot.data.FileCategory
import com.nexauren.filepilot.data.FileEntry
import com.nexauren.filepilot.data.FileRepository
import com.nexauren.filepilot.data.FavoritesRepository
import com.nexauren.filepilot.data.RecentFilesRepository
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// FilePilot keeps the familiar, straightforward file-manager layout with its own violet identity.
private val AppBlue = Color(0xFF5A45D6)
private val PageBackground = Color(0xFFF5F5FA)
private val SecondaryText = Color(0xFF656579)
private val SoftBlue = Color(0xFFEAE7FF)
private val Ink = Color(0xFF202033)
private val Teal = Color(0xFF087F8C)
private val Green = Color(0xFF26845A)
private val Coral = Color(0xFFBA4B58)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = AppBlue,
                    onPrimary = Color.White,
                    background = PageBackground,
                    onBackground = Color(0xFF201B2C),
                    surface = Color.White,
                    onSurface = Color(0xFF172033),
                    surfaceVariant = Color(0xFFF1EFF7),
                    onSurfaceVariant = SecondaryText,
                ),
            ) { FilePilotApp() }
        }
    }
}

private enum class AppTab { HOME, BROWSE, CLEAN, SHARE, RECENTS, FAVORITES, SAFE, TOOLS, TRASH, CLEANER, APPS, SECURITY, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilePilotApp() {
    val context = LocalContext.current
    val stack = remember { mutableStateListOf<String>() }
    var rootLocation by remember { mutableStateOf(FileRepository.initialLocation(context)) }
    var tabName by rememberSaveable { mutableStateOf(AppTab.HOME.name) }
    var filterName by rememberSaveable { mutableStateOf(FileCategory.ALL.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var mainMenuExpanded by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var recentEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var trashEntries by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var trashRestorePendingId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var vaultPendingMove by remember { mutableStateOf<FileEntry?>(null) }
    var transferTarget by remember { mutableStateOf<FileEntry?>(null) }
    var transferAsMove by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tab = runCatching { AppTab.valueOf(tabName) }.getOrDefault(AppTab.HOME)
    val filter = runCatching { FileCategory.valueOf(filterName) }.getOrDefault(FileCategory.ALL)
    val currentLocation = stack.lastOrNull()

    fun resetToRoot() {
        rootLocation = FileRepository.initialLocation(context)
        stack.clear()
        rootLocation?.let { stack.add(it) }
        reload++
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resetToRoot() }

    val legacyPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resetToRoot() }

    fun launchAllFileSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            try {
                settingsLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            val wanted = buildList {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            legacyPermissionsLauncher.launch(wanted.toTypedArray())
        }
    }

    val mediaPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        resetToRoot()
        launchAllFileSettings()
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                FileRepository.saveSelectedTree(context, uri)
                rootLocation = uri.toString()
                stack.clear()
                stack.add(uri.toString())
                filterName = FileCategory.ALL.name
                query = ""
                tabName = AppTab.BROWSE.name
                reload++
            } catch (_: SecurityException) {
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_operation)) }
            }
        }
    }

    val destinationFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val target = transferTarget
        val shouldMove = transferAsMove
        transferTarget = null
        transferAsMove = false
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    FileRepository.copyToTree(context, target, uri)
                }
                if (result.isFailure) {
                    snackbarHostState.showSnackbar(context.getString(R.string.error_operation))
                } else if (shouldMove) {
                    val removed = withContext(Dispatchers.IO) {
                        FileRepository.delete(context, target).isSuccess
                    }
                    snackbarHostState.showSnackbar(
                        context.getString(if (removed) R.string.move_success else R.string.move_partial)
                    )
                    reload++
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.copy_success))
                    reload++
                }
            }
        }
    }


    val shareFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share))) }
                .onFailure { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_operation)) } }
        }
    }

    val trashRestorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val id = trashRestorePendingId
        trashRestorePendingId = null
        if (uri != null && id != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val item = TrashRepository.find(context, id)
                    if (item == null) Result.failure(IllegalStateException("Trash item not found"))
                    else TrashRepository.restoreToTree(context, item, uri)
                }
                snackbarHostState.showSnackbar(
                    context.getString(if (result.isSuccess) R.string.trash_restore_success else R.string.trash_operation_error)
                )
                reload++
            }
        }
    }

    fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                )
            )
        } else {
            launchAllFileSettings()
        }
    }

    LaunchedEffect(rootLocation) {
        stack.clear()
        rootLocation?.let { stack.add(it) }
    }

    LaunchedEffect(tabName, reload) {
        when (tab) {
            AppTab.FAVORITES -> favorites = withContext(Dispatchers.IO) { FavoritesRepository.list(context) }
            AppTab.RECENTS, AppTab.HOME, AppTab.SHARE -> recentEntries = withContext(Dispatchers.IO) { RecentFilesRepository.list(context) }
            AppTab.TRASH -> trashEntries = withContext(Dispatchers.IO) { TrashRepository.list(context) }
            else -> Unit
        }
    }

    LaunchedEffect(rootLocation, currentLocation, filterName, query, reload, tabName) {
        if (tab == AppTab.BROWSE) {
            val collectionMode = filter != FileCategory.ALL
            val listRoot = if (collectionMode) rootLocation else currentLocation
            if (listRoot != null) {
                loading = true
                val loaded = withContext(Dispatchers.IO) {
                    if (collectionMode) FileRepository.listFilesRecursively(context, listRoot)
                    else FileRepository.listChildren(context, listRoot)
                }
                val filtered = loaded.filter { entry ->
                    val typeMatches = if (collectionMode) {
                        !entry.isDirectory && filter.matches(entry.name, false, entry.location)
                    } else {
                        filter.matches(entry.name, entry.isDirectory, entry.location)
                    }
                    typeMatches && entry.name.contains(query.trim(), ignoreCase = true)
                }
                entries = if (collectionMode) filtered.sortedByDescending { it.modifiedAt } else filtered
                loading = false
            } else {
                entries = emptyList()
                loading = false
            }
        }
    }

    fun runFileOperation(successText: String, operation: suspend () -> Result<Unit>) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { operation() }
            snackbarHostState.showSnackbar(
                if (result.isSuccess) successText else context.getString(R.string.error_operation)
            )
            reload++
        }
    }

    fun moveToTrash(entry: FileEntry) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { TrashRepository.moveToTrash(context, entry) }
            snackbarHostState.showSnackbar(
                context.getString(if (result.isSuccess) R.string.trash_success else R.string.trash_operation_error)
            )
            reload++
        }
    }

    fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            stack.add(entry.location)
            filterName = FileCategory.ALL.name
            query = ""
            showSearch = false
            tabName = AppTab.BROWSE.name
            return
        }
        try {
            val uri = FileRepository.shareUri(context, entry)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, entry.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(viewIntent, entry.name))
            RecentFilesRepository.recordOpen(context, entry)
            reload++
        } catch (_: Exception) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.open_error)) }
        }
    }

    BackHandler(enabled = tab == AppTab.RECENTS || tab == AppTab.FAVORITES || tab == AppTab.CLEAN || tab == AppTab.SHARE) {
        tabName = AppTab.HOME.name
    }

    BackHandler(enabled = tab == AppTab.SAFE) {
        tabName = AppTab.HOME.name
        vaultPendingMove = null
    }

    BackHandler(enabled = tab == AppTab.TOOLS) {
        tabName = AppTab.HOME.name
    }

    BackHandler(enabled = tab == AppTab.TRASH || tab == AppTab.CLEANER || tab == AppTab.APPS || tab == AppTab.SECURITY) {
        tabName = AppTab.TOOLS.name
    }

    BackHandler(enabled = tab == AppTab.BROWSE && stack.size > 1) {
        stack.removeAt(stack.lastIndex)
        filterName = FileCategory.ALL.name
        query = ""
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch && (tab == AppTab.BROWSE || tab == AppTab.HOME || tab == AppTab.SHARE)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                        )
                    } else {
                        Column {
                            Text(
                                when (tab) {
                                    AppTab.HOME -> stringResource(R.string.tab_browse)
                                    AppTab.BROWSE -> if (filter == FileCategory.ALL) {
                                        currentLocation?.let { displayLocationName(context, it) } ?: stringResource(R.string.browse_title)
                                    } else categoryLabel(filter)
                                    AppTab.CLEAN, AppTab.CLEANER -> stringResource(R.string.tab_clean)
                                    AppTab.SHARE -> stringResource(R.string.tab_share)
                                    AppTab.RECENTS -> stringResource(R.string.recents_title)
                                    AppTab.FAVORITES -> stringResource(R.string.favorites_title)
                                    AppTab.SAFE -> stringResource(R.string.vault_title)
                                    AppTab.TOOLS -> stringResource(R.string.toolbox_title)
                                    AppTab.TRASH -> stringResource(R.string.trash_title)
                                    AppTab.CLEANER -> stringResource(R.string.cleaner_title)
                                    AppTab.APPS -> stringResource(R.string.apps_title)
                                    AppTab.SECURITY -> stringResource(R.string.security_title)
                                    AppTab.SETTINGS -> stringResource(R.string.settings_title)
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            if (tab == AppTab.HOME) {
                                Text(
                                    stringResource(R.string.home_subtitle),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SecondaryText,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (tab == AppTab.SAFE || (tab == AppTab.BROWSE && stack.size > 1)) {
                        IconButton(onClick = {
                            if (tab == AppTab.SAFE) {
                                tabName = AppTab.HOME.name
                                vaultPendingMove = null
                            } else {
                                stack.removeAt(stack.lastIndex)
                            }
                        }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (tab == AppTab.HOME || tab == AppTab.BROWSE || tab == AppTab.SHARE) {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) query = ""
                        }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_hint))
                        }
                    }
                    if (tab == AppTab.BROWSE && filter == FileCategory.ALL) {
                        IconButton(onClick = {
                            newFolderName = ""
                            showCreateFolderDialog = true
                        }) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
                        }
                    }
                    IconButton(onClick = { mainMenuExpanded = true }) {
                        Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.menu_title))
                    }
                    DropdownMenu(expanded = mainMenuExpanded, onDismissRequest = { mainMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.tab_browse)) }, onClick = { mainMenuExpanded = false; tabName = AppTab.HOME.name; filterName = FileCategory.ALL.name; query = "" })
                        DropdownMenuItem(text = { Text(stringResource(R.string.recents_title)) }, onClick = { mainMenuExpanded = false; tabName = AppTab.RECENTS.name })
                        DropdownMenuItem(text = { Text(stringResource(R.string.favorites_title)) }, onClick = { mainMenuExpanded = false; tabName = AppTab.FAVORITES.name })
                        DropdownMenuItem(text = { Text(stringResource(R.string.vault_title)) }, onClick = { mainMenuExpanded = false; vaultPendingMove = null; tabName = AppTab.SAFE.name })
                        DropdownMenuItem(text = { Text(stringResource(R.string.toolbox_title)) }, onClick = { mainMenuExpanded = false; tabName = AppTab.TOOLS.name })
                        DropdownMenuItem(text = { Text(stringResource(R.string.trash_title)) }, onClick = { mainMenuExpanded = false; tabName = AppTab.TRASH.name })
                        DropdownMenuItem(text = { Text(stringResource(R.string.apps_title)) }, onClick = { mainMenuExpanded = false; tabName = AppTab.APPS.name })
                        DropdownMenuItem(text = { Text(stringResource(R.string.security_title)) }, onClick = { mainMenuExpanded = false; tabName = AppTab.SECURITY.name })
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { mainMenuExpanded = false; tabName = AppTab.SETTINGS.name })
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = tab == AppTab.CLEAN || tab == AppTab.CLEANER,
                    onClick = { tabName = AppTab.CLEAN.name },
                    icon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_clean)) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.HOME || tab == AppTab.BROWSE || tab == AppTab.RECENTS || tab == AppTab.FAVORITES || tab == AppTab.SAFE || tab == AppTab.TOOLS || tab == AppTab.TRASH || tab == AppTab.APPS || tab == AppTab.SECURITY || tab == AppTab.SETTINGS,
                    onClick = { tabName = AppTab.HOME.name; filterName = FileCategory.ALL.name; query = "" },
                    icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_browse)) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.SHARE,
                    onClick = { tabName = AppTab.SHARE.name },
                    icon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_share)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (tab) {
            AppTab.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                hasAccess = FileRepository.hasStorageAccess(context),
                hasLocation = rootLocation != null,
                onOpenFiles = { tabName = AppTab.BROWSE.name; filterName = FileCategory.ALL.name },
                onOpenRecents = { tabName = AppTab.RECENTS.name },
                onOpenFavorites = { tabName = AppTab.FAVORITES.name },
                onOpenSafeFolder = { vaultPendingMove = null; tabName = AppTab.SAFE.name },
                recentFiles = recentEntries,
                onOpenRecentEntry = ::openEntry,
                onCategory = { category ->
                    filterName = category.name
                    query = ""
                    stack.clear()
                    rootLocation?.let { stack.add(it) }
                    tabName = AppTab.BROWSE.name
                },
                onRequestAccess = ::requestStorageAccess,
                onChooseFolder = { folderPicker.launch(null) },
            )
            AppTab.CLEAN, AppTab.CLEANER -> CleanerScreen(
                modifier = Modifier.padding(padding),
                rootLocation = rootLocation,
                onRequestAccess = ::requestStorageAccess,
                onMoveToTrash = ::moveToTrash,
            )
            AppTab.SHARE -> ShareScreen(
                modifier = Modifier.padding(padding),
                recentFiles = recentEntries,
                onShare = { entry ->
                    runCatching {
                        val uri = FileRepository.shareUri(context, entry)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = entry.mimeType ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, entry.name))
                    }.onFailure { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_operation)) } }
                },
                onPickFile = { shareFilePicker.launch(arrayOf("*/*")) },
            )
            AppTab.TOOLS -> ToolsScreen(
                modifier = Modifier.padding(padding),
                onOpenTrash = { tabName = AppTab.TRASH.name },
                onOpenCleaner = { tabName = AppTab.CLEANER.name },
                onOpenApps = { tabName = AppTab.APPS.name },
                onOpenSecurity = { tabName = AppTab.SECURITY.name },
            )
            AppTab.TRASH -> TrashScreen(
                modifier = Modifier.padding(padding),
                items = trashEntries,
                onRestore = { id -> trashRestorePendingId = id; trashRestorePicker.launch(null) },
                onDeletePermanently = { id ->
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { TrashRepository.deletePermanently(context, id) }
                        snackbarHostState.showSnackbar(context.getString(if (result.isSuccess) R.string.trash_permanent_deleted else R.string.trash_operation_error))
                        reload++
                    }
                },
                onEmpty = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { TrashRepository.empty(context) }
                        snackbarHostState.showSnackbar(context.getString(if (result.isSuccess) R.string.trash_emptied else R.string.trash_operation_error))
                        reload++
                    }
                },
            )
            AppTab.CLEANER -> CleanerScreen(
                modifier = Modifier.padding(padding),
                rootLocation = rootLocation,
                onRequestAccess = ::requestStorageAccess,
                onMoveToTrash = ::moveToTrash,
            )
            AppTab.APPS -> AppManagerScreen(
                modifier = Modifier.padding(padding),
                onOpenInfo = { packageName ->
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
                        .onFailure { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_operation)) } }
                },
                onUninstall = { packageName ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))) }
                        .onFailure { scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_operation)) } }
                },
            )
            AppTab.SECURITY -> SecurityScreen(
                modifier = Modifier.padding(padding),
                rootLocation = rootLocation,
                onRequestAccess = ::requestStorageAccess,
            )
            AppTab.BROWSE -> {
                if (rootLocation == null) {
                    PermissionScreen(
                        modifier = Modifier.padding(padding),
                        onRequestAccess = ::requestStorageAccess,
                        onChooseFolder = { folderPicker.launch(null) },
                    )
                } else {
                    FileListScreen(
                        modifier = Modifier.padding(padding),
                        entries = entries,
                        loading = loading,
                        onOpen = ::openEntry,
                        onRename = { entry -> renameTarget = entry; renameText = entry.name },
                        onDelete = { deleteTarget = it },
                        onMoveToSafeFolder = { entry ->
                            vaultPendingMove = entry
                            tabName = AppTab.SAFE.name
                        },
                        onCopyToFolder = { entry ->
                            transferTarget = entry
                            transferAsMove = false
                            destinationFolderPicker.launch(null)
                        },
                        onMoveToFolder = { entry ->
                            transferTarget = entry
                            transferAsMove = true
                            destinationFolderPicker.launch(null)
                        },
                        onToggleFavorite = { entry ->
                            val nowFavorite = FavoritesRepository.toggle(context, entry)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(if (nowFavorite) R.string.favorite_added else R.string.favorite_removed)
                                )
                            }
                            reload++
                        },
                        isFavorite = { entry -> FavoritesRepository.isFavorite(context, entry) },
                        onShare = { entry ->
                            try {
                                val uri = FileRepository.shareUri(context, entry)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = entry.mimeType ?: "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, entry.name))
                            } catch (_: Exception) {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_operation)) }
                            }
                        },
                        onClearFilter = { filterName = FileCategory.ALL.name; query = "" },
                    )
                }
            }
            AppTab.RECENTS -> FileListScreen(
                modifier = Modifier.padding(padding),
                entries = recentEntries,
                loading = false,
                onOpen = ::openEntry,
                onRename = { entry -> renameTarget = entry; renameText = entry.name },
                onDelete = { deleteTarget = it },
                onMoveToSafeFolder = { entry ->
                    vaultPendingMove = entry
                    tabName = AppTab.SAFE.name
                },
                onCopyToFolder = { entry ->
                    transferTarget = entry
                    transferAsMove = false
                    destinationFolderPicker.launch(null)
                },
                onMoveToFolder = { entry ->
                    transferTarget = entry
                    transferAsMove = true
                    destinationFolderPicker.launch(null)
                },
                onToggleFavorite = { entry ->
                    val nowFavorite = FavoritesRepository.toggle(context, entry)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(if (nowFavorite) R.string.favorite_added else R.string.favorite_removed)
                        )
                    }
                    reload++
                },
                isFavorite = { entry -> FavoritesRepository.isFavorite(context, entry) },
                onShare = { entry ->
                    try {
                        val uri = FileRepository.shareUri(context, entry)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = entry.mimeType ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, entry.name))
                    } catch (_: Exception) {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_operation)) }
                    }
                },
                onClearFilter = { tabName = AppTab.HOME.name },
            )
            AppTab.FAVORITES -> FileListScreen(
                modifier = Modifier.padding(padding),
                entries = favorites,
                loading = false,
                onOpen = ::openEntry,
                onRename = { entry -> renameTarget = entry; renameText = entry.name },
                onDelete = { deleteTarget = it },
                onMoveToSafeFolder = { entry ->
                    vaultPendingMove = entry
                    tabName = AppTab.SAFE.name
                },
                onCopyToFolder = { entry ->
                    transferTarget = entry
                    transferAsMove = false
                    destinationFolderPicker.launch(null)
                },
                onMoveToFolder = { entry ->
                    transferTarget = entry
                    transferAsMove = true
                    destinationFolderPicker.launch(null)
                },
                onToggleFavorite = { entry ->
                    val nowFavorite = FavoritesRepository.toggle(context, entry)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(if (nowFavorite) R.string.favorite_added else R.string.favorite_removed)
                        )
                    }
                    reload++
                },
                isFavorite = { entry -> FavoritesRepository.isFavorite(context, entry) },
                onShare = { entry ->
                    try {
                        val uri = FileRepository.shareUri(context, entry)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = entry.mimeType ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, entry.name))
                    } catch (_: Exception) {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.error_operation)) }
                    }
                },
                onClearFilter = { tabName = AppTab.HOME.name },
            )
            AppTab.SAFE -> SecureFolderScreen(
                modifier = Modifier.padding(padding),
                pendingMove = vaultPendingMove,
                onMoveHandled = { vaultPendingMove = null },
                onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            )
            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                hasAccess = FileRepository.hasStorageAccess(context),
                hasCustomFolder = FileRepository.initialLocation(context)?.startsWith("content://") == true,
                onRequestAccess = ::requestStorageAccess,
                onChooseFolder = { folderPicker.launch(null) },
            )
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.rename_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget
                    val newName = renameText
                    renameTarget = null
                    if (target != null) runFileOperation(context.getString(R.string.rename_success)) {
                        FileRepository.rename(context, target, newName)
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget
                    deleteTarget = null
                    if (target != null) moveToTrash(target)
                }) { Text(stringResource(R.string.confirm_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(stringResource(R.string.new_folder_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val location = currentLocation
                    val name = newFolderName
                    showCreateFolderDialog = false
                    if (location != null) runFileOperation(context.getString(R.string.folder_created)) {
                        FileRepository.createDirectory(context, location, name)
                    }
                }) { Text(stringResource(R.string.create_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    hasAccess: Boolean,
    hasLocation: Boolean,
    onOpenFiles: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSafeFolder: () -> Unit,
    recentFiles: List<FileEntry>,
    onOpenRecentEntry: (FileEntry) -> Unit,
    onCategory: (FileCategory) -> Unit,
    onRequestAccess: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        StorageCard(hasAccess = hasAccess, hasLocation = hasLocation)
        if (!hasLocation) PermissionCard(onRequestAccess, onChooseFolder)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.quick_access), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = Ink)
            TextButton(onClick = onOpenFiles) { Text(stringResource(R.string.category_all)) }
        }
        val categories = listOf(
            FileCategory.DOWNLOADS, FileCategory.IMAGES,
            FileCategory.VIDEOS, FileCategory.AUDIO,
            FileCategory.DOCUMENTS, FileCategory.APKS,
            FileCategory.ARCHIVES, FileCategory.OTHER,
        )
        categories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { category -> CategoryCard(category, Modifier.weight(1f)) { onCategory(category) } }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recent_files_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = Ink)
            TextButton(onClick = onOpenRecents) { Text(stringResource(R.string.see_all)) }
        }
        if (recentFiles.isEmpty()) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE3F4F4)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = Teal)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.recents_title), fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(stringResource(R.string.recents_subtitle), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                recentFiles.take(6).forEachIndexed { index, entry ->
                    Card(
                        modifier = Modifier.width(164.dp).clickable { onOpenRecentEntry(entry) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = when (index % 3) { 0 -> Color(0xFFE8E5FF); 1 -> Color(0xFFE0F3F0); else -> Color(0xFFFFECE6) }),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                Icon(if (entry.isDirectory) Icons.Outlined.Folder else categoryIcon(entry.category), contentDescription = null, tint = when (index % 3) { 0 -> AppBlue; 1 -> Teal; else -> Coral })
                            }
                            Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, color = Ink)
                            Text(formatBytes(entry.sizeBytes), color = SecondaryText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.collection_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CollectionCard(
                title = stringResource(R.string.favorites_title),
                subtitle = stringResource(R.string.favorites_subtitle),
                icon = Icons.Outlined.Star,
                tint = Color(0xFFFFF0D9),
                accent = Color(0xFF986100),
                modifier = Modifier.weight(1f),
                onClick = onOpenFavorites,
            )
            CollectionCard(
                title = stringResource(R.string.vault_title),
                subtitle = stringResource(R.string.vault_home_subtitle),
                icon = Icons.Outlined.Lock,
                tint = Color(0xFFE7E3FF),
                accent = AppBlue,
                modifier = Modifier.weight(1f),
                onClick = onOpenSafeFolder,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFiles),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE1F2F1)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = Teal)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.storage_devices), fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(if (hasLocation) stringResource(R.string.access_enabled) else stringResource(R.string.storage_unavailable), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CollectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = tint)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Text(title, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShareScreen(
    modifier: Modifier,
    recentFiles: List<FileEntry>,
    onShare: (FileEntry) -> Unit,
    onPickFile: () -> Unit,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE4E0FF))) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Share, contentDescription = null, tint = AppBlue, modifier = Modifier.size(28.dp))
                }
                Text(stringResource(R.string.share_screen_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                Text(stringResource(R.string.share_screen_body), color = SecondaryText)
                Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_pick_action)) }
            }
        }
        Text(stringResource(R.string.share_recent_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
        if (recentFiles.isEmpty()) {
            Text(stringResource(R.string.share_empty), color = SecondaryText)
        } else {
            recentFiles.take(12).forEach { entry ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(SoftBlue), contentAlignment = Alignment.Center) {
                            Icon(categoryIcon(entry.category), contentDescription = null, tint = AppBlue)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Ink)
                            Text(formatBytes(entry.sizeBytes), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onShare(entry) }) { Text(stringResource(R.string.action_share)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCard(hasAccess: Boolean, hasLocation: Boolean) {
    val usage = remember(hasAccess) {
        if (!hasAccess) null else runCatching {
            val stat = StatFs(File(FileRepository.storageRoot()).absolutePath)
            val total = stat.totalBytes.coerceAtLeast(1L)
            val available = stat.availableBytes.coerceAtLeast(0L)
            (total - available).coerceAtLeast(0L) to total
        }.getOrNull()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(SoftBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = AppBlue)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.storage_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            usage != null -> stringResource(R.string.storage_used, formatBytes(usage.first), formatBytes(usage.second))
                            hasLocation -> stringResource(R.string.access_limited)
                            else -> stringResource(R.string.storage_unavailable)
                        },
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (usage != null) {
                val progress = (usage.first.toFloat() / usage.second.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFE8EDF5))) {
                    Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(CircleShape).background(AppBlue))
                }
            } else {
                Text(stringResource(R.string.access_body), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestAccess: () -> Unit, onChooseFolder: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.access_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.access_body), color = SecondaryText)
            Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.access_full)) }
            TextButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.access_choose)) }
        }
    }
}

@Composable
private fun PermissionScreen(modifier: Modifier, onRequestAccess: () -> Unit, onChooseFolder: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Folder, contentDescription = null, tint = AppBlue, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.access_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.permission_missing), color = SecondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.access_full)) }
        TextButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.access_choose)) }
    }
}

@Composable
private fun CategoryCard(category: FileCategory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = when (category) {
        FileCategory.DOWNLOADS -> Color(0xFFE4F4E8)
        FileCategory.IMAGES -> Color(0xFFFFE7E9)
        FileCategory.VIDEOS -> Color(0xFFDFF3F1)
        FileCategory.AUDIO -> Color(0xFFF1E5FF)
        FileCategory.DOCUMENTS -> Color(0xFFE4ECFF)
        FileCategory.APKS -> Color(0xFFFFEBD9)
        FileCategory.ARCHIVES -> Color(0xFFE7E7F0)
        else -> Color(0xFFE4F0F8)
    }
    val accent = when (category) {
        FileCategory.DOWNLOADS -> Green
        FileCategory.IMAGES -> Coral
        FileCategory.VIDEOS -> Teal
        FileCategory.AUDIO -> Color(0xFF7B43B5)
        FileCategory.DOCUMENTS -> Color(0xFF315FB8)
        FileCategory.APKS -> Color(0xFFB45C18)
        FileCategory.ARCHIVES -> Color(0xFF55556F)
        else -> Color(0xFF34708C)
    }
    Card(
        modifier = modifier.height(104.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tint),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(category), contentDescription = null, tint = accent)
            }
            Text(categoryLabel(category), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
        }
    }
}

@Composable
private fun FileListScreen(
    modifier: Modifier,
    entries: List<FileEntry>,
    loading: Boolean,
    onOpen: (FileEntry) -> Unit,
    onRename: (FileEntry) -> Unit,
    onDelete: (FileEntry) -> Unit,
    onMoveToSafeFolder: (FileEntry) -> Unit,
    onCopyToFolder: (FileEntry) -> Unit,
    onMoveToFolder: (FileEntry) -> Unit,
    onToggleFavorite: (FileEntry) -> Unit,
    isFavorite: (FileEntry) -> Boolean,
    onShare: (FileEntry) -> Unit,
    onClearFilter: () -> Unit,
) {
    if (entries.isEmpty() && !loading) {
        Column(
            modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = Color(0xFF9AA6B8), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.empty_title), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.empty_message), color = SecondaryText, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClearFilter) { Text(stringResource(R.string.category_all)) }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
        ) {
            if (loading) item { Text("Loading…", color = SecondaryText, modifier = Modifier.padding(16.dp)) }
            items(entries, key = { it.location }) { entry ->
                FileRow(entry, onClick = { onOpen(entry) }, onRename = { onRename(entry) }, onDelete = { onDelete(entry) }, onMoveToSafeFolder = { onMoveToSafeFolder(entry) }, onCopyToFolder = { onCopyToFolder(entry) }, onMoveToFolder = { onMoveToFolder(entry) }, onToggleFavorite = { onToggleFavorite(entry) }, isFavorite = isFavorite(entry), onShare = { onShare(entry) })
            }
        }
    }
}

@Composable
private fun FileRow(
    entry: FileEntry,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveToSafeFolder: () -> Unit,
    onCopyToFolder: () -> Unit,
    onMoveToFolder: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorite: Boolean,
    onShare: () -> Unit,
) {
    var menuExpanded by remember(entry.location) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (entry.isDirectory) SoftBlue else Color(0xFFF0F3F8)), contentAlignment = Alignment.Center) {
            Icon(if (entry.isDirectory) Icons.Outlined.Folder else categoryIcon(entry.category), contentDescription = null, tint = if (entry.isDirectory) AppBlue else SecondaryText)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(if (entry.isDirectory) stringResource(R.string.folder) else formatBytes(entry.sizeBytes), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = SecondaryText) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.action_share)) }, onClick = { menuExpanded = false; onShare() })
                DropdownMenuItem(
                    text = { Text(stringResource(if (isFavorite) R.string.favorite_remove_action else R.string.favorite_add_action)) },
                    onClick = { menuExpanded = false; onToggleFavorite() },
                )
                if (!entry.isDirectory) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_copy_to)) }, onClick = { menuExpanded = false; onCopyToFolder() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_to)) }, onClick = { menuExpanded = false; onMoveToFolder() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_safe)) }, onClick = { menuExpanded = false; onMoveToSafeFolder() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menuExpanded = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuExpanded = false; onDelete() })
            }
        }
    }
    Divider(color = Color(0xFFF0F2F6), thickness = 0.7.dp, modifier = Modifier.padding(start = 66.dp))
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    hasAccess: Boolean,
    hasCustomFolder: Boolean,
    onRequestAccess: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.settings_access), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        hasAccess -> stringResource(R.string.access_enabled)
                        hasCustomFolder -> stringResource(R.string.access_limited)
                        else -> stringResource(R.string.permission_missing)
                    },
                    color = SecondaryText,
                )
                Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_full_access)) }
                TextButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_folder)) }
            }
        }
        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_version), color = SecondaryText)
                Text("Files stay on your device in this early build. No account or cloud service is required.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.ALL -> Icons.Outlined.Folder
    FileCategory.IMAGES -> Icons.Outlined.Image
    FileCategory.VIDEOS -> Icons.Outlined.VideoLibrary
    FileCategory.AUDIO -> Icons.Outlined.MusicNote
    FileCategory.DOCUMENTS -> Icons.Outlined.Description
    FileCategory.ARCHIVES -> Icons.Outlined.Archive
    FileCategory.APKS -> Icons.Outlined.Apps
    FileCategory.DOWNLOADS -> Icons.Outlined.Download
    FileCategory.OTHER -> Icons.Outlined.Folder
}

@Composable
private fun categoryLabel(category: FileCategory): String = when (category) {
    FileCategory.ALL -> stringResource(R.string.category_all)
    FileCategory.IMAGES -> stringResource(R.string.category_images)
    FileCategory.VIDEOS -> stringResource(R.string.category_videos)
    FileCategory.AUDIO -> stringResource(R.string.category_audio)
    FileCategory.DOCUMENTS -> stringResource(R.string.category_documents)
    FileCategory.ARCHIVES -> stringResource(R.string.category_archives)
    FileCategory.APKS -> stringResource(R.string.category_apks)
    FileCategory.DOWNLOADS -> stringResource(R.string.category_downloads)
    FileCategory.OTHER -> stringResource(R.string.category_other)
}

private fun displayLocationName(context: android.content.Context, location: String): String {
    return if (location.startsWith("content://")) {
        runCatching {
            val uri = Uri.parse(location)
            if (uri.pathSegments.contains("document")) DocumentFile.fromSingleUri(context, uri)?.name
            else DocumentFile.fromTreeUri(context, uri)?.name
        }.getOrNull() ?: context.getString(R.string.browse_title)
    } else {
        File(location).name.ifBlank { context.getString(R.string.storage_title) }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}
