package com.github.inindev.authentool

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.getSystemService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.inindev.authentool.ui.theme.AppColorTheme
import com.github.inindev.authentool.ui.theme.customColorScheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val HIGHLIGHT_DELAY_MS = 8000L
private const val ERROR_DISPLAY_DURATION_MS = 3000L
private const val ANIMATION_DURATION_MS = 100
private const val DATE_FORMAT = "yyyyMMdd"
private const val APP_NAME = "authentool"

// countdown bar placement
private enum class CountdownLocation {
    NONE,
    TOP,
    BOTTOM,
    BOTH
}
private val countdown_location = CountdownLocation.BOTH

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel = viewModel<MainViewModel>(factory = MainViewModelFactory(applicationContext))
            MainActivityContent(viewModel = viewModel, activity = this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainActivityContent(viewModel: MainViewModel, activity: MainActivity) {
    val uiState by viewModel.uiState.collectAsState()
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (uiState.themeMode) {
        ThemeMode.SYSTEM -> systemDarkTheme
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
    }
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long
                )
                viewModel.dispatch(AuthCommand.SetError(null))
            }
        }
    }

    BackHandler(enabled = true) {
        if (showAddDialog) {
            showAddDialog = false
        }
    }

    AppColorTheme(darkTheme = darkTheme, dynamicColor = false) {
        val targetDensity = 320f / 160f
        val currentDensity = LocalDensity.current.density
        val adjustedDensity = if (currentDensity > targetDensity) targetDensity else currentDensity

        CompositionLocalProvider(
            LocalDensity provides Density(density = adjustedDensity, fontScale = 1f)
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "Authentool",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.customColorScheme.TopBarText
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.customColorScheme.TopBarBackground,
                            titleContentColor = MaterialTheme.customColorScheme.TopBarText
                        ),
                        actions = {
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "add entry",
                                    tint = MaterialTheme.customColorScheme.TopBarText
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    color = MaterialTheme.customColorScheme.AppBackground
                ) {
                    AuthGrid(viewModel = viewModel, activity = activity)
                    if (showAddDialog) {
                        AddEntryDialog(
                            onDismiss = { showAddDialog = false },
                            onAdd = { name, seed ->
                                viewModel.dispatch(AuthCommand.AddCard(name, seed))
                                showAddDialog = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthGrid(
    viewModel: MainViewModel,
    activity: MainActivity,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val countdownProgress by viewModel.countdownProgress.collectAsState()
    val animatedProgress by animateFloatAsState(
        targetValue = countdownProgress,
        animationSpec = tween(durationMillis = ANIMATION_DURATION_MS, easing = LinearEasing),
        label = "countdownProgress"
    )
    val codes = uiState.codes
    val totpCodes = uiState.totpCodes
    val colorScheme = MaterialTheme.customColorScheme
    val context = LocalContext.current
    val storageManager = context.getSystemService<StorageManager>()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val columns = 2

    // generate filename with current date
    val fileName by produceState(initialValue = "authentool_backup.enc") {
        val date = java.text.SimpleDateFormat(DATE_FORMAT, java.util.Locale.US)
            .format(java.util.Date())
        value = "${APP_NAME}_${date}.enc"
    }

    var showSystemMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset(0.dp, 0.dp)) }
    var showThemeSubmenu by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreStorageCheckDialog by remember { mutableStateOf(false) }
    var showRestoreSeedsDialog by remember { mutableStateOf(false) }
    var showOperationSuccessDialog by remember { mutableStateOf(false) }
    var operationSuccessMessage by remember { mutableStateOf("") }
    var encryptedData by remember { mutableStateOf<String?>(null) }
    var storageVolumes by remember { mutableStateOf(emptyList<Pair<File, Boolean>>()) }
    var backupPassword by remember { mutableStateOf("") }

    // custom contract to set initial uri to removable storage only for backup
    val saveFileLauncher = rememberLauncherForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                val removableVolumes = storageVolumes.filter { it.second }
                if (removableVolumes.isEmpty()) {
                    Log.w("MainActivity", "No removable volumes detected - backup aborted")
                    coroutineScope.launch {
                        viewModel.dispatch(AuthCommand.SetError("No removable storage detected"))
                        delay(ERROR_DISPLAY_DURATION_MS)
                        viewModel.dispatch(AuthCommand.SetError(null))
                    }
                } else {
                    val usbVolume = storageManager?.storageVolumes?.find { vol ->
                        vol.isRemovable && vol.directory != null && removableVolumes.any { it.first == vol.directory }
                    }
                    usbVolume?.uuid?.let { uuid ->
                        val usbUri = "content://com.android.externalstorage.documents/document/$uuid%3A".toUri()
                        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, usbUri)
                        Log.d("MainActivity", "Set initial URI to USB for backup: $usbUri")
                    } ?: Log.w("MainActivity", "No valid removable USB volume found")
                }
                return intent
            }
        }
    ) { uri ->
        uri?.let { saveUri ->
            viewModel.exportSeedsCrypt(backupPassword)?.let { seedsJson ->
                context.contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                    outputStream.write(seedsJson.toByteArray(Charsets.UTF_8))
                } ?: Log.e("MainActivity", "AuthGrid: Failed to open output stream for uri $saveUri")
                context.contentResolver.releasePersistableUriPermission(saveUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                coroutineScope.launch {
                    viewModel.dispatch(AuthCommand.SetError("Backup saved to USB"))
                    delay(ERROR_DISPLAY_DURATION_MS)
                    viewModel.dispatch(AuthCommand.SetError(null))
                }
                operationSuccessMessage = "Backup successful"
                showOperationSuccessDialog = true
            }
            showBackupDialog = false
            backupPassword = "" // clear password after use
        } ?: run {
            coroutineScope.launch {
                viewModel.dispatch(AuthCommand.SetError("Failed to save backup"))
                delay(ERROR_DISPLAY_DURATION_MS)
                viewModel.dispatch(AuthCommand.SetError(null))
            }
        }
    }

    // custom contract for restore from removable storage only
    val restoreFileLauncher = rememberLauncherForActivityResult(
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                val intent = super.createIntent(context, input)
                val removableVolumes = storageVolumes.filter { it.second }
                if (removableVolumes.isEmpty()) {
                    Log.w("MainActivity", "No removable volumes detected for restore - restore aborted")
                    coroutineScope.launch {
                        viewModel.dispatch(AuthCommand.SetError("No removable storage detected"))
                        delay(ERROR_DISPLAY_DURATION_MS)
                        viewModel.dispatch(AuthCommand.SetError(null))
                    }
                } else {
                    val usbVolume = storageManager?.storageVolumes?.find { vol ->
                        vol.isRemovable && vol.directory != null && removableVolumes.any { it.first == vol.directory }
                    }
                    usbVolume?.uuid?.let { uuid ->
                        val usbUri = "content://com.android.externalstorage.documents/document/$uuid%3A".toUri()
                        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, usbUri)
                        Log.d("MainActivity", "Set initial URI to USB for restore: $usbUri")
                    } ?: Log.w("MainActivity", "No valid removable USB volume found")
                }
                return intent
            }
        }
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                encryptedData = inputStream.readBytes().toString(Charsets.UTF_8)
                showRestoreSeedsDialog = true
            }
        }
    }

    // implicit refresh when backup or restore dialog opens
    LaunchedEffect(showBackupDialog, showRestoreStorageCheckDialog) {
        if (showBackupDialog || showRestoreStorageCheckDialog) {
            storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                volume.directory?.let { it to volume.isRemovable }
            } ?: emptyList()
            Log.d("MainActivity", "Storage volumes updated: ${storageVolumes.map { "${it.first.path} (removable=${it.second})" }}")
        }
    }

    BackHandler(enabled = showSystemMenu || showBackupDialog || showRestoreStorageCheckDialog || showRestoreSeedsDialog || showOperationSuccessDialog) {
        if (showThemeSubmenu) {
            showThemeSubmenu = false
        } else if (showBackupDialog) {
            showBackupDialog = false
        } else if (showRestoreStorageCheckDialog) {
            showRestoreStorageCheckDialog = false
        } else if (showRestoreSeedsDialog) {
            showRestoreSeedsDialog = false
            encryptedData = null
        } else if (showOperationSuccessDialog) {
            showOperationSuccessDialog = false
        } else {
            showSystemMenu = false
        }
    }

    val cardStates = codes.mapIndexed { index, card ->
        AuthCardUiState(
            card = card,
            totpCode = totpCodes.getOrElse(index) { "000000" },
            isEditing = uiState.editingCardId == card.id,
            isHighlighted = uiState.highlightedCardId == card.id,
            position = GridPosition(index, index / columns, index % columns)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (countdown_location == CountdownLocation.TOP || countdown_location == CountdownLocation.BOTH) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colorScheme.ProgressFill,
                trackColor = colorScheme.ProgressTrack
            )
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = colorScheme.AppBackground
        ) {
            Box {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = modifier
                        .fillMaxSize()
                        .padding(all = 16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    uiState.editingCardId?.let { cardId ->
                                        viewModel.dispatch(AuthCommand.StopEditing(cardId))
                                    }
                                },
                                onLongPress = { offset ->
                                    if (uiState.editingCardId == null) {
                                        Log.d("MainActivity", "AuthGrid: Background long press at $offset")
                                        with(density) {
                                            menuOffset = DpOffset(offset.x.toDp(), offset.y.toDp())
                                        }
                                        showSystemMenu = true
                                    }
                                }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = cardStates,
                        key = { _, state -> state.card.id }
                    ) { _, state ->
                        AuthenticatorCard(state = state, viewModel = viewModel)
                    }
                }
                SystemMenu(
                    showSystemMenu = showSystemMenu,
                    showThemeSubmenu = showThemeSubmenu,
                    menuOffset = menuOffset,
                    themeMode = uiState.themeMode,
                    onThemeSelected = { viewModel.dispatch(AuthCommand.SetTheme(it)) },
                    onBackupRequested = { showBackupDialog = true },
                    onRestoreRequested = {
                        storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                            volume.directory?.let { it to volume.isRemovable }
                        } ?: emptyList()
                        val removableVolumes = storageVolumes.filter { it.second }
                        if (removableVolumes.isNotEmpty()) {
                            restoreFileLauncher.launch(arrayOf("application/octet-stream"))
                        } else {
                            showRestoreStorageCheckDialog = true
                        }
                    },
                    onDismiss = { showSystemMenu = false; showThemeSubmenu = false },
                    onThemeSubmenuToggle = { showThemeSubmenu = it }
                )
                BackupDialog(
                    showBackupDialog = showBackupDialog && fileName.isNotEmpty(),
                    storageVolumes = storageVolumes,
                    storageManager = storageManager,
                    context = context,
                    onSaveRequested = { password ->
                        backupPassword = password
                        saveFileLauncher.launch(fileName)
                    },
                    onDismiss = { showBackupDialog = false },
                    onRetry = {
                        storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                            volume.directory?.let { it to volume.isRemovable }
                        } ?: emptyList()
                    }
                )
                RestoreStorageCheckDialog(
                    showRestoreStorageCheckDialog = showRestoreStorageCheckDialog,
                    onDismiss = { showRestoreStorageCheckDialog = false },
                    onRetry = {
                        storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                            volume.directory?.let { it to volume.isRemovable }
                        } ?: emptyList()
                        val removableVolumes = storageVolumes.filter { it.second }
                        if (removableVolumes.isNotEmpty()) {
                            restoreFileLauncher.launch(arrayOf("application/octet-stream"))
                            showRestoreStorageCheckDialog = false
                        }
                    }
                )
                RestoreSeedsDialog(
                    showDialog = showRestoreSeedsDialog,
                    encryptedData = encryptedData,
                    viewModel = viewModel,
                    onDismiss = {
                        showRestoreSeedsDialog = false
                        encryptedData = null
                    },
                    onSuccess = { count ->
                        operationSuccessMessage = "Restore successful ($count entries)"
                        showOperationSuccessDialog = true
                    }
                )
                OperationSuccessDialog(
                    showDialog = showOperationSuccessDialog,
                    message = operationSuccessMessage,
                    onDismiss = { showOperationSuccessDialog = false },
                    onLaunchMediaManager = { launchMediaManagerForUnmount(activity, viewModel) }
                )
            }
        }
        if (countdown_location == CountdownLocation.BOTTOM || countdown_location == CountdownLocation.BOTH) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colorScheme.ProgressFill,
                trackColor = colorScheme.ProgressTrack
            )
        }
    }
}

