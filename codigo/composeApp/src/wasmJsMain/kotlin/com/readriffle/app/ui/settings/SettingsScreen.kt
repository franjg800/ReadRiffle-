package com.readriffle.app.ui.settings

import com.readriffle.app.ui.common.desktopCentered
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.readriffle.app.R
import com.readriffle.app.platform.SimplePrefs
import com.readriffle.app.ui.common.BookRoomTextField
import com.readriffle.app.theme.AccentColorOption
import com.readriffle.app.theme.ReaderFontOption
import com.readriffle.app.theme.RsvpBackgroundMode
import com.readriffle.app.theme.ThemeMode
import com.readriffle.app.ui.common.DockDestination
import com.readriffle.app.ui.common.MainDock
import com.readriffle.app.ui.common.ScreenTitle
import com.readriffle.app.viewmodel.SettingsViewModel
import com.readriffle.app.viewmodel.SyncViewModel
import com.readriffle.app.platform.SimpleDateFormat
import com.readriffle.app.platform.Date

private enum class SettingsPage { APPEARANCE, RSVP, PACING, NARRATION, AI, SYNC, LOCK }

/**
 * Ajustes como un "menú principal" de categorías: cada una es una fila con icono, título y un
 * resumen de lo que hay elegido ahora; al tocarla se abre su propia página con solo esos ajustes.
 * Sustituye al acordeón anterior (todo en una misma pantalla larga, plegado por secciones), que
 * al usuario no le gustaba como estaba organizado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    syncViewModel: SyncViewModel,
    onOpenLibrary: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenStore: () -> Unit
) {
    val prefs by viewModel.uiState.collectAsState()
    val syncState by syncViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    val useDarkSwatches = when (ThemeMode.fromKey(prefs.themeMode)) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).desktopCentered(820.dp)) {
            val current = page
            if (current == null) {
                SettingsHub(
                    prefs = prefs,
                    syncEmail = syncState.accountEmail,
                    onOpen = { page = it }
                )
                MainDock(
                    current = DockDestination.SETTINGS,
                    onLibrary = onOpenLibrary,
                    onNotes = onOpenNotes,
                    onStore = onOpenStore,
                    onSettings = {},
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    SettingsPageHeader(
                        title = stringResource(pageTitleRes(current)),
                        onBack = { page = null }
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            when (current) {
                                SettingsPage.APPEARANCE -> AppearancePage(prefs, viewModel, useDarkSwatches)
                                SettingsPage.RSVP -> RsvpPage(prefs, viewModel)
                                SettingsPage.PACING -> PacingPage(prefs, viewModel)
                                SettingsPage.NARRATION -> Unit
                                SettingsPage.AI -> AiPage(prefs.geminiApiKey, viewModel::setGeminiApiKey)
                                SettingsPage.LOCK -> SettingsCard {
                                    WebLockSettings(
                                        requireAuth = prefs.coverLockRequireAuth,
                                        onRequireAuthChange = viewModel::setCoverLockRequireAuth
                                    )
                                }
                                SettingsPage.SYNC -> SettingsCard {
                                    SettingRow(title = stringResource(R.string.settings_sync_title)) {
                                        SyncSection(
                                            state = syncState,
                                            onConnect = syncViewModel::connect,
                                            onSyncNow = { syncViewModel.syncNow() },
                                            onSetClientId = syncViewModel::setClientId,
                                            onDisconnect = syncViewModel::signOut,
                                            onDismissError = syncViewModel::dismissError
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pageTitleRes(page: SettingsPage): Int = when (page) {
    SettingsPage.APPEARANCE -> R.string.settings_section_appearance
    SettingsPage.RSVP -> R.string.settings_page_rsvp
    SettingsPage.PACING -> R.string.settings_section_pacing
    SettingsPage.NARRATION -> R.string.settings_section_narration
    SettingsPage.AI -> R.string.settings_section_ai_image
    SettingsPage.SYNC -> R.string.settings_section_sync
    SettingsPage.LOCK -> R.string.settings_section_lock
}

@Composable
private fun SettingsHub(
    prefs: com.readriffle.app.data.preferences.UserPreferences,
    syncEmail: String?,
    onOpen: (SettingsPage) -> Unit
) {
    val fontLabel = ReaderFontOption.fromKey(prefs.fontKey).label
    val themeLabel = stringResource(
        when (prefs.themeMode) {
            ThemeMode.LIGHT.name -> R.string.settings_theme_light
            ThemeMode.DARK.name -> R.string.settings_theme_dark
            else -> R.string.settings_theme_system
        }
    )
    val rsvpBg = stringResource(
        when (prefs.rsvpBackgroundKey) {
            RsvpBackgroundMode.BLACK_LINES.name -> R.string.rsvp_background_black_lines
            RsvpBackgroundMode.WHITE_SCREEN.name -> R.string.rsvp_background_white
            else -> R.string.rsvp_background_flowing
        }
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, top = 32.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsHeader() }

        // Sincronización arriba del todo, como "tarjeta de cuenta": es lo único que tiene estado.
        item {
            val connected = syncEmail != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), MaterialTheme.colorScheme.surface)
                        )
                    )
                    .clickable { onOpen(SettingsPage.SYNC) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CategoryIcon(R.drawable.ic_cloud_sync, MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_section_sync),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        syncEmail ?: stringResource(R.string.settings_hub_sync_off),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (connected) Box(Modifier.size(9.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF3DDC84)))
                Chevron()
            }
        }

        item { HubLabel(stringResource(R.string.settings_hub_group_look)) }
        item {
            SettingsCard(padding = 6.dp) {
                CategoryRow(
                    R.drawable.ic_font, stringResource(R.string.settings_section_appearance),
                    "$fontLabel · $themeLabel", MaterialTheme.colorScheme.primary
                ) { onOpen(SettingsPage.APPEARANCE) }
            }
        }

        item { HubLabel(stringResource(R.string.settings_hub_group_reading)) }
        item {
            SettingsCard(padding = 6.dp) {
                CategoryRow(
                    R.drawable.ic_mode_rsvp, stringResource(R.string.settings_page_rsvp),
                    rsvpBg, MaterialTheme.colorScheme.secondary
                ) { onOpen(SettingsPage.RSVP) }
                CategoryRow(
                    R.drawable.ic_skip_forward_words, stringResource(R.string.settings_section_pacing),
                    "${prefs.wpmMin} – ${prefs.wpmMax} PPM", MaterialTheme.colorScheme.tertiary
                ) { onOpen(SettingsPage.PACING) }
            }
        }

        item { HubLabel(stringResource(R.string.settings_hub_group_extras)) }
        item {
            SettingsCard(padding = 6.dp) {
                CategoryRow(
                    R.drawable.ic_image, stringResource(R.string.settings_section_ai_image),
                    stringResource(if (prefs.geminiApiKey.isNotBlank()) R.string.settings_hub_ai_on else R.string.settings_hub_ai_off),
                    MaterialTheme.colorScheme.secondary
                ) { onOpen(SettingsPage.AI) }
                if (!com.readriffle.app.platform.isInstalledApp()) {
                    CategoryRow(
                        R.drawable.ic_add, "Instalar en la pantalla de inicio",
                        "Abre Book Room como una app, a pantalla completa", MaterialTheme.colorScheme.tertiary
                    ) { com.readriffle.app.platform.openInstallGuide() }
                }
                CategoryRow(
                    R.drawable.ic_lock, stringResource(R.string.settings_section_lock),
                    if (prefs.coverLockRequireAuth) "Pide tu PIN de Book Room" else stringResource(R.string.settings_lock_off),
                    MaterialTheme.colorScheme.primary
                ) { onOpen(SettingsPage.LOCK) }
            }
        }
    }
}

@Composable
private fun HubLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp)
    )
}

@Composable
private fun CategoryIcon(iconRes: Int, tint: Color) {
    Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(iconRes), null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Chevron() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_down),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = -90f }
    )
}

@Composable
private fun CategoryRow(iconRes: Int, title: String, summary: String, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CategoryIcon(iconRes, tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Chevron()
    }
}

@Composable
private fun SettingsCard(padding: androidx.compose.ui.unit.Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(padding),
        verticalArrangement = Arrangement.spacedBy(if (padding < 10.dp) 0.dp else 24.dp),
        content = content
    )
}

@Composable
private fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.readriffle.app.theme.TitleDisplayFontFamily, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AppearancePage(
    prefs: com.readriffle.app.data.preferences.UserPreferences,
    viewModel: SettingsViewModel,
    useDarkSwatches: Boolean
) {
    val font = ReaderFontOption.fromKey(prefs.fontKey)
    val accent = if (useDarkSwatches) AccentColorOption.fromKey(prefs.darkAccentKey).darkColor
    else AccentColorOption.fromKey(prefs.lightAccentKey).lightColor

    // Vista previa en vivo con la tipografía y el color elegidos.
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.settings_preview_label).uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append(stringResource(R.string.settings_preview_before))
                pushStyle(androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = FontWeight.Bold))
                append(stringResource(R.string.settings_preview_word))
                pop()
                append(stringResource(R.string.settings_preview_after))
            },
            fontFamily = font.fontFamily, fontSize = 22.sp, lineHeight = 32.sp, color = MaterialTheme.colorScheme.onSurface
        )
    }

    Spacer(Modifier.height(0.dp))
    SettingsCard {
        SettingRow(title = stringResource(R.string.settings_font)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ReaderFontOption.entries) { option ->
                    FontChip(option = option, selected = prefs.fontKey == option.name, onClick = { viewModel.setFont(option) })
                }
            }
        }

        SettingRow(title = stringResource(R.string.settings_theme)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.settings_theme_system),
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_dark)
                    ),
                    selectedIndex = when (prefs.themeMode) {
                        ThemeMode.LIGHT.name -> 1
                        ThemeMode.DARK.name -> 2
                        else -> 0
                    },
                    onSelect = { index ->
                        viewModel.setThemeMode(
                            when (index) {
                                1 -> ThemeMode.LIGHT
                                2 -> ThemeMode.DARK
                                else -> ThemeMode.SYSTEM
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.toggleTheme(currentlyDark = useDarkSwatches) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_theme_toggle),
                        contentDescription = stringResource(R.string.settings_theme_toggle),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        SettingRow(title = stringResource(R.string.settings_amoled_black)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_amoled_black_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Switch(checked = prefs.amoledBlack, onCheckedChange = viewModel::setAmoledBlack)
            }
        }
    }

    Spacer(Modifier.height(0.dp))
    SettingsCard {
        SettingRow(title = stringResource(R.string.settings_accent_color_light)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(AccentColorOption.entries) { option ->
                    AccentSwatch(color = option.lightColor, selected = prefs.lightAccentKey == option.name, onClick = { viewModel.setLightAccent(option) })
                }
            }
        }
        SettingRow(title = stringResource(R.string.settings_accent_color_dark)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(AccentColorOption.entries) { option ->
                    AccentSwatch(color = option.darkColor, selected = prefs.darkAccentKey == option.name, onClick = { viewModel.setDarkAccent(option) })
                }
            }
        }
    }
}

@Composable
private fun RsvpPage(prefs: com.readriffle.app.data.preferences.UserPreferences, viewModel: SettingsViewModel) {
    SettingsCard {
        SettingRow(title = stringResource(R.string.settings_rsvp_background)) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.rsvp_background_flowing),
                    stringResource(R.string.rsvp_background_black_lines),
                    stringResource(R.string.rsvp_background_white)
                ),
                selectedIndex = when (prefs.rsvpBackgroundKey) {
                    RsvpBackgroundMode.BLACK_LINES.name -> 1
                    RsvpBackgroundMode.WHITE_SCREEN.name -> 2
                    else -> 0
                },
                onSelect = { index ->
                    viewModel.setRsvpBackground(
                        when (index) {
                            1 -> RsvpBackgroundMode.BLACK_LINES
                            2 -> RsvpBackgroundMode.WHITE_SCREEN
                            else -> RsvpBackgroundMode.FLOWING
                        }
                    )
                }
            )
        }

        SettingRow(title = stringResource(R.string.settings_background_motion)) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.settings_background_motion_auto),
                    stringResource(R.string.settings_background_motion_hold)
                ),
                selectedIndex = if (prefs.backgroundMotionAuto) 0 else 1,
                onSelect = { index -> viewModel.setBackgroundMotionAuto(index == 0) }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (prefs.backgroundMotionAuto) R.string.settings_background_motion_hint_auto else R.string.settings_background_motion_hint_hold
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingRow(title = stringResource(R.string.settings_word_skip_amount)) {
            Stepper(value = prefs.wordSkipAmount, range = 1..15, onValueChange = viewModel::setWordSkipAmount)
        }
    }
}

@Composable
private fun PacingPage(prefs: com.readriffle.app.data.preferences.UserPreferences, viewModel: SettingsViewModel) {
    SettingsCard {
        SettingRow(title = stringResource(R.string.settings_speed_range)) {
            var range by remember(prefs.wpmMin, prefs.wpmMax) { mutableStateOf(prefs.wpmMin.toFloat()..prefs.wpmMax.toFloat()) }
            ValueLabel("${prefs.wpmMin} – ${prefs.wpmMax} PPM")
            Spacer(Modifier.height(6.dp))
            RangeSlider(
                value = range,
                onValueChange = { range = it },
                onValueChangeFinished = { viewModel.setWpmRange(range.start.toInt(), range.endInclusive.toInt()) },
                valueRange = 100f..900f,
                steps = 15,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        SettingRow(title = stringResource(R.string.settings_slowdown_intensity)) {
            Slider(
                value = prefs.slowdownIntensity,
                onValueChange = { viewModel.setSlowdownIntensity(it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            SliderCaptionRow(
                start = stringResource(R.string.settings_slowdown_subtle),
                end = stringResource(R.string.settings_slowdown_marked)
            )
        }

        SettingRow(title = stringResource(R.string.settings_paragraph_pause)) {
            PauseSlider(valueMs = prefs.paragraphPauseMs, onValueChange = viewModel::setParagraphPauseMs)
        }
        SettingRow(title = stringResource(R.string.settings_comma_pause)) {
            PauseSlider(valueMs = prefs.commaPauseMs, onValueChange = viewModel::setCommaPauseMs)
        }
        SettingRow(title = stringResource(R.string.settings_sentence_pause)) {
            PauseSlider(valueMs = prefs.sentenceEndPauseMs, onValueChange = viewModel::setSentenceEndPauseMs)
        }
    }
}


@Composable
private fun AiPage(apiKey: String, onSave: (String) -> Unit) {
    SettingsCard {
        SettingRow(title = stringResource(R.string.settings_gemini_api_key)) {
            GeminiApiKeyField(initialValue = apiKey, onSave = onSave)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_gemini_api_key_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Cabecera grande de la pantalla principal de Ajustes. */
