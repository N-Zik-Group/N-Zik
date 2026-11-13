package app.n_zik.android.components.menu

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.models.Song
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.Flow

/** Shares committed metadata between the visible menu and retained action callbacks. */
internal class EntityMenuSnapshot<T : Any, ID>(entry: T, val profile: String, private val idOf: (T) -> ID) {
    val id = idOf(entry)
    var value by mutableStateOf(entry)
        private set

    fun accept(row: T?, activeProfile: String) {
        if (activeProfile == profile && row != null && idOf(row) == id) value = row
    }
}

/** Closes a retained menu before it can attach to another profile's database. */
@Composable
internal fun ObserveMenuProfile(profile: String, onProfileChanged: () -> Unit): Boolean {
    val context = LocalContext.current
    val close by rememberUpdatedState(onProfileChanged)
    val preferences = context.profilePreferences
    DisposableEffect(profile, preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if ((key == null || key == activeProfileKey) && getActiveProfile(context) != profile) close()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    if (getActiveProfile(context) == profile) return true
    SideEffect { close() }
    return false
}

/** Observes one stable entity identity for the lifetime of an open menu. */
@Composable
internal fun <T : Any, ID> ObserveEntityMenuSnapshot(
    snapshot: EntityMenuSnapshot<T, ID>,
    query: (ID) -> Flow<T?>,
    onProfileChanged: () -> Unit
): Boolean {
    if (!ObserveMenuProfile(snapshot.profile, onProfileChanged)) return false
    val context = LocalContext.current
    // Read during composition so database emissions schedule the commit effect.
    val row = remember(snapshot) { query(snapshot.id) }
        .collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA).value
    SideEffect { snapshot.accept(row, getActiveProfile(context)) }
    return true
}

/** Updates metadata without replacing a provider's complete queue with a partial local cache. */
internal fun overlayMenuSongs(remote: List<Song>?, saved: List<Song>): List<Song>? {
    val byId = saved.associateBy(Song::id)
    return remote?.map { byId[it.id] ?: it }
}
