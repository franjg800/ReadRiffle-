package com.readriffle.app.data.model

import com.readriffle.app.platform.System
import kotlinx.serialization.Serializable
import com.readriffle.app.platform.UUID

/**
 * Un capítulo ya parseado: título y texto plano (párrafos separados por líneas en blanco).
 * Este mismo texto alimenta tanto el motor RSVP como la capa de fondo que fluye en la
 * pantalla de lectura.
 */
data class Chapter(
    val index: Int,
    val title: String,
    val text: String,
    /** Libro/parte al que pertenece (p. ej. "Libro 1 · Parte 2") en colecciones; vacío si no hay. */
    val group: String = ""
)

/** Libro completo con su contenido ya extraído del EPUB, listo para leer. */
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val chapters: List<Chapter>
)

/**
 * Un fragmento resaltado/subrayado por el usuario en modo de lectura normal: mantener pulsado
 * sobre una palabra la selecciona, y arrastrar sin soltar extiende la selección a una frase.
 * [startWordIndex]/[endWordIndex] son índices de palabra dentro del capítulo -- los mismos que
 * usa el motor RSVP -- ambos inclusive, lo que permite tanto volver a pintarlo al reabrir el
 * capítulo como saltar directamente a él desde la nueva pestaña "Subrayados" del índice.
 */