@Composable
private fun SettingsHeader() {
    Column {
        ScreenTitle(text = stringResource(R.string.settings_title))
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Selector numérico compacto (−/valor/+) en vez de un slider -- pensado para rangos pequeños y
 * discretos como "palabras al saltar" (1 a 15), donde arrastrar un slider es menos preciso que
 * tocar para sumar o restar de uno en uno.
 */
@Composable
private fun Stepper(value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        StepperButton(label = "−", enabled = value > range.first) {
            onValueChange((value - 1).coerceIn(range))
        }
        Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        StepperButton(label = "+", enabled = value < range.last) {
            onValueChange((value + 1).coerceIn(range))
        }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

/**
 * Campo para pegar/editar la API key de Gemini -- se mantiene un valor local mientras se edita
 * (igual que [NoteComposerSheet]) y solo se persiste al tocar "Guardar", para no escribir en
 * DataStore en cada pulsación de tecla.
 */
@Composable
private fun GeminiApiKeyField(initialValue: String, onSave: (String) -> Unit) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    Column(modifier = Modifier.fillMaxWidth()) {
        com.readriffle.app.ui.common.BookRoomTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = stringResource(R.string.gemini_key_prompt_placeholder),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.TextButton(onClick = { onSave(value) }) {
            Text(stringResource(R.string.settings_gemini_api_key_save))
        }
    }
}

private val syncDateFormatter = SimpleDateFormat("d MMM yyyy, HH:mm")

/**
 * Estado de la sincronización con Drive: sin conectar (botón para elegir cuenta de Google),
 * conectado (cuenta + última sincronización + botón manual + desconectar), con un aviso de
 * error si la última sincronización falló (p. ej. sin conexión a internet).
 */
@Composable
private fun SyncSection(
    state: com.readriffle.app.viewmodel.SyncUiState,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onSetClientId: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismissError: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.accountEmail == null) {
            if (state.clientId.isBlank()) {
                WebClientIdSetup(origin = state.signingSha1, onSave = onSetClientId)
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = stringResource(R.string.settings_sync_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onConnect) {
                Text(stringResource(R.string.settings_sync_connect))
            }
            state.errorMessage?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_sync_error, message),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Dirección de esta web (regístrala en Google Cloud): ${state.signingSha1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.clientId.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Cambiar ID de cliente web",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSetClientId("") }
                )
            }
        } else {
            Text(
                text = stringResource(R.string.settings_sync_connected_as, state.accountEmail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.lastSyncedAtMillis > 0L) {
                    stringResource(R.string.settings_sync_last_sync, syncDateFormatter.format(Date(state.lastSyncedAtMillis)))
                } else {
                    stringResource(R.string.settings_sync_never)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.errorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_sync_error, message),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = onSyncNow, enabled = !state.isSyncing) {
                    if (state.isSyncing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.settings_sync_now))
                    }
                }
                androidx.compose.material3.TextButton(onClick = onDisconnect) {
                    Text(stringResource(R.string.settings_sync_disconnect))
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ValueLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun SliderCaptionRow(start: String, end: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(start, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(end, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Slider compartido por las tres pausas de RSVP configurables como valor absoluto (párrafo,
 * coma, fin de frase): mismo rango generoso (0,1 s a 5 s) y misma presentación -- etiqueta
 * numérica en vivo destacada + descriptores "Casi nada" / "Muy larga" en los extremos.
 */
@Composable
private fun PauseSlider(valueMs: Int, onValueChange: (Int) -> Unit) {
    ValueLabel(stringResource(R.string.settings_pause_value, valueMs / 1000f))
    Spacer(Modifier.height(6.dp))
    var pauseMs by remember(valueMs) { mutableFloatStateOf(valueMs.toFloat()) }
    Slider(
        value = pauseMs,
        onValueChange = { pauseMs = it },
        onValueChangeFinished = { onValueChange(pauseMs.toInt()) },
        valueRange = 100f..5000f,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
        )
    )
    SliderCaptionRow(
        start = stringResource(R.string.settings_pause_short),
        end = stringResource(R.string.settings_pause_long)
    )
}

/**
 * Control de opción única propio, en forma de pastilla que se desliza sobre una pista tenue --
 * sustituye a la fila de [androidx.compose.material3.FilterChip] usada antes (tema, voz de
 * narración, control de lectura al tocar), que tenía un aire demasiado genérico de Material por
 * defecto.
 */
@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (selected) {
                            Modifier.shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp), clip = false)
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 6.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                // `maxLines = 2` (antes 1): con etiquetas de dos palabras como "Pantalla negra"/
                // "Pantalla blanca" (sección "Fondo RSVP"), una sola línea con `maxLines = 1` no
                // recortaba con "…", directamente cortaba la segunda palabra entera y dejaba solo
                // "Pantalla" visible -- pedido explícito del usuario tras ver la captura. Con 2
                // líneas permitidas, el texto que no cabe en una sola pasa a la siguiente en vez
                // de desaparecer; las etiquetas de una palabra (Sistema/Claro/Oscuro, etc.) no se
                // ven afectadas, siguen ocupando una sola línea como antes.
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Pastilla de tipografía: rellena de color cuando está seleccionada, tenue cuando no. */
@Composable
private fun FontChip(option: ReaderFontOption, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = option.label,
            fontFamily = option.fontFamily,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Círculo de color sólido; al seleccionarlo aparece una marca de verificación en vez del anillo de antes. */
@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


/**
 * Google exige un "ID de cliente OAuth" de tipo "Aplicación web" (del mismo proyecto de Google
 * Cloud que la app Android) para que la web pueda entrar en Drive. Se pega aquí una sola vez.
 */
@Composable
private fun WebClientIdSetup(origin: String, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Para conectar Drive desde la web, crea en Google Cloud (el mismo proyecto que la app Android) un ID de cliente OAuth de tipo «Aplicación web». Añade esta dirección en «Orígenes de JavaScript autorizados» y en «URI de redireccionamiento autorizados»:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(origin, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground)
        BookRoomTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = "xxxxxxxx.apps.googleusercontent.com",
            onAction = { if (value.isNotBlank()) onSave(value) }
        )
        androidx.compose.material3.TextButton(onClick = { if (value.isNotBlank()) onSave(value) }) {
            Text("Guardar ID de cliente")
        }
    }
}

/**
 * Bloqueo con portada en la web: el móvil no deja a una web pedir la huella o la cara, así que se
 * usa un PIN propio de Book Room (guardado solo en este navegador).
 */
@Composable
private fun WebLockSettings(requireAuth: Boolean, onRequireAuthChange: (Boolean) -> Unit) {
    val lockPrefs = remember { SimplePrefs("cover_lock") }
    var pin by remember { mutableStateOf(lockPrefs.getString("pin", "").orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Pedir PIN para quitarlo",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "El botón del candado cubre la app con la portada del libro, como un e-reader apagado. Con esto activado, para quitarlo hay que escribir tu PIN; desactivado, basta un toque.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(checked = requireAuth, onCheckedChange = onRequireAuthChange)
        }
        if (requireAuth) {
            BookRoomTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8); saved = false },
                label = "PIN (4 a 8 cifras)",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            androidx.compose.material3.TextButton(
                enabled = pin.length >= 4,
                onClick = { lockPrefs.putString("pin", pin); saved = true }
            ) { Text(if (saved) "PIN guardado" else "Guardar PIN") }
        }
    }
}
