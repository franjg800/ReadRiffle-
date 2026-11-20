package com.readriffle.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.readriffle.app.R

/**
 * Se muestra UNA SOLA VEZ al entrar en la app, cada vez que se instala el APK de nuevo --
 * pedido explícito del usuario: "cuando entre en la app me pregunto cuál es mi API key y yo la
 * pego... solo una vez cada vez que instale de nuevo el APK". Como el ajuste que recuerda si ya
 * se mostró vive en DataStore (que se borra al desinstalar), no hace falta ninguna lógica extra
 * para que vuelva a preguntar en una instalación nueva: simplemente deja de existir ese ajuste.
 *
 * Pegar la key aquí es opcional ("Más tarde" la deja vacía, y se puede añadir después desde
 * Ajustes) -- en cualquier caso, tras esta pantalla no se vuelve a preguntar en esta instalación.
 */
@Composable
fun GeminiApiKeyPromptDialog(onSave: (String) -> Unit, onSkip: () -> Unit) {
    var key by remember { mutableStateOf("") }

    BookRoomDialog(
        title = stringResource(R.string.gemini_key_prompt_title),
        onDismiss = onSkip,
        confirmText = stringResource(R.string.gemini_key_prompt_save),
        onConfirm = { onSave(key) },
        dismissText = stringResource(R.string.gemini_key_prompt_skip)
    ) {
        BookRoomDialogText(stringResource(R.string.gemini_key_prompt_body))
        BookRoomTextField(
            value = key, onValueChange = { key = it },
            placeholder = stringResource(R.string.gemini_key_prompt_placeholder),
            visualTransformation = PasswordVisualTransformation()
        )
    }
}
