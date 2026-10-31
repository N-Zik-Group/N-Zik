package app.n_zik.android.core.backup

import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.core.migration.RemovedSettingsMigration
import org.json.JSONArray

/** Separate typed CSV record: its key can also exist in normal settings without collision. */
internal object SettingsPreferencesBridge {
    const val GLASS_PREFERENCES = "nzik_square_glass_v2"
    private const val GLASS_RECORD_TYPE = "NzikGlassPreferencesV2"

    fun export(context: Context, plain: SharedPreferences): MutableList<Triple<String, String, Any>> {
        val entries = plain.all.mapNotNull { (key, value) ->
            if (RemovedSettingsMigration.isRemovedKey(key)) return@mapNotNull null
            value?.let { Triple(it::class.simpleName ?: "null", key, it) }
        }.toMutableList()
        val snapshot = JSONArray()
        context.getSharedPreferences(GLASS_PREFERENCES, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (value != null) snapshot.put(JSONArray().put(value::class.simpleName).put(key).put(value))
        }
        // Carry an empty snapshot too, so a fresh/default store restores without stale overrides.
        entries.add(Triple(GLASS_RECORD_TYPE, GLASS_PREFERENCES, snapshot.toString()))
        return entries
    }

    /** Validates the whole namespace before staging replacement; old CSVs do not touch it. */
    fun stageGlass(context: Context, rows: List<Triple<String, String, String>>): SharedPreferences.Editor? {
        val records = rows.filter { isGlassRecord(it.first) }
        if (records.isEmpty()) return null
        require(records.size == 1 && records.single().second == GLASS_PREFERENCES) { "Invalid Glass settings record" }
        val snapshot = JSONArray(records.single().third)
        val values = (0 until snapshot.length()).map { index ->
            val entry = snapshot.getJSONArray(index)
            require(entry.length() == 3) { "Invalid Glass setting" }
            val type = entry.getString(0)
            val key = entry.getString(1)
            val value = entry.getString(2)
            val parsed: Any = when (type.lowercase()) {
                "string" -> value
                "int", "integer" -> value.toInt()
                "long" -> value.toLong()
                "float" -> value.toFloat().also { require(it.isFinite()) }
                "boolean" -> value.toBooleanStrict()
                else -> error("Unsupported Glass setting type")
            }
            key to parsed
        }
        return context.getSharedPreferences(GLASS_PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
    }

    fun isGlassRecord(type: String): Boolean = type == GLASS_RECORD_TYPE
}
