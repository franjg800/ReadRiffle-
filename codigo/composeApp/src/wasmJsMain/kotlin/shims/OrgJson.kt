package org.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Lo justo de org.json (Android) sobre kotlinx.serialization, para el buscador de portadas. */
class JSONObject(private val obj: JsonObject) {
    constructor(text: String) : this(Json.parseToJsonElement(text) as? JsonObject ?: JsonObject(emptyMap()))

    fun has(key: String) = obj.containsKey(key) && obj[key] !is JsonNull
    fun optJSONArray(key: String): JSONArray? = (obj[key] as? JsonArray)?.let { JSONArray(it) }
    fun optJSONObject(key: String): JSONObject? = (obj[key] as? JsonObject)?.let { JSONObject(it) }
    fun getJSONObject(key: String): JSONObject = optJSONObject(key) ?: error("No es un objeto: $key")
    fun optString(key: String): String = (obj[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""
    fun getLong(key: String): Long = (obj[key] as? JsonPrimitive)?.longOrNull ?: (obj[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: 0L
    fun keys(): Iterator<String> = obj.keys.iterator()
    fun optInt(key: String, def: Int = 0): Int = (obj[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: def
    fun optDouble(key: String, def: Double = 0.0): Double = (obj[key] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: def
}

class JSONArray(private val arr: JsonArray) {
    fun length() = arr.size
    fun getJSONObject(i: Int): JSONObject = JSONObject(arr[i] as? JsonObject ?: JsonObject(emptyMap()))
    fun optLong(i: Int): Long = (arr.getOrNull(i) as? JsonPrimitive)?.longOrNull ?: 0L
    fun optString(i: Int): String = (arr.getOrNull(i) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""
    @Suppress("unused") fun get(i: Int): JsonElement = arr[i]
}
