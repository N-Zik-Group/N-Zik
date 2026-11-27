package app.n_zik.android.core.rewind

import android.content.Context
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.components.ui.screens.rewind.RewindYearlyReminderWorker
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.utils.DataStoreUtils

internal fun updateRewindWorkPreference(context: Context, key: String, enabled: Boolean) {
    DataStoreUtils.saveBoolean(context, key, enabled)
    scheduleRewindWork(context, key)
}

/** Reconciles the active profile's work with the current scheduling preferences. */
@Synchronized
internal fun scheduleRewindWork(context: Context, changedKey: String = DataStoreUtils.KEY_REWIND_ENABLED) {
    val owner = ProfileWorkOwner.capture(context) ?: return
    when (changedKey) {
        DataStoreUtils.KEY_REWIND_ENABLED -> {
            RewindReminderWorker.schedule(context, owner)
            RewindYearlyReminderWorker.schedule(context, owner)
            RewindPlaylistSchedule.Monthly.schedule(context, owner)
            RewindPlaylistSchedule.Yearly.schedule(context, owner)
        }
        DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED ->
            RewindReminderWorker.schedule(context, owner)
        DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED ->
            RewindYearlyReminderWorker.schedule(context, owner)
        DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED ->
            RewindPlaylistSchedule.Monthly.schedule(context, owner)
        DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED ->
            RewindPlaylistSchedule.Yearly.schedule(context, owner)
    }
}
