package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.syncStatus

object SyncStatusDialog : InteractiveDialog {
    override var isActive: Boolean by mutableStateOf(false)

    @get:Composable
    override val dialogTitle: String
        get() = stringResource(R.string.sync_status_detail)

    @Composable
    override fun DialogBody() {
        val status by syncStatus.collectAsState()
        var expandedItem by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

        val statusItems = listOf(
            stringResource(R.string.autosync_likes) to status.likedSongs,
            stringResource(R.string.sync_import_library_songs) to status.librarySongs,
            stringResource(R.string.sync_import_uploaded_songs) to status.uploadedSongs,
            stringResource(R.string.autosync_albums) to status.likedAlbums,
            stringResource(R.string.sync_import_uploaded_albums) to status.uploadedAlbums,
            stringResource(R.string.autosync_channels) to status.artists,
            stringResource(R.string.autosync) to status.playlists,
            stringResource(R.string.sync_import_episodes) to status.episodes,
            stringResource(R.string.sync_push_song_like) to status.pushLikedSongs,
            stringResource(R.string.sync_push_album_bookmark) to status.pushAlbumBookmarks,
            stringResource(R.string.sync_push_artist_follow) to status.pushArtistFollows,
            stringResource(R.string.sync_push_playlist) to status.pushPlaylists,
            stringResource(R.string.sync_push_episode) to status.pushEpisodes,
        )

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            statusItems.forEach { (name, syncItemStatus) ->
                val isExpanded = expandedItem == name
                val hasDetail = !syncItemStatus.detailMessage.isNullOrBlank()
                val rotation by androidx.compose.animation.core.animateFloatAsState(if (isExpanded) 180f else 0f)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(uiRoundnessShape())
                        .clickable(enabled = hasDetail) {
                            expandedItem = if (isExpanded) null else name
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = name,
                            style = typography().xs,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            val (iconRes, tintColor) = when (syncItemStatus.state) {
                                "syncing" -> R.drawable.sync to colorPalette().accent
                                "idle" -> R.drawable.checkmark to colorPalette().textDisabled
                                "error" -> R.drawable.close to colorPalette().red
                                else -> R.drawable.information to colorPalette().textDisabled
                            }
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(iconRes),
                                contentDescription = null,
                                tint = tintColor,
                                modifier = Modifier.size(16.dp)
                            )
                            
                            if (hasDetail) {
                                androidx.compose.material3.Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.chevron_down),
                                    contentDescription = null,
                                    tint = colorPalette().textDisabled,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = rotation }
                                )
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(visible = isExpanded && hasDetail) {
                        BasicText(
                            text = syncItemStatus.detailMessage ?: "",
                            style = typography().xxs.copy(color = if (syncItemStatus.state == "error") colorPalette().red else colorPalette().textDisabled),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun Buttons() {
        Button(
            onClick = { hideDialog() },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorPalette().accent,
                contentColor = colorPalette().textSecondary
            ),
            shape = uiRoundnessShape()
        ) {
            InteractiveDialog.ConfirmButton(onConfirm = { hideDialog() })
        }
    }
}
