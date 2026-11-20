package com.readriffle.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.readriffle.app.R
import com.readriffle.app.platform.File

/** Una imagen del libro dentro de una página; al tocarla se abre a pantalla completa con zoom. */
@Composable
fun PageImageBlock(path: String, modifier: Modifier = Modifier, onZoom: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onZoom),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(File(path)).size(Size(1600, 1600)).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_zoom), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
    }
}

/** Visor a pantalla completa: pellizco para zoom, arrastre y doble toque. */
@Composable
fun ImageZoomDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
            ZoomableImage(path = path, modifier = Modifier.fillMaxSize().statusBarsPadding().padding(8.dp))
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(44.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_close), null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
