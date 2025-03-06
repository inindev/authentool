package com.github.inindev.authentool

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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
    val id: String = UUID.randomUUID().toString(), // unique identifier
    val name: String,
    val seedBase32: String? = null, // Base32 seed, null if using random secret
    val code: String = "",
    val period: Int = 30,
    val digits: Int = 6,
    val generator: TotpGenerator = seedBase32?.let { TotpGenerator(Base32.decode(it)) } ?: TotpGenerator()
)

class MainViewModel(private val context: Context) : ViewModel() {
    val themeMode: ThemeMode
        get() = determineThemeMode()

    val countdownProgress: MutableState<Float> = mutableStateOf(1f)
    val authentoolCodes: MutableState<List<AuthCode>> = mutableStateOf(emptyList())

    private val prefs = EncryptedSharedPreferences.create(
        "authentool_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
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
            val (id, name, seed) = entry.split("|", limit = 3)
            AuthCode(id, name, seed.takeIf { it.isNotEmpty() })
        }.map { it.copy(code = it.generator.generateCode()) }
        if (authentoolCodes.value.isEmpty()) {
            initializeDefaultCodes()
        }
    }

    private fun saveCodes() {
        val codesSet = authentoolCodes.value.map { "${it.id}|${it.name}|${it.seedBase32.orEmpty()}" }.toSet()
        prefs.edit().putStringSet("auth_codes", codesSet).apply()
    }

    private fun initializeDefaultCodes() {
        authentoolCodes.value = listOf(
            AuthCode(name = "Facebook"),
            AuthCode(name = "Google"),
            AuthCode(name = "Instagram"),
            AuthCode(name = "xAI"),
            AuthCode(name = "GitHub"),
            AuthCode(name = "Proton"),
            AuthCode(name = "Amazon"),
            AuthCode(name = "Tesla"),
            AuthCode(name = "Acme"),
            AuthCode(name = "Cloudflare")
        ).map { it.copy(code = it.generator.generateCode()) }
        saveCodes()
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

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
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

/** Simple Base32 decoder for TOTP seeds (RFC 4648). */
object Base32 {
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    fun decode(base32: String): ByteArray {
        val cleaned = base32.uppercase().filter { it in alphabet }
        val bits = cleaned.map { alphabet.indexOf(it).toString(2).padStart(5, '0') }.joinToString("")
        val bytes = (0 until bits.length / 8).map {
            bits.substring(it * 8, (it + 1) * 8).toInt(2).toByte()
        }
        return bytes.toByteArray()
    }
}
