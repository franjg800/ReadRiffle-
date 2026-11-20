package com.readriffle.app.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R
import com.readriffle.app.data.model.BookMeta
import com.readriffle.app.ui.library.BookCoverImage

/**
 * Capa que simula un e-reader apagado: fondo casi negro, la portada del libro grande y apagada,
 * barras del sistema ocultas y brillo muy bajo. Se quita tocando o deslizando hacia arriba; con
 * [requireAuth] antes pide huella, cara o el PIN/patrón del móvil.
 */
@Composable
fun CoverLockOverlay(book: BookMeta, requireAuth: Boolean, @Suppress("UNUSED_PARAMETER") pin: Boolean, onUnlock: () -> Unit) {
    val lockPrefs = remember { com.readriffle.app.platform.SimplePrefs("cover_lock") }
    var askPin by remember { mutableStateOf(false) }

    BackHandler { /* Bloqueado: el botón atrás no hace nada. */ }

    // En la web no se puede pedir la huella ni la cara del móvil: con [requireAuth] se pide el PIN
    // propio de Book Room (Ajustes › Bloqueo). Sin PIN guardado, basta un toque.
    val requestUnlock: () -> Unit = {
        val saved = lockPrefs.getString("pin", "").orEmpty()
        if (requireAuth && saved.isNotEmpty()) askPin = true else onUnlock()
    }

    if (askPin) {
        var typed by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        val check = {
            if (typed == lockPrefs.getString("pin", "")) { askPin = false; onUnlock() } else { wrong = true; typed = "" }
        }
        com.readriffle.app.ui.common.BookRoomDialog(
            title = book.title,
            onDismiss = { askPin = false },
            confirmText = "Desbloquear",
            onConfirm = check,
            confirmEnabled = typed.length >= 4
        ) {
            com.readriffle.app.ui.common.BookRoomDialogText(if (wrong) "PIN incorrecto. Vuelve a intentarlo." else "Escribe tu PIN para seguir leyendo.")
            com.readriffle.app.ui.common.BookRoomTextField(
                value = typed,
                onValueChange = { v -> typed = v.filter { it.isDigit() }.take(8); wrong = false },
                placeholder = "PIN",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                onAction = { if (typed.length >= 4) check() }
            )
        }
    }

    // La portada SIEMPRE se ve en vertical, también con el móvil en horizontal: allí se coloca a la
    // izquierda ocupando todo el alto y el título va a su lado, para aprovechar la pantalla.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050506))
            .pointerInput(Unit) { detectVerticalDragGestures { _, drag -> if (drag < -24f) requestUnlock() } }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = requestUnlock)
    ) {
        val landscape = maxWidth > maxHeight
        val availableHeight = maxHeight
        val availableWidth = maxWidth
        @androidx.compose.runtime.Composable
        fun Cover(modifier: Modifier) {
            Box(modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))) {
                BookCoverImage(book, Modifier.fillMaxSize().alpha(0.9f))
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
            }
        }
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                Cover(Modifier.fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        book.title, color = Color.White.copy(alpha = 0.9f),
                        fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold,
                        maxLines = 5, overflow = TextOverflow.Ellipsis
                    )
                    Text(book.author, color = Color.White.copy(alpha = 0.65f), fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 28.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Cover(Modifier.width(minOf(availableWidth - 44.dp, availableHeight * 0.66f * 2f / 3f)))
                Spacer(Modifier.height(22.dp))
                Text(
                    book.title, color = Color.White.copy(alpha = 0.92f),
                    fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                Text(
                    book.author, color = Color.White.copy(alpha = 0.65f),
                    fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
