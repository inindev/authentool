package com.github.inindev.authentool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.inindev.authentool.ui.theme.AppColorTheme
import com.github.inindev.authentool.ui.theme.customColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// countdown bar placement
private enum class CountdownLocation {
    NONE,
    TOP,
    BOTTOM,
    BOTH
}
private val COUNTDOWN_LOCATION = CountdownLocation.BOTH

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel = viewModel<MainViewModel>(factory = MainViewModelFactory(applicationContext))
            MainActivityContent(viewModel = viewModel)
        }
    }
}

@Composable
fun MainActivityContent(viewModel: MainViewModel) {
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (viewModel.themeMode) {
        ThemeMode.SYSTEM -> systemDarkTheme
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
    }
    AppColorTheme(darkTheme = darkTheme, dynamicColor = false) {
        Authentool(viewModel = viewModel)
    }
}

/**
 * Main composable for the Authentool app, displaying a grid of authenticator cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Authentool(viewModel: MainViewModel) {
    val countdownProgress = viewModel.countdownProgress.value
    val codes = viewModel.authentoolCodes.value
    val colorScheme = MaterialTheme.customColorScheme

    var showAddDialog by remember { mutableStateOf(false) }
    var codeToDelete by remember { mutableStateOf<AuthCode?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editedName by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = editingIndex == null) { /* ignore */ }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Authentool", color = colorScheme.TopBarText) },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "add entry", tint = colorScheme.TopBarText)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.TopBarBackground)
                )
                if (COUNTDOWN_LOCATION == CountdownLocation.TOP || COUNTDOWN_LOCATION == CountdownLocation.BOTH) {
                    LinearProgressIndicator(
                        progress = { countdownProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = colorScheme.ProgressFill,
                        trackColor = colorScheme.ProgressTrack
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(editingIndex) {
                    if (editingIndex != null) {
                        detectTapGestures {
                            editedName?.let { name ->
                                if (name != codes[editingIndex!!].name) {
                                    viewModel.updateAuthCodeName(editingIndex!!, name)
                                }
                            }
                            editingIndex = null
                        }
                    }
                },
            color = colorScheme.AppBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).padding(all = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(codes) { index, code ->
                        AuthenticatorCard(
                            code = code,
                            textColor = colorScheme.CardName,
                            cardBackground = colorScheme.CardBackground,
                            highlightColor = colorScheme.CardHiBackground,
                            isEditing = index == editingIndex,
                            onLongPress = { editingIndex = index },
                            onDeleteClick = { codeToDelete = code },
                            moveActions = MoveActions(
                                onMoveUp = {
                                    viewModel.swapAuthCode(index, Direction.UP)
                                    editedName?.let { name ->
                                        if (name != code.name) {
                                            viewModel.updateAuthCodeName(index, name)
                                        }
                                    }
                                    editingIndex = null
                                },
                                onMoveDown = {
                                    viewModel.swapAuthCode(index, Direction.DOWN)
                                    editedName?.let { name ->
                                        if (name != code.name) {
                                            viewModel.updateAuthCodeName(index, name)
                                        }
                                    }
                                    editingIndex = null
                                },
                                onMoveLeft = {
                                    viewModel.swapAuthCode(index, Direction.LEFT)
                                    editedName?.let { name ->
                                        if (name != code.name) {
                                            viewModel.updateAuthCodeName(index, name)
                                        }
                                    }
                                    editingIndex = null
                                },
                                onMoveRight = {
                                    viewModel.swapAuthCode(index, Direction.RIGHT)
                                    editedName?.let { name ->
                                        if (name != code.name) {
                                            viewModel.updateAuthCodeName(index, name)
                                        }
                                    }
                                    editingIndex = null
                                }
                            ),
                            onNameChanged = { newName -> editedName = newName },
                            index = index,
                            totalItems = codes.size,
                            onEditingDismissed = {
                                editedName?.let { name ->
                                    if (name != code.name) {
                                        viewModel.updateAuthCodeName(index, name)
                                    }
                                }
                                editingIndex = null
                            }
                        )
                    }
                }
                if (COUNTDOWN_LOCATION == CountdownLocation.BOTTOM || COUNTDOWN_LOCATION == CountdownLocation.BOTH) {
                    LinearProgressIndicator(
                        progress = { countdownProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = colorScheme.ProgressFill,
                        trackColor = colorScheme.ProgressTrack
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEntryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, seed ->
                viewModel.addAuthCode(name, seed)
                showAddDialog = false
            }
        )
    }

    codeToDelete?.let { code ->
        AlertDialog(
            onDismissRequest = { codeToDelete = null },
            title = { Text("Delete Entry", color = colorScheme.AppText) },
            text = { Text("Are you sure you want to delete ${code.name}?", color = colorScheme.AppText) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAuthCode(code)
                    codeToDelete = null
                    editingIndex = null
                }) {
                    Text("delete", color = colorScheme.CardTotp)
                }
            },
            dismissButton = {
                TextButton(onClick = { codeToDelete = null }) {
                    Text("cancel", color = colorScheme.CardTotp)
                }
            }
        )
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
 * when tapped, highlights the background and copies the code to the clipboard without spaces.
 * when long-pressed, enters editing mode with a text field and move/delete buttons.
 */
@Composable
fun AuthenticatorCard(
    code: AuthCode,
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
    onEditingDismissed: () -> Unit
) {
    val colors = MaterialTheme.customColorScheme
    var editedName by remember(isEditing) { mutableStateOf(code.name) }
    var isHighlighted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    BackHandler(enabled = isEditing) {
        onEditingDismissed()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        isHighlighted = true
                        val codeWithoutSpaces = code.code.replace("\\s".toRegex(), "")
                        clipboardManager.setText(AnnotatedString(codeWithoutSpaces))
                        coroutineScope.launch {
                            delay(8000)  // long highlight for easy reference
                            isHighlighted = false
                        }
                    },
                    onLongPress = { onLongPress() }
                )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isHighlighted) highlightColor else cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
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
                        label = { Text("name", color = colors.AppText) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp).align(Alignment.Top)
                    ) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Cancel, contentDescription = "delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Text(
                        text = code.name,
                        color = if (isHighlighted) colors.CardHiName else textColor,
                        fontSize = 14.sp,
                        fontFamily = FontFamily(Font(R.font.lato_regular))
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatCode(code.code),
                color = if (isHighlighted) colors.CardHiTotp else colors.CardTotp,
                fontSize = 36.sp,
                fontFamily = FontFamily(Font(R.font.lato_bold)),
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
 * catches exceptions on "add" and displays an error message, allowing the user to fix or cancel.
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
                Text("Add Authenticator Entry", fontSize = 20.sp, color = colors.AppText)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("name", color = colors.AppText) },
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
                    label = { Text("base32 seed", color = colors.AppText) },
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
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("cancel", color = colors.CardTotp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            when {
                                name.isBlank() -> errorMessage = "Name cannot be empty."
                                seed.isBlank() -> errorMessage = "Seed cannot be empty."
                                !isValidBase32(seed) -> errorMessage = "Seed must be valid Base32 (A-Z, 2-7)."
                                else -> {
                                    try {
                                        onAdd(name, seed)
                                        errorMessage = null
                                    } catch (e: Exception) {
                                        errorMessage = "Unable to add entry. Please check the seed and try again."
                                    }
                                }
                            }
                        }
                    ) {
                        Text("add", color = colors.CardTotp)
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

/**
 * Validates that a string is a valid Base32-encoded seed.
 * Ensures it only contains characters from the Base32 alphabet and has a length compatible with Base32 decoding.
 */
private fun isValidBase32(seed: String): Boolean {
    val alphabet = TotpGenerator.ALPHABET
    val cleanedSeed = seed.uppercase().filter { it != '=' } // Ignore padding for initial check
    return cleanedSeed.isNotEmpty() &&
            cleanedSeed.all { it in alphabet } &&
            (cleanedSeed.length % 8 == 0 || cleanedSeed.length % 8 in listOf(2, 4, 5, 7)) // Valid Base32 lengths
}
