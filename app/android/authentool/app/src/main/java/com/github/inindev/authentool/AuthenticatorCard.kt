package com.github.inindev.authentool

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

import com.github.inindev.authentool.ui.theme.customColorScheme
import java.util.UUID

@Stable
@Serializable
data class AuthCard(
    @Transient val id: String = UUID.randomUUID().toString(),
    val name: String,
    val seed: String,
    @Transient val generator: TotpGenerator = TotpGenerator(TotpGenerator.decodeBase32(seed))
) {
    fun generateTotpCode(): String = generator.generateCode()
    fun formattedTotpCode(): String = generateTotpCode().let { code ->
        if (code.length == 6) "${code.substring(0, 3)}\u205F${code.substring(3)}" else code
    }
}

@Immutable
data class AuthCardUiState(
    val card: AuthCard,
    val totpCode: String,
    val isEditing: Boolean = false,
    val isHighlighted: Boolean = false,
    val position: GridPosition = GridPosition(0, 0, 0)
)

@Immutable
data class GridPosition(val index: Int, val row: Int, val column: Int) {
    fun canMove(direction: Direction, totalItems: Int, columns: Int): Boolean = when (direction) {
        Direction.UP -> row > 0
        Direction.DOWN -> index + columns < totalItems
        Direction.LEFT -> column > 0
        Direction.RIGHT -> column < columns - 1 && index < totalItems - 1
    }
}

// controller interface
interface AuthCardController {
    fun onCopyCode(code: String)
    fun onEditStarted(cardId: String)
    fun onEditName(cardId: String, newName: String)
    fun onEditDismissed(cardId: String)
    fun startEdit(state: AuthCardUiState)
    fun onDelete(cardId: String)
    fun onMove(cardId: String, direction: Direction)
    fun onHighlight(cardId: String)
}

/**
 * Displays an authenticator card with name, totp code, and controls.
 */
@Composable
fun AuthenticatorCard(
    state: AuthCardUiState,
    controller: AuthCardController,
    modifier: Modifier = Modifier,
    colors: AuthCardColors = AuthCardColors.default()
) {
    val card = state.card
    val clipboardManager = LocalClipboardManager.current
    var editedName by rememberSaveable(state.isEditing) { mutableStateOf(card.name) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!state.isEditing) {
                            controller.onHighlight(card.id)
                            clipboardManager.setText(AnnotatedString(card.generateTotpCode()))
                        }
                    },
                    onLongPress = {
                        if (!state.isEditing) {
                            println("Long-press on card.id: ${card.id}")
                            controller.onEditStarted(card.id)
                        }
                    }
                )
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (state.isHighlighted) colors.highlightBackground else colors.background),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = if (state.isEditing) 0.dp else 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (state.isEditing) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("name", color = colors.text) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    TextButton(onClick = { controller.onDelete(card.id) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Delete, "delete", tint = colors.error)
                            Text("delete", color = colors.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Text(
                        text = card.name,
                        color = if (state.isHighlighted) colors.highlightText else colors.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = card.formattedTotpCode(),
                color = if (state.isHighlighted) colors.highlightTotp else colors.totp,
                style = MaterialTheme.typography.displayLarge
            )
            if (state.isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MoveButton(Icons.Default.ArrowUpward, "move up", colors.text, state.position.canMove(Direction.UP, Int.MAX_VALUE, 2)) {
                        controller.onMove(card.id, Direction.UP)
                    }
                    MoveButton(Icons.AutoMirrored.Filled.ArrowBack, "move left", colors.text, state.position.canMove(Direction.LEFT, Int.MAX_VALUE, 2)) {
                        controller.onMove(card.id, Direction.LEFT)
                    }
                    MoveButton(Icons.AutoMirrored.Filled.ArrowForward, "move right", colors.text, state.position.canMove(Direction.RIGHT, Int.MAX_VALUE, 2)) {
                        controller.onMove(card.id, Direction.RIGHT)
                    }
                    MoveButton(Icons.Default.ArrowDownward, "move down", colors.text, state.position.canMove(Direction.DOWN, Int.MAX_VALUE, 2)) {
                        controller.onMove(card.id, Direction.DOWN)
                    }
                }
            }
        }
    }

    // handle edit dismissal via back press or external trigger
    BackHandler(enabled = state.isEditing) {
        if (editedName != card.name) controller.onEditName(card.id, editedName)
        controller.onEditDismissed(card.id)
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = desc, tint = tint)
    }
}

@Immutable
data class AuthCardColors(
    val text: Color,
    val totp: Color,
    val background: Color,
    val highlightText: Color,
    val highlightTotp: Color,
    val highlightBackground: Color,
    val error: Color
) {
    companion object {
        @Composable
        fun default() = with(MaterialTheme.customColorScheme) {
            AuthCardColors(
                text = CardName,
                totp = CardTotp,
                background = CardBackground,
                highlightText = CardHiName,
                highlightTotp = CardHiTotp,
                highlightBackground = CardHiBackground,
                error = MaterialTheme.colorScheme.error
            )
        }
    }
}
