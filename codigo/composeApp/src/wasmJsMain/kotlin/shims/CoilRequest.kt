package coil.request

import coil.size.Size

/** Petición de imagen (solo datos y tamaño máximo), como la de Coil. */
class ImageRequest private constructor(val data: Any?, val size: Size?) {
    class Builder(@Suppress("UNUSED_PARAMETER") context: Any?) {
        private var data: Any? = null
        private var size: Size? = null
        fun data(d: Any?) = apply { data = d }
        fun size(s: Size) = apply { size = s }
        fun crossfade(@Suppress("UNUSED_PARAMETER") b: Boolean) = this
        fun build() = ImageRequest(data, size)
    }

    override fun toString(): String = "$data@$size"
}
