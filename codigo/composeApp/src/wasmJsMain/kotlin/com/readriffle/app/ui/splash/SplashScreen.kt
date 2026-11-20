package com.readriffle.app.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 1300L
private const val WORDMARK = "Book Room"

/** Fuente fija de marca para el logotipo — independiente de la tipografía elegida en Ajustes. */
private val brandFontFamily = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

/**
 * Pantalla de carga: el logotipo "Book Room" (con la B y la R en el color de acento, mismo
 * lenguaje visual que la letra de fijación óptica en lectura) y el crédito "By GarzaStudios"
 * debajo. Se muestra un tiempo fijo y pasa sola a la Biblioteca.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        onFinished()
    }

    val accentColor = MaterialTheme.colorScheme.primary
    val wordmark = remember(accentColor) {
        buildAnnotatedString {
            WORDMARK.forEach { c ->
                if (c == 'B' || c == 'R') {
                    withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) { append(c) }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(c) }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF09362F), Color(0xFF09362F)))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(174.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = wordmark,
                fontFamily = brandFontFamily,
                fontSize = 40.sp,
                letterSpacing = 0.2.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "By GarzaStudios",
                fontFamily = brandFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
