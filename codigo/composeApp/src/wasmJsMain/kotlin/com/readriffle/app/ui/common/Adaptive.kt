package com.readriffle.app.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * true en pantallas anchas (ordenador, tablet en horizontal): la app cambia a su diseño de
 * escritorio (barra lateral, rejillas más anchas, fichas a dos columnas, atajos de teclado).
 */
val LocalWide = staticCompositionLocalOf { false }

/** Ancho a partir del cual se usa el diseño de ordenador. */
val WIDE_BREAKPOINT = 900.dp

/** En ordenador, centra el contenido con un ancho máximo cómodo de leer. */
fun Modifier.desktopCentered(max: Dp = 880.dp): Modifier = composed {
    if (LocalWide.current) this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max) else this
}

/** Mano al pasar el ratón por encima (lo esperable en algo que se puede pulsar en una web). */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/**
 * Al pasar el ratón por encima, el elemento se eleva un poco (escala y sombra), como las tarjetas
 * de las webs modernas. En móvil no hace nada.
 */
fun Modifier.hoverLift(shape: Shape, lift: Float = 1.045f, elevation: Dp = 18.dp): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) lift else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "hover")
    val shadow by animateDpAsState(if (hovered) elevation else 0.dp, label = "hover-sombra")
    this.hoverable(interaction).handCursor()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (shadow > 0.dp) Modifier.shadow(shadow, shape) else Modifier)
}
