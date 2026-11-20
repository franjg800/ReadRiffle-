package com.readriffle.app.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.WebDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val userPreferencesStore = WebDataStore("user_preferences")

/** Repositorio de ajustes: envuelve DataStore Preferences con la forma tipada de [UserPreferences]. */
class UserPreferencesRepository {

    private object context { val dataStore = userPreferencesStore }

    private object Keys {
        val FONT_KEY = stringPreferencesKey("font_key")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LIGHT_ACCENT_KEY = stringPreferencesKey("light_accent_key")
        val DARK_ACCENT_KEY = stringPreferencesKey("dark_accent_key")
        val READING_MODE_KEY = stringPreferencesKey("reading_mode_key")
        val WPM_MIN = intPreferencesKey("wpm_min")
        val WPM_MAX = intPreferencesKey("wpm_max")
        val LAST_WPM = intPreferencesKey("last_wpm")
        val SLOWDOWN_INTENSITY = floatPreferencesKey("slowdown_intensity")
        val BACKGROUND_MOTION_AUTO = booleanPreferencesKey("background_motion_auto")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val PARAGRAPH_PAUSE_MS = intPreferencesKey("paragraph_pause_ms")
        val COMMA_PAUSE_MS = intPreferencesKey("comma_pause_ms")
        val SENTENCE_END_PAUSE_MS = intPreferencesKey("sentence_end_pause_ms")
        val NARRATION_VOICE_ID = stringPreferencesKey("narration_voice_id")
        val THEME_VARIANT_KEY = stringPreferencesKey("theme_variant_key")
        val RSVP_BACKGROUND_KEY = stringPreferencesKey("rsvp_background_key")
        val WORD_SKIP_AMOUNT = intPreferencesKey("word_skip_amount")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_KEY_PROMPT_SHOWN = booleanPreferencesKey("gemini_key_prompt_shown")
        val LAST_SYNCED_AT_MILLIS = longPreferencesKey("last_synced_at_millis")
        val IMPORT_FOLDER_URI = stringPreferencesKey("import_folder_uri")
        val COVER_LOCK_REQUIRE_AUTH = booleanPreferencesKey("cover_lock_require_auth")
        val COVER_LOCK_PIN = booleanPreferencesKey("cover_lock_pin")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val defaults = UserPreferences()
        UserPreferences(
            fontKey = prefs[Keys.FONT_KEY] ?: defaults.fontKey,
            themeMode = prefs[Keys.THEME_MODE] ?: defaults.themeMode,
            amoledBlack = prefs[Keys.AMOLED_BLACK] ?: defaults.amoledBlack,
            lightAccentKey = prefs[Keys.LIGHT_ACCENT_KEY] ?: defaults.lightAccentKey,
            darkAccentKey = prefs[Keys.DARK_ACCENT_KEY] ?: defaults.darkAccentKey,
            readingModeKey = prefs[Keys.READING_MODE_KEY] ?: defaults.readingModeKey,
            wpmMin = prefs[Keys.WPM_MIN] ?: defaults.wpmMin,
            wpmMax = prefs[Keys.WPM_MAX] ?: defaults.wpmMax,
            lastWpm = prefs[Keys.LAST_WPM] ?: defaults.lastWpm,
            slowdownIntensity = prefs[Keys.SLOWDOWN_INTENSITY] ?: defaults.slowdownIntensity,
            backgroundMotionAuto = prefs[Keys.BACKGROUND_MOTION_AUTO] ?: defaults.backgroundMotionAuto,
            textScale = prefs[Keys.TEXT_SCALE] ?: defaults.textScale,
            paragraphPauseMs = prefs[Keys.PARAGRAPH_PAUSE_MS] ?: defaults.paragraphPauseMs,
            commaPauseMs = prefs[Keys.COMMA_PAUSE_MS] ?: defaults.commaPauseMs,
            sentenceEndPauseMs = prefs[Keys.SENTENCE_END_PAUSE_MS] ?: defaults.sentenceEndPauseMs,
            narrationVoiceId = prefs[Keys.NARRATION_VOICE_ID] ?: defaults.narrationVoiceId,
            themeVariantKey = prefs[Keys.THEME_VARIANT_KEY] ?: defaults.themeVariantKey,
            rsvpBackgroundKey = prefs[Keys.RSVP_BACKGROUND_KEY] ?: defaults.rsvpBackgroundKey,
            wordSkipAmount = prefs[Keys.WORD_SKIP_AMOUNT] ?: defaults.wordSkipAmount,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: defaults.geminiApiKey,
            geminiKeyPromptShown = prefs[Keys.GEMINI_KEY_PROMPT_SHOWN] ?: defaults.geminiKeyPromptShown,
            lastSyncedAtMillis = prefs[Keys.LAST_SYNCED_AT_MILLIS] ?: defaults.lastSyncedAtMillis,
            importFolderUri = prefs[Keys.IMPORT_FOLDER_URI] ?: defaults.importFolderUri,
            coverLockRequireAuth = prefs[Keys.COVER_LOCK_REQUIRE_AUTH] ?: defaults.coverLockRequireAuth,
            coverLockPin = prefs[Keys.COVER_LOCK_PIN] ?: defaults.coverLockPin
        )
    }

