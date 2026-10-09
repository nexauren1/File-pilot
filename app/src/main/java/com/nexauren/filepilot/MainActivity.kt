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
import androidx.compose.foundation.lazy.LazyColumn
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
private val AppBlue = Color(0xFF6D4AE8)
private val PageBackground = Color(0xFFF8F7FC)
private val SecondaryText = Color(0xFF6B6680)
private val SoftBlue = Color(0xFFEFEAFF)

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

private enum class AppTab { HOME, BROWSE, RECENTS, FAVORITES, SAFE, SETTINGS }

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
    var reload by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var recentEntries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
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

    fun requestStorageAccess() {
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
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            legacyPermissionsLauncher.launch(wanted.toTypedArray())
        }
    }

    LaunchedEffect(rootLocation) {
        stack.clear()
        rootLocation?.let { stack.add(it) }
    }

    LaunchedEffect(tabName, reload) {
        when (tab) {
            AppTab.FAVORITES -> favorites = withContext(Dispatchers.IO) { FavoritesRepository.list(context) }
            AppTab.RECENTS -> recentEntries = withContext(Dispatchers.IO) { RecentFilesRepository.list(context) }
            else -> Unit
        }
    }

    LaunchedEffect(currentLocation, filterName, query, reload, tabName) {
        if (currentLocation != null && tab == AppTab.BROWSE) {
            loading = true
            val loaded = withContext(Dispatchers.IO) {
                FileRepository.listChildren(context, currentLocation)
            }
            entries = loaded.filter { entry ->
                filter.matches(entry.name, entry.isDirectory, entry.location) &&
                    entry.name.contains(query.trim(), ignoreCase = true)
            }
            loading = false
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

    BackHandler(enabled = tab == AppTab.RECENTS || tab == AppTab.FAVORITES) {
        tabName = AppTab.HOME.name
    }

    BackHandler(enabled = tab == AppTab.SAFE) {
        tabName = AppTab.HOME.name
        vaultPendingMove = null
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
                    if (showSearch && tab == AppTab.BROWSE) {
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
                                    AppTab.HOME -> stringResource(R.string.app_name)
                                    AppTab.BROWSE -> currentLocation?.let { displayLocationName(context, it) }
                                        ?: stringResource(R.string.browse_title)
                                    AppTab.RECENTS -> stringResource(R.string.recents_title)
                                    AppTab.FAVORITES -> stringResource(R.string.favorites_title)
                                    AppTab.SAFE -> stringResource(R.string.vault_title)
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
                    if (tab == AppTab.BROWSE) {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) query = ""
                        }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_hint))
                        }
                        IconButton(onClick = {
                            newFolderName = ""
                            showCreateFolderDialog = true
                        }) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = tab == AppTab.HOME || tab == AppTab.RECENTS || tab == AppTab.FAVORITES,
                    onClick = { tabName = AppTab.HOME.name },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.BROWSE,
                    onClick = {
                        tabName = AppTab.BROWSE.name
                        filterName = FileCategory.ALL.name
                    },
                    icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_browse)) },
                )
                NavigationBarItem(
                    selected = tab == AppTab.SETTINGS,
                    onClick = { tabName = AppTab.SETTINGS.name },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
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
                onCategory = { category ->
                    filterName = category.name
                    query = ""
                    tabName = AppTab.BROWSE.name
                },
                onRequestAccess = ::requestStorageAccess,
                onChooseFolder = { folderPicker.launch(null) },
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
                    if (target != null) runFileOperation(context.getString(R.string.delete_success)) {
                        FileRepository.delete(context, target)
                    }
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
    onCategory: (FileCategory) -> Unit,
    onRequestAccess: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.home_subtitle), color = SecondaryText)
        StorageCard(hasAccess = hasAccess, hasLocation = hasLocation)
        if (!hasLocation) PermissionCard(onRequestAccess, onChooseFolder)
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenRecents),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(SoftBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = AppBlue)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recents_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.recents_subtitle), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.quick_access), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenFiles) { Text(stringResource(R.string.category_all)) }
        }
        val categories = listOf(
            FileCategory.IMAGES, FileCategory.VIDEOS,
            FileCategory.AUDIO, FileCategory.DOCUMENTS,
            FileCategory.ARCHIVES, FileCategory.APKS,
            FileCategory.DOWNLOADS, FileCategory.OTHER,
        )
        categories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { category ->
                    CategoryCard(category, Modifier.weight(1f)) { onCategory(category) }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFavorites),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1EFF7)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFE2DCF6)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Star, contentDescription = null, tint = Color(0xFF6650A4))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.favorites_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.favorites_subtitle), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSafeFolder),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFECE7FF)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFDCD3FF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFF6650A4))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vault_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.vault_home_subtitle), color = Color(0xFF655A80), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFiles),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(SoftBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = AppBlue)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.browse_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasLocation) stringResource(R.string.access_enabled) else stringResource(R.string.storage_unavailable),
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
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
    Card(
        modifier = modifier.height(112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(SoftBlue), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(category), contentDescription = null, tint = AppBlue)
            }
            Text(categoryLabel(category), fontWeight = FontWeight.Medium, fontSize = 14.sp)
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
