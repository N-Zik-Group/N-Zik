package app.n_zik.android.components.dialog.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import app.n_zik.android.components.settings.appearance.preview.PortableControlList
import app.n_zik.android.utils.preferences

internal val homeToolbarLockedIds = setOf("sort", "sync", "position_lock")

internal data class HomeToolbarConfiguration(
    val order: List<String>,
    val visibility: Map<String, Boolean>,
) {
    val visibleIds get() = order.filter { visibility.getValue(it) }
}

internal fun PortableControlList.readHomeToolbar(prefs: SharedPreferences): HomeToolbarConfiguration {
    val keys = prefs.all
    val saved = DockToolbarPreferences.parseOrder(keys[orderKey] as? String ?: "").filter { it in ids }
    val order = saved + ids.filterNot { it in saved }
    val visibility = ids.associateWith { id ->
        val explicit = keys[visibilityPrefix + id + visibilitySuffix] as? Boolean
        // Older editors omitted disabled buttons from the saved list.
        id in homeToolbarLockedIds || (explicit ?: (saved.isEmpty() || id in saved))
    }
    return HomeToolbarConfiguration(order, visibility)
}

@Composable
internal fun PortableControlList.visibleHomeToolbarIds(): List<String> {
    val prefs = LocalContext.current.preferences
    val visibleIds = remember(this, prefs) { mutableStateOf(readHomeToolbar(prefs).visibleIds) }
    DisposableEffect(this, prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { source, key ->
            if (key == null || key == orderKey || key.startsWith(visibilityPrefix)) {
                visibleIds.value = readHomeToolbar(source).visibleIds
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        visibleIds.value = readHomeToolbar(prefs).visibleIds
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return visibleIds.value
}
