package com.github.inindev.authentool

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class AuthCardData(val name: String, val seed: String) {
    private val generator: TotpGenerator = TotpGenerator(TotpGenerator.decodeBase32(seed))
    var totpCode by mutableStateOf(generateTotp())

    fun updateTotp() {
        totpCode = generator.generateCode()
    }

    private fun generateTotp(): String {
        return generator.generateCode()
    }
}

@SuppressLint("StaticFieldLeak") // safe with application context
class MainViewModel(private val context: Context) : ViewModel() {
    val countdownProgress: MutableState<Float> = mutableFloatStateOf(1f)
    private val authentoolCodes: MutableList<AuthCardData> = mutableListOf()
    val codesState: MutableState<List<AuthCardData>> = mutableStateOf(authentoolCodes)
    val errorMessage: MutableState<String?> = mutableStateOf(null)
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
            Log.e("MainViewModel", "Failed to initialize EncryptedSharedPreferences: ${e.message}")
            errorMessage.value = "Storage initialization failed. Changes will not persist."
            null
        }
    }

    val themeMode: MutableState<ThemeMode> = mutableStateOf(ThemeMode.SYSTEM) // Now a MutableState

    init {
        loadThemeMode() // Load initial theme mode
        loadCodes()
        startCountdown()
    }

    private fun loadThemeMode() {
        prefs?.let {
            val savedMode = it.getString("theme_preference", "SYSTEM") ?: "SYSTEM"
            themeMode.value = ThemeMode.valueOf(savedMode)
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val millis = System.currentTimeMillis()
                val seconds = (millis / 1000) % 30
                val millisFraction = (millis % 1000) / 1000f
                val progress = 1f - ((seconds + millisFraction) / 30f)
                countdownProgress.value = progress
                if (seconds == 0L && millisFraction < 0.05f) {
                    updateCodes()
                }
                delay(100)
            }
        }
    }

    private fun loadCodes() {
        val preferences = prefs
        if (preferences == null) {
            Log.w("MainViewModel", "Prefs unavailable - using in-memory storage")
            return
        }
        try {
            val json = preferences.getString("auth_codes_list", "[]") ?: "[]"
            val loadedPairs = Json.decodeFromString<List<Pair<String, String>>>(json)
            authentoolCodes.clear()
            authentoolCodes.addAll(loadedPairs.map { AuthCardData(it.first, it.second) })
            codesState.value = authentoolCodes
            errorMessage.value = null
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to load codes: ${e.stackTraceToString()}")
            errorMessage.value = "Failed to load saved codes. Using temporary storage."
            authentoolCodes.clear()
            codesState.value = authentoolCodes
        }
    }

    private fun saveCodes() {
        val preferences = prefs
        if (preferences == null) {
            Log.w("MainViewModel", "Prefs unavailable - skipping save")
            errorMessage.value = "Storage unavailable. Changes won’t persist."
            return
        }
        try {
            val pairs = authentoolCodes.map { it.name to it.seed }
            val json = Json.encodeToString(pairs)
            preferences.edit { putString("auth_codes_list", json) }
            errorMessage.value = null
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to save codes: ${e.stackTraceToString()}")
            errorMessage.value = "Failed to save changes. Data may be lost."
        }
    }

    fun addAuthCode(name: String, seed: String) {
        try {
            val newCard = AuthCardData(name, seed)
            authentoolCodes.add(newCard)
            codesState.value = authentoolCodes.toList()
            saveCodes()
        } catch (e: IllegalArgumentException) {
            errorMessage.value = "Invalid seed: ${e.message}"
        } catch (e: Exception) {
            Log.e("MainViewModel", "Unexpected error adding code: ${e.message}")
            errorMessage.value = "Failed to add entry: ${e.message}"
        }
    }

    fun deleteAuthCode(cardToDelete: AuthCardData) {
        authentoolCodes.remove(cardToDelete)
        codesState.value = authentoolCodes.toList()
        saveCodes()
    }

    fun swapAuthCode(index: Int, direction: Direction) {
        val toIndex = when (direction) {
            Direction.UP -> index - 2
            Direction.DOWN -> index + 2
            Direction.LEFT -> index - 1
            Direction.RIGHT -> index + 1
        }
        if (toIndex < 0 || toIndex >= authentoolCodes.size || index == toIndex) return
        when (direction) {
            Direction.LEFT -> if (index % 2 == 0) return
            Direction.RIGHT -> if (index % 2 != 0) return
            else -> {}
        }
        val temp = authentoolCodes[index]
        authentoolCodes[index] = authentoolCodes[toIndex]
        authentoolCodes[toIndex] = temp
        codesState.value = authentoolCodes.toList()
        saveCodes()
    }

    fun updateAuthCodeName(index: Int, newName: String) {
        if (index < 0 || index >= authentoolCodes.size || newName.isBlank()) return
        val oldCard = authentoolCodes[index]
        authentoolCodes[index] = AuthCardData(newName, oldCard.seed)
        codesState.value = authentoolCodes.toList()
        saveCodes()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs?.edit { putString("theme_preference", mode.name) }
        themeMode.value = mode // Update the state to trigger recomposition
    }

    private fun updateCodes() {
        for (card in authentoolCodes) {
            card.updateTotp()
        }
        codesState.value = authentoolCodes.toList()
    }

    override fun onCleared() {
        countdownJob?.cancel()
        authentoolCodes.clear()
        codesState.value = authentoolCodes
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
