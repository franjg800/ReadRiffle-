package com.readriffle.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.readriffle.app.platform.httpRequest
import kotlin.io.encoding.Base64

/** Error al generar la imagen -- [message] ya viene en un lenguaje pensado para mostrarse tal cual al usuario. */
class GeminiImageException(message: String) : Exception(message)

/**
 * Cliente mínimo para la API de generación de imágenes de Gemini -- pedido explícito del
 * usuario: mandar la descripción de un personaje o paisaje del libro y recibir una imagen de
 * vuelta. Es la ÚNICA llamada de red de toda la app (que, fuera de esto, sigue siendo 100% local).
 *
 * Se usa `HttpURLConnection` + `kotlinx.serialization` en vez de añadir una librería de red nueva
 * (Retrofit/OkHttp/Ktor): es una única llamada sencilla y el proyecto no tenía ninguna dependencia
 * de red hasta ahora.
 *
 * La respuesta se analiza de forma defensiva, probando varias formas posibles en las que puede
 * venir la imagen (la API de Gemini ha ido cambiando de forma de endpoint a endpoint) en vez de
 * asumir una única estructura exacta.
 */
class GeminiImageClient {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateImage(apiKey: String, prompt: String): ByteArray = withContext(Dispatchers.Default) {
        if (apiKey.isBlank()) {
            throw GeminiImageException("Falta la API key de Gemini. Añádela en Ajustes.")
        }

        // Prefijo de estilo fijo, para que la imagen salga como una ilustración cuidada del
        // libro (no una foto ni un cómic) y sin texto superpuesto, en vez de depender de que el
        // usuario tenga que describir el estilo cada vez.
        val styledPrompt =
            "Ilustración digital de estilo pintura editorial, atmosférica y detallada, sin ningún " +
                "texto ni letras en la imagen, de la siguiente descripción de un libro: $prompt"

        val requestBody = buildJsonObject {
            put("model", "gemini-nano-banana-2.1")
            put("input", styledPrompt)
            put("response_format", buildJsonObject { put("type", "image") })
        }

        val response = runCatching {
            httpRequest(
                "POST", "https://generativelanguage.googleapis.com/v1beta/interactions",
                mapOf("Content-Type" to "application/json", "x-goog-api-key" to apiKey),
                body = json.encodeToString(JsonObject.serializer(), requestBody)
            )
        }.getOrElse { throw GeminiImageException("No se pudo conectar con Gemini: ${it.message}") }
        val responseCode = response.code
        val rawResponse = response.text()

        if (responseCode !in 200..299) {
            throw GeminiImageException("Gemini respondió con un error ($responseCode): ${rawResponse.take(300)}")
        }

        val root = runCatching { json.parseToJsonElement(rawResponse).jsonObject }.getOrElse {
            throw GeminiImageException("La respuesta de Gemini no se pudo interpretar.")
        }

        val base64Data = findImageBase64(root)
            ?: throw GeminiImageException("Gemini no devolvió ninguna imagen. Prueba con otra descripción.")

        runCatching { Base64.Default.decode(base64Data) }.getOrElse {
            throw GeminiImageException("La imagen recibida no se pudo decodificar.")
        }
    }

    /**
     * Busca el base64 de la imagen en varias formas posibles de respuesta, de la más a la menos
     * probable según la documentación actual de la API:
     * 1) `output_image.data` (endpoint de interacciones).
     * 2) `steps[].model_output.content[]` con un bloque de tipo imagen.
     * 3) `candidates[].content.parts[].inlineData.data` (forma clásica de `generateContent`,
     *    por si la cuenta del usuario sigue dando esta forma de respuesta).
     */
    private fun findImageBase64(root: JsonObject): String? {
        root["output_image"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull()?.let { return it }

        root["steps"]?.jsonArray?.forEach { step ->
            val content = step.jsonObject["model_output"]?.jsonObject?.get("content")?.jsonArray
            content?.forEach { block ->
                block.jsonObject["data"]?.jsonPrimitive?.contentOrNull()?.let { return it }
            }
        }

        root["candidates"]?.jsonArray?.forEach { candidate ->
            val parts = candidate.jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray
            parts?.forEach { part ->
                val inline = part.jsonObject["inlineData"]?.jsonObject ?: part.jsonObject["inline_data"]?.jsonObject
                inline?.get("data")?.jsonPrimitive?.contentOrNull()?.let { return it }
            }
        }

        return null
    }

    private fun JsonPrimitive.contentOrNull(): String? = if (this.isString) this.content else null
}
