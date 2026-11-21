package com.readriffle.app.data.epub

import com.readriffle.app.data.model.Chapter
import org.w3c.dom.Document
import org.w3c.dom.Element
import com.readriffle.app.platform.File
import com.readriffle.app.platform.URLDecoder
import com.readriffle.app.platform.ZipFile
import com.readriffle.app.platform.attr
import com.readriffle.app.platform.attrNS
import com.readriffle.app.platform.parseXmlString

/** Resultado de parsear un EPUB: metadatos, capítulos ya en texto plano y portada (si existe). */
data class ParsedEpub(
    val title: String,
    val author: String,
    val chapters: List<Chapter>,
    val coverBytes: ByteArray?,
    val series: String? = null
)

private data class ManifestItem(val href: String, val mediaType: String, val properties: String)

private class TocNode(val title: String, val href: String?, val children: MutableList<TocNode> = mutableListOf())

/** Punto de corte dentro de un archivo del spine: dónde empieza una entrada del índice. */
private class Cut(val offset: Int, val title: String, val group: String, val order: Int, val isParent: Boolean = false)

/**
 * Parser de EPUB dependiente solo de la librería estándar (java.util.zip + DOM): un EPUB es un
 * ZIP; META-INF/container.xml apunta al content.opf, que define el manifest (recursos) y el spine
 * (orden de lectura).
 *
 * Los capítulos se reparten según el ÍNDICE del propio libro (nav.xhtml de EPUB3 o toc.ncx de
 * EPUB2), cortando los archivos por las anclas (`archivo.xhtml#capitulo3`). Así, una colección
 * donde cada archivo es un libro entero se divide en sus capítulos reales, agrupados por
 * libro/parte. Si el EPUB no tiene índice utilizable, se vuelve a un capítulo por archivo (y los
 * archivos enormes con varios encabezados se trocean por ellos).
 *
 * Las imágenes se extraen a [imagesDir] y entran en el texto como párrafos `[[IMG:ruta]]`.
 */
class EpubParser {

