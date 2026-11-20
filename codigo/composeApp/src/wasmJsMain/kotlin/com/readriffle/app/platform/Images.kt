package com.readriffle.app.platform

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/** Utilidades de imagen con Skia (lo que en Android hacía BitmapFactory/Bitmap.compress). */
object ImageTools {
    /** Dimensiones de una imagen codificada, o null si no se puede leer. */
    fun size(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        val img = Image.makeFromEncoded(bytes)
        img.width to img.height
    }.getOrNull()

    /**
     * Reduce la imagen para que su lado largo (o su alto si [byHeight]) no pase de [max] y la vuelve
     * a codificar. PNG si [png], si no JPEG. Null si no es una imagen legible.
     */
    fun reencode(bytes: ByteArray, max: Int, byHeight: Boolean = false, png: Boolean = false, quality: Int = 92): ByteArray? = runCatching {
        val img = Image.makeFromEncoded(bytes)
        val ref = if (byHeight) img.height else maxOf(img.width, img.height)
        val scaled = if (ref > max) {
            val s = max.toFloat() / ref
            val w = (img.width * s).toInt().coerceAtLeast(1)
            val h = (img.height * s).toInt().coerceAtLeast(1)
            val surface = Surface.makeRasterN32Premul(w, h)
            surface.canvas.drawImageRect(
                img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
                SamplingMode.LINEAR, Paint(), true
            )
            surface.makeImageSnapshot()
        } else img
        val data = scaled.encodeToData(if (png) EncodedImageFormat.PNG else EncodedImageFormat.JPEG, quality)
        data?.bytes
    }.getOrNull()

    /** ¿Tiene canal alfa (transparencias)? Para decidir entre PNG y JPEG. */
    fun hasAlpha(bytes: ByteArray): Boolean = runCatching {
        !Image.makeFromEncoded(bytes).imageInfo.isOpaque
    }.getOrDefault(false)
}