@Composable
fun AddEntryDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val colors = MaterialTheme.customColorScheme
    var name by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = true, onBack = onDismiss)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colors.AppBackground
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Authenticator Entry", style = MaterialTheme.typography.titleLarge, color = colors.AppText)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("name", color = colors.AppText, style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = seed,
                    onValueChange = { seed = it },
                    label = { Text("base32 seed", color = colors.AppText, style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("cancel", color = colors.CardTotp, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val trimmedName = name.trim()
                            val trimmedSeed = seed.trim()
                            when {
                                trimmedName.isBlank() -> errorMessage = "Name cannot be empty."
                                trimmedSeed.isBlank() -> errorMessage = "Seed cannot be empty."
                                !isValidBase32(trimmedSeed) -> errorMessage = "Seed must be valid Base32 (A-Z, 2-7)."
                                else -> {
                                    onAdd(trimmedName, trimmedSeed)
                                    errorMessage = null
                                }
                            }
                        }
                    ) {
                        Text("add", color = colors.CardTotp, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun SystemMenu(
    showSystemMenu: Boolean,
    showThemeSubmenu: Boolean,
    menuOffset: DpOffset,
    themeMode: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onBackupRequested: () -> Unit,
    onRestoreRequested: () -> Unit,
    onDismiss: () -> Unit,
    onThemeSubmenuToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.customColorScheme

    DropdownMenu(
        expanded = showSystemMenu,
        onDismissRequest = onDismiss,
        offset = menuOffset,
        modifier = modifier.background(colors.AppBackground).width(IntrinsicSize.Max)
    ) {
        if (!showThemeSubmenu) {
            DropdownMenuItem(
                text = { Text("Theme", color = colors.AppText, style = MaterialTheme.typography.bodyMedium) },
                onClick = { onThemeSubmenuToggle(true) },
                modifier = Modifier.height(48.dp)
            )
            DropdownMenuItem(
                text = { Text("Backup", color = colors.AppText, style = MaterialTheme.typography.bodyMedium) },
                onClick = {
                    onBackupRequested()
                    onDismiss()
                },
                modifier = Modifier.height(48.dp)
            )
            DropdownMenuItem(
                text = { Text("Restore", color = colors.AppText, style = MaterialTheme.typography.bodyMedium) },
                onClick = {
                    onRestoreRequested()
                    onDismiss()
                },
                modifier = Modifier.height(48.dp)
            )
        } else {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (themeMode == mode) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.AppText,
                                    modifier = Modifier.size(20.dp).padding(end = 8.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.width(28.dp))
                            }
                            Text(
                                text = when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.DAY -> "Light"
                                    ThemeMode.NIGHT -> "Dark"
                                },
                                color = colors.AppText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    onClick = {
                        onThemeSelected(mode)
                        onDismiss()
                    },
                    modifier = Modifier.height(48.dp)
                )
            }
        }
    }
}

