package app.n_zik.android.components.menu.artist

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.IconButton

import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.thumbnailShape
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.secondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.n_zik.android.components.dialog.artist.ChangeArtistTitleDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistCoverDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.tab.SongShuffler

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class LocalArtistItemMenu private constructor(
    private val artist: Artist,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    companion object {
        @Composable
        operator fun invoke(artist: Artist): LocalArtistItemMenu =
            LocalArtistItemMenu(
                artist = artist,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    lateinit var buttons: List<Button>
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        SectionTitle(stringResource(R.string.playback))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Management
        SectionTitle(stringResource(R.string.management))
        buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }
    }

    @Composable
    override fun GridMenu() = GridMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.playback))
        }
        buttons.getOrNull(0)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        // Section: Management
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.management))
        }
        buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
    }

    @Composable
    private fun ArtistItemDisplay(
        title: String?,
        thumbnailUrl: String?,
        subscribersCount: String?,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxWidth()
                .background(colorPalette().background1)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = Dimensions.itemsVerticalPadding,
                        horizontal = 16.dp
                    )
            ) {
                // Artist's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2)
                ) {
                    ImageCacheFactory.Thumbnail(
                        thumbnailUrl = thumbnailUrl,
                        modifier = Modifier
                            .size(Dimensions.thumbnails.album / 2)
                            .clip(artistThumbnailShape())
                    )
                }

                // Artist's information
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = cleanPrefix(title ?: ""),
                        style = typography().xs.semiBold.copy(
                            color = colorPalette().text,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )

                    subscribersCount?.let {
                        if (it.isNotBlank()) {
                            BasicText(
                                text = it,
                                style = typography().xxs.semiBold.secondary.copy(
                                    color = colorPalette().textSecondary,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                            )
                        }
                    }
                }

                // Trailing content (Bookmark & Share)
                val isFollowing by remember(artist.id) {
                    Database.artistTable
                        .isFollowing(artist.id)
                        .distinctUntilChanged()
                }.collectAsState(false, Dispatchers.IO)

                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        icon = if (isFollowing) R.drawable.bookmark else R.drawable.bookmark_outline,
                        color = colorPalette().favoritesIcon,
                        onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                Database.artistTable.toggleFollow(artist.id)
                            }
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )

                    IconButton(
                        icon = R.drawable.share_social,
                        color = colorPalette().text,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/channel/${artist.id}")
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )
                }
            }

            HorizontalDivider(Modifier.height(1.dp))
        }
    }

    @Composable
    override fun MenuComponent() {
        val changeTitle = ChangeArtistTitleDialog { artist }
        val changeCover = ChangeArtistCoverDialog { artist }
        val changeId = ChangeArtistBrowseIdDialog(menuState = menuState) { artist }

        var displayTitle by remember { mutableStateOf(artist.name) }
        var displayThumbnailUrl by remember { mutableStateOf(artist.thumbnailUrl) }

        val dbArtist by Database.artistTable.findById(artist.id).collectAsState(initial = artist, context = Dispatchers.IO)

        LaunchedEffect(dbArtist) {
            dbArtist?.let {
                displayTitle = it.name
                displayThumbnailUrl = it.thumbnailUrl
            }
        }

        val binder = LocalPlayerServiceBinder.current
        val songs by Database.artistSongs(artist.id).collectAsState(initial = emptyList(), context = Dispatchers.IO)

        val playAll = object : MenuIcon, Descriptive, Clickable {
            override val iconId: Int = R.drawable.play
            override val messageId: Int = R.string.play_all_local_songs
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() {
                if (songs.isNotEmpty()) {
                    binder?.stopRadio()
                    binder?.player?.forcePlayAtIndex(songs.map { it.asMediaItem }, 0)
                    menuState.hide()
                } else {
                    Toaster.e(R.string.no_song_found)
                }
            }
            override fun onLongClick() {}
        }

        val shuffle = SongShuffler { songs }

        buttons = mutableListOf<Button>().apply {
            add(playAll)
            add(shuffle)
            add(changeTitle)
            add(changeCover)
            add(changeId)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0)
        ) {
            changeTitle.Render()
            changeCover.Render()
            changeId.Render()
            ArtistItemDisplay(
                title = displayTitle,
                thumbnailUrl = displayThumbnailUrl,
                subscribersCount = "${songs.size} ${stringResource(R.string.songs)}"
            )

            if (menuStyle == MenuStyle.List)
                ListMenu()
            else
                GridMenu()
        }
    }

    @Composable
    private fun SectionTitle(title: String) {
        BasicText(
            text = title,
            style = typography().xxs.semiBold.copy(
                color = colorPalette().accent,
                textAlign = TextAlign.Start
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp)
        )
    }
}

