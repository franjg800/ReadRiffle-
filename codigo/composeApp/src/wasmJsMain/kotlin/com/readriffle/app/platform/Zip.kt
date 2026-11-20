package com.readriffle.app.platform

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.parsing.DOMParser

/** Entrada de un ZIP (solo lo que usa el parser de EPUB). */
class ZipEntry(val name: String, val size: Long)

/**
 * Sustituto de java.util.zip.ZipFile: descomprime el EPUB con fflate en el navegador y deja las
 * entradas allí; cada lectura trae solo el archivo pedido.
 */
class ZipFile(bytes: ByteArray) : AutoCloseable {
    constructor(file: File) : this(file.readBytes())

    private val handle: Int = jsZipOpen(bytes.toLatin1()).also { if (it == 0) error("EPUB inválido: no es un ZIP legible") }
    private val names: Set<String> = jsZipNames(handle).split('\n').filter { it.isNotEmpty() }.toSet()

    fun getEntry(name: String): ZipEntry? =
        if (name in names) ZipEntry(name, jsZipSize(handle, name).toLong()) else null

    fun getInputStream(entry: ZipEntry): ByteInput =
        ByteInput(jsZipReadBytes(handle, entry.name)?.latin1ToBytes() ?: ByteArray(0))

    /** Lectura directa como texto UTF-8 (más rápida que pasar por bytes). */
    fun readText(entry: ZipEntry): String = jsZipReadText(handle, entry.name) ?: ""

    fun entryNames(): Set<String> = names

    override fun close() = jsZipClose(handle)
}

/** Analiza XML con el DOMParser del navegador (con espacios de nombres, como el parser de Java). */
fun parseXmlString(xml: String): Document =
    DOMParser().parseFromString(xml, "application/xml".toJsString()) as Document

/** getAttribute de Java devuelve "" si no existe; el del navegador, null. */
fun Element.attr(name: String): String = getAttribute(name) ?: ""
fun Element.attrNS(ns: String, name: String): String = getAttributeNS(ns, name) ?: ""
