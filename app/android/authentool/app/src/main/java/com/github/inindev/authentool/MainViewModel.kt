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
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class ThemeMode {
    DAY, NIGHT
}

data class AuthCode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val seedBase32: String,
    val code: String = "",
    val period: Int = 30,
    val digits: Int = 6,
    val generator: TotpGenerator = TotpGenerator(TotpGenerator.decodeBase32(seedBase32))
)

@SuppressLint("StaticFieldLeak")  // safe because we use application context
class MainViewModel(private val context: Context) : ViewModel() {
    val themeMode: ThemeMode
        get() = determineThemeMode()

    val countdownProgress: MutableState<Float> = mutableFloatStateOf(1f)
    val authentoolCodes: MutableState<List<AuthCode>> = mutableStateOf(emptyList())

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "authentool_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var countdownJob: Job? = null

    init {
        loadCodes()
        startCountdown()
    }

    private fun determineThemeMode(): ThemeMode {
        val currentTime = LocalTime.now(ZoneId.systemDefault())
        val dayStart = LocalTime.of(6, 0)
        val nightStart = LocalTime.of(20, 0)
        return when {
            currentTime.isAfter(dayStart) && currentTime.isBefore(nightStart) -> ThemeMode.DAY
            else -> ThemeMode.NIGHT
        }
    }

    private fun loadCodes() {
        val codesSet = prefs.getStringSet("auth_codes", emptySet()) ?: emptySet()
        authentoolCodes.value = codesSet.mapNotNull { entry ->
            val parts = entry.split("|", limit = 3)
            if (parts.size < 3 || parts[2].isEmpty()) null // skip if seed is missing/invalid
            else AuthCode(parts[0], parts[1], parts[2]).let {
                it.copy(code = it.generator.generateCode())
            }
        }
    }

    private fun saveCodes() {
        val codesSet = authentoolCodes.value.map { "${it.id}|${it.name}|${it.seedBase32}" }.toSet()
        prefs.edit { putStringSet("auth_codes", codesSet) }
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

    fun addAuthCode(name: String, seedBase32: String) {
        val newCode = AuthCode(name = name, seedBase32 = seedBase32).let {
            it.copy(code = it.generator.generateCode())
        }
        authentoolCodes.value = authentoolCodes.value + newCode
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
        super.onCleared()
    }
}

enum class Direction {
    UP, DOWN, LEFT, RIGHT
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
