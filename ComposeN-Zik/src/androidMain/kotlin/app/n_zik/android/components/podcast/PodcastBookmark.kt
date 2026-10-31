package app.n_zik.android.components.podcast

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.core.database.Database
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber

internal enum class PodcastBookmarkOperation { Create, Remove, ExistingImport }

/** Editable imports and remote-library entries belong to their existing workflows. */
internal fun podcastBookmarkOperation(existing: Playlist?): PodcastBookmarkOperation = when {
    existing == null -> PodcastBookmarkOperation.Create
    !existing.isEditable && !existing.isYoutubePlaylist -> PodcastBookmarkOperation.Remove
    else -> PodcastBookmarkOperation.ExistingImport
}

/** Podcast browse IDs (including MPSP...) are not playlist IDs: keep them verbatim and local. */
internal fun podcastBookmarkPlaylist(browseId: String, title: String): Playlist =
    Playlist(name = title, browseId = browseId, isEditable = false, isYoutubePlaylist = false)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PodcastBookmarkAction(
    browseId: String,
    title: String,
    episodes: List<MediaItem>,
    onExistingImport: () -> Unit,
) {
    val existing by remember(browseId) { Database.playlistTable.findByBrowseId(browseId) }
        .collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    var busy by remember(browseId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    GlassToolbarRow(actionCount = 1) {
        HeaderIconButton(
            icon = if (existing != null) R.drawable.bookmark else R.drawable.bookmark_outline,
            color = if (existing != null) colorPalette().favoritesIcon else colorPalette().text,
            enabled = !busy,
            modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
            onClick = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            val operation = withContext(NzikDispatchers.DATA) {
                                var action = PodcastBookmarkOperation.Create
                                Database.transaction {
                                    val latest = playlistTable.findByBrowseId(browseId).first()
                                    action = podcastBookmarkOperation(latest)
                                    when (action) {
                                        PodcastBookmarkOperation.Create -> {
                                            val playlist = podcastBookmarkPlaylist(browseId, title)
                                            // mapIgnore intentionally skips an empty track list.
                                            if (episodes.isEmpty()) playlistTable.insert(playlist)
                                            else mapIgnore(playlist, *episodes.toTypedArray())
                                        }
                                        PodcastBookmarkOperation.Remove -> latest?.let(playlistTable::delete)
                                        PodcastBookmarkOperation.ExistingImport -> Unit
                                    }
                                }
                                action
                            }
                            if (operation == PodcastBookmarkOperation.ExistingImport) onExistingImport()
                            else Toaster.done()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Timber.tag("PodcastBookmark").e(error, "Unable to update local podcast bookmark")
                        } finally { busy = false }
                    }
                }
            },
            onLongClick = { Toaster.i(R.string.bookmark) },
        )
    }
}
