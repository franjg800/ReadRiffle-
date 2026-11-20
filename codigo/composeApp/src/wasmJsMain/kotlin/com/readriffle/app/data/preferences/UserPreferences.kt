package com.readriffle.app.data.preferences

/**
 * Ajustes del usuario, persistidos con DataStore. Las claves de fuente/tema/acento se guardan
 * como String (el nombre del enum correspondiente) para que esta capa de datos no dependa
 * de la capa de UI/tema.
 *
 * El acento se guarda por separado para tema claro y tema oscuro (en vez de uno solo
 * compartido), así cada tema puede tener su propio color por defecto sin que el usuario
 * tenga que volver a elegirlo cada vez que cambia de tema.
 */
data class UserPreferences(
    val fontKey: String = "LITERATA",
    val themeMode: String = "SYSTEM",
    /**
     * Variante de contraste dentro de cada tema (ver [com.readriffle.app.theme.ThemeVariant]):
     * además de claro/oscuro "estándar", hay una segunda variante para cada uno (claro cálido y
     * oscuro con letras más atenuadas), pensadas para simetría ("dos y dos") y para lectura
     * nocturna cómoda. Se elige junto al tema en el menú del botón de alternar tema.
     */
    val themeVariantKey: String = "STANDARD",
    /**
     * Negro puro (#000000) como fondo del tema oscuro, para pantallas AMOLED (los píxeles negros
     * se apagan del todo: más contraste y algo de batería). Solo afecta al tema oscuro.
     */
    val amoledBlack: Boolean = false,
    val lightAccentKey: String = "AMBER",
    val darkAccentKey: String = "SKY",
    val readingModeKey: String = "RSVP",
    val wpmMin: Int = 150,
    val wpmMax: Int = 600,
    val lastWpm: Int = 300,
    val slowdownIntensity: Float = 0.5f,
    val backgroundMotionAuto: Boolean = true,
    val textScale: Float = 1.0f,
    /**
     * Pausa extra (en ms) tras la última palabra de cada párrafo en RSVP, ajustable en Ajustes
     * de forma independiente de "Intensidad de ralentizado" -- ese slider general no dejaba
     * notar bien el cambio de párrafo, así que esta pausa es un valor absoluto propio, con un
     * rango muy amplio (de casi imperceptible a varios segundos).
     */
    val paragraphPauseMs: Int = 750,
    /**
     * Pausas extra (en ms) tras una coma y tras un punto (fin de frase) en RSVP, cada una
     * ajustable por separado en Ajustes -- igual que la pausa de párrafo, son valores absolutos
     * propios, independientes de "Intensidad de ralentizado", con un rango generoso.
     */
    val commaPauseMs: Int = 120,
    val sentenceEndPauseMs: Int = 260,
    /**
     * Voz elegida para la narración (id interno de [android.speech.tts.Voice]). Vacío significa
     * "la que el sistema traiga por defecto" -- así, en un dispositivo nuevo o si esa voz deja de
     * existir, la app no se queda sin voz ninguna.
     */
    val narrationVoiceId: String = "",
    /** Fondo del modo RSVP elegido en Ajustes (ver [com.readriffle.app.theme.RsvpBackgroundMode]). */
    val rsvpBackgroundKey: String = "FLOWING",
    /**
     * Cuántas palabras saltan los botones de retroceder/adelantar en RSVP -- pedido explícito,
     * "lo típico de los vídeos de sumar 10 segundos o retroceder 10 segundos, pero con
     * palabras", con la cantidad ajustable en Ajustes.
     */
    val wordSkipAmount: Int = 5,
    /**
     * API key de Gemini (Google AI Studio), para la función de "generar imagen" de un personaje
     * o paisaje descrito del libro -- la única llamada de red de toda la app, pedida
     * explícitamente por el usuario. Vacía = la función no está disponible todavía.
     */
    val geminiApiKey: String = "",
    /**
     * Si ya se le preguntó por su API key de Gemini al entrar en la app -- pedido explícito:
     * "que me pregunte... solo una vez cada vez que instale de nuevo el APK". Como DataStore se
     * borra al desinstalar, este valor vuelve a `false` solo (y por tanto vuelve a preguntar) en
     * una instalación nueva, sin ninguna lógica adicional.
     */
    val geminiKeyPromptShown: Boolean = false,
    /**
     * Cuándo se sincronizó la biblioteca por última vez con Drive (ver
     * [com.readriffle.app.data.sync.DriveSyncManager]) -- 0 significa "nunca". Solo es para
     * mostrar "Última sincronización: ..." en Ajustes, no decide nada de la fusión en sí.
     */
    val lastSyncedAtMillis: Long = 0L,
    /** Carpeta del móvil vigilada para importar EPUB nuevos (Uri del árbol de documentos); vacía = ninguna. */
    val importFolderUri: String = "",
    /** Pedir huella / cara / PIN del móvil para quitar el bloqueo con portada. */
    val coverLockRequireAuth: Boolean = false,
    /** Fijar la pantalla (Android) al bloquear con portada, para que no se pueda salir de la app. */
    val coverLockPin: Boolean = true
)
