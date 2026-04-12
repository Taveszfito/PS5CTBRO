package com.DueBoysenberry1226.ps5ctbro.ui.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.DueBoysenberry1226.ps5ctbro.audio.AudioController
import com.DueBoysenberry1226.ps5ctbro.audio.AudioControllerImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.util.Locale

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val audioController: AudioController = AudioControllerImpl.getInstance(application)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            currentLanguage = getCurrentLanguageCode(),
            appVersion = getVersionName(),
            audioGain = audioController.uiState.value.audioGain
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        audioController.uiState
            .onEach { audioState ->
                _uiState.update { it.copy(audioGain = audioState.audioGain) }
            }
            .launchIn(viewModelScope)
    }

    private fun getCurrentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        val lang = if (locales.isEmpty) {
            Locale.getDefault().language
        } else {
            locales.get(0)?.language
        }
        return if (lang?.startsWith("hu", ignoreCase = true) == true) "hu" else "en"
    }

    private fun getVersionName(): String {
        return try {
            val context = getApplication<Application>()
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    fun setLanguage(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
        
        // Frissítjük a lokális állapotot, hogy a gombokon látszódjon a kijelölés
        _uiState.update { it.copy(currentLanguage = languageCode) }
    }

    fun setAudioGain(gain: Float) {
        audioController.setAudioGain(gain)
    }
}
