package app.n_zik.android.components.player.lyrics

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import app.n_zik.android.components.glass.GlassTextField as TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.models.Lyrics
import app.n_zik.android.R
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.core.database.Database
import app.n_zik.android.ui.components.LocalMenuBack
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.themed.IconButton
import androidx.compose.material3.Icon
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.lrclib.LrcLib
import app.n_zik.lrclib.models.Track
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds
import app.n_zik.android.enums.lyrics.LyricsType

private val trackSelectorTextFieldColors: TextFieldColors
    @Composable
    get() = TextFieldDefaults.colors(
        unfocusedTextColor = colorPalette().text,
        focusedTextColor = colorPalette().text,
        unfocusedIndicatorColor = colorPalette().text,
        focusedIndicatorColor = colorPalette().text,
        focusedContainerColor = surfaceColor(colorPalette().background1),
        unfocusedContainerColor = colorPalette().background0
    )

@Composable
private fun SettingIcon(@DrawableRes icon: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .iconBadgeBackground(
                color = colorPalette().accent.copy(alpha = 0.1f),
                shape = uiRoundnessShape()
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            tint = colorPalette().accent,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun pickedRow(track: Track, lyricsType: LyricsType): Pair<LyricsType, String>? =
    when (lyricsType) {
        LyricsType.Unsynced -> {
            val plain = track.plainLyrics.orEmpty().ifBlank { LrcLib.Lyrics(track.syncedLyrics.orEmpty()).plainText }
            plain.takeIf { it.isNotBlank() }?.let { LyricsType.Unsynced to it }
        }
        else -> track.syncedLyrics.orEmpty().takeIf { it.isNotBlank() }?.let { LyricsType.Synced to it }
    }

/** Whether picking [track] in [lyricsType] mode would store something the mode can display. */
internal fun canPick(track: Track, lyricsType: LyricsType): Boolean = pickedRow(track, lyricsType) != null

/**
 * Marks a manual LrcLib choice as edited to prevent an automatic fetch from overwriting it.
 * Stores the choice under the active mode so "Fetch lyrics again" can clear it.
 */
internal fun pickedLyrics(mediaId: String, track: Track, lyricsType: LyricsType): Lyrics? =
    pickedRow(track, lyricsType)?.let { (type, data) ->
        Lyrics(songId = mediaId, type = type.name, data = data, isEdited = true)
    }

@Composable
internal fun LyricsTrackSelector(
    mediaId: String,
    lyricsType: LyricsType,
    initialTitle: String,
    initialArtistName: String,
    onSearch: (String, String) -> Unit,
    showProviderMessages: Boolean,
    searchTracks: suspend (artist: String, title: String) -> List<Track> = { artist, title ->
        LrcLib.lyrics(artist = artist, title = title)?.getOrThrow().orEmpty()
    },
) {
    val providerName = stringResource(R.string.lyrics_brand_lrclib)
    val menuState = LocalMenuState.current
    var title by remember { mutableStateOf(initialTitle) }
    var artistName by remember { mutableStateOf(initialArtistName) }
    var searchRequest by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var resultMessage by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(searchRequest) {
        tracks = emptyList()
        resultMessage = null
        try {
            val found = searchTracks(artistName, title)
            tracks = found.filter { canPick(it, lyricsType) }
            if (tracks.isEmpty()) {
                resultMessage = R.string.lyrics_provider_missing_format
                if (showProviderMessages) Toaster.e(R.string.lyrics_provider_missing_format, providerName, duration = Toast.LENGTH_LONG)
            } else if (showProviderMessages) {
                Toaster.s(R.string.lyrics_provider_tracks_found_format, providerName, duration = Toast.LENGTH_LONG)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("LyricsTrackSelector").e(error, "Lyrics search failed")
            resultMessage = R.string.common_load_failed
            if (showProviderMessages) {
                Toaster.e(R.string.lyrics_provider_fetch_error_message, duration = Toast.LENGTH_LONG)
            }
        } finally {
            loading = false
        }
    }

    val style by rememberPreference(menuStyleKey, MenuStyle.List)
    val menuTitle = stringResource(R.string.lyrics_title)
    val showCancel = LocalMenuBack.current == null
    val searchFields: @Composable () -> Unit = {
        if (showCancel) {
            ListMenu.Entry(
                text = stringResource(R.string.common_action_cancel),
                icon = { SettingIcon(R.drawable.chevron_back) },
                onClick = menuState::pop,
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                enabled = !loading,
                label = { Text(stringResource(R.string.metadata_title)) },
                colors = trackSelectorTextFieldColors,
                modifier = Modifier.padding(horizontal = 6.dp).weight(1f),
            )
            TextField(
                value = artistName,
                onValueChange = { artistName = it },
                singleLine = true,
                enabled = !loading,
                label = { Text(stringResource(R.string.metadata_artist)) },
                colors = trackSelectorTextFieldColors,
                modifier = Modifier.padding(horizontal = 6.dp).weight(1f),
            )
            IconButton(
                icon = R.drawable.search,
                contentDescription = stringResource(R.string.search_title),
                enabled = !loading,
                color = colorPalette().text,
                onClick = {
                    loading = true
                    onSearch(title, artistName)
                    searchRequest++
                },
                modifier = Modifier
                    .background(color = surfaceColor(colorPalette().background2), shape = uiRoundnessShape())
                    .padding(4.dp)
                    .size(24.dp)
                    .align(Alignment.CenterVertically),
            )
        }
    }
    val searchStatus: @Composable () -> Unit = {
        if (loading) {
            repeat(4) {
                ListMenu.Entry(text = "", icon = { SettingIcon(R.drawable.text) }, loading = true)
            }
        }
        resultMessage?.let { message ->
            Text(
                if (message == R.string.lyrics_provider_missing_format) stringResource(message, providerName)
                else stringResource(message),
                color = colorPalette().text,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
    val selectTrack: (Track) -> Unit = { track ->
        pickedLyrics(mediaId, track, lyricsType)?.let { row ->
            Database.asyncTransaction { lyricsTable.upsert(row) }
        }
        menuState.hide()
    }

    if (style == MenuStyle.List) {
        ListMenu.Menu(title = menuTitle) {
            searchFields()
            searchStatus()
            tracks.forEach { track ->
                ListMenu.Entry(
                    text = "${track.artistName} - ${track.trackName}",
                    icon = { SettingIcon(R.drawable.text) },
                    subtitle = trackSubtitle(track),
                    onClick = { selectTrack(track) },
                )
            }
        }
    } else {
        GridMenu.Menu(title = menuTitle) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    searchFields()
                    searchStatus()
                }
            }
            items(tracks) { track ->
                GridMenu.Entry(
                    text = "${track.artistName} - ${track.trackName}",
                    icon = { SettingIcon(R.drawable.text) },
                    iconHasOwnBackground = true,
                    subtitle = trackSubtitle(track),
                    onClick = { selectTrack(track) },
                )
            }
        }
    }
}

@Composable
private fun trackSubtitle(track: Track): String {
    val duration = track.duration.seconds.toComponents { minutes, seconds, _ ->
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
    val durationLabel = stringResource(R.string.library_sort_duration)
    val idLabel = stringResource(R.string.player_statistics_media_id_label)
    return "($durationLabel $duration $idLabel ${track.id})"
}