@Composable
fun BackupDialog(
    showBackupDialog: Boolean,
    storageVolumes: List<Pair<File, Boolean>>,
    storageManager: StorageManager?,
    context: Context,
    onSaveRequested: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showBackupDialog) return

    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>("Password must be at least 8 characters") }
    val colors = MaterialTheme.customColorScheme
    val removableVolumes = storageVolumes.filter { it.second }

    LaunchedEffect(password) {
        val trimmedPassword = password.trim()
        passwordError = if (trimmedPassword.length < 8) "Password must be at least 8 characters" else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Backup to External Storage", color = colors.AppText) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Encryption Password (min 8 chars)", color = colors.AppText) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordError != null,
                    supportingText = { Text(passwordError ?: " ", color = if (passwordError != null) MaterialTheme.colorScheme.error else colors.AppText) },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    removableVolumes.isEmpty() -> Text(
                        "No removable storage detected. Please insert a USB drive and press 'Retry'.",
                        color = colors.AppText
                    )
                    removableVolumes.size == 1 -> {
                        val volume = storageManager?.storageVolumes?.find { it.directory == removableVolumes[0].first }
                        val volumeName = volume?.getDescription(context) ?: "Removable Storage"
                        Text(
                            "$volumeName detected. Press 'Save' to backup.",
                            color = colors.AppText
                        )
                    }
                    else -> {
                        val volume = storageManager?.storageVolumes?.find { it.directory == removableVolumes[0].first }
                        val volumeName = volume?.getDescription(context) ?: "Removable Storage"
                        Text(
                            "Multiple removable storages detected. Using '$volumeName'. Press 'Save' to backup.",
                            color = colors.AppText
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (removableVolumes.isNotEmpty()) {
                TextButton(
                    onClick = {
                        val trimmedPassword = password.trim()
                        if (trimmedPassword.length >= 8) {
                            onSaveRequested(trimmedPassword)
                        }
                    },
                    enabled = password.trim().length >= 8
                ) {
                    Text("Save", color = colors.CardTotp)
                }
            } else {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = colors.CardTotp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.CardTotp)
            }
        }
    )
}

