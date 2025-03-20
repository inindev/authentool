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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.github.inindev.authentool.ui.theme.AppColorTheme
import com.github.inindev.authentool.ui.theme.customColorScheme

const val highlight_delay_ms = 8000L
private const val error_display_duration_ms = 2000L
private const val animation_duration_ms = 100

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
            MainActivityContent(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainActivityContent(viewModel: MainViewModel) {
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
                viewModel.clearError()
            }
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
                        title = { Text("Authentool", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.customColorScheme.TopBarText) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.customColorScheme.TopBarBackground,
                            titleContentColor = MaterialTheme.customColorScheme.TopBarText
                        ),
                        actions = {
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "add entry", tint = MaterialTheme.customColorScheme.TopBarText)
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
                    AuthGrid(viewModel = viewModel)
                    if (showAddDialog) {
                        AddEntryDialog(
                            onDismiss = { showAddDialog = false },
                            onAdd = { name, seed ->
                                viewModel.addAuthCode(name, seed)
                                showAddDialog = false
                            }
                        )
                    }
                    uiState.pendingDeleteCardId?.let { cardId ->
                        val card = uiState.codes.find { it.id == cardId }
                        card?.let {
                            AlertDialog(
                                onDismissRequest = { viewModel.cancelDelete() },
                                title = { Text("Delete Entry", color = MaterialTheme.customColorScheme.AppText, style = MaterialTheme.typography.titleLarge) },
                                text = { Text("Are you sure you want to delete ${card.name}?", color = MaterialTheme.customColorScheme.AppText, style = MaterialTheme.typography.bodyLarge) },
                                confirmButton = {
                                    TextButton(onClick = { viewModel.confirmDelete() }) {
                                        Text("delete", color = MaterialTheme.customColorScheme.CardTotp, style = MaterialTheme.typography.labelLarge)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { viewModel.cancelDelete() }) {
                                        Text("cancel", color = MaterialTheme.customColorScheme.CardTotp, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthGrid(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val countdownProgress by viewModel.countdownProgress.collectAsState()
    val animatedProgress by animateFloatAsState(
        targetValue = countdownProgress,
        animationSpec = tween(durationMillis = animation_duration_ms, easing = LinearEasing),
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

    // log recomposition only when codes change
    LaunchedEffect(codes) {
        Log.d("MainActivity", "authgrid: recomposing with ${codes.size} codes: ${codes.map { it.name }}")
    }

    var editedName by remember { mutableStateOf<String?>(null) }
    var showSystemMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset(0.dp, 0.dp)) }
    var showThemeSubmenu by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var storageVolumes by remember { mutableStateOf(emptyList<Pair<File, Boolean>>()) }

    // custom contract to set initial uri to removable storage
    val saveFileLauncher = rememberLauncherForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/json") {
            override fun createIntent(context: Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                val removableVolume = storageVolumes.firstOrNull { it.second }
                removableVolume?.let { (dir, _) ->
                    val volume = storageManager?.storageVolumes?.find { it.directory == dir }
                    volume?.uuid?.let { uuid ->
                        intent.putExtra(
                            DocumentsContract.EXTRA_INITIAL_URI,
                            "content://com.android.externalstorage.documents/document/primary:$uuid".toUri()
                        )
                    }
                }
                return intent
            }
        }
    ) { uri ->
        uri?.let {
            viewModel.exportSeeds()?.let { seedsJson ->
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(seedsJson.toByteArray())
                } ?: Log.e("MainActivity", "AuthGrid: Failed to open output stream for uri $it")
                coroutineScope.launch {
                    viewModel.setError("Backup saved to USB.")
                    delay(error_display_duration_ms)
                    viewModel.clearError()
                }
            }
            showBackupDialog = false
        } ?: run {
            coroutineScope.launch {
                viewModel.setError("Failed to save backup.")
                delay(error_display_duration_ms)
                viewModel.clearError()
            }
        }
    }

    // implicit refresh when dialog opens
    LaunchedEffect(showBackupDialog) {
        if (showBackupDialog) {
            storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                volume.directory?.let { it to volume.isRemovable }
            } ?: emptyList()
        }
    }

    // poll storage changes efficiently while dialog is open
    LaunchedEffect(showBackupDialog) {
        if (showBackupDialog) {
            snapshotFlow {
                storageManager?.storageVolumes?.mapNotNull { volume ->
                    volume.directory?.let { it to volume.isRemovable }
                } ?: emptyList()
            }.collect { newStorageVolumes ->
                if (newStorageVolumes != storageVolumes) {
                    storageVolumes = newStorageVolumes
                }
            }
        }
    }

    BackHandler(enabled = uiState.editingIndex == null && !showSystemMenu) { /* ignore */ }
    BackHandler(enabled = showSystemMenu || showBackupDialog) {
        if (showThemeSubmenu) {
            showThemeSubmenu = false
        } else if (showBackupDialog) {
            showBackupDialog = false
        } else {
            showSystemMenu = false
        }
    }

    val cardStates = codes.mapIndexed { index, card ->
        AuthCardUiState(
            card = card,
            totpCode = totpCodes.getOrElse(index) { "000000" },
            isEditing = uiState.editingIndex == index,
            isHighlighted = uiState.highlightedIndex == index,
            position = GridPosition(index, index / columns, index % columns)
        )
    }

    val controller = object : AuthCardController {
        override fun onCopyCode(code: String) { /* handled in composable */ }
        override fun onEditStarted(cardId: String) = viewModel.setEditingIndex(codes.indexOfFirst { it.id == cardId })
        override fun onEditName(cardId: String, newName: String) = viewModel.updateAuthCodeName(codes.indexOfFirst { it.id == cardId }, newName)
        override fun startEdit(state: AuthCardUiState) = viewModel.setEditingIndex(codes.indexOfFirst { it.id == state.card.id })
        override fun onEditDismissed(cardId: String) = viewModel.setEditingIndex(null)
        override fun onDelete(cardId: String) = viewModel.requestDelete(cardId)
        override fun onMove(cardId: String, direction: Direction) = viewModel.swapAuthCode(codes.indexOfFirst { it.id == cardId }, direction)
        override fun onHighlight(cardId: String) = viewModel.setHighlightedIndex(codes.indexOfFirst { it.id == cardId })
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
                                    if (uiState.editingIndex != null) {
                                        editedName?.let { name ->
                                            val editingIndex = uiState.editingIndex!!
                                            if (name != codes[editingIndex].name) {
                                                viewModel.updateAuthCodeName(editingIndex, name)
                                            }
                                        }
                                        viewModel.setEditingIndex(null)
                                    }
                                },
                                onLongPress = { offset ->
                                    if (uiState.editingIndex == null) {
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
                        AuthenticatorCard(state = state, controller = controller)
                    }
                }
                SystemMenu(
                    showSystemMenu = showSystemMenu,
                    showThemeSubmenu = showThemeSubmenu,
                    menuOffset = menuOffset,
                    themeMode = uiState.themeMode,
                    onThemeSelected = { viewModel.setThemeMode(it) },
                    onBackupRequested = { showBackupDialog = true },
                    onDismiss = { showSystemMenu = false; showThemeSubmenu = false },
                    onThemeSubmenuToggle = { showThemeSubmenu = it }
                )
                BackupDialog(
                    showBackupDialog = showBackupDialog,
                    storageVolumes = storageVolumes,
                    storageManager = storageManager,
                    context = context,
                    onSaveRequested = { saveFileLauncher.launch("authentool_seeds_${System.currentTimeMillis()}.json") },
                    onDismiss = { showBackupDialog = false },
                    onRetry = {
                        storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                            volume.directory?.let { it to volume.isRemovable }
                        } ?: emptyList()
                    }
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

/**
 * Dialog for adding a new authenticator entry with name and seed fields.
 */
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
                        keyboardType = KeyboardType.Ascii,
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
                            when {
                                name.isBlank() -> errorMessage = "Name cannot be empty."
                                seed.isBlank() -> errorMessage = "Seed cannot be empty."
                                !isValidBase32(seed) -> errorMessage = "Seed must be valid Base32 (A-Z, 2-7)."
                                else -> {
                                    onAdd(name, seed)
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
    onSaveRequested: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showBackupDialog) return

    val colors = MaterialTheme.customColorScheme
    val removableVolumes = storageVolumes.filter { it.second }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Backup to External Storage", color = colors.AppText) },
        text = {
            Column {
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
                TextButton(onClick = onSaveRequested) {
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

// validate base32 seed
private fun isValidBase32(seed: String): Boolean {
    val alphabet = TotpGenerator.ALPHABET
    val cleanedSeed = seed.uppercase().filter { it != '=' }
    return cleanedSeed.isNotEmpty() &&
            cleanedSeed.all { it in alphabet } &&
            (cleanedSeed.length % 8 == 0 || cleanedSeed.length % 8 in listOf(2, 4, 5, 7))
}
