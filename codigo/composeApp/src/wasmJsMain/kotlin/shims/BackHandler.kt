package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.readriffle.app.platform.jsAlVolver
import com.readriffle.app.platform.jsAtrasActivo

/**
 * El gesto o botón de atrás del navegador (y la tecla Esc en ordenador) hace lo mismo que el
 * botón atrás de Android. Varios sitios pueden pedirlo a la vez (navegación, hojas, diálogos):
 * responde el último registrado que esté activo.
 */
private class BackEntry(var enabled: Boolean, var onBack: () -> Unit)

private val entries = mutableListOf<BackEntry>()
private var bound = false

internal fun refreshBackState() = jsAtrasActivo(entries.any { it.enabled })

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    val entry = remember { BackEntry(enabled) { current.value() } }
    entry.enabled = enabled
    DisposableEffect(Unit) {
        entries.add(entry)
        if (!bound) {
            bound = true
            jsAlVolver { entries.lastOrNull { it.enabled }?.onBack?.invoke() }
        }
        onDispose { entries.remove(entry); refreshBackState() }
    }
    DisposableEffect(enabled) {
        refreshBackState()
        onDispose { }
    }
}
