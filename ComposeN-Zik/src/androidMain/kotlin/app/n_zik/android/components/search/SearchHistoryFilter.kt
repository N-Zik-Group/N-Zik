package app.n_zik.android.components.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.Normalizer
import java.util.Locale

private val searchOptionalAccents = Regex("([\\p{IsLatin}\\p{IsGreek}])\\p{M}+")
private val searchCyrillicYo = Regex("([Ее])\\u0308")
private val searchApostrophes = Regex("[‘’ʼ]")
private val searchWhitespace = Regex("[\\s\\p{Z}]+")

internal fun normalizeSearchText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(searchOptionalAccents, "$1")
    .replace(searchCyrillicYo, "$1")
    .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
    .lowercase(Locale.ROOT)
    .replace('ı', 'i')
    .replace(searchApostrophes, "'")
    .replace(searchWhitespace, " ")
    .trim()

/** Keeps saved queries matching the current input, in their existing recency order. */
fun filterSearchHistory(queries: List<String>, input: String): List<String> =
    filterSearchHistory(queries, input, ::normalizeSearchText)

internal fun filterSearchHistory(queries: List<String>, input: String, normalize: (String) -> String): List<String> {
    val query = normalize(input)
    return if (query.isEmpty()) queries else queries.filter { normalize(it).contains(query) }
}

/** Retains the outgoing rows so a disappearing history can finish its shrink animation. */
@Composable
fun rememberSearchHistoryRows(matches: List<String>): List<String> {
    var rows by remember { mutableStateOf(matches) }
    if (matches.isNotEmpty()) rows = matches
    return rows
}
