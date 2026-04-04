package com.github.inindev.authentool

import android.content.Context
import android.content.Intent
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val ERROR_DISPLAY_DURATION_MS = 3000L
private const val DATE_FORMAT = "yyyyMMdd"
private const val APP_NAME = "authentool"

@Stable
class BackupRestoreManager(
    private val context: Context,
    private val storageManager: StorageManager?,
    private val coroutineScope: CoroutineScope,
    private val viewModel: MainViewModel,
) {
    var showBackupDialog by mutableStateOf(false)
    var showRestoreStorageCheckDialog by mutableStateOf(false)
    var showRestoreSeedsDialog by mutableStateOf(false)
    var showOperationSuccessDialog by mutableStateOf(false)
    var operationSuccessMessage by mutableStateOf("")
    var encryptedData by mutableStateOf<String?>(null)
    var storageVolumes by mutableStateOf(emptyList<Pair<File, Boolean>>())
    var backupPassword by mutableStateOf("")

    val fileName: String
        get() {
            val date = java.text.SimpleDateFormat(DATE_FORMAT, java.util.Locale.US)
                .format(java.util.Date())
            return "${APP_NAME}_${date}.enc"
        }

    val hasActiveDialog: Boolean
        get() = showBackupDialog || showRestoreStorageCheckDialog || showRestoreSeedsDialog || showOperationSuccessDialog

    fun refreshStorageVolumes() {
        storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
            volume.directory?.let { it to volume.isRemovable }
        } ?: emptyList()
        Log.d("BackupRestoreManager", "Storage volumes updated: ${storageVolumes.map { "${it.first.path} (removable=${it.second})" }}")
    }

    fun handleBackRequested(): Boolean {
        return when {
            showBackupDialog -> { showBackupDialog = false; true }
            showRestoreStorageCheckDialog -> { showRestoreStorageCheckDialog = false; true }
            showRestoreSeedsDialog -> { showRestoreSeedsDialog = false; encryptedData = null; true }
            showOperationSuccessDialog -> { showOperationSuccessDialog = false; true }
            else -> false
        }
    }

    fun requestBackup() {
        showBackupDialog = true
    }

    fun requestRestore(restoreFileLauncher: ActivityResultLauncher<Array<String>>) {
        refreshStorageVolumes()
        val removableVolumes = storageVolumes.filter { it.second }
        if (removableVolumes.isNotEmpty()) {
            restoreFileLauncher.launch(arrayOf("application/octet-stream"))
        } else {
            showRestoreStorageCheckDialog = true
        }
    }

    fun retryRestore(restoreFileLauncher: ActivityResultLauncher<Array<String>>) {
        refreshStorageVolumes()
        val removableVolumes = storageVolumes.filter { it.second }
        if (removableVolumes.isNotEmpty()) {
            restoreFileLauncher.launch(arrayOf("application/octet-stream"))
            showRestoreStorageCheckDialog = false
        }
    }

    fun onSaveRequested(password: String, saveFileLauncher: ActivityResultLauncher<String>) {
        backupPassword = password
        saveFileLauncher.launch(fileName)
    }

    fun onSaveCompleted(uri: android.net.Uri?) {
        uri?.let { saveUri ->
            viewModel.exportSeedsCrypt(backupPassword)?.let { seedsJson ->
                context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                    outputStream.write(seedsJson.toByteArray(Charsets.UTF_8))
                } ?: Log.e("BackupRestoreManager", "Failed to open output stream for uri $saveUri")
                context.contentResolver.releasePersistableUriPermission(saveUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                showTimedMessage("Backup saved to USB")
                operationSuccessMessage = "Backup successful"
                showOperationSuccessDialog = true
            }
            showBackupDialog = false
            backupPassword = ""
        } ?: showTimedMessage("Failed to save backup")
    }

    fun onRestoreFileSelected(uri: android.net.Uri?) {
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                encryptedData = inputStream.readBytes().toString(Charsets.UTF_8)
                showRestoreSeedsDialog = true
            }
        }
    }

    fun onRestoreSuccess(count: Int) {
        operationSuccessMessage = "Restore successful ($count entries)"
        showOperationSuccessDialog = true
    }

    fun dismissBackupDialog() { showBackupDialog = false }
    fun dismissRestoreStorageCheckDialog() { showRestoreStorageCheckDialog = false }
    fun dismissRestoreSeedsDialog() { showRestoreSeedsDialog = false; encryptedData = null }
    fun dismissOperationSuccessDialog() { showOperationSuccessDialog = false }

    fun createSaveIntent(baseIntent: Intent): Intent {
        val removableVolumes = storageVolumes.filter { it.second }
        if (removableVolumes.isEmpty()) {
            Log.w("BackupRestoreManager", "No removable volumes detected - backup aborted")
            showTimedMessage("No removable storage detected")
        } else {
            findRemovableUsbUuid(removableVolumes)?.let { uuid ->
                val usbUri = "content://com.android.externalstorage.documents/document/$uuid%3A".toUri()
                baseIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, usbUri)
                Log.d("BackupRestoreManager", "Set initial URI to USB for backup: $usbUri")
            } ?: Log.w("BackupRestoreManager", "No valid removable USB volume found")
        }
        return baseIntent
    }

    fun createRestoreIntent(baseIntent: Intent): Intent {
        val removableVolumes = storageVolumes.filter { it.second }
        if (removableVolumes.isEmpty()) {
            Log.w("BackupRestoreManager", "No removable volumes detected for restore - restore aborted")
            showTimedMessage("No removable storage detected")
        } else {
            findRemovableUsbUuid(removableVolumes)?.let { uuid ->
                val usbUri = "content://com.android.externalstorage.documents/document/$uuid%3A".toUri()
                baseIntent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, usbUri)
                Log.d("BackupRestoreManager", "Set initial URI to USB for restore: $usbUri")
            } ?: Log.w("BackupRestoreManager", "No valid removable USB volume found")
        }
        return baseIntent
    }

    private fun findRemovableUsbUuid(removableVolumes: List<Pair<File, Boolean>>): String? {
        return storageManager?.storageVolumes?.find { vol ->
            vol.isRemovable && vol.directory != null && removableVolumes.any { it.first == vol.directory }
        }?.uuid
    }

    private fun showTimedMessage(message: String) {
        coroutineScope.launch {
            viewModel.dispatch(AuthCommand.SetError(message))
            delay(ERROR_DISPLAY_DURATION_MS)
            viewModel.dispatch(AuthCommand.SetError(null))
        }
    }
}

