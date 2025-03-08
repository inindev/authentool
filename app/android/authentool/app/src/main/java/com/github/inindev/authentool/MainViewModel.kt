package com.github.inindev.authentool

import android.annotation.SuppressLint
import android.content.Context
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

@SuppressLint("StaticFieldLeak")  // safe because we use application context
class MainViewModel(private val context: Context) : ViewModel() {
    val countdownProgress: MutableState<Float> = mutableFloatStateOf(1f)
    private val authentoolCodes: List<AuthCardData> = mutableListOf()
    val codesState: MutableState<List<AuthCardData>> = mutableStateOf(authentoolCodes)

    val themeMode: ThemeMode
        get() = ThemeMode.valueOf(prefs.getString("theme_preference", "SYSTEM") ?: "SYSTEM")

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "authentool_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var nextId: Int = 1
    private var countdownJob: Job? = null

    init {
        loadCodes()
        startCountdown()
    }

    private fun loadCodes() {
        val json = prefs.getString("auth_codes_list", "[]") ?: "[]"
        val loadedPairs = Json.decodeFromString<List<Pair<String, String>>>(json)
        authentoolCodes as MutableList<AuthCardData>
        authentoolCodes.clear()
        authentoolCodes.addAll(loadedPairs.map { AuthCardData(it.first, it.second) })
        codesState.value = authentoolCodes
        // nextId not needed anymore, but kept for potential future use
        nextId = authentoolCodes.size + 1
    }

    private fun saveCodes() {
        val pairs = authentoolCodes.map { it.name to it.seed }
        val json = Json.encodeToString(pairs)
        prefs.edit { putString("auth_codes_list", json) }
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
                delay(64) // 10 updates per second
            }
        }
    }

    private fun updateCodes() {
        for (card in authentoolCodes) {
            card.updateTotp()
        }
    }

    fun addAuthCode(name: String, seed: String) {
        val newCard = AuthCardData(name, seed)
        authentoolCodes as MutableList<AuthCardData>
        authentoolCodes.add(newCard)
        codesState.value = authentoolCodes
        saveCodes()
    }

    fun deleteAuthCode(cardToDelete: AuthCardData) {
        authentoolCodes as MutableList<AuthCardData>
        authentoolCodes.remove(cardToDelete)
        codesState.value = authentoolCodes
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
        authentoolCodes as MutableList<AuthCardData>
        val temp = authentoolCodes[index]
        authentoolCodes[index] = authentoolCodes[toIndex]
        authentoolCodes[toIndex] = temp
        codesState.value = authentoolCodes
        saveCodes()
    }

    fun updateAuthCodeName(index: Int, newName: String) {
        if (index < 0 || index >= authentoolCodes.size || newName.isBlank()) return
        authentoolCodes as MutableList<AuthCardData>
        val oldCard = authentoolCodes[index]
        authentoolCodes[index] = AuthCardData(newName, oldCard.seed)
        codesState.value = authentoolCodes
        saveCodes()
    }

    override fun onCleared() {
        countdownJob?.cancel()
        authentoolCodes as MutableList<AuthCardData>
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
