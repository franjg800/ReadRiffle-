package com.readriffle.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readriffle.app.AppContainer
import com.readriffle.app.data.preferences.UserPreferencesRepository
import com.readriffle.app.data.sync.DriveAuthManager
import com.readriffle.app.data.sync.DriveSyncManager
import com.readriffle.app.data.sync.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val accountEmail: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncedAtMillis: Long = 0L,
    val errorMessage: String? = null,
    val signingSha1: String = "",
    /** ID de cliente web de Google (vacío = falta configurarlo). */
    val clientId: String = ""
)

/**
 * Sincronización con el Drive del propio usuario (ver [DriveSyncManager]/[DriveAuthManager]) --
 * pedido explícito: "tener sincronizados mis libros... y el progreso... en todos los
 * dispositivos donde esté la app". Se crea UNA SOLA VEZ (ver `AppNavigation`, misma idea que
 * `NotesViewModel`) y se comparte entre Biblioteca y Ajustes, para que una sincronización
 * disparada desde cualquiera de las dos pantallas actualice el estado que ve la otra.
 */
class SyncViewModel(
    private val authManager: DriveAuthManager,
    private val syncManager: DriveSyncManager,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(signingSha1 = authManager.webOrigin(), clientId = authManager.clientId()) }
        authManager.lastSignedInAccount()?.let { account ->
            _uiState.update { it.copy(accountEmail = account.email ?: "Cuenta de Google") }
        }
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(lastSyncedAtMillis = prefs.lastSyncedAtMillis) }
            }
        }
        // Venimos de la pantalla de Google: si dio permiso, se sincroniza ya.
        authManager.consumeRedirectResult()?.let { result ->
            if (result.ok) {
                viewModelScope.launch {
                    val email = authManager.refreshEmail()
                    _uiState.update { it.copy(accountEmail = email ?: it.accountEmail ?: "Cuenta de Google", errorMessage = null) }
                    syncNow()
                }
            } else {
                _uiState.update { it.copy(errorMessage = result.error) }
            }
        }
    }

    /** Conecta la cuenta de Google (la página va a Google y vuelve). */
    fun connect() {
        authManager.startSignIn(silent = false)?.let { msg -> _uiState.update { it.copy(errorMessage = msg) } }
    }

    fun setClientId(id: String) {
        authManager.setClientId(id)
        _uiState.update { it.copy(clientId = authManager.clientId()) }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _uiState.update { it.copy(accountEmail = null) }
        }
    }

    /**
     * Sincroniza ahora -- no hace nada si ya hay una sincronización en curso o no hay sesión. En la
     * web el permiso de Google dura una hora: si ha caducado y [interactive], se renueva con una ida
     * y vuelta a Google (sin preguntar nada si la sesión sigue abierta) y se sincroniza al volver.
     */
    fun syncNow(interactive: Boolean = true) {
        if (_uiState.value.isSyncing) return
        if (authManager.lastSignedInAccount() == null) return
        if (!authManager.hasValidToken()) {
            if (interactive) authManager.startSignIn(silent = true)?.let { msg -> _uiState.update { it.copy(errorMessage = msg) } }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, errorMessage = null) }
            when (val result = syncManager.syncAll()) {
                is SyncResult.Success -> {
                    preferencesRepository.setLastSyncedAtMillis(result.syncedAtMillis)
                    _uiState.update { it.copy(isSyncing = false, lastSyncedAtMillis = result.syncedAtMillis) }
                }
                is SyncResult.NotSignedIn -> _uiState.update {
                    it.copy(isSyncing = false, errorMessage = authManager.lastTokenError)
                }
                is SyncResult.Failure -> _uiState.update { it.copy(isSyncing = false, errorMessage = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SyncViewModel(container.driveAuthManager, container.driveSyncManager, container.userPreferencesRepository)
            }
        }
    }
}