@Serializable
data class HighlightMeta(
    val id: String = UUID.randomUUID().toString(),
    val chapterIndex: Int,
    // Título del capítulo en el momento de crearse -- una foto fija, igual que la página, para
    // poder listar "libro · capítulo · página · fecha" en la pantalla de Notas sin tener que
    // volver a parsear el EPUB entero solo para saber cómo se llamaba el capítulo.
    val chapterTitle: String = "",
    val startWordIndex: Int,
    val endWordIndex: Int,
    val text: String,
    // Comentario opcional sobre este subrayado -- "anotar" un pasaje concreto, en vez de solo
    // marcarlo. null = solo resaltado, sin nota.
    val note: String? = null,
    // Página/total de páginas en el momento de crearse (según la paginación que tenía la
    // pantalla entonces): una foto fija solo para mostrar referencia, no se recalcula si luego
    // cambia el tamaño de letra o la tipografía.
    val pageNumber: Int = 0,
    val totalPages: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Una nota suelta del usuario, sin texto citado del libro: se crea desde el botón "Nota" del
 * menú de pausa/controles (en RSVP o en modo normal), ligada a la posición exacta donde se
 * pausó -- capítulo + palabra -- para poder volver a ella luego. A diferencia de [HighlightMeta],
 * no cita ningún fragmento del libro.
 */
@Serializable
data class NoteMeta(
    val id: String = UUID.randomUUID().toString(),
    val chapterIndex: Int,
    val chapterTitle: String = "",
    val wordIndex: Int,
    val pageNumber: Int = 0,
    val totalPages: Int = 0,
    val text: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Un marcador de página: "esta página me importa". Se guarda la palabra con la que empieza la
 * página en el momento de marcarla; si luego cambia el tamaño de letra y la paginación, el
 * marcador sigue señalando la página que contiene esa palabra.
 */
@Serializable
data class BookmarkMeta(
    val id: String = UUID.randomUUID().toString(),
    val chapterIndex: Int,
    val chapterTitle: String = "",
    val wordIndex: Int,
    val pageNumber: Int = 0,
    val totalPages: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Una página de consulta fijada (un mapa descrito, un árbol de personajes, un glosario...): se
 * puede abrir en una ventanita desde la lectura normal sin perder el sitio donde se va leyendo.
 * [label] es un nombre opcional puesto por el usuario.
 */
@Serializable
data class PinnedPageMeta(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val chapterIndex: Int,
    val chapterTitle: String = "",
    val wordIndex: Int,
    val pageNumber: Int = 0,
    val totalPages: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** Si no es null, esta entrada es una imagen del usuario (p. ej. un mapa) en vez de una página del libro. Solo existe en este dispositivo. */
    val imagePath: String? = null
)

/**
 * Una imagen generada por IA (Gemini) a partir de una descripción de un personaje o paisaje del
 * libro, seleccionada por el usuario en modo de lectura normal -- pedido explícito: "añadir un
 * modo en el que mandes una descripción de un personaje o paisaje del libro... y una IA te envíe
 * una imagen". [prompt] es el propio texto seleccionado que se usó como descripción (se muestra
 * en Notas igual que la cita de un subrayado); [imagePath] es el archivo PNG ya guardado en el
 * almacenamiento interno de la app.
 */
@Serializable
data class GeneratedImageMeta(
    val id: String = UUID.randomUUID().toString(),
    val chapterIndex: Int,
    val chapterTitle: String = "",
    val wordIndex: Int,
    val prompt: String,
    val imagePath: String,
    val pageNumber: Int = 0,
    val totalPages: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Metadatos persistidos de un libro importado (sin el contenido de los capítulos,
 * que se vuelve a extraer del EPUB local cada vez que se abre). Esto es lo que vive
 * en el índice de la biblioteca.
 */
@Serializable
data class BookMeta(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String? = null,
    val epubFileName: String,
    val chapterCount: Int,
    /**
     * Número total de palabras del libro (todos los capítulos), calculado una vez al importar
     * (ver `LibraryRepository.finishImport`) con el mismo tokenizador que usa el motor RSVP --
     * así la Biblioteca puede mostrar el tiempo restante del libro sin tener que volver a abrir
     * y tokenizar el EPUB entero en cada render de la lista. 0 en libros importados antes de esta
     * función: `ReadingViewModel.loadBook()` lo rellena solo la primera vez que se abren (ya
     * calcula este mismo total para el progreso general) y lo persiste con
     * `LibraryRepository.updateTotalWordCount`.
     */
    val totalWordCount: Int = 0,
    val lastChapterIndex: Int = 0,
    val lastWordIndex: Int = 0,
    val progressFraction: Float = 0f,
    // Cuándo se avanzó por última vez en la lectura de este libro (se actualiza en cada
    // `updateProgress`, no solo al abrirlo) -- así la Biblioteca sabe cuál es "el libro que
    // estamos leyendo ahora mismo" para destacarlo arriba, sin depender de un orden manual.
    val lastOpenedAtMillis: Long = 0L,
    val addedAtMillis: Long = System.currentTimeMillis(),
    val highlights: List<HighlightMeta> = emptyList(),
    val notes: List<NoteMeta> = emptyList(),
    val generatedImages: List<GeneratedImageMeta> = emptyList(),
    val bookmarks: List<BookmarkMeta> = emptyList(),
    val pinnedPages: List<PinnedPageMeta> = emptyList(),
    /** Versión del reparto en capítulos: 0 = antiguo (un archivo del EPUB = un capítulo), 2 = por índice (TOC). */
    val chapterScheme: Int = 0,
    /**
     * Cuándo cambió por última vez algún dato "sincronizable" de este libro (progreso,
     * resaltados, notas) -- lo usa [com.readriffle.app.data.sync.DriveSyncManager] para decidir,
     * al fusionar con otro dispositivo, qué copia es la más reciente. Se actualiza en
     * [com.readriffle.app.data.repository.LibraryRepository] cada vez que cambia alguno de esos
     * datos, nunca a mano desde la UI.
     */
    val lastModifiedMillis: Long = System.currentTimeMillis(),
    /**
     * Id del archivo EPUB ya subido a la carpeta oculta de Drive del usuario, si lo hay --
     * null significa "todavía no se ha subido en ningún dispositivo". El propio contenido del
     * EPUB nunca cambia tras importarlo, así que cada libro solo necesita subirse una vez en
     * toda su vida, por muchas veces que se sincronice después.
     */
    val driveFileId: String? = null,
    /** Estantes propios a los que pertenece este libro (etiquetas libres del usuario). */
    val shelves: List<String> = emptyList(),
    /** Saga / colección del libro según los metadatos del EPUB (calibre:series o belongs-to-collection). */
    val series: String? = null,
    /** Identificador del archivo de origen (nombre+tamaño) para no volver a importar lo mismo desde una carpeta. */
    val sourceKey: String? = null,
    /**
     * Ids de subrayados, notas, marcadores y páginas fijadas que el usuario BORRÓ. Sin esto la
     * sincronización con Drive (que une las listas de los dos dispositivos) los volvía a traer
     * desde la copia remota y el subrayado "resucitaba" justo después de borrarlo.
     */
    val deletedItemIds: List<String> = emptyList()
)

/**
 * "Lápida" de un libro borrado: sin esto, borrar un libro en un dispositivo no se enteraba de
 * nada el resto -- en la siguiente sincronización, [com.readriffle.app.data.sync.DriveSyncManager]
 * veía ese id en el manifiesto remoto (subido por OTRO dispositivo que nunca lo borró) como "un
 * libro que aún no tengo", lo volvía a descargar, y el borrado "no se quedaba" (bug reportado:
 * "parece que se borra, pero cuando se sincroniza vuelve a aparecer"). Con esta marca, un borrado
 * se propaga igual que cualquier otro cambio.
 */
@Serializable
data class DeletedBookMeta(
    val id: String,
    val deletedAtMillis: Long = System.currentTimeMillis()
)

/** Índice completo de la biblioteca, persistido como JSON en el almacenamiento interno de la app. */
@Serializable
data class LibraryIndex(
    val books: List<BookMeta> = emptyList(),
    val deletedBooks: List<DeletedBookMeta> = emptyList(),
    /** Secciones creadas por el usuario (pueden estar vacías); el orden es el de creación. */
    val shelfNames: List<String> = emptyList()
)
