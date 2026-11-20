package androidx.datastore.preferences.core

import com.readriffle.app.platform.jsLsGet
import com.readriffle.app.platform.jsLsSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** DataStore Preferences de Android sobre localStorage: misma forma de uso, guardado al instante. */
open class Preferences internal constructor(internal val values: MutableMap<String, Any>) {
    class Key<T>(val name: String, internal val type: Char)

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Key<T>): T? = values[key.name] as? T
    operator fun <T> contains(key: Key<T>): Boolean = key.name in values
}

class MutablePreferences internal constructor(values: MutableMap<String, Any>) : Preferences(values) {
    operator fun <T : Any> set(key: Key<T>, value: T) { values[key.name] = value }
    fun <T> remove(key: Key<T>) { values.remove(key.name) }
}

fun stringPreferencesKey(name: String) = Preferences.Key<String>(name, 's')
fun booleanPreferencesKey(name: String) = Preferences.Key<Boolean>(name, 'b')
fun intPreferencesKey(name: String) = Preferences.Key<Int>(name, 'i')
fun longPreferencesKey(name: String) = Preferences.Key<Long>(name, 'l')
fun floatPreferencesKey(name: String) = Preferences.Key<Float>(name, 'f')

class WebDataStore(private val name: String) {
    private val storageKey = "br_ds_$name"
    private val state = MutableStateFlow(Preferences(load()))
    val data: StateFlow<Preferences> get() = state

    private fun load(): MutableMap<String, Any> {
        val raw = jsLsGet(storageKey) ?: return mutableMapOf()
        val obj = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return mutableMapOf()
        val out = mutableMapOf<String, Any>()
        for ((k, v) in obj) {
            val arr = runCatching { v.jsonArray }.getOrNull() ?: continue
            val t = arr[0].jsonPrimitive.content
            val p = arr[1].jsonPrimitive
            val value: Any? = when (t) {
                "s" -> p.content
                "b" -> p.content.toBooleanStrictOrNull()
                "i" -> p.content.toIntOrNull()
                "l" -> p.content.toLongOrNull()
                "f" -> p.content.toFloatOrNull()
                else -> null
            }
            if (value != null) out[k] = value
        }
        return out
    }

    private fun save(values: Map<String, Any>) {
        val obj = JsonObject(values.mapValues { (_, v) ->
            val t = when (v) { is String -> "s"; is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; else -> "s" }
            val p = when (v) { is String -> JsonPrimitive(v); is Boolean -> JsonPrimitive(v); is Number -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
            JsonArray(listOf(JsonPrimitive(t), p))
        })
        jsLsSet(storageKey, obj.toString())
    }

    suspend fun edit(transform: suspend (MutablePreferences) -> Unit): Preferences {
        val copy = MutablePreferences(state.value.values.toMutableMap())
        transform(copy)
        save(copy.values)
        val result = Preferences(copy.values.toMutableMap())
        state.value = result
        return result
    }
}

suspend fun WebDataStore.edit(transform: suspend (MutablePreferences) -> Unit): Preferences = this.edit(transform)
