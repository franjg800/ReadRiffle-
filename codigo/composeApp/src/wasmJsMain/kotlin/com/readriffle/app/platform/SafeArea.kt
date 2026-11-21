package com.readriffle.app.platform

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Equivalente web de `navigationBarsPadding()`: deja libre abajo el alto de la barra de inicio del
 * iPhone (safe-area-inset-bottom). La app llega hasta el borde de la pantalla y solo el dock y los
 * controles se apartan de esa barra. Se recalcula al girar la pantalla.
 */
fun Modifier.navigationBarsPadding(): Modifier = composed {
    val size = LocalWindowInfo.current.containerSize
    val bottom = remember(size) { runCatching { jsSafeBottom() }.getOrDefault(0.0) }
    this.padding(bottom = bottom.dp)
}
