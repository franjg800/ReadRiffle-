package com.readriffle.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.readriffle.app.theme.TitleDisplayFontFamily

/**
 * Ventana de diálogo con el estilo de Book Room: tarjeta muy redondeada, título en la tipografía
 * de la app y botones en píldora. Sustituye a los `AlertDialog` básicos de Material.
 *
 * @param confirmText texto del botón principal; null = solo hay botón de cerrar ([dismissText]).
 * @param destructive pinta el botón principal en el color de error (borrar…).
 */
@Composable
fun BookRoomDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    dismissText: String? = "Cancelar",
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 18.dp)
        ) {
            Text(
                title,
                fontFamily = TitleDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { content() }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dismissText != null) {
                    Text(
                        dismissText,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    )
                }
                if (confirmText != null) {
                    val bg = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val fg = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                    Box(
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50))
                            .background(if (confirmEnabled) bg else bg.copy(alpha = 0.35f))
                            .clickable(enabled = confirmEnabled, onClick = onConfirm)
                            .padding(horizontal = 22.dp, vertical = 11.dp)
                    ) { Text(confirmText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg) }
                }
            }
        }
    }
}

/** Texto corriente dentro de un [BookRoomDialog]. */
@Composable
fun BookRoomDialogText(text: String) {
    Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Campo de texto de Book Room: etiqueta pequeña encima y caja redondeada sin borde grueso; al
 * enfocarlo, un filo fino del color de acento. Sustituye a `OutlinedTextField`.
 */
@Composable
fun BookRoomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
    onAction: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    fieldColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.background
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    Column(modifier) {
        if (label != null) {
            Text(
                label.uppercase(),
                fontSize = 11.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(fieldColor)
                .border(1.5.dp, if (focused) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                BasicTextField(
                    value = value, onValueChange = onValueChange,
                    singleLine = singleLine, maxLines = maxLines,
                    textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = KeyboardActions(onDone = { onAction?.invoke() }, onSearch = { onAction?.invoke() }, onGo = { onAction?.invoke() }),
                    visualTransformation = visualTransformation,
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            trailing?.invoke()
        }
    }
}
