package app.n_zik.android.components.search

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey

/**
 * Per-screen search history: each screen keeps its own list of past queries
 * (JSON in SharedPreferences, newest first) instead of the shared SearchQuery
 * table, so the histories never mix between screens. The prefs instance is
 * per-profile, matching the rest of the app's history data.
 */
object SearchHistoryStore {
    const val PAGE_HISTORY = "history"
    const val PAGE_LOCAL = "local"
    const val PAGE_ONLINE = "online"
    const val PAGE_SETTINGS_GLOBAL = "settings_global"
    const val PAGE_SETTINGS_GENERAL = "settings_general"
    const val PAGE_SETTINGS_APPEARANCE = "settings_appearance"
    const val PAGE_SETTINGS_UI = "settings_ui"
    const val PAGE_SETTINGS_DATA = "settings_data"
    const val PAGE_SETTINGS_NETWORK = "settings_network"
    const val PAGE_SETTINGS_OTHER = "settings_other"
    const val PAGE_SETTINGS_ACCOUNTS = "settings_accounts"
    const val PAGE_SETTINGS_AI = "settings_ai"

    val pages = listOf(
        PAGE_HISTORY, PAGE_LOCAL, PAGE_ONLINE, PAGE_SETTINGS_GLOBAL,
        PAGE_SETTINGS_GENERAL, PAGE_SETTINGS_UI, PAGE_SETTINGS_APPEARANCE,
        PAGE_SETTINGS_AI, PAGE_SETTINGS_DATA, PAGE_SETTINGS_ACCOUNTS,
        PAGE_SETTINGS_NETWORK, PAGE_SETTINGS_OTHER,
    )

    /** Per-profile page preference, independent from the global pause switch. */
    fun enabledKey(page: String): String = "searchHistoryEnabled_$page"

    /** Global pause suspends every page without overwriting its individual choice. */
    fun isEnabled(prefs: SharedPreferences, page: String): Boolean =
        !prefs.getBoolean(pauseSearchHistoryKey, false) && prefs.getBoolean(enabledKey(page), true)

    /** Counts stored queries, including pages whose history is currently disabled. */
    fun countAll(prefs: SharedPreferences): Int = pages.sumOf { all(prefs, it).size }

    /** Clears search data only; page enablement and other preferences are preserved. */
    fun clearAll(prefs: SharedPreferences) {
        val editor = prefs.edit()
        pages.forEach { editor.remove(KEY_PREFIX + it) }
        editor.apply()
    }

    /** Maximum number of stored entries per screen. */
    const val CAP = 20

    private const val KEY_PREFIX = "searchHistory_"

    private val json = Json { ignoreUnknownKeys = true }

    /** All stored queries for a page, newest first. */
    fun all(prefs: SharedPreferences, page: String): List<String> {
        val raw = prefs.getString(KEY_PREFIX + page, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }
            .onFailure { Timber.tag(TAG).e(it, "Failed to decode search history for page $page") }
            .getOrDefault(emptyList())
    }

    /** Records a query: trims, dedupes (moved to the front), caps at [CAP]. */
    fun record(prefs: SharedPreferences, page: String, query: String): List<String> {
        if (!isEnabled(prefs, page)) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all(prefs, page)
        val updated = (listOf(trimmed) + all(prefs, page).filter { it != trimmed }).take(CAP)
        save(prefs, page, updated)
        return updated
    }

    /** Deletes one entry; returns the remaining list. */
    fun delete(prefs: SharedPreferences, page: String, query: String): List<String> {
        val updated = all(prefs, page).filter { it != query }
        save(prefs, page, updated)
        return updated
    }

    /** Clears the page's history; returns the (empty) remaining list. */
    fun deleteAll(prefs: SharedPreferences, page: String): List<String> {
        save(prefs, page, emptyList())
        return emptyList()
    }

    private fun save(prefs: SharedPreferences, page: String, queries: List<String>) {
        runCatching {
            prefs.edit().putString(KEY_PREFIX + page, json.encodeToString(queries)).apply()
        }.onFailure { Timber.tag(TAG).e(it, "Failed to save search history for page $page") }
    }
}

private const val TAG = "SearchHistoryStore"

/** Observes history edits, page enablement, and global pause for the active profile. */
@Composable
fun rememberSearchHistory(page: String, isPaused: Boolean = false): MutableState<List<String>> {
    val prefs = LocalContext.current.preferences
    val state = remember(prefs, page) { mutableStateOf(emptyList<String>()) }
    DisposableEffect(prefs, page, isPaused) {
        fun refresh() {
            state.value = if (!isPaused && SearchHistoryStore.isEnabled(prefs, page))
                SearchHistoryStore.all(prefs, page) else emptyList()
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "searchHistory_$page" ||
                key == SearchHistoryStore.enabledKey(page) || key == pauseSearchHistoryKey
            ) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        refresh()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/** Reactive total for the existing clear-history action. */
@Composable
fun rememberSearchHistoryCount(): State<Int> {
    val prefs = LocalContext.current.preferences
    val count = remember(prefs) { mutableStateOf(SearchHistoryStore.countAll(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("searchHistory_")) {
                count.value = SearchHistoryStore.countAll(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return count
}
