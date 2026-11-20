package com.readriffle.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.readriffle.app.R
import com.readriffle.app.data.dictionary.DictSection
import com.readriffle.app.data.dictionary.DictionaryClient
import com.readriffle.app.data.dictionary.DictionaryResult

/**
 * Ventanita flotante con la definición de una palabra: no saca al usuario de la lectura ni abre
 * ningún navegador. Se cierra tocando fuera o con "Cerrar".
 */
@Composable
fun DictionaryDialog(word: String, onDismiss: () -> Unit) {
    var result by remember(word) { mutableStateOf<DictionaryResult?>(null) }
    LaunchedEffect(word) { result = DictionaryClient.lookup(word) }

    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    val shown = (result as? DictionaryResult.Found)?.word ?: word.trim().trim { !it.isLetterOrDigit() }.lowercase()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = maxHeight)
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(R.drawable.ic_dictionary), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(shown, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        (result as? DictionaryResult.Found)?.let {
                            Text(it.source, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Box(Modifier.padding(top = 14.dp).weight(1f, fill = false)) {
                    when (val r = result) {
                        null -> Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                        }
                        DictionaryResult.NotFound -> Text(
                            stringResource(R.string.dictionary_not_found, shown),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp
                        )
                        DictionaryResult.Offline -> Text(
                            stringResource(R.string.dictionary_offline),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp
                        )
                        is DictionaryResult.Found -> Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) { r.sections.forEach { SectionBlock(it) } }
                    }
                }
                Text(
                    stringResource(R.string.dictionary_close),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 14.dp)
                        .clickable(onClick = onDismiss).padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionBlock(section: DictSection) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            section.heading, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.primary
        )
        if (section.intro.isNotBlank()) {
            Text(section.intro, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        section.definitions.forEach { d ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    d.number?.toString() ?: "•", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(width = 18.dp, height = 20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (d.tag.isNotBlank()) {
                        Text(d.tag, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(d.text, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface)
                    d.notes.forEach {
                        Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
