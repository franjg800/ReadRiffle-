package com.readriffle.app.data.sync

import com.readriffle.app.platform.SimplePrefs
import com.readriffle.app.platform.System
import com.readriffle.app.platform.appOrigin
import com.readriffle.app.platform.contactEmail
import com.readriffle.app.platform.isOnline
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
            val msg = describeError(error)
            // Si fallaba una renovación silenciosa no se molesta con un aviso (se verá en Ajustes).
            if (!prefs.getBoolean("last_silent", false)) startupError = msg
            RedirectResult(false, msg)
        }
    }

    /**
     * Error de la vuelta desde Google cuando el usuario pidió conectar (p. ej. su cuenta no está
     * dada de alta): la app vuelve a enseñar el aviso de sincronización con este mensaje.
     */
    var startupError: String? = null
        private set

    fun consumeRedirectResult(): RedirectResult? = redirectResult.also { redirectResult = null }

    private fun contact(): String = contactEmail().let { if (it.isBlank()) "contacta con el desarrollador" else "escribe a $it" }

    private fun describeError(code: String) = when (code) {
        "access_denied" -> "Google no ha dado permiso. Si lo cancelaste, vuelve a intentarlo. Si Google te dijo que no tienes acceso o que la app no está verificada, tu cuenta aún no está dada de alta: ${contact()} para que te añada."
        "interaction_required", "login_required", "consent_required" -> "Tienes que volver a entrar en tu cuenta de Google: pulsa Conectar."
        "redirect_uri_mismatch" -> "Google no reconoce esta web (${webOrigin()}). ${contact().replaceFirstChar { it.uppercase() }}."
        "invalid_client", "unauthorized_client" -> "La sincronización de esta web está mal configurada. ${contact().replaceFirstChar { it.uppercase() }}."
        "org_internal", "admin_policy_enforced" -> "Tu cuenta de Google no puede usar esta app. ${contact().replaceFirstChar { it.uppercase() }}."
        else -> "Google devolvió un error ($code). ${contact().replaceFirstChar { it.uppercase() }}."
    }

    /** Texto para cuando la web aún no tiene ID de cliente de Google. */
    fun notConfiguredMessage(): String = "La sincronización con Google Drive todavía no está activada en esta web. ${contact().replaceFirstChar { it.uppercase() }}."

    /** ¿Se recuerda una cuenta conectada pero su permiso de una hora ha caducado? */
    fun needsRenewal(): Boolean = prefs.getBoolean("connected", false) && !hasValidToken()

    /**
     * Al abrir la web: si hay cuenta conectada pero el permiso caducó, se renueva solo con una ida
     * y vuelta a Google (sin preguntar nada). Como mucho una vez cada 20 minutos, y solo con
     * conexión, para no quedarse en bucle si Google pide volver a entrar.
     * Devuelve true si la página va a salir hacia Google.
     */
    fun renewOnStartupIfNeeded(): Boolean {
        if (!needsRenewal() || !isOnline() || clientId().isBlank()) return false
        val last = prefs.getLong("silent_at", 0L)
        if (System.currentTimeMillis() - last < 20 * 60_000L) return false
        prefs.putLong("silent_at", System.currentTimeMillis())
        return startSignIn(silent = true) == null
    }

    /** Primera visita: ¿se ha enseñado ya el aviso de sincronización? */
    fun welcomeShown(): Boolean = prefs.getBoolean("welcome_shown", false)
    fun markWelcomeShown() = prefs.putBoolean("welcome_shown", true)

    fun lastSignedInAccount(): DriveAccount? =
        if (prefs.getBoolean("connected", false)) DriveAccount(prefs.getString("email", null)) else null

    fun hasValidToken(): Boolean =
        prefs.getString("token", null) != null && prefs.getLong("token_expiry", 0L) > System.currentTimeMillis() + 60_000L

    /** Va a Google para conectar la cuenta (o renovar el permiso si [silent]). La página se recarga al volver. */
    fun startSignIn(silent: Boolean = false): String? {
        val id = clientId()
        if (id.isBlank()) return notConfiguredMessage()
        prefs.putBoolean("last_silent", silent)
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
