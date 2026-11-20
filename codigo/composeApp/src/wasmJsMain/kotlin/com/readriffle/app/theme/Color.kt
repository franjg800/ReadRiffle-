package com.readriffle.app.theme

import androidx.compose.ui.graphics.Color

// Tema oscuro — negro real (con un mínimo de matiz para no ser negro absoluto y evitar
// el "smearing" de las pantallas OLED), acento por defecto en azul claro.
val DarkBackground = Color(0xFF0A0A0C)
val DarkSurface = Color(0xFF161618)
val DarkOnBackground = Color(0xFFEDEEF0)
val DarkOnBackgroundMuted = Color(0xFF9AA0AC)

// Negro puro para pantallas AMOLED (opción en Ajustes): fondo #000000 real; las superficies
// (tarjetas, dock, botones) apenas se despegan del fondo para seguir distinguiéndose.
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0C0C0E)

// Tema claro — cálido, sin blanco puro.
val LightBackground = Color(0xFFF7F6F3)
val LightSurface = Color(0xFFEFEDE8)
val LightOnBackground = Color(0xFF1B1E27)
val LightOnBackgroundMuted = Color(0xFF767A85)

// Oscuro "profundo" -- mismo fondo casi negro, pero letras más atenuadas (gris, no casi blanco)
// para reducir el brillo/contraste en lectura nocturna. Pedido explícito: "un oscuro con las
// letras más oscuras... buscando que la gente pueda leer de noche de manera cómoda".
val DarkOnBackgroundDim = Color(0xFFAEB2BC)
val DarkOnBackgroundMutedDim = Color(0xFF7C818C)

// Claro "cálido" -- variante tipo papel/sepia, para hacer simétrico el par claro/oscuro (dos y
// dos): fondo y superficie más cremosos, texto marrón oscuro en vez del gris frío del claro
// estándar.
val LightWarmBackground = Color(0xFFF2E8D5)
val LightWarmSurface = Color(0xFFEADFC7)
val LightWarmOnBackground = Color(0xFF3A2F22)
val LightWarmOnBackgroundMuted = Color(0xFF8A7A63)

/**
 * Paleta de acentos para la palabra destacada (ver Ajustes): 9 opciones. Cada una trae un tono
 * para el tema oscuro y otro, algo más oscurecido, para mantener contraste sobre el fondo claro.
 *
 * Por pedido explícito del usuario se añadieron cinco colores NEÓN de contraste fuerte (rosa,
 * cian, lima, naranja y verde) y se eliminaron cinco de los anteriores (coral, verde salvia,
 * lila, azul intenso y verde intenso) para no tener demasiados. Se conservan ámbar y azul cielo
 * (los acentos por defecto de claro y oscuro), más rojo y morado intensos. Si un usuario tenía
 * guardado uno de los eliminados, vuelve al ámbar (ver [fromKey]).
 */
enum class AccentColorOption(val label: String, val darkColor: Color, val lightColor: Color) {
    AMBER("Ámbar", Color(0xFFE8A33D), Color(0xFFC98A2E)),
    SKY("Azul cielo", Color(0xFF7EB6D9), Color(0xFF3E7FA8)),
    RED("Rojo", Color(0xFFFF5252), Color(0xFFD32F2F)),
    PURPLE("Morado intenso", Color(0xFFB36BFF), Color(0xFF7B1FA2)),
    NEON_PINK("Rosa neón", Color(0xFFFF2DAA), Color(0xFFE0007A)),
    NEON_CYAN("Cian neón", Color(0xFF00F0FF), Color(0xFF009FB3)),
    NEON_LIME("Lima neón", Color(0xFFC6FF00), Color(0xFF6BBF00)),
    NEON_ORANGE("Naranja neón", Color(0xFFFF6A00), Color(0xFFE85A00)),
    NEON_GREEN("Verde neón", Color(0xFF39FF14), Color(0xFF00B312));

    companion object {
        fun fromKey(key: String): AccentColorOption = entries.find { it.name == key } ?: AMBER
    }
}
