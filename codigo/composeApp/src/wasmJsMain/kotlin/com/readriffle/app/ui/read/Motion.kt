package com.readriffle.app.ui.read

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Entrada escalonada: el elemento aparece subiendo desde [distance] con un fundido y un ligero
 * cambio de escala, [index] * [stepMs] después de entrar en pantalla. Si [seen] ya contiene
 * [key] (por ejemplo, al volver a hacer scroll hasta él) aparece directamente, sin repetir.
 */
fun Modifier.appearIn(
    index: Int,
    key: Any? = null,
    seen: MutableSet<Any>? = null,
    distance: Dp = 26.dp,
    stepMs: Int = 45,
    maxDelayMs: Int = 420
): Modifier = composed {
    val already = key != null && seen?.contains(key) == true
    val progress = remember { Animatable(if (already) 1f else 0f) }
    val dist = with(LocalDensity.current) { distance.toPx() }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay((index * stepMs).coerceAtMost(maxDelayMs).toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
            if (key != null) seen?.add(key)
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * dist
        val s = 0.94f + 0.06f * p
        scaleX = s; scaleY = s
    }
}

/** Número que cuenta desde 0 hasta [target] al aparecer (y que se anima si cambia). */
@Composable
fun animatedNumber(target: Float, durationMs: Int = 900, delayMs: Int = 0): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        if (delayMs > 0 && anim.value == 0f) delay(delayMs.toLong())
        anim.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return anim.value
}

/**
 * Confeti: una ráfaga de papelitos de colores que salen hacia arriba desde el centro superior y
 * caen girando. Se lanza una vez cada vez que cambia [trigger].
 */
@Composable
fun ConfettiBurst(colors: List<Color>, modifier: Modifier = Modifier, trigger: Any = Unit, count: Int = 46) {
    val progress = remember(trigger) { Animatable(0f) }
    val pieces = remember(trigger) {
        val r = Random(trigger.hashCode())
        List(count) {
            val angle = ((-90 + r.nextDouble(-70.0, 70.0)) * kotlin.math.PI / 180.0)
            Piece(
                vx = (cos(angle) * r.nextDouble(260.0, 620.0)).toFloat(),
                vy = (sin(angle) * r.nextDouble(520.0, 980.0)).toFloat(),
                spin = r.nextDouble(-720.0, 720.0).toFloat(),
                color = colors[r.nextInt(colors.size)],
                w = r.nextDouble(7.0, 13.0).toFloat(),
                h = r.nextDouble(4.0, 8.0).toFloat(),
                delay = r.nextDouble(0.0, 0.12).toFloat()
            )
        }
    }
    LaunchedEffect(trigger) { launch { progress.animateTo(1f, tween(1900, easing = LinearEasing)) } }
    val density = LocalDensity.current.density
    Canvas(modifier) {
        val t0 = progress.value
        if (t0 <= 0f || t0 >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.35f)
        pieces.forEach { p ->
            val t = ((t0 - p.delay) / (1f - p.delay)).coerceIn(0f, 1f) * 1.9f
            if (t <= 0f) return@forEach
            val g = 1500f
            val x = origin.x + p.vx * t * density / 2.2f
            val y = origin.y + (p.vy * t + 0.5f * g * t * t) * density / 2.2f
            val alpha = (1f - (t0 - 0.65f).coerceAtLeast(0f) / 0.35f).coerceIn(0f, 1f)
            val rot = p.spin * t
            drawContext.canvas.save()
            drawContext.canvas.translate(x, y)
            drawContext.canvas.rotate(rot)
            drawRect(p.color.copy(alpha = alpha), topLeft = Offset(-p.w * density / 2f, -p.h * density / 2f),
                size = androidx.compose.ui.geometry.Size(p.w * density, p.h * density))
            drawContext.canvas.restore()
        }
    }
}

private class Piece(val vx: Float, val vy: Float, val spin: Float, val color: Color, val w: Float, val h: Float, val delay: Float)
