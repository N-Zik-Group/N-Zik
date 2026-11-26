package app.n_zik.android.components.dialog.playlist

import app.n_zik.android.components.glass.GlassDialogIconButton as IconButton
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.cleanPrefix
import app.n_zik.android.models.Playlist
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.glassDialogButtonBackground
import app.n_zik.android.surfaceColor
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.components.dialog.common.BrowseIdSearchResultRow
import app.n_zik.android.components.dialog.common.BrowseIdSave
import app.n_zik.android.components.navigation.RETIRED_PLAYLIST_BROWSE_KEY_PREFIX
import app.n_zik.android.components.dialog.common.rememberBrowseIdSearch
import app.n_zik.android.core.database.Database
import app.n_zik.android.uiRoundnessShape
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.utils.from
import java.util.UUID
import app.n_zik.android.appContext
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind

class ChangePlaylistBrowseIdDialog private constructor(
    activeState: MutableState<Boolean>,
    private val menuState: MenuState?,
    private val getPlaylist: () -> Playlist?,
    private val save: BrowseIdSave,
) : InteractiveDialog, MenuIcon, Descriptive {

    override var isActive: Boolean by activeState

    companion object {
        @Composable
        operator fun invoke( menuState: MenuState? = null, getPlaylist: () -> Playlist? ): ChangePlaylistBrowseIdDialog =
            ChangePlaylistBrowseIdDialog(
                remember { mutableStateOf(false) },
                menuState,
                getPlaylist,
                remember { BrowseIdSave() },
            )
    }

    override val iconId: Int = R.drawable.title_edit
    override val messageId: Int = R.string.metadata_playlist_action_change_browse_id
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override val isEnabled: Boolean get() = !save.saving && getPlaylist() != null

    override fun onShortClick() {
        if (isEnabled) showDialog()
    }

    private suspend fun changePlaylistId(playlist: Playlist, newId: String?, reset: Boolean = false): Boolean =
        Database.awaitWriteBatch {
            val preferences = appContext().preferences
            val origin = readPlaylistBrowseIdOrigin(playlist)
            if (playlistTable.updateBrowseId(playlist.id, newId) == 0) return@awaitWriteBatch false
            preferences.edit().apply {
                val previousId = playlist.browseId?.takeIf { it != newId }
                if (previousId != null) putLong("$RETIRED_PLAYLIST_BROWSE_KEY_PREFIX$previousId", playlist.id)
                if (reset) remove(playlistBrowseIdOriginKey(playlist.id))
                else if (origin != null) putString(playlistBrowseIdOriginKey(playlist.id), origin.id.orEmpty())
                apply()
            }
            true
        }

    @Composable
    override fun DialogBody() {
        val playlist = getPlaylist() ?: return
        val search = rememberBrowseIdSearch(cleanPrefix(playlist.name)) { query ->
            Innertube.searchPage<Innertube.PlaylistItem>(
                query = query, params = Innertube.SearchFilter.CommunityPlaylist.value,
                fromMusicShelfRendererContent = { Innertube.PlaylistItem.from(it) },
            )?.map { page -> page?.items.orEmpty().distinctBy { it.key } }
        }
        val results = search.results

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = search.query,
                    onValueChange = { search.query = it },
                    placeholder = { Text(stringResource(R.string.search_title)) },
                    modifier = Modifier.weight(1f),
                    colors = InputDialog.defaultTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { search.submit() }
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    foregroundFilm = surfaceColor(colorPalette().background2),
                    onClick = search::submit,
                    modifier = Modifier.glassDialogButtonBackground(
                        backgroundColor = Color.Transparent,
                        shape = uiRoundnessShape(),
                        filmColor = surfaceColor(colorPalette().background2)
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.search_title),
                        tint = colorPalette().text,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            search.Feedback()
            if (search.isSearching) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(5, key = { "browse-id-loading-$it" }) {
                        BrowseIdSearchResultRow(artworkShape = uiRoundnessShape(), loading = true)
                    }
                }
            } else if (results?.isEmpty() == true) {
                Spacer(Modifier.height(160.dp))
            } else if (results != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                ) {
                    items(results, key = { it.key }, contentType = { "playlist" }) { item ->
                        BrowseIdSearchResultRow(
                            artworkShape = uiRoundnessShape(),
                            thumbnailUrl = item.thumbnail?.url,
                            title = item.title.orEmpty(),
                            subtitle = item.channel?.name.orEmpty(),
                            modifier = Modifier.animateItem().clickable(enabled = !save.saving) {
                                val browseId = item.key.takeIf(String::isNotBlank) ?: return@clickable
                                val newId = "$MODIFIED_PREFIX$browseId"
                                save.submit(this@ChangePlaylistBrowseIdDialog, menuState) {
                                    changePlaylistId(playlist, newId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun Buttons() {
        val playlist = getPlaylist() ?: return
        val currentId = playlist.browseId
        val origin = currentId?.takeIf {
            it.startsWith(MODIFIED_PREFIX) || it.startsWith(UNLINKED_PLAYLIST_ID_PREFIX)
        }?.let { readPlaylistBrowseIdOrigin(playlist) }
        ButtonRow(
            buttons = buildList {
                add(
                    DialogButton(
                        text = stringResource(R.string.common_action_cancel),
                        onClick = ::hideDialog,
                        danger = true
                    )
                )
                // Reset Button
                if (origin != null) {
                    add(
                        DialogButton(
                            text = stringResource(R.string.common_action_reset),
                            enabled = !save.saving,
                            onClick = {
                                save.submit(this@ChangePlaylistBrowseIdDialog, menuState) {
                                    changePlaylistId(playlist, origin.id, reset = true)
                                }
                            }
                        )
                    )
                }
                // Unlink Button
                if (playlistLinkKind(currentId) != PlaylistLinkKind.Local) {
                    add(
                        DialogButton(
                            text = stringResource(R.string.common_action_unlink),
                            enabled = !save.saving,
                            onClick = {
                                save.submit(this@ChangePlaylistBrowseIdDialog, menuState) {
                                    changePlaylistId(playlist, "$UNLINKED_PLAYLIST_ID_PREFIX${UUID.randomUUID()}")
                                }
                            },
                            primary = true
                        )
                    )
                }
            }
        )
    }
}
