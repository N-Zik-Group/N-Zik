package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.navigation.RESET_BROWSE_ALIAS_KEY_PREFIX
import app.n_zik.android.core.database.trackBrowseIdOrigin
import app.n_zik.android.core.database.trackBrowseIdOriginKey
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

internal class BrowseIdSave {
    var saving by mutableStateOf(false)
        private set

    fun submit(dialog: Dialog, menuState: MenuState?, update: suspend () -> Boolean) {
        if (saving) return
        val context = appContext()
        val owner = ProfileWorkOwner.capture(context)
        if (owner == null) {
            Toaster.e(R.string.metadata_save_failed)
            return
        }
        val menuKey = menuState?.transitionKey
        saving = true
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            try {
                if (!ProfileWorkOwner.isBound(context, owner)) return@launch
                val saved = update()
                if (!ProfileWorkOwner.isBound(context, owner)) return@launch
                if (saved) {
                    Toaster.done()
                    if (dialog.isActive) {
                        dialog.hideDialog()
                        if (menuKey != null) menuState?.takeIf { it.isCurrentMenu(menuKey) }?.hide()
                    }
                } else {
                    Toaster.e(R.string.metadata_save_failed)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("BrowseIdSave").e(error, "Unable to save browse ID")
                if (ProfileWorkOwner.isBound(context, owner)) Toaster.e(R.string.metadata_save_failed)
            } finally {
                saving = false
            }
        }
    }
}

internal suspend fun saveEntityBrowseId(
    oldId: String,
    newId: String,
    reset: Boolean = false,
    update: suspend () -> Boolean,
): Boolean {
    val preferences = appContext().preferences
    val sourceKey = "old_browse_id_$oldId"
    val targetKey = "old_browse_id_$newId"
    val originalId = preferences.getString(sourceKey, null) ?: oldId
    if (!update()) return false
    if (oldId == newId) return true

    if (reset) {
        preferences.edit()
            .remove(sourceKey)
            .putString("$RESET_BROWSE_ALIAS_KEY_PREFIX$oldId", newId)
            .apply()
    } else if (!preferences.contains(targetKey)) {
        preferences.edit().putString(targetKey, originalId).apply()
    }
    return true
}

internal suspend fun saveTrackBrowseId(
    songId: String,
    oldId: String?,
    newId: String,
    reset: Boolean,
    update: suspend () -> Boolean,
): Boolean {
    val preferences = appContext().preferences
    val previousKey = oldId?.let { trackBrowseIdOriginKey(songId, it) }
    val original = oldId?.let { preferences.trackBrowseIdOrigin(songId, it) ?: it }
    if (!update()) return false
    if (oldId == newId) return true

    val editor = preferences.edit()
    if (previousKey != null) editor.remove(previousKey)
    if (!reset && original != null) editor.putString(trackBrowseIdOriginKey(songId, newId), original)
    editor.apply()
    return true
}
