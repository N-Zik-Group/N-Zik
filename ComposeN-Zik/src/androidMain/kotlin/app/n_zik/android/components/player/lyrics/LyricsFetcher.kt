package app.n_zik.android.components.player.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaMetadata
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import kotlinx.coroutines.CoroutineScope

// Dedup globals: last mediaId a fetch attempt was made for, per type. Written from the
// DATA dispatcher by LyricsFetchWorker (issue #606); thread-safety is guaranteed by the
//
// structured per-song chain (one writer at a time, serialized by the Room flow).
internal var globalLastKaraokeAttemptMediaId: String? = null
internal var globalLastSyncedAttemptMediaId: String? = null
internal var globalLastUnSyncedAttemptMediaId: String? = null

fun resetGlobalAttemptForType(type: LyricsType) {
    when (type) {
        LyricsType.Karaoke -> globalLastKaraokeAttemptMediaId = null
        LyricsType.Synced -> globalLastSyncedAttemptMediaId = null
        LyricsType.Unsynced -> globalLastUnSyncedAttemptMediaId = null
        LyricsType.Auto -> {
            globalLastKaraokeAttemptMediaId = null
            globalLastSyncedAttemptMediaId = null
            globalLastUnSyncedAttemptMediaId = null
        }
    }
}

/**
 * Type of the stored row "Fetch lyrics again" resets: the displayed row's type, else the mode's own
 * type. Null in Auto with nothing displayed: Auto is a display mode, not a stored row type, so there
 * is no row to reset.
 */
internal fun fetchAgainRowType(displayedType: String?, mode: LyricsType): String? =
    displayedType ?: mode.takeIf { it != LyricsType.Auto }?.name

@Composable
fun LyricsFetcher(
    mediaId: String,
    lyricsType: LyricsType,
    checkLyrics: Boolean,
    artistName: String?,
    title: String?,
    mediaMetadata: MediaMetadata,
    durationProvider: () -> Long,
    @Suppress("UNUSED_PARAMETER") coroutineScope: CoroutineScope, // Kept for API compatibility; the fetch chain runs under this effect's structured scope
    playerEnableLyricsPopupMessage: Boolean,
    onLyricsUpdated: (Lyrics?) -> Unit,
    onErrorUpdated: (Boolean) -> Unit,
    onCheckedLrcUpdated: (Boolean) -> Unit,
    onCheckedKugouUpdated: (Boolean) -> Unit,
    onCheckedInnertubeUpdated: (Boolean) -> Unit,
    onFetchingStateChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var previousLyricsType by remember { mutableStateOf(lyricsType) }
    var previousCheckLyrics by remember { mutableStateOf(checkLyrics) }
    LaunchedEffect(mediaId, lyricsType, checkLyrics, mediaMetadata.title, mediaMetadata.artist) {
        if (checkLyrics != previousCheckLyrics) {
            globalLastSyncedAttemptMediaId = null
            globalLastKaraokeAttemptMediaId = null
            globalLastUnSyncedAttemptMediaId = null
            previousCheckLyrics = checkLyrics
            onLyricsUpdated(null)
        }

        // Mode switch
        if (lyricsType != previousLyricsType) {
            globalLastSyncedAttemptMediaId = null
            globalLastKaraokeAttemptMediaId = null
            globalLastUnSyncedAttemptMediaId = null
            previousLyricsType = lyricsType
            onLyricsUpdated(null)
        }

        LyricsFetchWorker().fetch(
            context = context,
            mediaId = mediaId,
            lyricsType = lyricsType,
            artistName = artistName,
            title = title,
            mediaMetadata = mediaMetadata,
            durationProvider = durationProvider,
            playerEnableLyricsPopupMessage = playerEnableLyricsPopupMessage,
            onLyricsUpdated = onLyricsUpdated,
            onErrorUpdated = onErrorUpdated,
            onCheckedLrcUpdated = onCheckedLrcUpdated,
            onCheckedKugouUpdated = onCheckedKugouUpdated,
            onCheckedInnertubeUpdated = onCheckedInnertubeUpdated,
            onFetchingStateChanged = onFetchingStateChanged
        )
    }
}