    fun parse(epubFile: File, imagesDir: File? = null): ParsedEpub {
        ZipFile(epubFile).use { zip ->
            val pkg = readPackage(zip, epubFile)
            val coverBytes = resolveCoverBytes(zip, pkg.opfDoc, pkg.manifest, pkg.opfDir)

            // Archivos del spine, en orden, con su HTML.
            class SpineFile(val path: String, val raw: String, val body: String)
            val files = pkg.spineIds.mapNotNull { id ->
                val item = pkg.manifest[id] ?: return@mapNotNull null
                val path = resolvePath(pkg.opfDir, item.href)
                val entry = zip.getEntry(path) ?: return@mapNotNull null
                val raw = zip.readText(entry)
                SpineFile(path, raw, HtmlTextExtractor.bodyOf(raw))
            }

            val imageCache = HashMap<String, String?>()
            fun resolverFor(filePath: String): ((String) -> String?)? {
                if (imagesDir == null) return null
                val dir = filePath.substringBeforeLast('/', "")
                return { src ->
                    val clean = src.substringBefore('#').substringBefore('?')
                    val full = resolvePath(dir, clean)
                    imageCache.getOrPut(full) { extractImage(zip, full, imagesDir) }
                }
            }

            val (nodes, tocDir) = parseToc(zip, pkg)
            val pathToFile = files.withIndex().associate { (i, f) -> f.path to i }

            // Cortes por archivo: (offset en el body, título, grupo).
            val cutsPerFile = Array(files.size) { mutableListOf<Cut>() }
            var order = 0
            fun walk(list: List<TocNode>, ancestors: List<String>) {
                for (n in list) {
                    val group = ancestors.joinToString(" · ")
                    if (n.href != null) {
                        val pathPart = n.href.substringBefore('#')
                        val frag = n.href.substringAfter('#', "").ifBlank { null }
                        val full = resolvePath(tocDir, pathPart)
                        val fi = pathToFile[full]
                        if (fi != null && n.title.isNotBlank()) {
                            val offset = if (frag == null) 0 else findAnchor(files[fi].body, frag) ?: 0
                            cutsPerFile[fi].add(Cut(offset, n.title, group, order++, n.children.isNotEmpty()))
                        }
                    }
                    if (n.children.isNotEmpty()) walk(n.children, if (n.title.isNotBlank()) ancestors + n.title else ancestors)
                }
            }
            walk(nodes, emptyList())
            val hasToc = cutsPerFile.any { it.isNotEmpty() }

            // Trozos finales (capítulos) en orden de lectura.
            class Seg(val title: String?, val group: String, val text: String, val fromToc: Boolean, val fallbackTitle: String, val isParent: Boolean = false)
            val segs = mutableListOf<Seg>()
            for ((fi, f) in files.withIndex()) {
                val resolver = resolverFor(f.path)
                var cuts = cutsPerFile[fi].sortedWith(compareBy({ it.offset }, { it.order }))

                // Archivo enorme con un solo corte (o ninguno) pero varios encabezados: se trocea por ellos.
                if (cuts.size <= 1 && wordCountApprox(f.body) > 12_000) {
                    val heads = HtmlTextExtractor.headings(f.body)
                    if (heads.size >= 3) {
                        val parent = cuts.firstOrNull()
                        val parentGroup = parent?.let { listOf(it.group, it.title).filter { g -> g.isNotBlank() }.joinToString(" · ") } ?: ""
                        cuts = heads.mapIndexed { i, (pos, text) -> Cut(pos, text, parentGroup, i) }
                    }
                }

                val fallbackTitle = HtmlTextExtractor.extractTitle(f.raw) ?: ""
                if (cuts.isEmpty()) {
                    segs += Seg(null, "", HtmlTextExtractor.extractFragment(f.body, resolver), false, fallbackTitle)
                    continue
                }
                if (cuts.first().offset > 0) {
                    segs += Seg(null, "", HtmlTextExtractor.extractFragment(f.body.substring(0, cuts.first().offset), resolver), false, fallbackTitle)
                }
                for ((ci, cut) in cuts.withIndex()) {
                    val end = if (ci + 1 < cuts.size) cuts[ci + 1].offset else f.body.length
                    val text = HtmlTextExtractor.extractFragment(f.body.substring(cut.offset, end.coerceAtLeast(cut.offset)), resolver)
                    segs += Seg(cut.title, cut.group, text, true, fallbackTitle, cut.isParent)
                }
            }

            // Montaje: se descartan trozos vacíos y los trozos "huérfanos" (sin entrada en el
            // índice) pequeños se pegan al capítulo anterior.
            val out = mutableListOf<Chapter>()
            // Texto de una portada de libro/parte (solo su título, casi sin palabras): no es un
            // capítulo, se pega al principio del siguiente para no perder ni una palabra.
            var pendingPrefix = ""
            for (seg in segs) {
                if (seg.text.isBlank()) continue
                if (seg.fromToc) {
                    val tinyParent = seg.isParent && wordCountApprox(seg.text) < 40 && !seg.text.contains("[[IMG:")
                    if (tinyParent) {
                        pendingPrefix = if (pendingPrefix.isEmpty()) seg.text else pendingPrefix + "\n\n" + seg.text
                        continue
                    }
                    val text = if (pendingPrefix.isEmpty()) seg.text else pendingPrefix + "\n\n" + seg.text
                    pendingPrefix = ""
                    out += Chapter(out.size, seg.title.orEmpty(), text, seg.group)
                    continue
                }
                val text = if (pendingPrefix.isEmpty()) seg.text else pendingPrefix + "\n\n" + seg.text
                pendingPrefix = ""
                val words = wordCountApprox(text)
                val last = out.lastOrNull()
                when {
                    !hasToc || last == null || words >= 400 -> {
                        val title = seg.fallbackTitle.ifBlank {
                            when {
                                !hasToc -> "Capítulo ${out.size + 1}"
                                last == null && words < 30 -> "Portada"
                                last == null -> "Inicio"
                                else -> "Sección adicional"
                            }
                        }
                        out += Chapter(out.size, title, text, "")
                    }
                    else -> out[out.lastIndex] = last.copy(text = last.text + "\n\n" + text)
                }
            }
            if (pendingPrefix.isNotEmpty()) {
                val last = out.lastOrNull()
                if (last != null) out[out.lastIndex] = last.copy(text = last.text + "\n\n" + pendingPrefix)
                else out += Chapter(0, "Inicio", pendingPrefix, "")
            }
            return ParsedEpub(title = pkg.title, author = pkg.author, chapters = out, coverBytes = coverBytes, series = readSeries(pkg.opfDoc))
        }
    }

