package com.readriffle.app.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp

/**
 * Forma "squircle" para los botones de icono de toda la app: un cuadrado con las esquinas muy
 * redondeadas -- ni un círculo (como eran todos los botones hasta ahora) ni un cuadrado de
 * esquina recta. Pedido explícito del usuario: "ahora los botones son círculos pues quiero que
 * sean como cuadrados pero con las puntas súper redondeadas... que no sea un cuadrado cuadrado
 * sino redondeado, eso para todos los botones de la app".
 *
 * El radio es proporcional al tamaño del botón (en vez de un valor fijo en dp) para que el mismo
 * aspecto funcione igual de bien en un botón pequeño (36dp, el selector de modo) que en uno
 * grande (76dp, el play del reproductor de audio).
 */
fun squircleShape(size: Dp): RoundedCornerShape = RoundedCornerShape(size * 0.32f)