@Composable
fun RestoreStorageCheckDialog(
    showRestoreStorageCheckDialog: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showRestoreStorageCheckDialog) return

    val colors = MaterialTheme.customColorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Restore from External Storage", color = colors.AppText) },
        text = {
            Text(
                "No removable storage detected. Please insert a USB drive and press 'Retry'.",
                color = colors.AppText
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry", color = colors.CardTotp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.CardTotp)
            }
        }
    )
}

@Composable
fun RestoreSeedsDialog(
    showDialog: Boolean,
    encryptedData: String?,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onSuccess: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showDialog || encryptedData == null) return

    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>("Password must be at least 8 characters") }
    val colors = MaterialTheme.customColorScheme
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(password) {
        val trimmedPassword = password.trim()
        passwordError = if (trimmedPassword.length < 8) "Password must be at least 8 characters" else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Enter Decryption Password", color = colors.AppText) },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Decryption Password (min 8 chars)", color = colors.AppText) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordError != null,
                    supportingText = { Text(passwordError ?: " ", color = if (passwordError != null) MaterialTheme.colorScheme.error else colors.AppText) },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedPassword = password.trim()
                    if (trimmedPassword.length >= 8) {
                        viewModel.importSeedsCrypt(encryptedData, password)?.let { count ->
                            onSuccess(count)
                            coroutineScope.launch {
                                viewModel.dispatch(AuthCommand.SetError("Restored $count entries"))
                                delay(ERROR_DISPLAY_DURATION_MS)
                                viewModel.dispatch(AuthCommand.SetError(null))
                            }
                        } ?: run {
                            coroutineScope.launch {
                                viewModel.dispatch(AuthCommand.SetError("Restore failed: Invalid password or data"))
                                delay(ERROR_DISPLAY_DURATION_MS)
                                viewModel.dispatch(AuthCommand.SetError(null))
                            }
                        }
                        onDismiss()
                    }
                },
                enabled = password.trim().length >= 8
            ) {
                Text("Decrypt", color = colors.CardTotp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.CardTotp)
            }
        }
    )
}

