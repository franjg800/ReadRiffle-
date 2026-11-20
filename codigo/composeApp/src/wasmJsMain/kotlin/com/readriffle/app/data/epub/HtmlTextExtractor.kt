package com.readriffle.app.data.epub

/**
 * Extractor de texto plano muy ligero para el XHTML de un capítulo de EPUB.
 * No usa un parser HTML completo (para no añadir dependencias externas): limpia
 * scripts/estilos, convierte los saltos de bloque en párrafos y decodifica entidades.
 */
internal object HtmlTextExtractor {

    private val scriptStyleRegex = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val bodyRegex = Regex("(?is)<body[^>]*>(.*?)</body>")
    private val blockBreakRegex = Regex("(?i)</p>|</div>|<br\\s*/?>|</h[1-6]>|</li>|</tr>")
    private val tagRegex = Regex("<[^>]+>")
    private val titleTagRegex = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>")
    private val inlineWhitespaceRegex = Regex("[ \\t\\x0B\\f\\r]+")
    private val multiNewlineRegex = Regex("\\n{3,}")
    private val numericEntityRegex = Regex("&#(\\d+);")

    fun extractTitle(html: String): String? {
        val match = titleTagRegex.find(html) ?: return null
        val raw = tagRegex.replace(match.groupValues[1], " ")
        val decoded = decodeEntities(raw).trim()
        return decoded.ifBlank { null }
    }

    /** Solo el contenido de `<body>` (o todo si no lo hay). */
    fun bodyOf(html: String): String = bodyRegex.find(html)?.groupValues?.get(1) ?: html

    private val imageTagRegex = Regex("(?is)<(?:img|image)\\b[^>]*>")
    private val imageSrcRegex = Regex("(?is)(?:\\bsrc|\\bhref|xlink:href)\\s*=\\s*[\"']([^\"']+)[\"']")
    private val sizeAttrRegex = Regex("(?is)\\b(width|height)\\s*=\\s*[\"']?(\\d+)")

    /**
     * Como [extract] pero sobre un trozo de `<body>` ya recortado, y convirtiendo cada imagen en un
     * párrafo propio `[[IMG:ruta]]` (ver ImageMarker). [resolveImage] recibe el `src` tal como
     * aparece en el HTML y devuelve la ruta local de la imagen ya extraída, o null para ignorarla.
     */
    fun extractFragment(fragment: String, resolveImage: ((String) -> String?)?): String {
        var body = scriptStyleRegex.replace(fragment, "")
        if (resolveImage != null) {
            body = imageTagRegex.replace(body) { m ->
                val tag = m.value
                val tiny = sizeAttrRegex.findAll(tag).any { it.groupValues[2].toIntOrNull()?.let { v -> v in 1..47 } == true }
                val src = imageSrcRegex.find(tag)?.groupValues?.get(1)
                val path = if (tiny || src == null) null else resolveImage(decodeEntities(src))
                if (path == null) "" else "\n\n[[IMG:$path]]\n\n"
            }
        }
        body = blockBreakRegex.replace(body, "\n\n")
        body = tagRegex.replace(body, " ")
        body = decodeEntities(body)
        body = inlineWhitespaceRegex.replace(body, " ")
        body = body.lines().joinToString("\n") { it.trim() }
        body = multiNewlineRegex.replace(body, "\n\n")
        return body.trim()
    }

    private val headingRegex = Regex("(?is)<h[1-3]\\b[^>]*>(.*?)</h[1-3]>")

    /** Posición (en [body]) y texto de cada encabezado h1-h3 con texto. */
    fun headings(body: String): List<Pair<Int, String>> =
        headingRegex.findAll(body).mapNotNull { m ->
            val t = decodeEntities(tagRegex.replace(m.groupValues[1], " ")).replace(Regex("\\s+"), " ").trim()
            if (t.isBlank()) null else m.range.first to t
        }.toList()

    fun extract(html: String): String {
        var body = bodyRegex.find(html)?.groupValues?.get(1) ?: html
        body = scriptStyleRegex.replace(body, "")
        body = blockBreakRegex.replace(body, "\n\n")
        body = tagRegex.replace(body, " ")
        body = decodeEntities(body)
        body = inlineWhitespaceRegex.replace(body, " ")
        body = body.lines().joinToString("\n") { it.trim() }
        body = multiNewlineRegex.replace(body, "\n\n")
        return body.trim()
    }

    fun decodeEntities(text: String): String {
        var result = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
        result = numericEntityRegex.replace(result) { match ->
            runCatching { match.groupValues[1].toInt().toChar().toString() }.getOrDefault("")
        }
        return result
    }
}
