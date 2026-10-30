package app.n_zik.android.components.navigation.dock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/** Stable icon resource names identify tabs independently of localized text and resource integers. */
data class DockToolbarTab(val id: String, val title: String, val iconRes: Int)

/** Metadata comes from the actual navigation registrations; no route list is duplicated. */
object DockToolbarCatalogue {
    var entries: List<DockToolbarTab> by mutableStateOf(emptyList())
        private set

    fun register(tabs: List<DockToolbarTab>) {
        val merged = entries.associateByTo(linkedMapOf()) { it.id }
        tabs.forEach { merged[it.id] = it }
        val next = merged.values.toList()
        if (next != entries) entries = next
    }
}

/** Dock-only ordering; native navigation preferences and compact side actions are untouched. */
object DockToolbarPreferences {
    const val orderKey = "floatingDockToolbarOrder"

    fun parseOrder(serialized: String): List<String> = runCatching {
        val array = JSONArray(serialized)
        List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }.distinct()
    }.getOrDefault(emptyList())

    fun orderedIds(ids: List<String>, serialized: String): List<String> {
        val available = ids.distinct()
        val selected = parseOrder(serialized).filter { it in available }
        return selected + available.filterNot { it in selected }
    }

    fun serializeOrder(ids: List<String>): String = JSONArray(ids.distinct()).toString()
}
