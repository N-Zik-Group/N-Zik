package app.n_zik.android.components.settings.appearance.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val submissionDraftShapes by lazy {
    (1..ThemeAppearanceV3).associateWith { version ->
        Json.parseToJsonElement(
            encodeThemeAppearance(emptyMap<String, Any>(), emptyMap<String, Any>(), schemaVersion = version)
        ) as JsonObject
    }
}

/** Resumes local editor drafts without relaxing the external theme document parser. */
internal fun compatibleThemeSubmissionDraftAppearance(source: String): String? {
    if (runCatching { parseThemeAppearance(source) }.isSuccess) return source
    return runCatching {
        val root = Json.parseToJsonElement(source) as? JsonObject ?: error("Invalid draft appearance")
        val version = requireNotNull(themeAppearanceSchema(root))
        require(version < ThemeAppearanceV3)
        val shape = requireNotNull(submissionDraftShapes[version])
        val filtered = retainDraftFields(root, shape).toString()
        parseThemeAppearance(filtered)
        filtered
    }.getOrNull()
}

internal fun submissionDraftMinimumVersionInput(input: String): String =
    input.trim().toIntOrNull()?.takeIf { it in 1 until FirstThemeStoreVersionCode }
        ?.let { FirstThemeStoreVersionCode.toString() } ?: input

private fun retainDraftFields(source: JsonObject, shape: JsonObject): JsonObject =
    JsonObject(source.filterKeys { it in shape }.mapValues { (key, value) ->
        val group = shape[key]
        if (value is JsonObject && group is JsonObject) retainDraftFields(value, group) else value
    })
