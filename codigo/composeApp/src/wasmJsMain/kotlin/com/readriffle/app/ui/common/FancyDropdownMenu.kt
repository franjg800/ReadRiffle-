package com.readriffle.app.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.readriffle.app.R

/**
 * Menú desplegable "con gracia" reutilizable -- pedido explícito del usuario para los tres
 * menús nuevos (tipografía, zoom y tema): "que se despliega un menú con un poquito de gracia,
 * con unos efectos, un menú que sea bonito, no el típico desplegable tonto". En vez de un
 * [androidx.compose.material3.DropdownMenu] plano, es un [Popup] propio con una tarjeta que
 * aparece con una animación de escala con un pelín de rebote (spring) + fundido, creciendo
 * desde arriba (mismo origen que un desplegable normal, pero con más carácter).
 */
@Composable
fun FancyDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!expanded) return

    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        val visibleState = remember { MutableTransitionState(false) }
        visibleState.targetState = true

        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(140)) + scaleIn(
                initialScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 380f)
            ),
            exit = fadeOut(tween(110)) + scaleOut(
                targetScale = 0.9f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = tween(110)
            )
        ) {
            Column(
                modifier = modifier
                    .widthIn(min = 200.dp)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Panel del botón de "más opciones" (los tres puntitos): a diferencia de [FancyDropdownMenu]
 * (que crece desde arriba-centro, pensado para colgar de un botón centrado), este cae desde la
 * esquina superior derecha pegado al borde derecho del teléfono -- pedido explícito del usuario:
 * "se desplieguen hacia abajo paralelo al borde derecho del teléfono... con algún efecto chulo
 * de despliegue hacia abajo". El contenido son los propios botones que antes vivían sueltos por
 * la pantalla, apilados en vertical del mismo tamaño y estilo que tenían.
 */
@Composable
fun OverflowDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!expanded) return

    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(density) { IntOffset(0, 60.dp.roundToPx()) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        val visibleState = remember { MutableTransitionState(false) }
        visibleState.targetState = true

        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(160)) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 340f)
            ) + scaleIn(
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 340f)
            ),
            exit = fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(140))
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

/** Una fila seleccionable dentro de un [FancyDropdownMenu], con marca de verificación si está activa. */
@Composable
fun FancyMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (labelContent != null) {
            Column(modifier = Modifier.weight(1f)) { labelContent() }
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        if (selected) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(1.dp)
                )
            }
        }
    }
}
