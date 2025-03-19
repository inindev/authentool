package com.github.inindev.authentool

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
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

data class AuthCardData(val name: String, val seed: String) {
    internal val generator: TotpGenerator = TotpGenerator(TotpGenerator.decodeBase32(seed))
}

data class MainUiState(
    val codes: List<AuthCardData> = emptyList(),
    val totpCodes: List<String> = emptyList(),
    val editingIndex: Int? = null,
    val errorMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val highlightedIndex: Int? = null
)

@SuppressLint("StaticFieldLeak") // safe with application context
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
            Log.e("MainViewModel", "failed to initialize encryptedsharedpreferences: ${e.message}")
            _uiState.update { it.copy(errorMessage = "Storage initialization failed.") }
            null
        }
    }

    init {
        loadThemeMode()
        loadCodes()
        startCountdown()
    }

    fun addAuthCode(name: String, seed: String) {
        viewModelScope.launch {
            try {
                val newCard = AuthCardData(name, seed)
                _uiState.update { state ->
                    val newCodes = state.codes + newCard
                    state.copy(codes = newCodes)
                }
                saveCodes()
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = "Invalid seed: ${e.message}") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Failed to add entry: ${e.message}") }
            }
        }
    }

    fun deleteAuthCode(index: Int) {
        viewModelScope.launch {
            _uiState.update { state ->
                val newCodes = state.codes.toMutableList().apply { removeAt(index) }
                state.copy(codes = newCodes, editingIndex = null)
            }
            saveCodes()
        }
    }

    fun updateAuthCodeName(index: Int, newName: String) {
        if (newName.isBlank() || index !in _uiState.value.codes.indices) return
        viewModelScope.launch {
            _uiState.update { state ->
                val newCodes = state.codes.toMutableList().apply {
                    this[index] = AuthCardData(newName, this[index].seed)
                }
                state.copy(codes = newCodes)
            }
            saveCodes()
        }
    }

    fun swapAuthCode(index: Int, direction: Direction) {
        val state = _uiState.value
        if (index !in state.codes.indices) return

        val columns = 2 // matches GridCells.Fixed(2)
        val row = index / columns
        val col = index % columns
        val totalRows = (state.codes.size + columns - 1) / columns

        val toIndex = when (direction) {
            Direction.UP -> {
                if (row == 0) return // cannot move up from first row
                index - columns
            }
            Direction.DOWN -> {
                if (row == totalRows - 1) return // cannot move down from last row
                index + columns
            }
            Direction.LEFT -> {
                if (col == 0) return // cannot move left from left column
                index - 1
            }
            Direction.RIGHT -> {
                if (col == columns - 1) return // cannot move right from right column
                index + 1
            }
        }

        if (toIndex !in state.codes.indices) return

        viewModelScope.launch {
            _uiState.update { state ->
                val newCodes = state.codes.toMutableList().apply {
                    val temp = this[index]
                    this[index] = this[toIndex]
                    this[toIndex] = temp
                }
                state.copy(codes = newCodes)
            }
            saveCodes()
        }
    }

    fun setEditingIndex(index: Int?) {
        viewModelScope.launch {
            _uiState.update { state ->
                if (index != null && state.highlightedIndex != null) {
                    Log.d("MainViewModel", "clearing highlight due to editing start: $index")
                    state.copy(editingIndex = index, highlightedIndex = null)
                } else {
                    state.copy(editingIndex = index)
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs?.edit { putString("theme_preference", mode.name) }
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setHighlightedIndex(index: Int?) {
        viewModelScope.launch {
            _uiState.update { state ->
                if (index != null && state.editingIndex != null) {
                    Log.d("MainViewModel", "highlight rejected: editing active at ${state.editingIndex}")
                    state
                } else {
                    Log.d("MainViewModel", "setting highlight to $index")
                    state.copy(highlightedIndex = index)
                }
            }
            if (index != null && _uiState.value.editingIndex == null) {
                delay(highlight_delay_ms)
                _uiState.update { it.copy(highlightedIndex = null) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setError(message: String?) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun exportSeeds(): String? {
        val preferences = prefs ?: run {
            _uiState.update { it.copy(errorMessage = "Storage unavailable.") }
            return null
        }
        return try {
            preferences.getString("auth_codes_list", "[]") ?: "[]"
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to export seeds.") }
            null
        }
    }

    private fun loadThemeMode() {
        prefs?.let {
            val savedMode = it.getString("theme_preference", "SYSTEM") ?: "SYSTEM"
            _uiState.update { state -> state.copy(themeMode = ThemeMode.valueOf(savedMode)) }
        }
    }

    private fun loadCodes() {
        val preferences = prefs ?: run {
            Log.w("MainViewModel", "prefs unavailable - using in-memory storage")
            return
        }
        try {
            val json = preferences.getString("auth_codes_list", "[]") ?: "[]"
            val loadedPairs = Json.decodeFromString<List<Pair<String, String>>>(json)
            _uiState.update { it.copy(codes = loadedPairs.map { AuthCardData(it.first, it.second) }) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to load saved codes.") }
        }
    }

    private fun saveCodes() {
        val preferences = prefs ?: run {
            _uiState.update { it.copy(errorMessage = "Storage unavailable. Changes won’t persist.") }
            return
        }
        try {
            val pairs = _uiState.value.codes.map { it.name to it.seed }
            val json = Json.encodeToString(pairs)
            preferences.edit { putString("auth_codes_list", json) }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to save changes.") }
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
                    val currentCodes = _uiState.value.codes.map { it.generator.generateCode() }
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

enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

enum class ThemeMode {
    SYSTEM, DAY, NIGHT
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
