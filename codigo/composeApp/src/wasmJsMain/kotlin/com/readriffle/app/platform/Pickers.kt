package com.readriffle.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

/**
 * Selector de archivos del sistema (equivalente a rememberLauncherForActivityResult de Android).
 * Devuelve la función que lo abre; [onPicked] recibe los archivos ya leídos (vacío si se cancela).
 */
@Composable
fun rememberFilePicker(accept: String, multiple: Boolean = false, onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val cb = rememberUpdatedState(onPicked)
    return remember(accept, multiple) {
        { scope.launch { val files = pickFiles(accept, multiple); if (files.isNotEmpty()) cb.value(files) } }
    }
}
