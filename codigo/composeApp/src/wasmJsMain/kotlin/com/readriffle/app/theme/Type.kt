package com.readriffle.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.readriffle.app.R

/** Las 6 tipografías del selector — variedad deliberada, cada una con su regular y su negrita. */
enum class ReaderFontOption(val label: String, val fontFamily: FontFamily) {
    LITERATA(
        "Literata",
        FontFamily(Font(R.font.literata_regular, FontWeight.Normal), Font(R.font.literata_bold, FontWeight.Bold))
    ),
    SOURCE_SERIF(
        "Source Serif 4",
        FontFamily(Font(R.font.source_serif_regular, FontWeight.Normal), Font(R.font.source_serif_bold, FontWeight.Bold))
    ),
    INTER(
        "Inter",
        FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_bold, FontWeight.Bold))
    ),
    SPACE_GROTESK(
        "Space Grotesk",
        FontFamily(Font(R.font.space_grotesk_regular, FontWeight.Normal), Font(R.font.space_grotesk_bold, FontWeight.Bold))
    ),
    ATKINSON(
        "Atkinson Hyperlegible",
        FontFamily(Font(R.font.atkinson_regular, FontWeight.Normal), Font(R.font.atkinson_bold, FontWeight.Bold))
    ),
    PLEX_MONO(
        "IBM Plex Mono",
        FontFamily(Font(R.font.plex_mono_regular, FontWeight.Normal), Font(R.font.plex_mono_bold, FontWeight.Bold))
    );

    companion object {
        fun fromKey(key: String): ReaderFontOption = entries.find { it.name == key } ?: LITERATA
    }
}

/**
 * Tipografía exclusiva para los títulos grandes de cada pantalla (Biblioteca, Notas, Convertir,
 * Ajustes) -- pedido explícito del usuario: "salta de las tipografías que tenemos ya
 * establecidas", así que es una séptima familia (Fraunces, serif de exhibición) que nunca
 * aparece en el selector de 6 fuentes de lectura ni se usa para nada más que estos títulos.
 */
val TitleDisplayFontFamily: FontFamily by lazy { FontFamily(
    Font(R.font.title_display_semibold, FontWeight.SemiBold),
    Font(R.font.title_display_bold, FontWeight.Bold)
) }

/** Construye la escala tipográfica de Material 3 aplicando [fontFamily] a todos los estilos. */
fun rsvpTypography(fontFamily: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily)
    )
}
