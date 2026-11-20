// Archivo generado por tools/gen_r.py -- no editar a mano.
package com.readriffle.app

import com.readriffle.web.res.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.FontResource

object R {
    object string {
        const val app_name = 0
        const val library_title = 1
        const val library_empty_title = 2
        const val library_empty_subtitle = 3
        const val library_add_book = 4
        const val library_progress_format = 5
        const val library_progress_time_format = 6
        const val library_hero_label = 7
        const val library_hero_chapter_format = 8
        const val library_section_all = 9
        const val library_section_finished = 10
        const val time_remaining_minutes_format = 11
        const val time_remaining_hours_minutes_format = 12
        const val reading_time_remaining_chapter_book_format = 13
        const val reading_chapter_progress_format = 14
        const val reading_exit = 15
        const val reading_index = 16
        const val reading_play = 17
        const val reading_zoom = 18
        const val reading_chapters_title = 19
        const val reading_highlights_tab = 20
        const val reading_highlights_empty = 21
        const val reading_highlight_delete = 22
        const val selection_read_from_here = 23
        const val selection_highlight = 24
        const val selection_generate_image = 25
        const val book_detail_back = 26
        const val book_detail_share = 27
        const val book_detail_read_pct = 28
        const val book_detail_chapter_of = 29
        const val book_detail_finished = 30
        const val book_detail_continue = 31
        const val book_detail_start = 32
        const val book_detail_listen = 33
        const val book_detail_delete = 34
        const val book_detail_stat_remaining = 35
        const val book_detail_stat_total = 36
        const val book_detail_stat_chapters = 37
        const val book_detail_stat_words = 38
        const val book_detail_stat_highlights = 39
        const val book_detail_stat_notes = 40
        const val book_detail_stat_bookmarks = 41
        const val book_detail_stat_speed = 42
        const val book_detail_wpm_value = 43
        const val book_detail_added = 44
        const val book_detail_last_read = 45
        const val book_detail_share_text = 46
        const val book_detail_delete_title = 47
        const val book_detail_delete_text = 48
        const val book_detail_delete_confirm = 49
        const val book_detail_delete_cancel = 50
        const val reading_bookmark_add = 51
        const val reading_bookmark_remove = 52
        const val reading_bookmarks_tab = 53
        const val reading_bookmarks_empty = 54
        const val reading_bookmark_item = 55
        const val reference_open = 56
        const val reference_title = 57
        const val reference_subtitle = 58
        const val reference_name_hint = 59
        const val reference_pin_current = 60
        const val reference_add_image = 61
        const val reference_image_default = 62
        const val reference_image_missing = 63
        const val reference_image_hint = 64
        const val reference_only_this_book = 65
        const val reference_tab_view = 66
        const val reference_tab_add = 67
        const val reference_go_add = 68
        const val reference_kind_image = 69
        const val reference_kind_page = 70
        const val reference_add_page_title = 71
        const val reference_add_page_hint = 72
        const val reference_add_image_title = 73
        const val reference_add_image_hint = 74
        const val reference_empty = 75
        const val reference_default_label = 76
        const val reference_page_of = 77
        const val reference_remove = 78
        const val selection_dictionary = 79
        const val selection_dismiss = 80
        const val dictionary_loading = 81
        const val dictionary_not_found = 82
        const val dictionary_offline = 83
        const val dictionary_close = 84
        const val reading_theme_toggle = 85
        const val reading_font_cycle = 86
        const val reading_background = 87
        const val reading_skip_back = 88
        const val reading_skip_forward = 89
        const val reading_skip_label_back = 90
        const val reading_skip_label_forward = 91
        const val reading_overflow = 92
        const val theme_light = 93
        const val theme_light_warm = 94
        const val theme_dark = 95
        const val theme_dark_deep = 96
        const val reading_mode_rsvp = 97
        const val reading_mode_book = 98
        const val reading_progress_bar = 99
        const val reading_page_progress_format = 100
        const val reading_speed_slider = 101
        const val settings_page_rsvp = 102
        const val settings_hub_sync_off = 103
        const val settings_hub_group_look = 104
        const val settings_hub_group_reading = 105
        const val settings_hub_group_extras = 106
        const val settings_hub_narration_sub = 107
        const val settings_hub_ai_on = 108
        const val settings_hub_ai_off = 109
        const val settings_preview_label = 110
        const val settings_preview_before = 111
        const val settings_preview_word = 112
        const val settings_preview_after = 113
        const val settings_title = 114
        const val settings_subtitle = 115
        const val settings_section_appearance = 116
        const val settings_section_pacing = 117
        const val settings_section_rsvp_background = 118
        const val settings_rsvp_background = 119
        const val rsvp_background_flowing = 120
        const val rsvp_background_black_lines = 121
        const val rsvp_background_white = 122
        const val rsvp_background_clean = 123
        const val settings_section_reading = 124
        const val settings_word_skip_amount = 125
        const val settings_section_narration = 126
        const val settings_font = 127
        const val settings_theme = 128
        const val settings_theme_system = 129
        const val settings_theme_light = 130
        const val settings_theme_dark = 131
        const val settings_theme_toggle = 132
        const val settings_speed_range = 133
        const val settings_accent_color_light = 134
        const val settings_accent_color_dark = 135
        const val settings_slowdown_intensity = 136
        const val settings_slowdown_subtle = 137
        const val settings_slowdown_marked = 138
        const val settings_background_motion = 139
        const val settings_background_motion_auto = 140
        const val settings_background_motion_hold = 141
        const val settings_background_motion_hint_auto = 142
        const val settings_background_motion_hint_hold = 143
        const val settings_wpm_format = 144
        const val settings_paragraph_pause = 145
        const val settings_comma_pause = 146
        const val settings_sentence_pause = 147
        const val settings_pause_value = 148
        const val settings_pause_short = 149
        const val settings_pause_long = 150
        const val settings_narration_voice = 151
        const val settings_narration_voice_hint = 152
        const val reading_next_chapter = 153
        const val dock_library = 154
        const val convert_back = 155
        const val settings_converter_title = 156
        const val settings_converter_sub = 157
        const val dock_convert = 158
        const val dock_notes = 159
        const val dock_settings = 160
        const val reading_add_note = 161
        const val note_composer_title_standalone = 162
        const val note_composer_title_highlight = 163
        const val note_composer_placeholder = 164
        const val note_composer_save = 165
        const val note_composer_cancel = 166
        const val note_reference_format = 167
        const val note_reference_format_no_pages = 168
        const val notes_title = 169
        const val notes_subtitle = 170
        const val notes_empty_title = 171
        const val notes_empty_subtitle = 172
        const val notes_delete = 173
        const val notes_edit_note = 174
        const val notes_type_highlight = 175
        const val notes_type_note = 176
        const val notes_type_image = 177
        const val reading_listen = 178
        const val player_title = 179
        const val player_play = 180
        const val player_pause = 181
        const val player_previous_chapter = 182
        const val player_next_chapter = 183
        const val player_exit = 184
        const val player_notification_channel = 185
        const val player_chapter_of = 186
        const val player_speed_format = 187
        const val player_loading = 188
        const val player_free_voice_note = 189
        const val player_time_format = 190
        const val player_seek_bar = 191
        const val player_skip_back = 192
        const val player_skip_forward = 193
        const val player_skip_back_label = 194
        const val player_skip_forward_label = 195
        const val player_voice_picker = 196
        const val player_voice_picker_title = 197
        const val player_voice_default = 198
        const val player_voice_alt = 199
        const val player_voice_only_one = 200
        const val convert_title = 201
        const val convert_subtitle = 202
        const val convert_pick_pdf = 203
        const val convert_converting = 204
        const val convert_success_title = 205
        const val convert_success_body = 206
        const val convert_error_title = 207
        const val image_generating_title = 208
        const val image_generating_body = 209
        const val image_result_title = 210
        const val image_result_save = 211
        const val image_result_discard = 212
        const val image_error_title = 213
        const val image_error_dismiss = 214
        const val gemini_key_prompt_title = 215
        const val gemini_key_prompt_body = 216
        const val gemini_key_prompt_placeholder = 217
        const val gemini_key_prompt_save = 218
        const val gemini_key_prompt_skip = 219
        const val settings_section_ai_image = 220
        const val settings_gemini_api_key = 221
        const val settings_gemini_api_key_hint = 222
        const val settings_gemini_api_key_save = 223
        const val settings_section_sync = 224
        const val settings_sync_title = 225
        const val settings_sync_explainer = 226
        const val settings_sync_sha1 = 227
        const val settings_sync_connect = 228
        const val settings_sync_connected_as = 229
        const val settings_sync_last_sync = 230
        const val settings_sync_never = 231
        const val settings_sync_now = 232
        const val settings_sync_disconnect = 233
        const val settings_sync_error = 234
        const val settings_section_lock = 235
        const val settings_lock_on = 236
        const val settings_lock_off = 237
        const val settings_lock_auth_title = 238
        const val settings_lock_auth_hint = 239
        const val settings_lock_pin_title = 240
        const val settings_lock_pin_hint = 241
        const val settings_lock_pin_button = 242
        const val dock_store = 243
        const val store_title = 244
        const val store_subtitle = 245
        const val store_search_placeholder = 246
        const val store_search_action = 247
        const val store_empty = 248
        const val store_download_action = 249
        const val store_downloaded = 250
        const val store_link_subtitle = 251
        const val store_link_placeholder = 252
        const val store_link_open = 253
        const val store_link_cancel = 254
        const val store_link_remove = 255
        const val store_link_invalid = 256
        const val store_download_done = 257
        const val store_download_failed = 258
        const val store_download_unsupported = 259
        const val store_download_is_pdf = 260
        const val store_home_subtitle = 261
        const val store_add_card = 262
        const val store_add_card_hint = 263
        const val store_remove_title = 264
        const val store_remove_confirm = 265
        const val store_back_home = 266
        const val store_save_action = 267
        const val store_saved_label = 268
        const val store_save_title = 269
        const val store_save_confirm = 270
        const val store_name_hint = 271
        const val reading_pages_left_many = 272
        const val reading_pages_left_one = 273
        const val reading_last_page = 274
        const val settings_amoled_black = 275
        const val settings_amoled_black_hint = 276
    }
    object drawable {
        const val ic_add = 0
        const val ic_arrow_back = 1
        const val ic_background = 2
        const val ic_bookmark = 3
        const val ic_bookmark_filled = 4
        const val ic_check = 5
        const val ic_chevron_down = 6
        const val ic_close = 7
        const val ic_cloud_sync = 8
        const val ic_convert = 9
        const val ic_convert_simple = 10
        const val ic_cover_image = 11
        const val ic_dictionary = 12
        const val ic_edit = 13
        const val ic_exit = 14
        const val ic_font = 15
        const val ic_headphones = 16
        const val ic_highlight = 17
        const val ic_image = 18
        const val ic_index = 19
        const val ic_launcher_bg = 20
        const val ic_launcher_foreground = 21
        const val ic_launcher_monochrome = 22
        const val ic_library = 23
        const val ic_lock = 24
        const val ic_mode_book = 25
        const val ic_mode_page = 26
        const val ic_mode_rsvp = 27
        const val ic_more = 28
        const val ic_nav_library = 29
        const val ic_nav_notes = 30
        const val ic_nav_settings = 31
        const val ic_nav_store = 32
        const val ic_next_chapter = 33
        const val ic_note = 34
        const val ic_note_simple = 35
        const val ic_pause = 36
        const val ic_play = 37
        const val ic_reference_page = 38
        const val ic_search = 39
        const val ic_settings = 40
        const val ic_share = 41
        const val ic_skip_back_words = 42
        const val ic_skip_forward_words = 43
        const val ic_skip_next = 44
        const val ic_skip_previous = 45
        const val ic_store = 46
        const val ic_text_aa = 47
        const val ic_theme_toggle = 48
        const val ic_zoom = 49
    }
    object font {
        const val atkinson_bold = 0
        const val atkinson_regular = 1
        const val inter_bold = 2
        const val inter_regular = 3
        const val literata_bold = 4
        const val literata_regular = 5
        const val plex_mono_bold = 6
        const val plex_mono_regular = 7
        const val source_serif_bold = 8
        const val source_serif_regular = 9
        const val space_grotesk_bold = 10
        const val space_grotesk_regular = 11
        const val title_display_bold = 12
        const val title_display_semibold = 13
    }
}

