package com.readriffle.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R

/**
 * Redactor de notas, en una hoja inferior: se usa tanto para una nota SUELTA (ligada solo a la
 * posición actual de lectura -- capítulo/página/fecha, sin citar texto) como para comentar un
 * subrayado concreto (entonces se muestra el texto citado arriba, a modo de referencia). Es el
 * único punto de entrada para crear/editar una nota, disparado desde el botón "Nota" del menú de
 * pausa/controles en RSVP y en modo normal.
 *
 * Rediseñado (el usuario lo describió como "súper cutre... muy básico, muy Android"): el
 * `OutlinedTextField` de Material por defecto -- con su borde grueso y esquinas genéricas --
 * sustituido por un campo de texto propio (`BasicTextField` sobre una superficie tenue, sin
 * borde), mismo lenguaje visual que el resto de la app (etiqueta en versalitas con punto de
 * acento, igual que en Ajustes/Notas; tarjeta redondeada sin chrome de Material). La tipografía
 * del cuadro ya heredaba la fuente elegida en Ajustes (todo `MaterialTheme.typography` la usa,
 * ver `theme/Type.kt`); aquí además se fija explícitamente para que no quede ninguna duda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteComposerSheet(
    quotedText: String?,
    initialText: String = "",
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = stringResource(
                        if (quotedText != null) R.string.note_composer_title_highlight else R.string.note_composer_title_standalone
                    ).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (quotedText != null) {
                Text(
                    text = "“$quotedText”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                        .padding(14.dp)
                )
            }

            NoteTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.note_composer_cancel), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }
                Box(
                    modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onSave(text) },
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.note_composer_save), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary) }
            }
        }
    }
}

/**
 * Campo de texto propio para redactar la nota: superficie tenue redondeada, sin borde ni
 * "chrome" de Material por defecto -- mismo lenguaje visual que las tarjetas del resto de la
 * app -- con el cursor y el placeholder en los colores del tema activo.
 */
@Composable
private fun NoteTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f))
            .padding(16.dp)
            .defaultMinSize(minHeight = 120.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.note_composer_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
