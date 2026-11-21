package com.readriffle.app.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")

    data object Library : Screen("library")

    data object Reading : Screen("reading/{bookId}?chapterIndex={chapterIndex}&wordIndex={wordIndex}") {
        const val ARG_BOOK_ID = "bookId"
        const val ARG_CHAPTER_INDEX = "chapterIndex"
        const val ARG_WORD_INDEX = "wordIndex"
        fun createRoute(bookId: String) = "reading/$bookId?chapterIndex=-1&wordIndex=-1"

        /** Abre el libro saltando directamente a una posición concreta -- usado desde Notas. */
        fun createRouteAt(bookId: String, chapterIndex: Int, wordIndex: Int) =
            "reading/$bookId?chapterIndex=$chapterIndex&wordIndex=$wordIndex"
    }

    data object BookDetail : Screen("book/{bookId}") {
        const val ARG_BOOK_ID = "bookId"
        fun createRoute(bookId: String) = "book/$bookId"
    }

    data object Settings : Screen("settings")

    /** Ficha de un libro de Leídos. [key] = "lib:<id>" (de la biblioteca) o "ext:<id>" (apuntado a mano). */
    data object ReadBook : Screen("read/{key}") {
        const val ARG_KEY = "key"
        fun createRoute(key: String) = "read/$key"
    }

    data object Notes : Screen("notes")

    data object Stats : Screen("stats")

    data object Store : Screen("store")

}
