package com.readriffle.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** Modo de tema elegido en Ajustes: seguir el sistema, o forzar uno de los dos. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromKey(key: String): ThemeMode = entries.find { it.name == key } ?: SYSTEM
    }
}

/**
 * Variante de contraste dentro de cada tema -- pedido explícito del usuario: además del claro y
 * el oscuro "estándar" que ya había, un segundo claro (más cálido, tipo papel) y un segundo
 * oscuro (letras más atenuadas, para leer de noche sin tanto brillo), para que queden "dos y
 * dos", simétrico. Se elige junto al tema en el menú del botón de alternar tema (ver
 * [com.readriffle.app.ui.common.FancyDropdownMenu]), no en el segmented control de Ajustes, que
 * se deja intacto (Sistema/Claro/Oscuro).
 */
enum class ThemeVariant(val label: String) {
    STANDARD("Estándar"),
    ALT("Alternativo");

    companion object {
        fun fromKey(key: String): ThemeVariant = entries.find { it.name == key } ?: STANDARD
    }
}

/**
 * Fondo del modo RSVP, elegible en una nueva sección de Ajustes:
 * - [FLOWING]: el texto del capítulo fluyendo muy tenue de fondo (el de siempre, sin tocar).
 * - [BLACK_LINES]: pantalla negra pura con dos líneas horizontales marcando por encima y por
 *   debajo dónde cae la palabra -- pedido explícito del usuario.
 * - [WHITE_SCREEN]: pantalla blanca pura, mismo esquema que [BLACK_LINES] pero invertido --
 *   sustituye a la opción "Pulso" original, que el usuario pidió quitar ("el fondo de pulso
 *   quítalo... a mí se me ocurre que podría ser una pantalla blanca y ya está").
 */
enum class RsvpBackgroundMode(val label: String) {
    FLOWING("Texto fluido"),
    BLACK_LINES("Pantalla negra"),
    WHITE_SCREEN("Pantalla blanca");

    companion object {
        fun fromKey(key: String): RsvpBackgroundMode = entries.find { it.name == key } ?: FLOWING
    }
}

/**
 * El acento se recibe por separado para cada tema ([lightAccent] / [darkAccent]) para que cada
 * uno pueda tener su propio color por defecto (ámbar en claro, azul claro en oscuro) sin que
 * elegir uno afecte al otro. [themeVariant] añade una segunda variante de contraste dentro de
 * cada tema (ver [ThemeVariant]).
 */
@Composable
fun RsvpReaderTheme(
    themeMode: ThemeMode,
    themeVariant: ThemeVariant = ThemeVariant.STANDARD,
    amoledBlack: Boolean = false,
    lightAccent: AccentColorOption,
    darkAccent: AccentColorOption,
    fontFamily: FontFamily,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val isAlt = themeVariant == ThemeVariant.ALT

    val accentColor = if (useDarkTheme) darkAccent.darkColor else lightAccent.lightColor

    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = accentColor,
            secondary = accentColor,
            tertiary = accentColor,
            background = if (amoledBlack) AmoledBackground else DarkBackground,
            surface = if (amoledBlack) AmoledSurface else DarkSurface,
            onBackground = if (isAlt) DarkOnBackgroundDim else DarkOnBackground,
            onSurface = if (isAlt) DarkOnBackgroundDim else DarkOnBackground,
            surfaceVariant = if (amoledBlack) AmoledSurface else DarkSurface,
            onSurfaceVariant = if (isAlt) DarkOnBackgroundMutedDim else DarkOnBackgroundMuted,
            onPrimary = if (amoledBlack) AmoledBackground else DarkBackground
        )
    } else if (isAlt) {
        lightColorScheme(
            primary = accentColor,
            secondary = accentColor,
            tertiary = accentColor,
            background = LightWarmBackground,
            surface = LightWarmSurface,
            onBackground = LightWarmOnBackground,
            onSurface = LightWarmOnBackground,
            surfaceVariant = LightWarmSurface,
            onSurfaceVariant = LightWarmOnBackgroundMuted,
            onPrimary = LightWarmBackground
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            secondary = accentColor,
            tertiary = accentColor,
            background = LightBackground,
            surface = LightSurface,
            onBackground = LightOnBackground,
            onSurface = LightOnBackground,
            surfaceVariant = LightSurface,
            onSurfaceVariant = LightOnBackgroundMuted,
            onPrimary = LightBackground
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = rsvpTypography(fontFamily),
        content = content
    )
}
