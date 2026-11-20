package coil.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.request.ImageRequest
import com.readriffle.app.platform.File
import com.readriffle.app.platform.Vfs
import com.readriffle.app.platform.httpRequest
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/**
 * Sustituto mínimo de Coil para la web: carga imágenes de los archivos de la app o de internet,
 * las reduce para no gastar memoria y las guarda en una pequeña caché.
 */
object WebImageLoader {
    private const val MAX_ENTRIES = 80
    private val cache = LinkedHashMap<String, ImageBitmap>()

    private fun keyOf(source: Any?, maxSide: Int) = "$source@$maxSide"

    fun cached(model: Any?): ImageBitmap? {
        val (src, side) = unwrap(model)
        return cache[keyOf(src, side)]
    }

    fun unwrap(model: Any?): Pair<Any?, Int> = when (model) {
        is ImageRequest -> model.data to (model.size?.let { maxOf(it.width, it.height) } ?: DEFAULT_SIDE)
        else -> model to DEFAULT_SIDE
    }

    suspend fun load(model: Any?): ImageBitmap? {
        val (src, side) = unwrap(model)
        if (src == null) return null
        val key = keyOf(src, side)
        cache[key]?.let { return it }
        val bytes: ByteArray = when (src) {
            is File -> loadFile(src.path)
            is String -> if (src.startsWith("http://") || src.startsWith("https://")) {
                runCatching { httpRequest("GET", src).takeIf { it.ok }?.bytes }.getOrNull()
            } else loadFile(src)
            is ByteArray -> src
            else -> null
        } ?: return null
        val bmp = decode(bytes, side) ?: return null
        cache[key] = bmp
        while (cache.size > MAX_ENTRIES) cache.remove(cache.keys.first())
        return bmp
    }

    private suspend fun loadFile(path: String): ByteArray? {
        Vfs.ensureLoaded(path)
        return runCatching { Vfs.read(File(path).path) }.getOrNull()
    }

    fun decode(bytes: ByteArray, maxSide: Int): ImageBitmap? = runCatching {
        val img = Image.makeFromEncoded(bytes)
        val longest = maxOf(img.width, img.height)
        if (longest <= maxSide) return@runCatching img.toComposeImageBitmap()
        val scale = maxSide.toFloat() / longest
        val w = (img.width * scale).toInt().coerceAtLeast(1)
        val h = (img.height * scale).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(
            img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
            SamplingMode.LINEAR, Paint(), true
        )
        surface.makeImageSnapshot().toComposeImageBitmap()
    }.getOrNull()

    private const val DEFAULT_SIDE = 900
}

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null
) {
    val bitmap by produceState(WebImageLoader.cached(model), model) { value = WebImageLoader.load(model) }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription, modifier, alignment, contentScale, alpha, colorFilter)
    } else {
        Box(modifier)
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data object Error : LoadState
    class Ok(val bmp: ImageBitmap) : LoadState
}

@Composable
fun SubcomposeAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    loading: (@Composable () -> Unit)? = null,
    error: (@Composable () -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null
) {
    val state by produceState<LoadState>(
        WebImageLoader.cached(model)?.let { LoadState.Ok(it) } ?: LoadState.Loading, model
    ) {
        value = WebImageLoader.load(model)?.let { LoadState.Ok(it) } ?: LoadState.Error
    }
    when (val s = state) {
        is LoadState.Ok -> Image(s.bmp, contentDescription, modifier, alignment, contentScale, alpha, colorFilter)
        LoadState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { loading?.invoke() }
        LoadState.Error -> Box(modifier, contentAlignment = Alignment.Center) { error?.invoke() }
    }
}
