package com.github.inindev.authentool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Authentool(viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current))) {
    val countdownProgress = viewModel.countdownProgress.value
    val codes = viewModel.authentoolCodes.value
    val themeMode = viewModel.themeMode

    val isDayMode = themeMode == ThemeMode.DAY
    val backgroundColor = if (isDayMode) Color.White else Color(0xFF212121)
    val textColor = if (isDayMode) Color.Black else Color.White
    val cardBackground = if (isDayMode) Color(0xFFF5F5F5) else Color(0xFF424242)

    // state for add dialog
    var showAddDialog by remember { mutableStateOf(false) }
    // state for delete dialog
    var codeToDelete by remember { mutableStateOf<AuthCode?>(null) }

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
                .padding(paddingValues),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .padding(all = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(codes.size) { index ->
                        AuthenticatorCard(
                            code = codes[index],
                            textColor = textColor,
                            cardBackground = cardBackground,
                            onLongPress = { codeToDelete = codes[index] }
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

    // add entry dialog
    if (showAddDialog) {
        AddEntryDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, seed ->
                viewModel.addAuthCode(name, seed)
                showAddDialog = false
            }
        )
    }

    // delete confirmation dialog
    codeToDelete?.let { code ->
        AlertDialog(
            onDismissRequest = { codeToDelete = null },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete ${code.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAuthCode(code)
                    codeToDelete = null
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

@Composable
fun AuthenticatorCard(
    code: AuthCode,
    textColor: Color,
    cardBackground: Color,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier
                .padding(start = 24.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = code.name,
                color = textColor,
                fontSize = 14.sp,
                fontFamily = FontFamily(Font(R.font.lato_regular))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatCode(code.code),
                color = Color.Blue,
                fontSize = 36.sp,
                fontFamily = FontFamily(Font(R.font.lato_bold)),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
fun AddEntryDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }

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
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onAdd(name, seed) }) {
                        Text("add")
                    }
                }
            }
        }
    }
}

// format the 6-digit code as "xxx xxx"
private fun formatCode(code: String): String {
    return if (code.length == 6) {
        "${code.substring(0, 3)} ${code.substring(3)}"
    } else {
        code // fallback if code isn't 6 digits
    }
}
