package app.n_zik.android.intents

import android.content.Context
import android.content.Intent

import app.n_zik.android.utils.DataStoreUtils

/** The saved markers also reject repeated delivery from Android's task record. */
internal fun Context.consumeRewindReminderIntent(
    intent: Intent?,
    onDeck: (Pair<Int, Int>) -> Unit,
    onPlaylist: (Long) -> Unit,
) {
    val deck = rewindDeckTargetFromIntent(
        intent,
        DataStoreUtils.getInt(this, DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED, 0),
    )
    if (deck != null) {
        onDeck(deck)
        DataStoreUtils.saveInt(
            this,
            DataStoreUtils.KEY_REWIND_DECK_LAST_CONSUMED,
            rewindDeckTargetMarker(deck.first, deck.second),
        )
    }

    val playlist = rewindPlaylistTargetFromIntent(
        intent,
        DataStoreUtils.getLong(this, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, 0L),
    )
    if (playlist != null) {
        onPlaylist(playlist)
        DataStoreUtils.saveLong(this, DataStoreUtils.KEY_REWIND_PLAYLIST_LAST_CONSUMED, playlist)
    }

    consumeRewindDeckExtras(intent)
    consumeRewindPlaylistExtras(intent)
}
