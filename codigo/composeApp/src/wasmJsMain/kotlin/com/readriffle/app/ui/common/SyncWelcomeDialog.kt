package com.readriffle.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.readriffle.app.R
import com.readriffle.app.ui.read.appearIn

/**
 * Aviso de la primera visita: explica la sincronización con Google Drive y pregunta si se quiere.
 * Es una tarjeta centrada (no ocupa la pantalla). [error] aparece si no se pudo conectar.
 */
@Composable
fun SyncWelcomeDialog(error: String?, onAccept: () -> Unit, onDecline: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val pulse = rememberInfiniteTransition(label = "pulso")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "aro")
    Dialog(onDismissRequest = onDecline, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 430.dp).fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(96.dp).appearIn(0), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(96.dp).graphicsLayer { scaleX = 0.6f + ring * 0.5f; scaleY = 0.6f + ring * 0.5f; alpha = 1f - ring }
                        .clip(CircleShape).background(primary.copy(alpha = 0.35f))
                )
                Box(
                    Modifier.size(68.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.85f), primary))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_cloud_sync), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                }
            }
            Text(
                "¿Sincronizar tu biblioteca?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.appearIn(1)
            )
            Text(
                "Conecta tu cuenta de Google y tus libros, por dónde vas, subrayados, notas y leídos se guardarán en tu Drive " +
                    "(carpeta «ReadRiffle»). Los tendrás iguales en el móvil, la tablet, el ordenador y la app Android.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.appearIn(2)
            )
            Text(
                "Solo hay que hacerlo una vez: esta web lo recordará.",
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                color = primary, modifier = Modifier.appearIn(3)
            )
            if (error != null) {
                Text(
                    error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f)).padding(12.dp)
                )
            }
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).appearIn(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.88f), primary)))
                        .clickable(onClick = onAccept),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sí, sincronizar con Google Drive", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50)).clickable(onClick = onDecline),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Ahora no", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Puedes cambiarlo cuando quieras en Ajustes › Sincronización.",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}
