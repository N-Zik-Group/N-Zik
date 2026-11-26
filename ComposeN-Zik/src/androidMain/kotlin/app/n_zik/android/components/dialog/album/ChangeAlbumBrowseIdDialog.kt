package app.n_zik.android.components.dialog.album

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
import app.n_zik.android.models.Album
import app.n_zik.android.models.Song
import app.n_zik.android.components.song.trackAlbumSearchQuery
import app.n_zik.android.core.database.linkTrackAlbumManually
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.glassDialogButtonBackground
import app.n_zik.android.surfaceColor
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.components.dialog.common.BrowseIdSearchResultRow
import app.n_zik.android.components.dialog.common.BrowseIdSave
import app.n_zik.android.components.dialog.common.rememberBrowseIdSearch
import app.n_zik.android.components.dialog.common.saveEntityBrowseId
import app.n_zik.android.components.dialog.common.saveTrackBrowseId
import app.n_zik.android.core.database.trackBrowseIdOrigin
import app.n_zik.android.core.database.Database
import app.n_zik.android.uiRoundnessShape
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.utils.from
import java.util.UUID
import app.n_zik.android.appContext
import app.n_zik.android.ui.components.MenuState

class ChangeAlbumBrowseIdDialog private constructor(
    activeState: MutableState<Boolean>,
    private val menuState: MenuState?,
    private val targetTrack: Song?,
    private val initialQuery: String?,
    private val getAlbum: () -> Album?,
    private val editTarget: MutableState<Album?>,
    private val save: BrowseIdSave,
) : InteractiveDialog, MenuIcon, Descriptive {

    override var isActive: Boolean by activeState

    companion object {
        @Composable
        operator fun invoke(
            menuState: MenuState? = null,
            targetTrack: Song? = null,
            initialQuery: String? = null,
            getAlbum: () -> Album?,
        ): ChangeAlbumBrowseIdDialog =
            ChangeAlbumBrowseIdDialog(
                remember { mutableStateOf(false) },
                menuState,
                targetTrack,
                initialQuery,
                getAlbum,
                remember { mutableStateOf(null) },
                remember { BrowseIdSave() },
            )
    }

    override val iconId: Int = R.drawable.title_edit
    override val isEnabled: Boolean get() = !save.saving && (getAlbum() != null || targetTrack != null)
    override val messageId: Int = R.string.metadata_album_action_change_browse_id
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun onShortClick() {
        if (!isEnabled) return
        editTarget.value = getAlbum()
        showDialog()
    }

    private suspend fun changeAlbumId(album: Album, newId: String, reset: Boolean = false): Boolean {
        val track = targetTrack
        if (track != null) {
            return saveTrackBrowseId(track.id, album.id, newId, reset) {
                val selected = album.copy(id = newId, isYoutubeAlbum = false, bookmarkedAt = null, dislikedAt = null)
                Database.linkTrackAlbumManually(track, selected, album.id)
            }
        }
        return saveEntityBrowseId(album.id, newId, reset) {
            Database.updateAlbumId(
                album.id,
                newId,
                fallbackAlbum = album.takeUnless { reset },
            )
        }
    }

    @Composable
    override fun DialogBody() {
        val album = editTarget.value
        val track = targetTrack
        val startingQuery = album?.title ?: (initialQuery ?: trackAlbumSearchQuery(null, targetTrack?.title, targetTrack?.artistsText))
        val search = rememberBrowseIdSearch(cleanPrefix(startingQuery.orEmpty())) { query ->
            Innertube.searchPage<Innertube.AlbumItem>(
                query = query, params = Innertube.SearchFilter.Album.value,
                fromMusicShelfRendererContent = { Innertube.AlbumItem.from(it) },
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
                    items(results, key = { it.key }, contentType = { "album" }) { item ->
                        BrowseIdSearchResultRow(
                            artworkShape = uiRoundnessShape(),
                            thumbnailUrl = item.thumbnail?.url,
                            title = item.title.orEmpty(),
                            subtitle = item.authors?.joinToString { it.name ?: "" }.orEmpty(),
                            modifier = Modifier.animateItem().clickable(enabled = !save.saving) {
                                val browseId = item.key.takeIf(String::isNotBlank) ?: return@clickable
                                val newId = "$MODIFIED_PREFIX$browseId"
                                save.submit(this@ChangeAlbumBrowseIdDialog, menuState) {
                                    if (track == null) return@submit album != null && changeAlbumId(album, newId)
                                    val selected = Album(
                                        id = newId,
                                        title = album?.title?.takeIf { it.startsWith(MODIFIED_PREFIX) } ?: item.title,
                                        thumbnailUrl = album?.thumbnailUrl?.takeIf { it.startsWith(MODIFIED_PREFIX) } ?: item.thumbnail?.url,
                                        authorsText = album?.authorsText?.takeIf { it.startsWith(MODIFIED_PREFIX) }
                                            ?: item.authors?.mapNotNull { it.name }?.joinToString(", "),
                                    )
                                    saveTrackBrowseId(track.id, album?.id, newId, reset = false) {
                                        Database.linkTrackAlbumManually(track, selected, album?.id)
                                    }
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
        val album = editTarget.value
        ButtonRow(
            buttons = buildList {
                add(
                    DialogButton(
                        text = stringResource(R.string.common_action_cancel),
                        onClick = ::hideDialog,
                        danger = true
                    )
                )
                if (album == null) return@buildList
                // Reset Button
                if (album.id.startsWith(MODIFIED_PREFIX) || album.id.startsWith("LOCAL_ALBUM_")) {
                    add(
                        DialogButton(
                            text = stringResource(R.string.common_action_reset),
                            enabled = !save.saving,
                            onClick = {
                                save.submit(this@ChangeAlbumBrowseIdDialog, menuState) {
                                    val oldId = targetTrack?.let { trackBrowseIdOrigin(it.id, album.id) }
                                        ?: appContext().preferences.getString("old_browse_id_${album.id}", null)
                                    val cleanId = oldId ?: album.id.removePrefix(MODIFIED_PREFIX)
                                    changeAlbumId(album, cleanId, reset = true)
                                }
                            }
                        )
                    )
                }
                // Unlink Button
                add(
                    DialogButton(
                        text = stringResource(R.string.common_action_unlink),
                        enabled = !save.saving,
                        onClick = {
                            save.submit(this@ChangeAlbumBrowseIdDialog, menuState) {
                                changeAlbumId(album, "LOCAL_ALBUM_${UUID.randomUUID()}")
                            }
                        },
                        primary = true
                    )
                )
            }
        )
    }
}
