package com.readriffle.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.PreloadedFonts
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.readriffle.app.data.preferences.UserPreferences
import com.readriffle.app.navigation.AppNavigation
import com.readriffle.app.platform.Vfs
import com.readriffle.app.platform.setThemeColor
import com.readriffle.app.theme.AccentColorOption
import com.readriffle.app.theme.ReaderFontOption
import com.readriffle.app.theme.RsvpReaderTheme
import com.readriffle.app.theme.ThemeMode
import com.readriffle.app.theme.ThemeVariant
import com.readriffle.app.ui.common.GeminiApiKeyPromptDialog
import com.readriffle.web.res.Res
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Arranque de Book Room web: carga las tipografías y los archivos guardados en el navegador
 * (IndexedDB), recoge la vuelta del inicio de sesión de Google si la hay, y pinta la app dentro
 * del contenedor que prepara index.html.
 */
fun main() {
    MainScope().launch {
        FONT_FILES.forEachIndexed { i, path ->
            runCatching { PreloadedFonts.bytes[i] = Res.readBytes(path) }
        }
        Vfs.init()
        val container = AppContainer()
        container.driveAuthManager.captureRedirect()
        // Cuenta recordada con el permiso caducado: se renueva solo (ida y vuelta a Google sin preguntar).
        if (container.driveAuthManager.renewOnStartupIfNeeded()) return@launch
        container.coverLock.restore(container.libraryRepository)
        ComposeViewport(document.getElementById("app")!!) {
            RsvpApp(container)
        }
    }
}

@Composable
private fun RsvpApp(container: AppContainer) {
    val prefs by container.userPreferencesRepository.preferencesFlow.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val wide = with(density) { windowInfo.containerSize.width.toDp() } >= com.readriffle.app.ui.common.WIDE_BREAKPOINT
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, com.readriffle.app.ui.common.LocalWide provides wide) {
        RsvpReaderTheme(
            themeMode = ThemeMode.fromKey(prefs.themeMode),
            themeVariant = ThemeVariant.fromKey(prefs.themeVariantKey),
            amoledBlack = prefs.amoledBlack,
            lightAccent = AccentColorOption.fromKey(prefs.lightAccentKey),
            darkAccent = AccentColorOption.fromKey(prefs.darkAccentKey),
            fontFamily = ReaderFontOption.fromKey(prefs.fontKey).fontFamily
        ) {
            // La barra de estado del iPhone y el fondo de la página toman el color de la app.
            val bg = MaterialTheme.colorScheme.background
            LaunchedEffect(bg) {
                val argb = bg.toArgb()
                setThemeColor("#" + (argb and 0xFFFFFF).toString(16).padStart(6, '0'))
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppNavigation(container)

                // Bloqueo con portada: capa por encima de toda la app (ver [CoverLockOverlay]).
                val lockedBook by container.coverLock.locked.collectAsState()
                lockedBook?.let { book ->
                    com.readriffle.app.ui.lock.CoverLockOverlay(
                        book = book,
                        requireAuth = prefs.coverLockRequireAuth,
                        pin = prefs.coverLockPin,
                        onUnlock = container.coverLock::unlock
                    )
                }

                // Primera visita: aviso de sincronización con Google Drive (¿sí o no?).
                val auth = container.driveAuthManager
                var welcomeDone by remember { mutableStateOf((auth.welcomeShown() || auth.lastSignedInAccount() != null) && auth.startupError == null) }
                var welcomeError by remember { mutableStateOf(auth.startupError) }
                if (!welcomeDone) {
                    com.readriffle.app.ui.common.SyncWelcomeDialog(
                        error = welcomeError,
                        onAccept = {
                            val err = auth.startSignIn(silent = false)
                            if (err == null) auth.markWelcomeShown() else welcomeError = err
                        },
                        onDecline = { auth.markWelcomeShown(); welcomeDone = true }
                    )
                }
                // Cuando ya no queda ningún aviso de bienvenida, se ofrece instalarla en la pantalla de inicio.
                LaunchedEffect(welcomeDone, prefs.geminiKeyPromptShown) {
                    if (welcomeDone && prefs.geminiKeyPromptShown) {
                        kotlinx.coroutines.delay(1200)
                        com.readriffle.app.platform.offerInstallFirstTime()
                    }
                }

                // Pregunta por la API key de Gemini una sola vez (ver [GeminiApiKeyPromptDialog]).
                if (welcomeDone && !prefs.geminiKeyPromptShown) {
                    GeminiApiKeyPromptDialog(
                        onSave = { key ->
                            scope.launch {
                                container.userPreferencesRepository.setGeminiApiKey(key.trim())
                                container.userPreferencesRepository.setGeminiKeyPromptShown(true)
                            }
                        },
                        onSkip = {
                            scope.launch { container.userPreferencesRepository.setGeminiKeyPromptShown(true) }
                        }
                    )
                }
            }
        }
    }
}
