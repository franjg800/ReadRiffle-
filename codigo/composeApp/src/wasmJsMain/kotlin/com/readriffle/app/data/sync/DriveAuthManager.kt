package com.readriffle.app.data.sync

import com.readriffle.app.platform.SimplePrefs
import com.readriffle.app.platform.System
import com.readriffle.app.platform.appOrigin
import com.readriffle.app.platform.httpRequest
import com.readriffle.app.platform.jsConfiguredClientId
import com.readriffle.app.platform.jsOauthCapture
import com.readriffle.app.platform.jsOauthStart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Cuenta de Google conectada (solo el correo, para mostrarlo). */
class DriveAccount(val email: String?)

/**
 * Inicio de sesión con Google en la versión web, para leer y escribir en la carpeta "ReadRiffle"
 * de Drive (scope drive.file, el mismo que la app Android). Usa el flujo OAuth de Google para
 * webs: la página va a Google y vuelve con un token de acceso que dura una hora; cuando caduca se
 * renueva con otra ida y vuelta (sin preguntar nada si la sesión de Google sigue abierta).
 *
 * Necesita un "ID de cliente OAuth" de tipo "Aplicación web" del MISMO proyecto de Google Cloud
 * que la app Android, con la dirección de esta web en "Orígenes de JavaScript autorizados" y en
 * "URI de redireccionamiento autorizados".
 */
class DriveAuthManager {
    private val prefs = SimplePrefs("drive")
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val SCOPES = "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email"
    }

    /** Resultado de la última vuelta desde Google (token nuevo o error), si la hubo al arrancar. */
    private var redirectResult: RedirectResult? = null

    class RedirectResult(val ok: Boolean, val error: String?)

    var lastTokenError: String? = null
        private set

    /** ID de cliente: el de config.js si se configuró al publicar, o el pegado en Ajustes. */
    fun clientId(): String = prefs.getString("client_id", null)?.takeIf { it.isNotBlank() } ?: jsConfiguredClientId()

    fun setClientId(id: String) = prefs.putString("client_id", id.trim().ifBlank { null })

    /** Dirección de esta web: la que hay que registrar en Google Cloud. */
    fun webOrigin(): String = appOrigin()

    /** Se llama al arrancar: recoge el token si venimos de la pantalla de Google. */
    fun captureRedirect() {
        val raw = jsOauthCapture()
        if (raw.isBlank()) return
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val token = obj["token"]?.jsonPrimitive?.content.orEmpty()
        val expiresIn = obj["expiresIn"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0L
        val error = obj["error"]?.jsonPrimitive?.content.orEmpty()
        redirectResult = if (token.isNotBlank()) {
            prefs.putString("token", token)
            prefs.putLong("token_expiry", System.currentTimeMillis() + expiresIn * 1000L)
            prefs.putBoolean("connected", true)
            RedirectResult(true, null)
        } else {
            RedirectResult(false, describeError(error))
        }
    }

    fun consumeRedirectResult(): RedirectResult? = redirectResult.also { redirectResult = null }

    private fun describeError(code: String) = when (code) {
        "access_denied" -> "Has cancelado el permiso de Google."
        "interaction_required", "login_required", "consent_required" -> "Tienes que volver a entrar en tu cuenta de Google: pulsa Conectar."
        "redirect_uri_mismatch" -> "Google no reconoce esta web. Añade ${webOrigin()} en Google Cloud (orígenes y URI de redirección)."
        else -> "Google devolvió un error: $code"
    }

    fun lastSignedInAccount(): DriveAccount? =
        if (prefs.getBoolean("connected", false)) DriveAccount(prefs.getString("email", null)) else null

    fun hasValidToken(): Boolean =
        prefs.getString("token", null) != null && prefs.getLong("token_expiry", 0L) > System.currentTimeMillis() + 60_000L

    /** Va a Google para conectar la cuenta (o renovar el permiso si [silent]). La página se recarga al volver. */
    fun startSignIn(silent: Boolean = false): String? {
        val id = clientId()
        if (id.isBlank()) return "Falta el ID de cliente web de Google: pégalo en Ajustes › Sincronización."
        jsOauthStart(id, SCOPES, if (silent) "none" else "consent", prefs.getString("email", null))
        return null
    }

    /** Lee el correo de la cuenta conectada (para enseñarlo en Ajustes). */
    suspend fun refreshEmail(): String? {
        val token = getAccessToken() ?: return null
        val r = runCatching {
            httpRequest("GET", "https://www.googleapis.com/oauth2/v3/userinfo", mapOf("Authorization" to "Bearer $token"))
        }.getOrNull() ?: return null
        if (!r.ok) return null
        val email = runCatching { json.parseToJsonElement(r.text()).jsonObject["email"]?.jsonPrimitive?.content }.getOrNull()
        if (email != null) prefs.putString("email", email)
        return email
    }

    suspend fun signOut() {
        prefs.getString("token", null)?.let { token ->
            runCatching { httpRequest("POST", "https://oauth2.googleapis.com/revoke?token=$token") }
        }
        prefs.remove("token"); prefs.remove("token_expiry"); prefs.remove("email"); prefs.remove("connected")
    }

    /** Token de acceso válido, o null si no hay sesión o ha caducado (ver [lastTokenError]). */
    suspend fun getAccessToken(): String? {
        if (!prefs.getBoolean("connected", false)) return null
        if (hasValidToken()) { lastTokenError = null; return prefs.getString("token", null) }
        lastTokenError = "El permiso de Google ha caducado (dura una hora en la web). Pulsa Sincronizar para renovarlo."
        return null
    }
}
