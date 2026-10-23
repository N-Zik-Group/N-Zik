package app.n_zik.android.utils.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.excludeMediaItems
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext

/**
 * Issue #606 H8 (paired with M10 at the call sites that also build the list from
 * `Song.asMediaItem`) -- legacy `Player.addNext`/`Player.enqueue`
 * (`app.it.fast4x.rimusic.utils.Player.kt`) filter the incoming list through `excludeMediaItems`
 * whenever the caller passes a non-null [Context] -- up to 3 `runBlocking` DB reads
 * (disliked songs/artists/albums) on whatever thread the call runs on, Main in every case this
 * lot fixes. `Player.kt` is legacy and out of scope for editing (AD-4 tier 1: fixable only from
 * the caller, not the callee), so these two suspend extension functions are the caller-side fix,
 * reused by every `app.n_zik.android.*` site that adds/enqueues a filtered list: run the
 * filtering on [NzikDispatchers.DATA], then call the legacy function with `context = null` so
 * its own synchronous filtering branch is skipped -- the list is already filtered (also avoids
 * filtering twice).
 *
 * The actual queue mutation (`player.*`, ExoPlayer) happens exactly once, on whatever context
 * launched the enclosing coroutine: these functions are dispatcher-agnostic and don't guarantee
 * Main -- it's Main only because every current caller launches from Main.
 */
@UnstableApi
suspend fun Player.addNextOffMain(mediaItems: List<MediaItem>, context: Context) {
    val filteredMediaItems = withContext(NzikDispatchers.DATA) { excludeMediaItems(mediaItems, context) }
    addNext(filteredMediaItems, context = null)
}

@UnstableApi
suspend fun Player.enqueueOffMain(mediaItems: List<MediaItem>, context: Context) {
    val filteredMediaItems = withContext(NzikDispatchers.DATA) { excludeMediaItems(mediaItems, context) }
    enqueue(filteredMediaItems, context = null)
}