    suspend fun setFontKey(key: String) = context.dataStore.edit { it[Keys.FONT_KEY] = key }
    suspend fun setAmoledBlack(enabled: Boolean) = context.dataStore.edit { it[Keys.AMOLED_BLACK] = enabled }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setLightAccentKey(key: String) = context.dataStore.edit { it[Keys.LIGHT_ACCENT_KEY] = key }
    suspend fun setDarkAccentKey(key: String) = context.dataStore.edit { it[Keys.DARK_ACCENT_KEY] = key }
    suspend fun setReadingModeKey(key: String) = context.dataStore.edit { it[Keys.READING_MODE_KEY] = key }
    suspend fun setWpmRange(min: Int, max: Int) = context.dataStore.edit {
        it[Keys.WPM_MIN] = min
        it[Keys.WPM_MAX] = max
    }
    suspend fun setLastWpm(wpm: Int) = context.dataStore.edit { it[Keys.LAST_WPM] = wpm }
    suspend fun setSlowdownIntensity(value: Float) = context.dataStore.edit { it[Keys.SLOWDOWN_INTENSITY] = value }
    suspend fun setBackgroundMotionAuto(auto: Boolean) = context.dataStore.edit { it[Keys.BACKGROUND_MOTION_AUTO] = auto }
    suspend fun setTextScale(scale: Float) = context.dataStore.edit { it[Keys.TEXT_SCALE] = scale }
    suspend fun setParagraphPauseMs(ms: Int) = context.dataStore.edit { it[Keys.PARAGRAPH_PAUSE_MS] = ms }
    suspend fun setCommaPauseMs(ms: Int) = context.dataStore.edit { it[Keys.COMMA_PAUSE_MS] = ms }
    suspend fun setSentenceEndPauseMs(ms: Int) = context.dataStore.edit { it[Keys.SENTENCE_END_PAUSE_MS] = ms }
    suspend fun setNarrationVoiceId(id: String) = context.dataStore.edit { it[Keys.NARRATION_VOICE_ID] = id }
    suspend fun setThemeVariantKey(key: String) = context.dataStore.edit { it[Keys.THEME_VARIANT_KEY] = key }
    suspend fun setRsvpBackgroundKey(key: String) = context.dataStore.edit { it[Keys.RSVP_BACKGROUND_KEY] = key }
    suspend fun setWordSkipAmount(amount: Int) = context.dataStore.edit { it[Keys.WORD_SKIP_AMOUNT] = amount }
    suspend fun setGeminiApiKey(key: String) = context.dataStore.edit { it[Keys.GEMINI_API_KEY] = key }
    suspend fun setGeminiKeyPromptShown(shown: Boolean) = context.dataStore.edit { it[Keys.GEMINI_KEY_PROMPT_SHOWN] = shown }
    suspend fun setLastSyncedAtMillis(millis: Long) = context.dataStore.edit { it[Keys.LAST_SYNCED_AT_MILLIS] = millis }
    suspend fun setImportFolderUri(uri: String) = context.dataStore.edit { it[Keys.IMPORT_FOLDER_URI] = uri }
    suspend fun setCoverLockPin(enabled: Boolean) = context.dataStore.edit { it[Keys.COVER_LOCK_PIN] = enabled }
    suspend fun setCoverLockRequireAuth(enabled: Boolean) = context.dataStore.edit { it[Keys.COVER_LOCK_REQUIRE_AUTH] = enabled }
}
