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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.github.inindev.authentool.ui.theme.AppColorTheme
import com.github.inindev.authentool.ui.theme.customColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

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
    var codeToDelete by remember { mutableStateOf<AuthCardData?>(null) }
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
                    AuthGrid(
                        viewModel = viewModel,
                        onCodeToDeleteChange = { codeToDelete = it }
                    )
                    if (showAddDialog) {
                        AddEntryDialog(
                            onDismiss = { showAddDialog = false },
                            onAdd = { name, seed ->
                                viewModel.addAuthCode(name, seed)
                                showAddDialog = false
                            }
                        )
                    }
                    codeToDelete?.let { card ->
                        AlertDialog(
                            onDismissRequest = { codeToDelete = null },
                            title = { Text("Delete Entry", color = MaterialTheme.customColorScheme.AppText, style = MaterialTheme.typography.titleLarge) },
                            text = { Text("Are you sure you want to delete ${card.name}?", color = MaterialTheme.customColorScheme.AppText, style = MaterialTheme.typography.bodyLarge) },
                            confirmButton = {
                                TextButton(onClick = {
                                    val index = uiState.codes.indexOf(card)
                                    if (index >= 0) viewModel.deleteAuthCode(index)
                                    codeToDelete = null
                                }) {
                                    Text("delete", color = MaterialTheme.customColorScheme.CardTotp, style = MaterialTheme.typography.labelLarge)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { codeToDelete = null }) {
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

@Composable
fun AuthGrid(
    viewModel: MainViewModel,
    onCodeToDeleteChange: (AuthCardData?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val countdownProgress = uiState.countdownProgress
    val animatedProgress by animateFloatAsState(
        targetValue = countdownProgress,
        animationSpec = tween(durationMillis = animation_duration_ms, easing = LinearEasing)
    )
    val codes = uiState.codes
    val colorScheme = MaterialTheme.customColorScheme
    val context = LocalContext.current
    val storageManager = context.getSystemService<StorageManager>()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // log recomposition only when codes change
    LaunchedEffect(codes) {
        Log.d("MainActivity", "authgrid: recomposing with ${codes.size} codes: ${codes.map { it.name }}")
    }

    var editedName by remember { mutableStateOf<String?>(null) }
    var showSystemMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset(0.dp, 0.dp)) }
    var showThemeSubmenu by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    // state for storage info, initialized empty
    var externalDirs by remember { mutableStateOf(emptyList<File>()) }
    val storageVolumesState = remember { mutableStateOf(emptyList<Pair<File, Boolean>>()) }
    var storageVolumes by storageVolumesState

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
                } ?: Log.e("MainActivity", "authgrid: failed to open output stream for uri $it")
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
            externalDirs = context.getExternalFilesDirs(null).filterNotNull().toList()
            storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                volume.directory?.let { it to volume.isRemovable }
            } ?: emptyList()
        }
    }

    // poll storage changes efficiently while dialog is open
    LaunchedEffect(showBackupDialog) {
        if (showBackupDialog) {
            snapshotFlow {
                val newExternalDirs = context.getExternalFilesDirs(null).filterNotNull().toList()
                val newStorageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                    volume.directory?.let { it to volume.isRemovable }
                } ?: emptyList()
                newExternalDirs to newStorageVolumes
            }.collect { (newExternalDirs, newStorageVolumes) ->
                if (newExternalDirs != externalDirs || newStorageVolumes != storageVolumes) {
                    externalDirs = newExternalDirs
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
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(all = 16.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (uiState.editingIndex != null) {
                                        editedName?.let { name ->
                                            if (name != codes[uiState.editingIndex!!].name) {
                                                viewModel.updateAuthCodeName(uiState.editingIndex!!, name)
                                            }
                                        }
                                        viewModel.setEditingIndex(null)
                                    }
                                },
                                onLongPress = { offset ->
                                    if (uiState.editingIndex == null) {
                                        Log.d("MainActivity", "authgrid: background long press at $offset")
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
                        items = uiState.codes,
                        key = { _, card -> "${card.name}-${card.seed}" } // stable key
                    ) { index, card ->
                        Log.d("MainActivity", "authgrid: rendering card index=$index, name=${card.name}")
                        AuthenticatorCard(
                            card = card,
                            totpCode = uiState.totpCodes.getOrElse(index) { "000000" },
                            textColor = colorScheme.CardName,
                            cardBackground = colorScheme.CardBackground,
                            highlightColor = colorScheme.CardHiBackground,
                            isEditing = index == uiState.editingIndex,
                            onLongPress = { viewModel.setEditingIndex(index) },
                            onDeleteClick = { onCodeToDeleteChange(card) },
                            moveActions = MoveActions(
                                onMoveUp = { viewModel.swapAuthCode(index, Direction.UP); viewModel.setEditingIndex(null) },
                                onMoveDown = { viewModel.swapAuthCode(index, Direction.DOWN); viewModel.setEditingIndex(null) },
                                onMoveLeft = { viewModel.swapAuthCode(index, Direction.LEFT); viewModel.setEditingIndex(null) },
                                onMoveRight = { viewModel.swapAuthCode(index, Direction.RIGHT); viewModel.setEditingIndex(null) }
                            ),
                            onNameChanged = { newName -> editedName = newName },
                            index = index,
                            totalItems = codes.size,
                            onEditingDismissed = {
                                editedName?.let { name ->
                                    if (name != codes[uiState.editingIndex!!].name) {
                                        viewModel.updateAuthCodeName(index, name)
                                    }
                                }
                                viewModel.setEditingIndex(null)
                            },
                            isHighlighted = uiState.highlightedIndex == index,
                            onHighlight = { viewModel.setHighlightedIndex(index) },
                            isEditingActive = uiState.editingIndex != null
                        )
                    }
                }
                if (showSystemMenu) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { showSystemMenu = false },
                        offset = menuOffset,
                        modifier = Modifier
                            .background(colorScheme.AppBackground)
                            .width(IntrinsicSize.Max)
                    ) {
                        if (!showThemeSubmenu) {
                            DropdownMenuItem(
                                text = { Text("Theme", color = colorScheme.AppText, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { showThemeSubmenu = true },
                                modifier = Modifier.height(48.dp)
                            )
                            DropdownMenuItem(
                                text = { Text("Backup", color = colorScheme.AppText, style = MaterialTheme.typography.bodyMedium) },
                                onClick = { showBackupDialog = true; showSystemMenu = false },
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
                                            if (uiState.themeMode == mode) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = colorScheme.AppText,
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
                                                color = colorScheme.AppText,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setThemeMode(mode)
                                        showSystemMenu = false
                                        showThemeSubmenu = false
                                    },
                                    modifier = Modifier.height(48.dp)
                                )
                            }
                        }
                    }
                }
                if (showBackupDialog) {
                    AlertDialog(
                        onDismissRequest = { showBackupDialog = false },
                        title = { Text("Backup to External Storage", color = colorScheme.AppText) },
                        text = {
                            Column {
                                val removableVolumes = storageVolumes.filter { it.second }
                                when {
                                    removableVolumes.isEmpty() -> Text(
                                        "No removable storage detected. Please insert a USB drive and press 'Retry'.",
                                        color = colorScheme.AppText
                                    )
                                    removableVolumes.size == 1 -> {
                                        val volume = storageManager?.storageVolumes?.find { it.directory == removableVolumes[0].first }
                                        val volumeName = volume?.getDescription(context) ?: "Removable Storage"
                                        Text(
                                            "$volumeName detected. Press 'Save' to backup.",
                                            color = colorScheme.AppText
                                        )
                                    }
                                    else -> {
                                        val volume = storageManager?.storageVolumes?.find { it.directory == removableVolumes[0].first }
                                        val volumeName = volume?.getDescription(context) ?: "Removable Storage"
                                        Text(
                                            "Multiple removable storages detected. Using '$volumeName'. Press 'Save' to backup.",
                                            color = colorScheme.AppText
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            val removableVolumes = storageVolumes.filter { it.second }
                            if (removableVolumes.isNotEmpty()) {
                                TextButton(onClick = {
                                    saveFileLauncher.launch("authentool_seeds_${System.currentTimeMillis()}.json")
                                }) {
                                    Text("Save", color = colorScheme.CardTotp)
                                }
                            } else {
                                TextButton(onClick = {
                                    // retry: refresh storage volumes
                                    externalDirs = context.getExternalFilesDirs(null).filterNotNull().toList()
                                    storageVolumes = storageManager?.storageVolumes?.mapNotNull { volume ->
                                        volume.directory?.let { it to volume.isRemovable }
                                    } ?: emptyList()
                                }) {
                                    Text("Retry", color = colorScheme.CardTotp)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBackupDialog = false }) {
                                Text("Cancel", color = colorScheme.CardTotp)
                            }
                        }
                    )
                }
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
 * groups move-related actions for an authenticator card.
 */
data class MoveActions(
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onMoveLeft: () -> Unit,
    val onMoveRight: () -> Unit
)

/**
 * displays an authenticator card with a name, code, and editing controls.
 */
@Composable
fun AuthenticatorCard(
    card: AuthCardData,
    totpCode: String,
    textColor: Color,
    cardBackground: Color,
    highlightColor: Color,
    isEditing: Boolean,
    onLongPress: () -> Unit,
    onDeleteClick: () -> Unit,
    moveActions: MoveActions,
    onNameChanged: (String) -> Unit,
    index: Int,
    totalItems: Int,
    onEditingDismissed: () -> Unit,
    isHighlighted: Boolean,
    onHighlight: () -> Unit,
    isEditingActive: Boolean
) {
    val colors = MaterialTheme.customColorScheme
    var editedName by remember(isEditing) { mutableStateOf(card.name) }
    val clipboardManager = LocalClipboardManager.current

    // log card recomposition
    LaunchedEffect(Unit) {
        Log.d("MainActivity", "authenticatorcard: recomposing index=$index, name=${card.name}")
    }

    BackHandler(enabled = isEditing) {
        onEditingDismissed()
    }

    Card(
        modifier = Modifier
            .pointerInput(card) { // use card object as key
                detectTapGestures(
                    onTap = {
                        if (!isEditingActive) {
                            Log.d("MainActivity", "card tapped: index=$index, name=${card.name}")
                            onHighlight()
                            val codeWithoutSpaces = totpCode.replace("\\s".toRegex(), "")
                            clipboardManager.setText(AnnotatedString(codeWithoutSpaces))
                        }
                    },
                    onLongPress = {
                        if (!isEditingActive) {
                            Log.d("MainActivity", "card long-pressed: index=$index, name=${card.name}")
                            onLongPress()
                        }
                    }
                )
            }
            .fillMaxWidth()
            .padding(4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isHighlighted) highlightColor else cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = if (isEditing) 0.dp else 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { newValue ->
                            editedName = newValue
                            onNameChanged(newValue)
                        },
                        label = { Text("name", color = colors.AppText, style = MaterialTheme.typography.bodyLarge) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp) // add right padding for separation
                    )
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .align(Alignment.Top)
                    ) {
                        TextButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "delete",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = card.name,
                        color = if (isHighlighted) colors.CardHiName else textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatCode(totpCode),
                color = if (isHighlighted) colors.CardHiTotp else colors.CardTotp,
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Start
            )
            if (isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = moveActions.onMoveUp, enabled = index >= 2) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "move up", tint = colors.AppText)
                    }
                    IconButton(onClick = moveActions.onMoveLeft, enabled = index % 2 != 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "move left", tint = colors.AppText)
                    }
                    IconButton(onClick = moveActions.onMoveRight, enabled = index % 2 == 0 && index < totalItems - 1) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "move right", tint = colors.AppText)
                    }
                    IconButton(onClick = moveActions.onMoveDown, enabled = index < totalItems - 2) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "move down", tint = colors.AppText)
                    }
                }
            }
        }
    }
}

/**
 * dialog for adding a new authenticator entry with name and seed fields.
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

// format a 6-digit code as "xxx xxx" with a non-breaking space
private fun formatCode(code: String): String {
    return if (code.length == 6) {
        "${code.substring(0, 3)}\u00A0${code.substring(3)}"
    } else {
        code
    }
}

// validate base32 seed
private fun isValidBase32(seed: String): Boolean {
    val alphabet = TotpGenerator.ALPHABET
    val cleanedSeed = seed.uppercase().filter { it != '=' }
    return cleanedSeed.isNotEmpty() &&
            cleanedSeed.all { it in alphabet } &&
            (cleanedSeed.length % 8 == 0 || cleanedSeed.length % 8 in listOf(2, 4, 5, 7))
}