internal val STRING_TABLE: Array<String> = arrayOf(
    "Book Room",
    "Biblioteca",
    "Tu biblioteca está vacía",
    "Importa un EPUB para empezar a leer",
    "Añadir libro",
    "%1\$d%%",
    "%1\$d%% · %2\$s",
    "Continúa leyendo",
    "%1\$s · Cap. %2\$d",
    "Tu biblioteca",
    "Leídos",
    "%1\$d min",
    "%1\$d h %2\$d min",
    "%1\$s cap. · %2\$s libro",
    "Capítulo %1\$d · %2\$d%%",
    "Salir",
    "Índice de capítulos",
    "Reproducir",
    "Tamaño de texto",
    "Capítulos",
    "Subrayados",
    "Aún no has resaltado nada en este libro. Mantén pulsada una palabra en modo normal para empezar.",
    "Eliminar subrayado",
    "Cambiar a RSVP",
    "Resaltar texto",
    "Generar imagen",
    "Volver",
    "Compartir",
    "leído",
    "Capítulo %1\$d de %2\$d",
    "Libro terminado",
    "Continuar leyendo",
    "Empezar a leer",
    "Escuchar",
    "Eliminar",
    "Te queda",
    "Duración total",
    "Capítulos",
    "Palabras",
    "Subrayados",
    "Notas",
    "Marcadores",
    "Tu ritmo",
    "%1\$d ppm",
    "Añadido el %1\$s",
    "Última lectura el %1\$s",
    "Estoy leyendo «%1\$s» de %2\$s · %3\$d %% leído",
    "¿Eliminar «%1\$s»?",
    "Se borrará de tu biblioteca junto con su progreso, notas y marcadores.",
    "Eliminar",
    "Cancelar",
    "Marcar página",
    "Quitar marcador",
    "Marcadores",
    "Aún no has marcado ninguna página. Toca el icono del marcador mientras lees.",
    "Página %1\$d de %2\$d",
    "Páginas de consulta",
    "Páginas de consulta",
    "Fija páginas a las que vuelves a menudo (un glosario, un mapa descrito, un árbol de personajes) y échales un vistazo sin perder tu sitio.",
    "Nombre (opcional), p. ej. «Personajes»",
    "Fijar esta página",
    "Añadir imagen",
    "Imagen",
    "Esta imagen se añadió en otro dispositivo y solo está guardada allí.",
    "Pellizca para hacer zoom, arrastra para moverla y toca dos veces para ampliar.",
    "Solo de «%1\$s»",
    "Mis consultas (%1\$d)",
    "Añadir",
    "Añadir una consulta",
    "Imagen",
    "Página del libro",
    "Una página de este libro",
    "Guarda la página que estás leyendo ahora (pág. %1\$d de %2\$d) para consultarla luego.",
    "Una imagen",
    "Un mapa, un árbol de personajes… desde tu galería o archivos. Se verá con zoom.",
    "Todavía no has guardado nada para este libro.",
    "Cap. %1\$d · pág. %2\$d",
    "Página %1\$d de %2\$d",
    "Quitar",
    "Diccionario",
    "Cancelar",
    "Buscando…",
    "No he encontrado «%1\$s» en el diccionario.",
    "Sin conexión: el diccionario necesita internet.",
    "Cerrar",
    "Alternar tema",
    "Cambiar tipografía",
    "Fondo RSVP",
    "Retroceder %1\$d palabras",
    "Adelantar %1\$d palabras",
    "−%1\$d",
    "+%1\$d",
    "Más opciones",
    "Claro",
    "Claro cálido",
    "Oscuro",
    "Oscuro profundo",
    "Modo RSVP",
    "Modo lectura normal",
    "Página del capítulo, arrastra para saltar",
    "Página %1\$d de %2\$d",
    "Velocidad de lectura, arrastra para ajustar",
    "Modo RSVP",
    "Sin conectar · toca para activar Drive",
    "Aspecto",
    "Lectura y audio",
    "Extras",
    "Voz de narración",
    "API key configurada",
    "Sin configurar",
    "Vista previa",
    "Leer es un viaje que se hace",
    "palabra",
    "a palabra.",
    "Ajustes",
    "Personaliza tu lectura",
    "Apariencia",
    "Velocidad y ritmo",
    "Fondo RSVP",
    "Fondo",
    "Texto fluido",
    "Pantalla negra",
    "Pantalla blanca",
    "Pantalla limpia",
    "Control de lectura",
    "Palabras al saltar (RSVP)",
    "Narración por voz",
    "Tipografía",
    "Tema",
    "Sistema",
    "Claro",
    "Oscuro",
    "Alternar entre claro y oscuro",
    "Rango de velocidad",
    "Color de la palabra destacada (tema claro)",
    "Color de la palabra destacada (tema oscuro)",
    "Intensidad de ralentizado",
    "Sutil",
    "Marcado",
    "Control de lectura al tocar",
    "Automático",
    "Solo al mantener pulsado",
    "Toca la pantalla para reproducir o pausar.",
    "Mantén pulsado para que avance la palabra; suelta para detenerla.",
    "%1\$d PPM",
    "Pausa tras párrafo",
    "Pausa en coma",
    "Pausa en punto",
    "%1\$.2f s",
    "Casi nada",
    "Muy larga",
    "Voz de narración",
    "Se usa al escuchar el libro en el reproductor de audio.",
    "Siguiente capítulo",
    "Biblioteca",
    "Volver",
    "Convertir PDF a EPUB",
    "Pasa un PDF a un libro que puedas leer aquí",
    "Convertir PDF a EPUB",
    "Notas",
    "Ajustes",
    "Añadir nota",
    "Nueva nota",
    "Nota sobre el subrayado",
    "Escribe tu nota…",
    "Guardar",
    "Cancelar",
    "%1\$s · %2\$s · Pág. %3\$d de %4\$d · %5\$s",
    "%1\$s · %2\$s · %3\$s",
    "Notas",
    "Subrayados y anotaciones de todos tus libros",
    "Aún no tienes notas",
    "Resalta texto o añade una nota mientras lees, y aparecerá aquí.",
    "Eliminar",
    "Añadir/editar nota",
    "Subrayado",
    "Nota",
    "Imagen",
    "Escuchar",
    "Reproductor",
    "Reproducir",
    "Pausar",
    "Capítulo anterior",
    "Capítulo siguiente",
    "Cerrar reproductor",
    "Narración de libro",
    "Capítulo %1\$d de %2\$d",
    "%1\$.1f×",
    "Preparando la narración…",
    "Usando la voz de Android (gratis, sin conexión)",
    "%1\$s / %2\$s",
    "Progreso de la narración, arrastra para avanzar o retroceder",
    "Retroceder 10 segundos",
    "Avanzar 10 segundos",
    "−10",
    "+10",
    "Elegir voz",
    "Voz de narración",
    "Voz 1 (predeterminada)",
    "Voz %1\$d",
    "El sistema solo ofrece una voz para este idioma en este dispositivo.",
    "Convertir PDF a EPUB",
    "Extrae el texto del PDF y lo empaqueta como EPUB para poder leerlo en RSVP. Al no tener capítulos reales, se agrupan páginas en bloques: el resultado es orientativo, no una conversión perfecta.",
    "Elegir PDF",
    "Convirtiendo… %1\$d / %2\$d páginas",
    "Convertido",
    "El EPUB se ha añadido a tu biblioteca.",
    "No se pudo convertir",
    "Generando imagen…",
    "Mandando la descripción a Gemini. Puede tardar unos segundos.",
    "Imagen generada",
    "Guardar en Notas",
    "Descartar",
    "No se pudo generar la imagen",
    "Entendido",
    "Generar imágenes con IA",
    "Para generar una imagen de un personaje o paisaje del libro, Book Room necesita tu propia API key de Gemini (Google AI Studio). Puedes pegarla ahora o más tarde desde Ajustes. Solo se te preguntará una vez por instalación.",
    "Pega aquí tu API key de Gemini",
    "Guardar",
    "Más tarde",
    "Generación de imágenes (IA)",
    "API key de Gemini",
    "Necesaria para generar imágenes de personajes o paisajes del libro. Se guarda solo en este dispositivo.",
    "Guardar API key",
    "Sincronización",
    "Libros y progreso entre dispositivos",
    "Conecta tu cuenta de Google para guardar tus libros y tu progreso de lectura (capítulo, palabra, resaltados y notas) en una carpeta privada de tu propio Drive, y tenerlos sincronizados en todos tus dispositivos. Gratis, sin tarjeta -- las imágenes generadas con IA no se sincronizan.",
    "Huella SHA-1 de esta app: %1\$s",
    "Conectar con Google Drive",
    "Conectado como %1\$s",
    "Última sincronización: %1\$s",
    "Todavía no se ha sincronizado",
    "Sincronizar ahora",
    "Desconectar",
    "No se pudo sincronizar: %1\$s",
    "Bloqueo con portada",
    "Pide huella, cara o PIN",
    "Se quita con un toque",
    "Pedir huella, cara o PIN",
    "El botón del candado cubre la app con la portada del libro, como un e-reader apagado. Con esto activado, para quitarlo se pide el desbloqueo del móvil; desactivado, basta un toque.",
    "Que no se pueda salir de la app",
    "Al bloquear, la app se pone por encima de la pantalla de bloqueo del móvil y usa «Fijar apps» de Android para impedir salir con Inicio o Recientes. Para que funcione, activa «Fijar apps» (o «Fijar pantalla») en los ajustes de seguridad del móvil; Android te pedirá confirmar la primera vez. Para salir de la fijación hay que mantener pulsados Atrás y Recientes (Android pide el PIN si lo tienes activado).",
    "Abrir ajustes de seguridad",
    "Tienda",
    "Tienda",
    "Libros de dominio público, gratis y legales (Project Gutenberg) -- descárgalos directamente a tu biblioteca.",
    "Buscar por título o autor",
    "Buscar",
    "No se han encontrado libros.",
    "Descargar",
    "Ya en tu biblioteca",
    "Pega el enlace de la tienda donde compras tus libros y cómpralos desde aquí.",
    "ej. tienda.com",
    "Abrir tienda",
    "Cancelar",
    "Quitar",
    "Ese enlace no parece válido.",
    "%1\$s añadido a tu biblioteca",
    "No se pudo importar el libro descargado",
    "%1\$s no es un EPUB, así que no se puede leer aquí",
    "%1\$s es un PDF, no un EPUB. Conviértelo desde la pestaña Convertir.",
    "Tus tiendas de confianza. Toca una para abrirla; mantenla pulsada para quitarla.",
    "Añadir tienda",
    "Pega un enlace",
    "¿Quitar %1\$s de tus tiendas?",
    "Quitar",
    "‹ Tiendas",
    "Guardar tienda",
    "Guardada",
    "Guardar tienda",
    "Guardar",
    "Nombre de la tienda",
    "quedan %1\$d páginas",
    "queda 1 página",
    "última página",
    "Negro puro (AMOLED)",
    "Fondo #000000 en el tema oscuro: los píxeles se apagan del todo en pantallas AMOLED.",
)

