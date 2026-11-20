package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.appString

/** "Contexto" mínimo para el código portado: solo sirve para leer textos de R.string. */
object WebContext {
    fun getString(id: Int, vararg args: Any?): String = appString(id, *args)
}

val LocalContext = staticCompositionLocalOf { WebContext }

/** Tamaño de la ventana en dp, como Configuration.screenWidthDp/screenHeightDp de Android. */
class WebConfiguration(val screenWidthDp: Int, val screenHeightDp: Int)

object LocalConfiguration {
    val current: WebConfiguration
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() {
            val size = LocalWindowInfo.current.containerSize
            val density = LocalDensity.current.density
            return WebConfiguration((size.width / density).toInt(), (size.height / density).toInt())
        }
}
