package app.n_zik.android.components.playlist

import app.it.fast4x.rimusic.enums.QueueSwipeAction
import app.it.fast4x.rimusic.models.Playlist
import app.n_zik.android.core.database.canEditPlaylistMembership

/** Deleting the saved source is distinct from removing its selected songs. */
fun canOfferPlaylistDeleteAction(playlist: Playlist?, hasSelectedSongs: Boolean): Boolean =
    !hasSelectedSongs || playlist?.let(::canEditPlaylistMembership) == true

/** Removes the destructive swipe itself, including its icon and animation trigger. */
fun playlistMembershipSwipeAction(playlist: Playlist?, requested: QueueSwipeAction): QueueSwipeAction =
    if (requested == QueueSwipeAction.RemoveFromQueue &&
        playlist?.let(::canEditPlaylistMembership) != true) QueueSwipeAction.NoAction else requested