@Composable
fun OperationSuccessDialog(
    showDialog: Boolean,
    message: String,
    onDismiss: () -> Unit,
    onLaunchMediaManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showDialog) return

    val colors = MaterialTheme.customColorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(message, color = colors.AppText) },
        text = { Text("Launch media manager to unmount the USB Flash storage device?", color = colors.AppText) },
        confirmButton = {
            TextButton(onClick = {
                onLaunchMediaManager()
                onDismiss()
            }) {
                Text("Yes", color = colors.CardTotp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No", color = colors.CardTotp)
            }
        }
    )
}

private fun launchMediaManagerForUnmount(activity: MainActivity, viewModel: MainViewModel) {
    // try Samsung My Files first
    var intent = activity.packageManager.getLaunchIntentForPackage("com.sec.android.app.myfiles")
    if (intent != null) {
        try {
            activity.startActivity(intent)
            viewModel.dispatch(AuthCommand.SetError("Please eject USB via Samsung My Files"))
            return
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open Samsung My Files: ${e.message}")
            viewModel.dispatch(AuthCommand.SetError("Failed to open Samsung My Files"))
        }
    }

    // try Google Files next
    intent = activity.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.files")
    if (intent != null) {
        try {
            activity.startActivity(intent)
            viewModel.dispatch(AuthCommand.SetError("Please eject USB via Google Files"))
            return
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open Google Files: ${e.message}")
            viewModel.dispatch(AuthCommand.SetError("Failed to open Google Files"))
        }
    }

    // fallback to Settings
    intent = Intent(android.provider.Settings.ACTION_MEMORY_CARD_SETTINGS)
    try {
        activity.startActivity(intent)
        viewModel.dispatch(AuthCommand.SetError("Please eject USB via Settings"))
        return
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to open Settings: ${e.message}")
        viewModel.dispatch(AuthCommand.SetError("Failed to open Settings"))
    }

    // if all attempts fail
    viewModel.dispatch(AuthCommand.SetError("Please unmount USB manually via Settings or file manager"))
}

private fun isValidBase32(seed: String): Boolean {
    val alphabet = TotpGenerator.ALPHABET
    val cleanedSeed = seed.uppercase().filter { it in alphabet }
    val validLengths = setOf(2, 4, 5, 7) + (8..cleanedSeed.length step 8).toSet()
    return cleanedSeed.isNotEmpty() && cleanedSeed.length in validLengths
}