internal val DRAWABLE_TABLE: Array<DrawableResource> by lazy { arrayOf(
    Res.drawable.ic_add,
    Res.drawable.ic_arrow_back,
    Res.drawable.ic_background,
    Res.drawable.ic_bookmark,
    Res.drawable.ic_bookmark_filled,
    Res.drawable.ic_check,
    Res.drawable.ic_chevron_down,
    Res.drawable.ic_close,
    Res.drawable.ic_cloud_sync,
    Res.drawable.ic_convert,
    Res.drawable.ic_convert_simple,
    Res.drawable.ic_cover_image,
    Res.drawable.ic_dictionary,
    Res.drawable.ic_edit,
    Res.drawable.ic_exit,
    Res.drawable.ic_font,
    Res.drawable.ic_headphones,
    Res.drawable.ic_highlight,
    Res.drawable.ic_image,
    Res.drawable.ic_index,
    Res.drawable.ic_launcher_bg,
    Res.drawable.ic_launcher_foreground,
    Res.drawable.ic_launcher_monochrome,
    Res.drawable.ic_library,
    Res.drawable.ic_lock,
    Res.drawable.ic_mode_book,
    Res.drawable.ic_mode_page,
    Res.drawable.ic_mode_rsvp,
    Res.drawable.ic_more,
    Res.drawable.ic_nav_library,
    Res.drawable.ic_nav_notes,
    Res.drawable.ic_nav_settings,
    Res.drawable.ic_nav_store,
    Res.drawable.ic_next_chapter,
    Res.drawable.ic_note,
    Res.drawable.ic_note_simple,
    Res.drawable.ic_pause,
    Res.drawable.ic_play,
    Res.drawable.ic_reference_page,
    Res.drawable.ic_search,
    Res.drawable.ic_settings,
    Res.drawable.ic_share,
    Res.drawable.ic_skip_back_words,
    Res.drawable.ic_skip_forward_words,
    Res.drawable.ic_skip_next,
    Res.drawable.ic_skip_previous,
    Res.drawable.ic_store,
    Res.drawable.ic_text_aa,
    Res.drawable.ic_theme_toggle,
    Res.drawable.ic_zoom,
) }

internal val FONT_TABLE: Array<FontResource> by lazy { arrayOf(
    Res.font.atkinson_bold,
    Res.font.atkinson_regular,
    Res.font.inter_bold,
    Res.font.inter_regular,
    Res.font.literata_bold,
    Res.font.literata_regular,
    Res.font.plex_mono_bold,
    Res.font.plex_mono_regular,
    Res.font.source_serif_bold,
    Res.font.source_serif_regular,
    Res.font.space_grotesk_bold,
    Res.font.space_grotesk_regular,
    Res.font.title_display_bold,
    Res.font.title_display_semibold,
) }

internal val FONT_FILES: Array<String> = arrayOf(
    "font/atkinson_bold.ttf",
    "font/atkinson_regular.ttf",
    "font/inter_bold.ttf",
    "font/inter_regular.ttf",
    "font/literata_bold.ttf",
    "font/literata_regular.ttf",
    "font/plex_mono_bold.ttf",
    "font/plex_mono_regular.ttf",
    "font/source_serif_bold.ttf",
    "font/source_serif_regular.ttf",
    "font/space_grotesk_bold.ttf",
    "font/space_grotesk_regular.ttf",
    "font/title_display_bold.ttf",
    "font/title_display_semibold.ttf",
)
