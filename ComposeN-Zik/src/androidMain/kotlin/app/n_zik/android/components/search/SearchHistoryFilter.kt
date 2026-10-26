package app.n_zik.android.components.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Keeps saved queries matching the current input, in their existing recency order. */
fun filterSearchHistory(queries: List<String>, input: String): List<String> {
    val query = input.trim()
    return if (query.isEmpty()) queries else queries.filter { it.contains(query, ignoreCase = true) }
}

/** Retains the outgoing rows so a disappearing history can finish its shrink animation. */
@Composable
fun rememberSearchHistoryRows(matches: List<String>): List<String> {
    var rows by remember { mutableStateOf(matches) }
    if (matches.isNotEmpty()) rows = matches
    return rows
}