    /**
     * Palabras por capítulo con el reparto ANTIGUO (un archivo del spine = un capítulo). Sirve para
     * trasladar la posición de lectura, subrayados, notas y marcadores guardados antes del cambio.
     */
    fun legacyWordCounts(epubFile: File): List<Int> {
        ZipFile(epubFile).use { zip ->
            val pkg = readPackage(zip, epubFile)
            return pkg.spineIds.mapNotNull { id ->
                val item = pkg.manifest[id] ?: return@mapNotNull null
                val entry = zip.getEntry(resolvePath(pkg.opfDir, item.href)) ?: return@mapNotNull null
                val text = HtmlTextExtractor.extract(zip.readText(entry))
                if (text.isBlank()) null else com.readriffle.app.data.tokenizer.WordTokenizer.tokenize(text).size
            }
        }
    }

    /** ISBN del libro según los metadatos (dc:identifier), o null si no trae ninguno válido. */
    fun readIsbn(epubFile: File): String? = runCatching {
        ZipFile(epubFile).use { zip ->
            val pkg = readPackage(zip, epubFile)
            val ids = pkg.opfDoc.getElementsByTagNameNS("*", "identifier")
            val candidates = mutableListOf<String>()
            for (i in 0 until ids.length) {
                val el = ids.item(i) as? Element ?: continue
                val text = el.textContent?.trim().orEmpty()
                val scheme = (el.attr("opf:scheme") + " " + el.attrNS("http://www.idpf.org/2007/opf", "scheme")).lowercase()
                val digits = text.replace(Regex("[^0-9Xx]"), "").uppercase()
                val looksIsbn = scheme.contains("isbn") || text.lowercase().contains("isbn")
                if ((digits.length == 13 && (digits.startsWith("978") || digits.startsWith("979"))) || (looksIsbn && digits.length == 10)) {
                    candidates += digits
                }
            }
            candidates.firstOrNull { it.length == 13 } ?: candidates.firstOrNull()
        }
    }.getOrNull()

    private fun wordCountApprox(text: String): Int {
        var n = 0
        var inWord = false
        for (c in text) {
            if (c.isWhitespace() || c == '<' || c == '>') inWord = false
            else if (!inWord) { inWord = true; n++ }
        }
        return n
    }

    private class Package(
        val opfDoc: Document,
        val opfDir: String,
        val manifest: Map<String, ManifestItem>,
        val spineIds: List<String>,
        val title: String,
        val author: String,
        val navPath: String?,
        val ncxPath: String?
    )

    private fun readPackage(zip: ZipFile, epubFile: File): Package {
        val containerEntry = zip.getEntry("META-INF/container.xml")
            ?: error("EPUB inválido: falta META-INF/container.xml")
        val containerDoc = parseXmlString(zip.readText(containerEntry))

        val rootFilePath = containerDoc.getElementsByTagNameNS("*", "rootfile")
            .item(0)?.let { it as Element }?.attr("full-path")
            ?: error("EPUB inválido: no se encontró el content.opf")

        val opfEntry = zip.getEntry(rootFilePath) ?: error("EPUB inválido: content.opf no existe")
        val opfDoc = parseXmlString(zip.readText(opfEntry))
        val opfDir = rootFilePath.substringBeforeLast('/', "")

        val title = firstElementText(opfDoc, "title") ?: epubFile.nameWithoutExtension
        val author = firstElementText(opfDoc, "creator") ?: "Autor desconocido"
        val manifest = parseManifest(opfDoc)
        val spineIds = parseSpine(opfDoc)

        val navItem = manifest.values.firstOrNull { it.properties.split(' ').contains("nav") }
        val spineEl = opfDoc.getElementsByTagNameNS("*", "spine").item(0) as? Element
        val ncxItem = spineEl?.attr("toc")?.takeIf { it.isNotBlank() }?.let { manifest[it] }
            ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
        return Package(
            opfDoc, opfDir, manifest, spineIds, title, author,
            navPath = navItem?.let { resolvePath(opfDir, it.href) },
            ncxPath = ncxItem?.let { resolvePath(opfDir, it.href) }
        )
    }

