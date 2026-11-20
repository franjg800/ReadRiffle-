package com.readriffle.app.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.readriffle.app.R
import com.readriffle.app.viewmodel.GeneratedImagePreview
import com.readriffle.app.platform.File

/**
 * Diálogo de "generando imagen…", mientras se espera la respuesta de Gemini -- pedido explícito
 * del usuario: "tiene que salir un cuadro cargando". No se puede descartar tocando fuera (la
 * generación sigue en marcha en segundo plano de todas formas, pero no tiene sentido dejar
 * cerrar esto a mitad, ya que no hay nada que cancelar).
 */
@Composable
fun ImageGenerationLoadingDialog() {
    com.readriffle.app.ui.common.BookRoomDialog(
        title = stringResource(R.string.image_generating_title),
        onDismiss = {}, dismissText = null
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.image_generating_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Diálogo con el resultado: la imagen ya generada (y ya escrita en disco, ver
 * [com.readriffle.app.viewmodel.ReadingViewModel.generateImage]) y la descripción usada, con
 * "Guardar en Notas" o "Descartar" -- pedido explícito del usuario: "después tiene que guardarse
 * esa imagen en otra sección como la sección de notas... que se guarde la imagen y la
 * descripción... o el prompt que se ha usado".
 */
@Composable
fun GeneratedImageResultDialog(
    preview: GeneratedImagePreview,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    com.readriffle.app.ui.common.BookRoomDialog(
        title = stringResource(R.string.image_result_title),
        onDismiss = onDiscard,
        confirmText = stringResource(R.string.image_result_save), onConfirm = onSave,
        dismissText = stringResource(R.string.image_result_discard)
    ) {
        AsyncImage(
            model = File(preview.imagePath),
            contentDescription = preview.prompt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
        )
        Text(
            text = "“${preview.prompt}”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Diálogo de error simple, con el mensaje ya pensado para mostrarse tal cual (ver [com.readriffle.app.data.ai.GeminiImageClient]). */
@Composable
fun ImageGenerationErrorDialog(message: String, onDismiss: () -> Unit) {
    com.readriffle.app.ui.common.BookRoomDialog(
        title = stringResource(R.string.image_error_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.image_error_dismiss), onConfirm = onDismiss, dismissText = null
    ) { com.readriffle.app.ui.common.BookRoomDialogText(message) }
}
