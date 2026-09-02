package app.n_zik.android.components.dialog.artist

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
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.preferences
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.artistThumbnailShape
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.fast4x.innertube.utils.*
import java.util.UUID
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.ui.components.MenuState

class ChangeArtistBrowseIdDialog private constructor(
    activeState: MutableState<Boolean>,
    private val menuState: MenuState?,
    private val getArtist: () -> Artist?
) : InteractiveDialog, MenuIcon, Descriptive {

    override var isActive: Boolean by activeState

    companion object {
        @Composable
        operator fun invoke( menuState: MenuState? = null, getArtist: () -> Artist? ): ChangeArtistBrowseIdDialog =
            ChangeArtistBrowseIdDialog(
                remember { mutableStateOf(false) },
                menuState,
                getArtist
            )
    }

    override val iconId: Int = R.drawable.title_edit
    override val messageId: Int = R.string.update_artist_browse_id
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
        val artist = getArtist() ?: return
        var query by remember { mutableStateOf(artist.name ?: "") }
        var isSearching by remember { mutableStateOf(false) }
        var results by remember { mutableStateOf<List<Innertube.ArtistItem>?>(null) }
        val coroutineScope = rememberCoroutineScope()

        val performSearch = {
            if (query.isNotEmpty()) {
                isSearching = true
                coroutineScope.launch {
                    val searchResult = withContext(Dispatchers.IO) {
                        Innertube.searchPage<Innertube.ArtistItem>(
                            SearchBody(query = query, params = Innertube.SearchFilter.Artist.value),
                            fromMusicShelfRendererContent = { Innertube.ArtistItem.from(it) }
                        )?.getOrNull()
                    }
                    results = searchResult?.items
                    isSearching = false
                }
            }
        }

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
                IconButton(onClick = { performSearch() }) {
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
            } else if (results != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                ) {
                    items(results ?: return@LazyColumn, key = { it.key }, contentType = { "artist" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newId = "$MODIFIED_PREFIX${item.key}"
                                    if (newId.isNotEmpty() && newId != MODIFIED_PREFIX) {
                                        appContext().preferences.edit().putString("old_browse_id_$newId", artist.id).apply()
                                        Database.updateArtistId(artist.id, newId)
                                        Toaster.done()
                                        hideDialog()
                                        menuState?.hide()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.thumbnail?.url,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(artistThumbnailShape())
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
                                    text = item.subscribersCountText ?: "",
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
        val artist = getArtist() ?: return
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp)
        ) {
            InteractiveDialog.CancelButton(
                modifier = InteractiveDialog.ButtonModifier()
                    .weight(1f)
                    .fillMaxWidth(.98f)
                    .border(
                        width = 2.dp,
                        color = colorPalette().red.copy(alpha = .3f),
                        shape = uiRoundnessShape()
                    )
                    .padding(vertical = 10.dp),
                onCancel = ::hideDialog
            )

            // Reset Button
            if (artist.id.startsWith(MODIFIED_PREFIX) || artist.id.startsWith("LOCAL_ARTIST_")) {
                Spacer(modifier = Modifier.width(4.dp))
                BasicText(
                    text = stringResource(R.string.reset),
                    style = typography().xs.medium.copy(color = colorPalette().text, textAlign = TextAlign.Center),
                    modifier = InteractiveDialog.ButtonModifier()
                        .weight(1f)
                        .fillMaxWidth(.98f)
                        .background(colorPalette().background0)
                        .border(1.dp, colorPalette().textDisabled, uiRoundnessShape())
                        .clickable {
                            val prefs = appContext().preferences
                            val oldId = prefs.getString("old_browse_id_${artist.id}", null)
                            val cleanId = oldId ?: artist.id.removePrefix(MODIFIED_PREFIX)
                            Database.updateArtistId(artist.id, cleanId)
                            prefs.edit().remove("old_browse_id_${artist.id}").apply()
                            Toaster.done()
                            hideDialog()
                            menuState?.hide()
                        }
                        .padding(vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
            // Unlink Button
            BasicText(
                text = stringResource(R.string.unlink),
                style = typography().xs.medium.copy(color = colorPalette().onAccent, textAlign = TextAlign.Center),
                modifier = InteractiveDialog.ButtonModifier()
                    .weight(1f)
                    .fillMaxWidth(.98f)
                    .background(colorPalette().accent)
                    .clickable {
                        Database.updateArtistId(artist.id, "LOCAL_ARTIST_${UUID.randomUUID()}")
                        Toaster.done()
                        hideDialog()
                        menuState?.hide()
                    }
                    .padding(vertical = 10.dp)
            )
        }
    }
}