@Composable
fun rememberBackupRestoreManager(
    viewModel: MainViewModel,
): BackupRestoreManager {
    val context = LocalContext.current
    val storageManager = context.getSystemService<StorageManager>()
    val coroutineScope = rememberCoroutineScope()

    val manager = remember {
        BackupRestoreManager(
            context = context,
            storageManager = storageManager,
            coroutineScope = coroutineScope,
            viewModel = viewModel,
        )
    }

    LaunchedEffect(manager.showBackupDialog, manager.showRestoreStorageCheckDialog) {
        if (manager.showBackupDialog || manager.showRestoreStorageCheckDialog) {
            manager.refreshStorageVolumes()
        }
    }

    return manager
}

@Composable
fun rememberSaveFileLauncher(manager: BackupRestoreManager): ActivityResultLauncher<String> {
    return rememberLauncherForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: Context, input: String): Intent {
                return manager.createSaveIntent(super.createIntent(context, input))
            }
        }
    ) { uri -> manager.onSaveCompleted(uri) }
}

@Composable
fun rememberRestoreFileLauncher(manager: BackupRestoreManager): ActivityResultLauncher<Array<String>> {
    return rememberLauncherForActivityResult(
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return manager.createRestoreIntent(super.createIntent(context, input))
            }
        }
    ) { uri -> manager.onRestoreFileSelected(uri) }
}