    // --- Índice (TOC) ------------------------------------------------------------------------

    /** Nodos del índice + carpeta del documento de índice (las rutas son relativas a ella). */
    private fun parseToc(zip: ZipFile, pkg: Package): Pair<List<TocNode>, String> {
        pkg.navPath?.let { path ->
            val nodes = runCatching { parseNav(zip, path) }.getOrDefault(emptyList())
            if (nodes.isNotEmpty()) return nodes to path.substringBeforeLast('/', "")
        }
        pkg.ncxPath?.let { path ->
            val nodes = runCatching { parseNcx(zip, path) }.getOrDefault(emptyList())
            if (nodes.isNotEmpty()) return nodes to path.substringBeforeLast('/', "")
        }
        return emptyList<TocNode>() to ""
    }

    private fun readCleanXml(zip: ZipFile, path: String): Document {
        val entry = zip.getEntry(path) ?: error("sin índice")
        var xml = zip.readText(entry)
        xml = xml.replace(Regex("(?is)<!DOCTYPE[^>\\[]*(\\[.*?\\])?[^>]*>"), "")
        xml = xml.replace("&nbsp;", " ").replace(Regex("&(?!(amp|lt|gt|quot|apos|#\\d+|#x[0-9a-fA-F]+);)\\w+;"), " ")
        return parseXmlString(xml)
    }

    private fun parseNav(zip: ZipFile, path: String): List<TocNode> {
        val doc = readCleanXml(zip, path)
        val navs = doc.getElementsByTagNameNS("*", "nav")
        var chosen: Element? = null
        for (i in 0 until navs.length) {
            val el = navs.item(i) as Element
            val type = el.attrNS("http://www.idpf.org/2007/ops", "type").ifBlank { el.attr("epub:type") }
            if (type.split(' ').contains("toc")) { chosen = el; break }
            if (chosen == null) chosen = el
        }
        val nav = chosen ?: return emptyList()
        val ol = childElements(nav).firstOrNull { it.localName == "ol" || it.tagName == "ol" } ?: return emptyList()
        return parseNavList(ol)
    }

    private fun parseNavList(ol: Element): List<TocNode> {
        val result = mutableListOf<TocNode>()
        for (li in childElements(ol)) {
            if (li.localName != "li" && li.tagName != "li") continue
            val label = childElements(li).firstOrNull { it.localName == "a" || it.localName == "span" }
            val title = label?.textContent?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            val href = label?.takeIf { it.localName == "a" }?.attr("href")?.takeIf { it.isNotBlank() }
            val node = TocNode(title, href)
            childElements(li).firstOrNull { it.localName == "ol" }?.let { node.children += parseNavList(it) }
            result += node
        }
        return result
    }

    private fun parseNcx(zip: ZipFile, path: String): List<TocNode> {
        val doc = readCleanXml(zip, path)
        val navMap = doc.getElementsByTagNameNS("*", "navMap").item(0) as? Element ?: return emptyList()
        return parseNavPoints(navMap)
    }

    private fun parseNavPoints(parent: Element): List<TocNode> {
        val result = mutableListOf<TocNode>()
        for (np in childElements(parent)) {
            if (np.localName != "navPoint") continue
            val label = childElements(np).firstOrNull { it.localName == "navLabel" }
                ?.let { l -> childElements(l).firstOrNull { it.localName == "text" } }
            val title = label?.textContent?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            val src = childElements(np).firstOrNull { it.localName == "content" }?.attr("src")?.takeIf { it.isNotBlank() }
            val node = TocNode(title, src)
            node.children += parseNavPoints(np)
            result += node
        }
        return result
    }

    private fun childElements(el: Element): List<Element> {
        val out = mutableListOf<Element>()
        var n = el.firstChild
        while (n != null) {
            if (n is Element) out += n
            n = n.nextSibling
        }
        return out
    }

