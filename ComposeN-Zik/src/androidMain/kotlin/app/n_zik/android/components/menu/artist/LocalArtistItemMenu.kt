package app.n_zik.android.components.menu.artist

import android.content.Intent
import app.n_zik.android.MODIFIED_PREFIX
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.menu.menuSectionHorizontalAlignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import app.n_zik.android.cleanPrefix
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Clickable
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.Menu
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.excludeDislikedArtistsKey
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.core.ondevice.DeviceLibraryResults
import app.n_zik.android.appContext
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistCoverDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistTitleDialog
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import app.n_zik.android.components.menu.EntityMenuSnapshot
import app.n_zik.android.components.menu.ObserveEntityMenuSnapshot
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.MenuBookmarkBadge
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.OpenEntityMenuAction
import app.n_zik.android.components.menu.menuLikeActionDescription
import app.n_zik.android.components.menu.menuSheetSectionBackground
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.components.menu.withMenuClick
import app.n_zik.android.utils.ToastRelay
import app.n_zik.android.utils.coroutines.NzikDispatchers

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class LocalArtistItemMenu private constructor(
    navController: NavController,
    artist: Artist,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    private val snapshot = EntityMenuSnapshot(artist, getActiveProfile(appContext()), Artist::id)
    private val artist: Artist get() = snapshot.value

    companion object {
        @Composable
        operator fun invoke(navController: NavController, artist: Artist): LocalArtistItemMenu =
            LocalArtistItemMenu(
                navController = navController,
                artist = artist,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    private val openArtist = OpenEntityMenuAction(
        navController, menuState, NavRoutes.artist, snapshot.id, R.drawable.people, R.string.artist_action_open,
    )
    lateinit var buttons: List<Button>
    override var menuStyle: MenuStyle by styleState

    @Composable
    override fun ListMenu() = ListMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        SectionTitle(stringResource(R.string.common_playback_section_title))
        buttons.getOrNull(0)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(1)?.let { if (it is MenuIcon) it.ListMenuItem() }

        // Section: Management
        SectionTitle(stringResource(R.string.library_management_section_title))
        buttons.getOrNull(2)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(3)?.let { if (it is MenuIcon) it.ListMenuItem() }
        buttons.getOrNull(4)?.let { if (it is MenuIcon) it.ListMenuItem() }

        SectionTitle(stringResource(R.string.common_navigation_section_title))
        openArtist.ListMenuItem()
    }

    @Composable
    override fun GridMenu() = GridMenu.Menu(title = null, showDragHandle = false) {
        // Section: Playback
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.common_playback_section_title))
        }
        buttons.getOrNull(0)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(1)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        // Section: Management
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.library_management_section_title))
        }
        buttons.getOrNull(2)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(3)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        buttons.getOrNull(4)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(stringResource(R.string.common_navigation_section_title))
        }
        item { openArtist.GridMenuItem() }
    }

    @Composable
    private fun ArtistItemDisplay(
        title: String?,
        thumbnailUrl: String?,
        subscribersCount: String?,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
        val showDisliked by rememberPreference(excludeDislikedArtistsKey, DislikeMode.Enabled)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxWidth()
                .menuSheetSectionBackground(surfaceColor(colorPalette().background1))
        ) {
            MenuSheetDragHandle()

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
                val likeState by remember(artist.id) {
                    Database.artistTable
                        .likeState(artist.id)
                        .distinctUntilChanged()
                }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

                // Artist's thumbnail
                Box(
                    Modifier.size(Dimensions.thumbnails.album / 2)
                ) {
                    Box(
                        modifier = Modifier
                            .size(Dimensions.thumbnails.album / 2)
                            .clip(artistThumbnailShape())
                    ) {
                        ImageCacheFactory.Thumbnail(
                            shape = artistThumbnailShape(),
                            thumbnailUrl = thumbnailUrl,
                            modifier = Modifier.size(Dimensions.thumbnails.album / 2)
                        )
                    }

                    likeState?.let { MenuBookmarkBadge(bookmarked = it) }
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
                }.collectAsStateWithLifecycle(false, context = NzikDispatchers.DATA)

                Column(
                    Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        contentDescription = menuLikeActionDescription(
                            likeState, showDisliked.isEnabled, R.string.artist_action_follow, R.string.artist_action_unfollow,
                        ),
                        icon = when(likeState) {
                            true -> R.drawable.bookmark
                            false -> R.drawable.bookmark_slash
                            null -> R.drawable.bookmark_outline
                        },
                        color = when(likeState) {
                            true -> colorPalette().favoritesIcon
                            false -> colorPalette().red
                            null -> colorPalette().text
                        },
                        onClick = {
                            coroutineScope.launch(NzikDispatchers.DATA) {
                                if (showDisliked.isEnabled) {
                                    Database.artistTable.rotateLikeState(artist.id)
                                } else {
                                    Database.artistTable.toggleBookmark(artist.id)
                                }
                            }
                            val newState = if (showDisliked.isEnabled) {
                                when(likeState) {
                                    true -> false // bookmarked → disliked
                                    false -> null // disliked → neutral
                                    null -> true  // neutral → bookmarked
                                }
                            } else {
                                when(likeState) {
                                    true -> null  // bookmarked → neutral
                                    false -> true // disliked → bookmarked
                                    null -> true  // neutral → bookmarked
                                }
                            }
                            val messageId = if (showDisliked.isEnabled) {
                                when(newState) {
                                    true -> R.string.library_favorites_added
                                    false -> R.string.library_dislikes_added
                                    null -> R.string.library_favorites_removed
                                }
                            } else {
                                when(newState) {
                                    true -> R.string.library_favorites_added
                                    null -> R.string.library_favorites_removed
                                    else -> R.string.library_favorites_added
                                }
                            }
                            with( artist ) {
                                if( name != null )
                                    Toaster.s( messageId, "\"$name\"" )
                                else
                                    Toaster.s( messageId )
                            }
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )

                    val sourceId = artist.id.removePrefix(MODIFIED_PREFIX)
                    if (sourceId.isNotBlank() && !sourceId.startsWith("LOCAL_")) {
                        IconButton(
                            icon = R.drawable.share_social,
                            contentDescription = stringResource(R.string.player_mini_button_share),
                            color = colorPalette().text,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/channel/$sourceId")
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            },
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(20.dp)
                        )
                    }
                }
            }

            GlassHorizontalSeparator(Modifier.height(1.dp))
        }
    }

    @Composable
    override fun MenuComponent() {
        if (!ObserveEntityMenuSnapshot(snapshot, { Database.artistTable.findById(it) }, menuState::hide)) return
        val changeTitle = ChangeArtistTitleDialog { artist }
        val changeCover = ChangeArtistCoverDialog { artist }
        val changeId = ChangeArtistBrowseIdDialog(menuState = menuState) { artist }

        val binder = LocalPlayerServiceBinder.current
        val mappedSongs = remember(artist.id) { Database.artistSongs(artist.id) }
            .collectAsStateWithLifecycle<List<Song>?>(null, context = NzikDispatchers.DATA).value

        val deviceGroup = isDeviceArtistId(artist.id)
        val profile = getActiveProfile(LocalContext.current)
        val snapshot by remember(profile) { DeviceLibraryResults.forProfile(profile) }
            .collectAsStateWithLifecycle(DeviceLibraryResults.current(profile))
        val eligible = snapshot?.artistSongIds?.get(artist.id).orEmpty()
        val songsLoading = mappedSongs == null || (deviceGroup && snapshot == null)
        val songs = mappedSongs.orEmpty().filter { it.isLocal == deviceGroup && (!deviceGroup || it.id in eligible) }

        val playAll = object : MenuIcon, Descriptive, Clickable {
            override val iconId: Int = R.drawable.play
            override val messageId: Int = if (deviceGroup) {
                R.string.artist_action_play_local_songs
            } else {
                R.string.artist_action_play_library_songs
            }
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() {
                if (songsLoading) {
                    ToastRelay.loadingWarning()
                    return
                }
                if (songs.isNotEmpty()) {
                    binder?.stopRadio()
                    binder?.player?.forcePlayAtIndex(songs.map { it.asMediaItem }, 0)
                    menuState.hide()
                } else {
                    Toaster.e(R.string.player_no_song_found)
                }
            }
            override fun onLongClick() {}
        }

        val shuffleAction = SongShuffler { songs }
        val shuffle = shuffleAction.withMenuClick {
            if (songsLoading) ToastRelay.loadingWarning() else shuffleAction.onShortClick()
        }

        buttons = mutableListOf<Button>().apply {
            add(playAll)
            add(shuffle)
            add(changeTitle)
            add(changeCover)
            if (!isDeviceArtistId(artist.id)) add(changeId)
        }

        MenuSheetSurface {
            changeTitle.Render()
            changeCover.Render()
            changeId.Render()
            ArtistItemDisplay(
                title = artist.name,
                thumbnailUrl = artist.thumbnailUrl,
                subscribersCount = pluralStringResource(R.plurals.library_song_count, songs.size, songs.size)
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
                .padding(vertical = 12.dp)
                .menuSectionHorizontalAlignment()
        )
    }
}

