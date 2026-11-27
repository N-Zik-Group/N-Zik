package app.n_zik.android.components.playlist

import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.enums.QueueSwipeAction
import app.n_zik.android.models.Playlist

/** Deleting the saved source is distinct from removing its selected songs. */
fun canOfferPlaylistDeleteAction(playlist: Playlist?, hasSelectedSongs: Boolean): Boolean =
    !hasSelectedSongs || playlist?.let(::canEditPlaylistMembership) == true

/** Returns whether the current playlist view allows membership edits. */
internal fun canEditPlaylistMembershipInView(playlist: Playlist?, forceOnDevice: Boolean): Boolean =
    !forceOnDevice && playlist?.let(::canEditPlaylistMembership) == true

/** Removes the destructive swipe itself, including its icon and animation trigger. */
internal fun playlistMembershipSwipeAction(membershipEditable: Boolean, requested: QueueSwipeAction): QueueSwipeAction =
    if (requested == QueueSwipeAction.RemoveFromQueue && !membershipEditable) QueueSwipeAction.NoAction else requested
