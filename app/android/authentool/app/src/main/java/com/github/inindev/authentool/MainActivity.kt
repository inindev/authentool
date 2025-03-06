package com.github.inindev.authentool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Authentool()
        }
    }
}

/**
 * Main composable for the Authentool app, displaying a grid of authenticator cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Authentool(viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current.applicationContext))) {
    val countdownProgress = viewModel.countdownProgress.value
    val codes = viewModel.authentoolCodes.value
    val themeMode = viewModel.themeMode

    val isDayMode by remember(themeMode) { mutableStateOf(themeMode == ThemeMode.DAY) }
    val backgroundColor by remember(themeMode) { mutableStateOf(if (isDayMode) Color.White else Color(0xFF212121)) }
    val textColor by remember(themeMode) { mutableStateOf(if (isDayMode) Color.Black else Color.White) }
    val cardBackground by remember(themeMode) { mutableStateOf(if (isDayMode) Color(0xFFF5F5F5) else Color(0xFF424242)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var codeToDelete by remember { mutableStateOf<AuthCode?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editedName by remember { mutableStateOf<String?>(null) }

    // prevent back press from closing the app
    BackHandler(enabled = true, onBack = { /* do nothing */ })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Authentool") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "add entry")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isDayMode) Color.LightGray else Color.DarkGray)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures {
                        val currentEditingIndex = editingIndex
                        if (currentEditingIndex != null &&
                            currentEditingIndex >= 0 &&
                            currentEditingIndex < codes.size &&
                            editedName != null
                        ) {
                            editedName?.let { name ->
                                if (name != codes[currentEditingIndex].name) {
                                    viewModel.updateAuthCodeName(currentEditingIndex, name)
                                }
                            }
                        }
                        editingIndex = null
                    }
                },
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(all = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(codes) { index, code ->
                        AuthenticatorCard(
                            code = code,
                            textColor = textColor,
                            cardBackground = cardBackground,
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
                            totalItems = codes.size
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { countdownProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color.Blue,
                    trackColor = Color.Gray
                )
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
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete ${code.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAuthCode(code)
                    codeToDelete = null
                    editingIndex = null
                }) {
                    Text("delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { codeToDelete = null }) {
                    Text("cancel")
                }
            }
        )
    }
}

/**
 * Groups move-related actions for an authenticator card.
 */
data class MoveActions(
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onMoveLeft: () -> Unit,
    val onMoveRight: () -> Unit
)

/**
 * Displays an authenticator card with a name, code, and editing controls.
 * When editing, shows a text field with a delete button positioned higher and further right, and move buttons below.
 */
@Composable
fun AuthenticatorCard(
    code: AuthCode,
    textColor: Color,
    cardBackground: Color,
    isEditing: Boolean,
    onLongPress: () -> Unit,
    onDeleteClick: () -> Unit,
    moveActions: MoveActions,
    onNameChanged: (String) -> Unit,
    index: Int,
    totalItems: Int
) {
    // edited name state, reset when isEditing changes
    var editedName by remember(isEditing) { mutableStateOf(code.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(start = 20.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // name display or edit field with delete button
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
                        label = { Text("name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, bottom = 8.dp)
                            .align(Alignment.Top)
                    ) {
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Text(
                        text = code.name,
                        color = textColor,
                        fontSize = 14.sp,
                        fontFamily = FontFamily(Font(R.font.lato_regular))
                    )
                }
            }

            // spacing between name and code
            Spacer(modifier = Modifier.height(6.dp))

            // authentication code
            Text(
                text = formatCode(code.code),
                color = Color.Blue,
                fontSize = 36.sp,
                fontFamily = FontFamily(Font(R.font.lato_bold)),
                textAlign = TextAlign.Start
            )

            // editing controls: up, left, right, down
            if (isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = moveActions.onMoveUp,
                        enabled = index >= 2
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "move up")
                    }
                    IconButton(
                        onClick = moveActions.onMoveLeft,
                        enabled = index % 2 != 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "move left")
                    }
                    IconButton(
                        onClick = moveActions.onMoveRight,
                        enabled = index % 2 == 0 && index < totalItems - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "move right")
                    }
                    IconButton(
                        onClick = moveActions.onMoveDown,
                        enabled = index < totalItems - 2
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "move down")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding a new authenticator entry with name and seed fields.
 * Catches exceptions on "add" and displays an error message, allowing the user to fix or cancel.
 */
@Composable
fun AddEntryDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // close dialog on back press (esc key)
    BackHandler(enabled = true, onBack = onDismiss)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color.White) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Authenticator Entry", fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = seed,
                    onValueChange = { seed = it },
                    label = { Text("base32 seed") },
                    modifier = Modifier.fillMaxWidth()
                )
                // display error message if present
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
                        Text("cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            try {
                                onAdd(name, seed)
                                errorMessage = null  // clear error on success
                            } catch (e: Exception) {
                                errorMessage = "Cannot add entry: ${e.message ?: "Invalid seed"}. Please fix the input."
                            }
                        }
                    ) {
                        Text("add")
                    }
                }
            }
        }
    }
}

// format a 6-digit code as "xxx xxx" with a non-breaking space
private fun formatCode(code: String): String {
    return if (code.length == 6) {
        "${code.substring(0, 3)}\u00A0${code.substring(3)}"  // use non-breaking space
    } else {
        code  // fallback if code isn't 6 digits
    }
}