    /** Posición (en el body) del inicio de la etiqueta que lleva `id="frag"` / `name="frag"`. */
    private fun findAnchor(body: String, frag: String): Int? {
        val decoded = runCatching { URLDecoder.decode(frag, "UTF-8") }.getOrDefault(frag)
        val regex = Regex("(?is)<[a-zA-Z][^>]*\\b(?:id|name)\\s*=\\s*[\"']" + Regex.escape(decoded) + "[\"'][^>]*>")
        return regex.find(body)?.range?.first
    }

    // --- Imágenes ---------------------------------------------------------------------------

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    /** Copia la imagen del ZIP a [dir] (una sola vez) y devuelve su ruta; null si no vale. */
    private fun extractImage(zip: ZipFile, zipPath: String, dir: File): String? {
        val ext = zipPath.substringAfterLast('.', "").lowercase()
        if (ext !in imageExtensions) return null
        val entry = zip.getEntry(zipPath) ?: return null
        if (entry.size in 0..3_000) return null // adornos e iconos diminutos
        val name = zipPath.hashCode().toUInt().toString(16) + "." + ext
        val target = File(dir, name)
        if (!target.exists()) {
            runCatching {
                dir.mkdirs()
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }.onFailure { return null }
        }
        return target.absolutePath
    }

    private fun parseManifest(opfDoc: Document): Map<String, ManifestItem> {
        val items = opfDoc.getElementsByTagNameNS("*", "item")
        val map = mutableMapOf<String, ManifestItem>()
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            val id = el.attr("id")
            val href = el.attr("href")
            if (id.isBlank() || href.isBlank()) continue
            map[id] = ManifestItem(
                href = href,
                mediaType = el.attr("media-type"),
                properties = el.attr("properties")
            )
        }
        return map
    }

    private fun parseSpine(opfDoc: Document): List<String> {
        val itemRefs = opfDoc.getElementsByTagNameNS("*", "itemref")
        val ids = mutableListOf<String>()
        for (i in 0 until itemRefs.length) {
            val el = itemRefs.item(i) as? Element ?: continue
            if (el.attr("linear") == "no") continue
            val idref = el.attr("idref")
            if (idref.isNotBlank()) ids.add(idref)
        }
        return ids
    }

    private fun resolveCoverBytes(
        zip: ZipFile,
        opfDoc: Document,
        manifest: Map<String, ManifestItem>,
        opfDir: String
    ): ByteArray? {
        // EPUB3: <item properties="cover-image" .../>
        val epub3Cover = manifest.values.firstOrNull { it.properties.contains("cover-image") }
        val coverItem = epub3Cover ?: run {
            // EPUB2: <meta name="cover" content="ID"/> -> manifest item con ese id
            val metas = opfDoc.getElementsByTagNameNS("*", "meta")
            var coverId: String? = null
            for (i in 0 until metas.length) {
                val el = metas.item(i) as? Element ?: continue
                if (el.attr("name") == "cover") {
                    coverId = el.attr("content")
                    break
                }
            }
            coverId?.let { manifest[it] }
        }
        val href = coverItem?.href ?: return null
        val path = resolvePath(opfDir, href)
        val entry = zip.getEntry(path) ?: return null
        return runCatching { zip.getInputStream(entry).readBytes() }.getOrNull()
    }

    /** Saga/colección del libro: `calibre:series` (EPUB2/Calibre) o `belongs-to-collection` (EPUB3). */
    private fun readSeries(doc: Document): String? {
        val metas = doc.getElementsByTagNameNS("*", "meta")
        for (i in 0 until metas.length) {
            val el = metas.item(i) as? Element ?: continue
            val name = el.attr("name")
            val prop = el.attr("property")
            val value = when {
                name == "calibre:series" -> el.attr("content")
                prop == "belongs-to-collection" -> el.textContent
                else -> null
            }?.replace(Regex("\\s+"), " ")?.trim()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun firstElementText(doc: Document, localName: String): String? {
        val nodes = doc.getElementsByTagNameNS("*", localName)
        if (nodes.length == 0) return null
        val text = nodes.item(0)?.textContent?.trim()
        return text?.ifBlank { null }
    }

    private fun resolvePath(baseDir: String, href: String): String {
        val decodedHref = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        val combined = if (baseDir.isEmpty()) decodedHref else "$baseDir/$decodedHref"
        val stack = ArrayDeque<String>()
        for (part in combined.split("/")) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

}
