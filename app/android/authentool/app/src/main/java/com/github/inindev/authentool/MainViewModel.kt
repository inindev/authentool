package com.github.inindev.authentool

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AuthCode(
    val id: Int,
    val name: String,
    val seed: String,
    val code: String = "", // transient, not serialized
    val period: Int = 30,  // transient
    val digits: Int = 6,   // transient
    @kotlinx.serialization.Transient // explicitly exclude from serialization
    val generator: TotpGenerator = TotpGenerator(TotpGenerator.decodeBase32(seed))
)

@SuppressLint("StaticFieldLeak")  // safe because we use application context
class MainViewModel(private val context: Context) : ViewModel() {
    val countdownProgress: MutableState<Float> = mutableFloatStateOf(1f)
    val authentoolCodes: MutableState<List<AuthCode>> = mutableStateOf(emptyList())
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
        authentoolCodes.value = Json.decodeFromString<List<AuthCode>>(json).map {
            it.copy(code = it.generator.generateCode())
        }
        // Compute nextId from loaded data
        nextId = authentoolCodes.value.maxOfOrNull { it.id }?.plus(1) ?: 1
    }

    private fun saveCodes() {
        val json = Json.encodeToString(authentoolCodes.value)
        prefs.edit { putString("auth_codes_list", json) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive) {
                val seconds = System.currentTimeMillis() / 1000 % 30
                val progress = 1f - (seconds / 30f)
                countdownProgress.value = progress

                if (seconds == 0L) {
                    updateCodes()
                }
                delay(1000)
            }
        }
    }

    private fun updateCodes() {
        authentoolCodes.value = authentoolCodes.value.map {
            it.copy(code = it.generator.generateCode())
        }
    }

    fun addAuthCode(name: String, seed: String) {
        val newCode = AuthCode(id = nextId, name = name, seed = seed).let {
            it.copy(code = it.generator.generateCode())
        }
        authentoolCodes.value = authentoolCodes.value + newCode
        nextId++ // Increment after use
        saveCodes()
    }

    fun deleteAuthCode(code: AuthCode) {
        authentoolCodes.value = authentoolCodes.value.filter { it.id != code.id }
        saveCodes()
    }

    fun swapAuthCode(index: Int, direction: Direction) {
        val toIndex = when (direction) {
            Direction.UP -> index - 2
            Direction.DOWN -> index + 2
            Direction.LEFT -> index - 1
            Direction.RIGHT -> index + 1
        }
        if (toIndex < 0 || toIndex >= authentoolCodes.value.size || index == toIndex) return
        when (direction) {
            Direction.LEFT -> if (index % 2 == 0) return
            Direction.RIGHT -> if (index % 2 != 0) return
            else -> {}
        }
        val updatedList = authentoolCodes.value.toMutableList().apply {
            val temp = this[index]
            this[index] = this[toIndex]
            this[toIndex] = temp
        }
        authentoolCodes.value = updatedList
        saveCodes()
    }

    fun updateAuthCodeName(index: Int, newName: String) {
        if (index < 0 || index >= authentoolCodes.value.size || newName.isBlank()) return
        val updatedList = authentoolCodes.value.toMutableList().apply {
            val oldCode = this[index]
            this[index] = oldCode.copy(name = newName)
        }
        authentoolCodes.value = updatedList
        saveCodes()
    }

    override fun onCleared() {
        countdownJob?.cancel()
        authentoolCodes.value = emptyList() // Clear sensitive data
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
