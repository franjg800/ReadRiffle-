package com.readriffle.app.data.share

import androidx.compose.ui.text.font.PreloadedFonts
import com.readriffle.app.R
import com.readriffle.app.platform.shareImage
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider

/**
 * Tarjeta de cita para compartir (1080x1350, formato de historia/post): la cita grande con
 * comillas, el título y el autor del libro y la firma "BOOK ROOM". Dibujada con Skia, igual que en
 * Android se dibujaba con Canvas.
 */
object QuoteCardRenderer {
    private const val W = 1080
    private const val H = 1350
    private const val AMBER = 0xFFE8A33A.toInt()
    private const val CREAM = 0xFFF5F0E6.toInt()

    private val fonts: FontCollection by lazy {
        val provider = TypefaceFontProvider()
        fun reg(id: Int, family: String) {
            PreloadedFonts.bytes[id]?.let { bytes ->
                FontMgr.default.makeFromData(Data.makeFromBytes(bytes))?.let { provider.registerTypeface(it, family) }
            }
        }
        reg(R.font.literata_regular, "Serif")
        reg(R.font.inter_regular, "Sans")
        reg(R.font.inter_bold, "Sans")
        FontCollection().setDefaultFontManager(FontMgr.default).setAssetFontManager(provider)
    }

    private fun paragraph(text: String, family: String, size: Float, color: Int, width: Float, bold: Boolean = false,
                          height: Float? = null, maxLines: Int? = null, letterSpacing: Float = 0f) =
        ParagraphBuilder(
            ParagraphStyle().apply {
                if (maxLines != null) { this.maxLinesCount = maxLines; ellipsis = "…" }
            },
            fonts
        ).apply {
            pushStyle(TextStyle().apply {
                fontFamilies = arrayOf(family)
                fontSize = size
                this.color = color
                if (height != null) this.height = height
                if (bold) fontStyle = org.jetbrains.skia.FontStyle.BOLD
                if (letterSpacing != 0f) this.letterSpacing = letterSpacing
            })
            addText(text)
        }.build().also { it.layout(width) }

    fun render(text: String, title: String, author: String, accent: Int = AMBER): ByteArray? {
        val surface = Surface.makeRasterN32Premul(W, H)
        val c = surface.canvas
        val bg = Paint().apply {
            shader = Shader.makeLinearGradient(0f, 0f, 0f, H.toFloat(), intArrayOf(0xFF1B1E26.toInt(), 0xFF090A0D.toInt()), null, GradientStyle.DEFAULT)
        }
        c.drawRect(Rect.makeWH(W.toFloat(), H.toFloat()), bg)

        val margin = 96f
        val textWidth = W - margin * 2

        paragraph("“", "Serif", 260f, accent, textWidth).paint(c, margin - 8f, 110f)

        val clean = text.trim().replace(Regex("\\s+"), " ")
        val maxTextHeight = 640f
        var size = 58f
        var body = paragraph(clean, "Serif", size, CREAM, textWidth, height = 1.22f)
        while (body.height > maxTextHeight && size > 30f) {
            size -= 2f
            body = paragraph(clean, "Serif", size, CREAM, textWidth, height = 1.22f)
        }
        if (body.height > maxTextHeight) {
            val lines = (maxTextHeight / (size * 1.22f)).toInt().coerceAtLeast(3)
            body = paragraph(clean, "Serif", size, CREAM, textWidth, height = 1.22f, maxLines = lines)
        }
        val textTop = 380f
        body.paint(c, margin, textTop)

        val infoTop = textTop + body.height + 64f
        val line = Paint().apply { color = accent; strokeWidth = 6f; strokeCap = PaintStrokeCap.ROUND; isAntiAlias = true }
        c.drawLine(margin, infoTop, margin + 90f, infoTop, line)
        val titleP = paragraph(title, "Sans", 40f, CREAM, textWidth, bold = true, maxLines = 2)
        titleP.paint(c, margin, infoTop + 30f)
        paragraph(author, "Sans", 32f, 0xFF9AA0AC.toInt(), textWidth, maxLines = 1).paint(c, margin, infoTop + 40f + titleP.height + 12f)

        paragraph("BOOK ROOM", "Sans", 30f, accent, textWidth, bold = true, letterSpacing = 3.6f).paint(c, margin, H - 110f)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes
    }

    /** Genera la imagen y abre la hoja de compartir del sistema (o la descarga si no se puede). */
    suspend fun share(text: String, title: String, author: String, accent: Int = AMBER) {
        val png = render(text, title, author, accent) ?: return
        shareImage("cita", png, "“${text.trim()}” — $title")
    }
}
