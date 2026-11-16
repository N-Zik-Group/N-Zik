package app.n_zik.android.components.settings.appearance.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val submissionDraftShape by lazy {
    Json.parseToJsonElement(defaultThemeAppearanceDocument()) as JsonObject
}

/** Resumes local editor drafts without relaxing the external theme document parser. */
internal fun compatibleThemeSubmissionDraftAppearance(source: String): String? {
    if (runCatching { parseThemeV1(source) }.isSuccess) return source
    return runCatching {
        val root = Json.parseToJsonElement(source) as? JsonObject ?: error("Invalid draft appearance")
        val filtered = retainDraftFields(root, submissionDraftShape).toString()
        parseThemeV1(filtered)
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
