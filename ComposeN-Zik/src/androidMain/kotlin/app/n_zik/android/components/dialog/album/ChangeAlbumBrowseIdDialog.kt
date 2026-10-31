package app.n_zik.android.components.dialog.album

import app.n_zik.android.components.glass.GlassDialogIconButton as IconButton
import androidx.compose.foundation.layout.height
import app.n_zik.android.components.search.SearchEmptyFeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.preferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.glassDialogButtonBackground
import app.n_zik.android.surfaceColor
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.core.coil.ImageCacheFactory
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.fast4x.innertube.utils.*
import java.util.UUID
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.ui.components.MenuState

class ChangeAlbumBrowseIdDialog private constructor(
    activeState: MutableState<Boolean>,
    private val menuState: MenuState?,
    private val getAlbum: () -> Album?
) : InteractiveDialog, MenuIcon, Descriptive {

    override var isActive: Boolean by activeState

    companion object {
        @Composable
        operator fun invoke( menuState: MenuState? = null, getAlbum: () -> Album? ): ChangeAlbumBrowseIdDialog =
            ChangeAlbumBrowseIdDialog(
                remember { mutableStateOf(false) },
                menuState,
                getAlbum
            )
    }

    override val iconId: Int = R.drawable.title_edit
    override val messageId: Int = R.string.update_album_browse_id
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun onShortClick() {
        showDialog()
    }

    @Composable
    override fun DialogBody() {
        val album = getAlbum() ?: return
        var query by remember { mutableStateOf(album.title ?: "") }
        var isSearching by remember { mutableStateOf(false) }
        var results by remember { mutableStateOf<List<Innertube.AlbumItem>?>(null) }
        val coroutineScope = rememberCoroutineScope()
        var submittedQuery by remember { mutableStateOf("") }
        var searchRequest by remember { mutableStateOf(0) }

        val performSearch = {
            if (query.isNotEmpty()) {
                val requestQuery = query
                val request = ++searchRequest
                submittedQuery = requestQuery
                results = null
                isSearching = true
                coroutineScope.launch {
                    val searchResult = withContext(NzikDispatchers.DATA) {
                        Innertube.searchPage<Innertube.AlbumItem>(
                            query = requestQuery, params = Innertube.SearchFilter.Album.value,
                            fromMusicShelfRendererContent = { Innertube.AlbumItem.from(it) }
                        )?.getOrNull()
                    }
                    if (request == searchRequest) {
                        results = searchResult?.items
                        isSearching = false
                    }
                }
            }
        }

        SearchEmptyFeedback(
            query = submittedQuery,
            empty = results?.isEmpty() == true,
            loading = isSearching || results == null || query != submittedQuery
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.weight(1f),
                    colors = InputDialog.defaultTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { performSearch() }
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { performSearch() },
                    modifier = Modifier.glassDialogButtonBackground(
                        backgroundColor = Color.Transparent,
                        shape = uiRoundnessShape(),
                        filmColor = surfaceColor(colorPalette().background2)
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.search),
                        tint = colorPalette().text,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
            } else if (results?.isEmpty() == true) {
                Spacer(Modifier.height(160.dp))
            } else if (results != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                ) {
                    items(results ?: return@LazyColumn, key = { it.key }, contentType = { "album" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newId = "$MODIFIED_PREFIX${item.key}"
                                    if (newId.isNotEmpty() && newId != MODIFIED_PREFIX) {
                                        appContext().preferences.edit().putString("old_browse_id_$newId", album.id).apply()
                                        Database.updateAlbumId(album.id, newId)
                                        Toaster.done()
                                        hideDialog()
                                        menuState?.hide()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ImageCacheFactory.Thumbnail(
                                shape = uiRoundnessShape(),
                                thumbnailUrl = item.thumbnail?.url,
                                modifier = Modifier.size(40.dp).clip(uiRoundnessShape())
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = item.title ?: "",
                                    style = typography().xs.medium,
                                    color = colorPalette().text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.authors?.joinToString { it.name ?: "" } ?: "",
                                    style = typography().xxs,
                                    color = colorPalette().textDisabled,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun Buttons() {
        val album = getAlbum() ?: return
        ButtonRow(
            buttons = buildList {
                add(
                    DialogButton(
                        text = stringResource(R.string.cancel),
                        onClick = ::hideDialog,
                        danger = true
                    )
                )
                // Reset Button
                if (album.id.startsWith(MODIFIED_PREFIX) || album.id.startsWith("LOCAL_ALBUM_")) {
                    add(
                        DialogButton(
                            text = stringResource(R.string.reset),
                            onClick = {
                                val prefs = appContext().preferences
                                val oldId = prefs.getString("old_browse_id_${album.id}", null)
                                val cleanId = oldId ?: album.id.removePrefix(MODIFIED_PREFIX)
                                Database.updateAlbumId(album.id, cleanId)
                                prefs.edit().remove("old_browse_id_${album.id}").apply()
                                Toaster.done()
                                hideDialog()
                                menuState?.hide()
                            }
                        )
                    )
                }
                // Unlink Button
                add(
                    DialogButton(
                        text = stringResource(R.string.unlink),
                        onClick = {
                            Database.updateAlbumId(album.id, "LOCAL_ALBUM_${UUID.randomUUID()}")
                            Toaster.done()
                            hideDialog()
                            menuState?.hide()
                        },
                        primary = true
                    )
                )
            }
        )
    }
}
