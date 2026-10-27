package app.n_zik.android.components.player.lyrics

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.models.Lyrics
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import androidx.compose.material3.Icon
import app.n_zik.android.components.menu.ListMenu
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.uiRoundnessShape
import it.fast4x.lrclib.LrcLib
import it.fast4x.lrclib.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
fun SettingIcon(@DrawableRes icon: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
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

/** Type and text a LrcLib [track] is stored as when picked in [lyricsType] mode, or null when the track has nothing usable in that mode: Unsynced mode takes the plain text (else the synced text without its timestamps), any other mode takes the synced text only, since plain text would break the synced view. */
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
 * Row saved when the user picks a LrcLib track, or null when the track has nothing usable in
 * [lyricsType] mode. It is a deliberate manual choice, so it is flagged [Lyrics.isEdited]: an automatic
 * fetch would otherwise replace it with the default match the user just rejected. The row always has the
 * type of the mode the pick was made in, so it is the displayed row and "Fetch lyrics again" clears it.
 */
internal fun pickedLyrics(mediaId: String, track: Track, lyricsType: LyricsType): Lyrics? =
    pickedRow(track, lyricsType)?.let { (type, data) ->
        Lyrics(songId = mediaId, type = type.name, data = data, isEdited = true)
    }

@Composable
fun LyricsTrackSelector(
    mediaId: String,
    lyrics: Lyrics?,
    lyricsType: LyricsType,
    initialTitle: String,
    initialArtistName: String,
    onTitleChange: (String) -> Unit,
    onArtistNameChange: (String) -> Unit,
    playerEnableLyricsPopupMessage: Boolean,
    coroutineScope: CoroutineScope,
    onSearchRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val menuState = LocalMenuState.current
    var loading by remember { mutableStateOf(true) }
    val tracks = remember { mutableStateListOf<Track>() }
    var error by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(initialTitle) }
    var artistName by remember { mutableStateOf(initialArtistName) }

    LaunchedEffect(title, artistName) {
        onTitleChange(title)
        onArtistNameChange(artistName)
    }

    LaunchedEffect(Unit) {
        kotlin.runCatching {
            LrcLib.lyrics(
                artist = artistName,
                title = title
            )?.onSuccess { found ->
                val pickable = found.filter { canPick(it, lyricsType) }
                if (pickable.isNotEmpty() && playerEnableLyricsPopupMessage)
                    coroutineScope.launch {
                        Toaster.s(
                            R.string.info_lyrics_tracks_found_on_s,
                            "LrcLib.net",
                            duration = Toast.LENGTH_LONG
                        )
                    }
                else
                    if (playerEnableLyricsPopupMessage)
                        coroutineScope.launch {
                            Toaster.e(
                                R.string.info_lyrics_not_found_on_s,
                                "LrcLib.net",
                                duration = Toast.LENGTH_LONG
                            )
                        }
                if (pickable.isEmpty()){
                        menuState.display {
                        ListMenu.Menu(title = stringResource(R.string.txt_lyrics)) {
                            ListMenu.Entry(
                                text = stringResource(R.string.cancel),
                                icon = { SettingIcon(R.drawable.chevron_back) },
                                onClick = {
                                    menuState.hide()
                                    onDismiss()
                                }
                            )
                            Row {
                                TextField(
                                    value = title,
                                    onValueChange = { newTitle ->
                                        title = newTitle
                                    },
                                    singleLine = true,
                                    colors = trackSelectorTextFieldColors,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .weight(1f)
                                )
                                TextField(
                                    value = artistName,
                                    onValueChange = { newArtistName ->
                                        artistName = newArtistName
                                    },
                                    singleLine = true,
                                    colors = trackSelectorTextFieldColors,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .weight(1f)
                                )
                                IconButton(
                                    icon = R.drawable.search,
                                    color = colorPalette().text,
                                    onClick = {
                                        menuState.hide()
                                        onSearchRetry()
                                    },
                                    modifier = Modifier
                                        .background(
                                            shape = uiRoundnessShape(),
                                            color = surfaceColor(colorPalette().background2)
                                        )
                                        .padding(all = 4.dp)
                                        .size(24.dp)
                                        .align(Alignment.CenterVertically)
                                        .weight(0.2f)
                                )
                            }
                        }
                    }
                }

                tracks.clear()
                tracks.addAll(pickable)
                loading = false
                error = false
            }?.onFailure {
                if (playerEnableLyricsPopupMessage)
                    coroutineScope.launch {
                        Toaster.e(
                            R.string.an_error_has_occurred_while_fetching_the_lyrics,
                            "KuGou.com",
                            duration = Toast.LENGTH_LONG
                        )
                    }

                loading = false
                error = true
            } ?: run { loading = false }
        }.onFailure {
            Timber.tag("LyricsTrackSelector").e("get error 1 ${it.stackTraceToString()}")
        }
    }

    DialogPresence(visible = loading) {
        DefaultDialog(
            title = stringResource(R.string.txt_lyrics),
            onDismiss = {
                onDismiss()
            }
        ) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    LaunchedEffect(tracks.size, title, artistName) {
        if (tracks.isNotEmpty()) {
            menuState.display {
                ListMenu.Menu(title = stringResource(R.string.txt_lyrics)) {
                    ListMenu.Entry(
                        text = stringResource(R.string.cancel),
                        icon = { SettingIcon(R.drawable.chevron_back) },
                        onClick = {
                            menuState.hide()
                            onDismiss()
                        }
                    )
                    Row{
                        TextField(
                            value = title,
                            onValueChange = { newTitle ->
                                title = newTitle
                            },
                            singleLine = true,
                            colors = trackSelectorTextFieldColors,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .weight(1f)
                        )
                        TextField(
                            value = artistName,
                            onValueChange = { newArtistName ->
                                artistName = newArtistName
                            },
                            singleLine = true,
                            colors = trackSelectorTextFieldColors,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .weight(1f)
                        )
                        IconButton(
                            icon = R.drawable.search,
                            color = colorPalette().text,
                            onClick = {
                                menuState.hide()
                                onSearchRetry()
                            },
                            modifier = Modifier
                                .background(
                                    shape = uiRoundnessShape(),
                                    color = surfaceColor(colorPalette().background2)
                                )
                                .padding(all = 4.dp)
                                .size(24.dp)
                                .align(Alignment.CenterVertically)
                                .weight(0.2f)
                        )
                    }
                    tracks.forEach {
                        ListMenu.Entry(
                            text = "${it.artistName} - ${it.trackName}",
                            icon = { SettingIcon(R.drawable.text) },
                            subtitle = "(${stringResource(R.string.sort_duration)} ${
                                it.duration.seconds.toComponents { minutes, seconds, _ ->
                                    "$minutes:${seconds.toString().padStart(2, '0')}"
                                }
                            } ${stringResource(R.string.id)} ${it.id}) ",
                            onClick = {
                                menuState.hide()
                                onDismiss()
                                pickedLyrics(mediaId, it, lyricsType)?.let { row ->
                                    Database.asyncTransaction { lyricsTable.upsert(row) }
                                }
                            }
                        )
                    }
                    ListMenu.Entry(
                        text = stringResource(R.string.cancel),
                        icon = { SettingIcon(R.drawable.chevron_back) },
                        onClick = { 
                            menuState.hide() 
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

