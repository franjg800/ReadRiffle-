package com.readriffle.app.data.model

/** Modo de lectura: palabra a palabra (RSVP) o libro paginado normal. */
enum class ReadingMode {
    NORMAL, RSVP;

    companion object {
        fun fromKey(key: String): ReadingMode = entries.find { it.name == key } ?: NORMAL
    }
}
