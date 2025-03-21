package com.github.inindev.authentool

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

data class MainUiState(
    val codes: List<AuthCard> = emptyList(),
    val totpCodes: List<String> = emptyList(),
    val editingCardId: String? = null,
    val highlightedCardId: String? = null,
    val errorMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

sealed class AuthCommand {
    data class AddCard(val name: String, val seed: String) : AuthCommand()
    data class RenameCard(val cardId: String, val newName: String) : AuthCommand()
    data class DeleteCard(val cardId: String) : AuthCommand()
    data class MoveCard(val cardId: String, val direction: Direction) : AuthCommand()
    data class StartEditing(val cardId: String) : AuthCommand()
    data class StopEditing(val cardId: String) : AuthCommand()
    data class HighlightCard(val cardId: String) : AuthCommand()
    data class ClearHighlight(val cardId: String) : AuthCommand()
    data class SetTheme(val mode: ThemeMode) : AuthCommand()
    data class SetError(val message: String?) : AuthCommand()
}

@SuppressLint("StaticFieldLeak")
class MainViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _countdownProgress = MutableStateFlow(1f)
    val countdownProgress: StateFlow<Float> = _countdownProgress.asStateFlow()

    private var countdownJob: Job? = null

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "authentool_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "failed to initialize encryptedsharedpreferences: ${e.message}")
            _uiState.update { it.copy(errorMessage = "Storage initialization failed.") }
            null
        }
    }

    init {
        loadThemeMode()
        loadCodes()
        startCountdown()
    }

    fun dispatch(command: AuthCommand) {
        viewModelScope.launch {
            val newState = reduce(_uiState.value, command)
            _uiState.value = newState
            if (shouldPersist(command)) {
                saveCodes(newState.codes)
            }
        }
    }

    private fun reduce(state: MainUiState, command: AuthCommand): MainUiState = when (command) {
        is AuthCommand.AddCard -> {
            try {
                val newCard = AuthCard(name = command.name, seed = command.seed)
                state.copy(codes = state.codes + newCard)
            } catch (e: IllegalArgumentException) {
                state.copy(errorMessage = "Invalid seed: ${e.message}")
            } catch (e: Exception) {
                state.copy(errorMessage = "Failed to add entry: ${e.message}")
            }
        }
        is AuthCommand.RenameCard -> {
            val index = state.codes.indexOfFirst { it.id == command.cardId }
            if (index >= 0 && command.newName.isNotBlank()) {
                val updatedCard = state.codes[index].copy(name = command.newName)
                state.copy(codes = state.codes.toMutableList().apply { this[index] = updatedCard })
            } else state
        }
        is AuthCommand.DeleteCard -> {
            state.copy(codes = state.codes.filter { it.id != command.cardId })
        }
        is AuthCommand.MoveCard -> {
            val index = state.codes.indexOfFirst { it.id == command.cardId }
            if (index >= 0) swapCards(state, index, command.direction) else state
        }
        is AuthCommand.StartEditing -> state.copy(editingCardId = command.cardId, highlightedCardId = null)
        is AuthCommand.StopEditing -> state.copy(editingCardId = null)
        is AuthCommand.HighlightCard -> if (state.editingCardId == null) {
            state.copy(highlightedCardId = command.cardId).also { scheduleHighlightClear(command.cardId) }
        } else state
        is AuthCommand.ClearHighlight -> state.copy(highlightedCardId = null)
        is AuthCommand.SetTheme -> state.copy(themeMode = command.mode).also { saveThemeMode(command.mode) }
        is AuthCommand.SetError -> state.copy(errorMessage = command.message)
    }

    private fun shouldPersist(command: AuthCommand): Boolean = when (command) {
        is AuthCommand.AddCard, is AuthCommand.RenameCard, is AuthCommand.DeleteCard, is AuthCommand.MoveCard -> true
        else -> false
    }

    private fun swapCards(state: MainUiState, index: Int, direction: Direction): MainUiState {
        val columns = 2
        val toIndex = calculateNewIndex(index, direction, state.codes.size, columns)
        return if (toIndex != null) {
            val newCodes = state.codes.toMutableList().apply {
                val fromCard = this[index]
                val toCard = this[toIndex]
                // swap without regenerating the moved card’s id
                this[index] = toCard.copy(id = java.util.UUID.randomUUID().toString()) // new id for the displaced card
                this[toIndex] = fromCard // keep original id for the moved card
            }
            // if the moved card was being edited, keep it in edit mode
            val newEditingCardId = if (state.editingCardId == state.codes[index].id) {
                state.codes[index].id // use the original id, now at toIndex
            } else {
                state.editingCardId
            }
            state.copy(codes = newCodes, editingCardId = newEditingCardId)
        } else state
    }

    private fun calculateNewIndex(index: Int, direction: Direction, size: Int, columns: Int): Int? {
        val row = index / columns
        val col = index % columns
        val totalRows = (size + columns - 1) / columns
        return when (direction) {
            Direction.UP -> if (row > 0) index - columns else null
            Direction.DOWN -> if (row < totalRows - 1 && index + columns < size) index + columns else null
            Direction.LEFT -> if (col > 0) index - 1 else null
            Direction.RIGHT -> if (col < columns - 1 && index + 1 < size) index + 1 else null
        }
    }

    private fun scheduleHighlightClear(cardId: String) {
        viewModelScope.launch {
            delay(HIGHLIGHT_DELAY_MS)
            if (_uiState.value.highlightedCardId == cardId) {
                dispatch(AuthCommand.ClearHighlight(cardId))
            }
        }
    }

    private fun saveCodes(codes: List<AuthCard>) {
        val preferences = prefs ?: run {
            _uiState.update { it.copy(errorMessage = "Storage unavailable. Changes won’t persist.") }
            return
        }
        try {
            val pairs = codes.map { it.name to it.seed }
            val json = Json.encodeToString(pairs)
            preferences.edit { putString("auth_codes_list", json) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to save changes.") }
        }
    }

    private fun loadCodes() {
        val preferences = prefs ?: run {
            android.util.Log.w("MainViewModel", "prefs unavailable - using in-memory storage")
            return
        }
        try {
            val json = preferences.getString("auth_codes_list", "[]") ?: "[]"
            val loadedPairs = try {
                Json.decodeFromString<List<Pair<String, String>>>(json)
            } catch (_: Exception) {
                Json.decodeFromString<List<ExportEntry>>(json).map { it.name to it.seed }
            }
            _uiState.update {
                it.copy(codes = loadedPairs.map { AuthCard("${it.first}-${it.second.hashCode()}", it.first, it.second) })
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to load saved codes.") }
        }
    }

    private fun saveThemeMode(mode: ThemeMode) {
        prefs?.edit { putString("theme_preference", mode.name) }
    }

    private fun loadThemeMode() {
        prefs?.let {
            val savedMode = it.getString("theme_preference", "SYSTEM") ?: "SYSTEM"
            _uiState.update { state -> state.copy(themeMode = ThemeMode.valueOf(savedMode)) }
        }
    }

    @Serializable
    private data class ExportEntry(val name: String, val seed: String)

    fun exportSeedsCrypt(password: String): String? {
        val preferences = prefs ?: run {
            _uiState.update { it.copy(errorMessage = "Storage unavailable.") }
            return null
        }
        return try {
            val codes = _uiState.value.codes.map { ExportEntry(it.name, it.seed) }
            val json = Json.encodeToString(codes)
            json.encrypt(password)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to export seeds.") }
            null
        }
    }

    fun importSeedsCrypt(encryptedData: String, password: String): Int? {
        return try {
            val json = encryptedData.decrypt(password)
            val entries = Json.decodeFromString<List<ExportEntry>>(json)
            val newCards = entries.map { AuthCard(name = it.name, seed = it.seed) }
            viewModelScope.launch {
                _uiState.update { current ->
                    val uniqueCards = newCards.filter { newCard ->
                        current.codes.none { it.name == newCard.name && it.seed == newCard.seed }
                    }
                    current.copy(codes = current.codes + uniqueCards)
                }
                saveCodes(_uiState.value.codes)
            }
            newCards.size
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Restore failed: ${e.message}") }
            null
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var lastEpoch = -1L
            while (isActive) {
                val now = System.currentTimeMillis()
                val timeInSeconds = now / 1000
                val epoch = timeInSeconds / TotpGenerator.TIME_STEP
                val timeInPeriod = now % (TotpGenerator.TIME_STEP * 1000)
                val denominator = TotpGenerator.TIME_STEP * 1000f
                val progress = if (denominator > 0f) 1f - (timeInPeriod / denominator) else 1f

                _countdownProgress.value = progress

                if (epoch != lastEpoch) {
                    val currentCodes = _uiState.value.codes.map { it.generateTotpCode() }
                    _uiState.update { it.copy(totpCodes = currentCodes) }
                    lastEpoch = epoch
                }
                delay(100)
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class ThemeMode { SYSTEM, DAY, NIGHT }

class MainViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
