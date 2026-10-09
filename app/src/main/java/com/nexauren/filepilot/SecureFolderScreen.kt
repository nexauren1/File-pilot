package com.nexauren.filepilot

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.nexauren.filepilot.data.FileEntry
import com.nexauren.filepilot.data.SecureFolderRepository
import com.nexauren.filepilot.data.SecureMoveResult
import com.nexauren.filepilot.data.SecureVaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val VaultTint = Color(0xFFECE7FF)
private val VaultInk = Color(0xFF6650A4)

@Composable
internal fun SecureFolderScreen(
    modifier: Modifier,
    pendingMove: FileEntry?,
    onMoveHandled: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(SecureFolderRepository.isConfigured(context)) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var pinInput by rememberSaveable { mutableStateOf("") }
    var pinConfirm by rememberSaveable { mutableStateOf("") }
    var sessionPin by remember { mutableStateOf("") }
    var records by remember { mutableStateOf<List<SecureVaultItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<SecureVaultItem?>(null) }
    var restoreTarget by remember { mutableStateOf<SecureVaultItem?>(null) }
    var processedMoveLocation by remember { mutableStateOf<String?>(null) }

    val addFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null && unlocked && sessionPin.isNotEmpty()) {
            scope.launch {
                busy = true
                val file = DocumentFile.fromSingleUri(context, uri)
                val name = file?.name ?: "file"
                val mime = context.contentResolver.getType(uri)
                val result = withContext(Dispatchers.IO) {
                    SecureFolderRepository.addUri(context, uri, name, mime, sessionPin)
                }
                result.onSuccess {
                    records = withContext(Dispatchers.IO) {
                        SecureFolderRepository.unlock(context, sessionPin).getOrDefault(emptyList())
                    }
                    onMessage(context.getString(R.string.vault_add_success))
                }.onFailure {
                    onMessage(context.getString(R.string.vault_operation_error))
                }
                busy = false
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri: Uri? ->
        val item = restoreTarget
        if (uri != null && item != null && sessionPin.isNotEmpty()) {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) {
                    SecureFolderRepository.restoreToUri(context, item, sessionPin, uri)
                }
                onMessage(
                    if (result.isSuccess) context.getString(R.string.vault_restore_success)
                    else context.getString(R.string.vault_operation_error),
                )
                restoreTarget = null
                busy = false
            }
        } else {
            restoreTarget = null
        }
    }

    LaunchedEffect(unlocked, pendingMove?.location) {
        val entry = pendingMove
        if (entry == null) {
            processedMoveLocation = null
            return@LaunchedEffect
        }
        if (!unlocked || processedMoveLocation == entry.location) return@LaunchedEffect

        processedMoveLocation = entry.location
        busy = true
        val result = withContext(Dispatchers.IO) {
            SecureFolderRepository.moveEntry(context, entry, sessionPin)
        }
        result.onSuccess { moved: SecureMoveResult ->
            records = withContext(Dispatchers.IO) {
                SecureFolderRepository.unlock(context, sessionPin).getOrDefault(emptyList())
            }
            onMessage(
                if (moved.sourceRemoved) context.getString(R.string.vault_move_success)
                else context.getString(R.string.vault_move_partial),
            )
        }.onFailure {
            onMessage(context.getString(R.string.vault_operation_error))
        }
        busy = false
        onMoveHandled()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!unlocked) {
            Box(
                Modifier.size(72.dp).background(VaultTint, CircleShape).align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = VaultInk, modifier = Modifier.size(34.dp))
            }
            Text(
                stringResource(if (configured) R.string.vault_unlock_title else R.string.vault_setup_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                stringResource(if (configured) R.string.vault_unlock_body else R.string.vault_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it.filter(Char::isDigit).take(12); errorMessage = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.vault_pin_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            if (!configured) {
                OutlinedTextField(
                    value = pinConfirm,
                    onValueChange = { pinConfirm = it.filter(Char::isDigit).take(12); errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.vault_confirm_pin)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
            if (errorMessage != null) {
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    scope.launch {
                        errorMessage = null
                        if (!SecureFolderRepository.isValidPin(pinInput)) {
                            errorMessage = context.getString(R.string.vault_pin_invalid)
                        } else if (!configured && pinInput != pinConfirm) {
                            errorMessage = context.getString(R.string.vault_pin_mismatch)
                        } else {
                            busy = true
                            val result = withContext(Dispatchers.IO) {
                                if (configured) SecureFolderRepository.unlock(context, pinInput)
                                else SecureFolderRepository.initialize(context, pinInput)
                            }
                            result.onSuccess { loaded ->
                                records = loaded
                                sessionPin = pinInput
                                pinInput = ""
                                pinConfirm = ""
                                configured = true
                                unlocked = true
                            }.onFailure {
                                errorMessage = context.getString(
                                    if (configured) R.string.vault_wrong_pin else R.string.vault_setup_error,
                                )
                            }
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(stringResource(if (configured) R.string.vault_unlock_action else R.string.vault_setup_action))
            }
            if (!configured) {
                Text(
                    stringResource(R.string.vault_forget_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(VaultTint, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = VaultInk)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vault_unlocked_title), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.vault_unlocked_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { unlocked = false; sessionPin = ""; pinInput = ""; records = emptyList() }) {
                    Text(stringResource(R.string.vault_lock_action))
                }
            }
            Button(
                onClick = { addFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.vault_add_action))
            }
            Text(
                stringResource(R.string.vault_local_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (records.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, tint = VaultInk, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.vault_empty_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.vault_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(records, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = VaultInk, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.size(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, maxLines = 2, fontWeight = FontWeight.Medium)
                                    Text(formatVaultBytes(item.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(
                                    onClick = {
                                        restoreTarget = item
                                        restoreLauncher.launch(item.name)
                                    },
                                    enabled = !busy,
                                ) { Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.vault_restore_action), tint = VaultInk) }
                                IconButton(onClick = { deleteTarget = item }, enabled = !busy) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.vault_delete_action), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.vault_delete_title)) },
            text = { Text(stringResource(R.string.vault_delete_body, deleteTarget?.name.orEmpty())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val item = deleteTarget
                        deleteTarget = null
                        if (item != null) {
                            scope.launch {
                                busy = true
                                val result = withContext(Dispatchers.IO) {
                                    SecureFolderRepository.remove(context, item, sessionPin)
                                }
                                result.onSuccess {
                                    records = it
                                    onMessage(context.getString(R.string.vault_delete_success))
                                }.onFailure {
                                    onMessage(context.getString(R.string.vault_operation_error))
                                }
                                busy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.vault_delete_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun formatVaultBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < units.lastIndex)
    return String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[index])
}
