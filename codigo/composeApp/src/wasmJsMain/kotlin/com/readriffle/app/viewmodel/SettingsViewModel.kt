package com.readriffle.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readriffle.app.AppContainer
import com.readriffle.app.data.preferences.UserPreferences
import com.readriffle.app.data.preferences.UserPreferencesRepository
import com.readriffle.app.theme.AccentColorOption
import com.readriffle.app.theme.ReaderFontOption
import com.readriffle.app.theme.RsvpBackgroundMode
import com.readriffle.app.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: UserPreferencesRepository) : ViewModel() {

    val uiState: StateFlow<UserPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences()
    )

    fun setFont(option: ReaderFontOption) = viewModelScope.launch { repository.setFontKey(option.name) }
    fun setCoverLockPin(enabled: Boolean) = viewModelScope.launch { repository.setCoverLockPin(enabled) }
    fun setCoverLockRequireAuth(enabled: Boolean) = viewModelScope.launch { repository.setCoverLockRequireAuth(enabled) }
    fun setAmoledBlack(enabled: Boolean) = viewModelScope.launch { repository.setAmoledBlack(enabled) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode.name) }

    /** Alterna directamente entre claro y oscuro (sin pasar por "Sistema"), para el botón rápido. */
    fun toggleTheme(currentlyDark: Boolean) = viewModelScope.launch {
        repository.setThemeMode((if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK).name)
    }

    fun setLightAccent(option: AccentColorOption) = viewModelScope.launch { repository.setLightAccentKey(option.name) }
    fun setDarkAccent(option: AccentColorOption) = viewModelScope.launch { repository.setDarkAccentKey(option.name) }
    fun setWpmRange(min: Int, max: Int) = viewModelScope.launch { repository.setWpmRange(min, max) }
    fun setSlowdownIntensity(value: Float) = viewModelScope.launch { repository.setSlowdownIntensity(value) }
    fun setBackgroundMotionAuto(auto: Boolean) = viewModelScope.launch { repository.setBackgroundMotionAuto(auto) }
    fun setParagraphPauseMs(ms: Int) = viewModelScope.launch { repository.setParagraphPauseMs(ms) }
    fun setCommaPauseMs(ms: Int) = viewModelScope.launch { repository.setCommaPauseMs(ms) }
    fun setSentenceEndPauseMs(ms: Int) = viewModelScope.launch { repository.setSentenceEndPauseMs(ms) }
    fun setRsvpBackground(mode: RsvpBackgroundMode) = viewModelScope.launch { repository.setRsvpBackgroundKey(mode.name) }
    fun setWordSkipAmount(amount: Int) = viewModelScope.launch { repository.setWordSkipAmount(amount) }
    fun setGeminiApiKey(key: String) = viewModelScope.launch { repository.setGeminiApiKey(key.trim()) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container.userPreferencesRepository) }
        }
    }
}
