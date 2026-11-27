package app.n_zik.android.components.menu.artist

import app.n_zik.android.core.sync.showFavoriteChange
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.menu.menuSectionHeading
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.ArtistPage
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Artist
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
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.excludeDislikedArtistsKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.artist.ChangeArtistBrowseIdDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistCoverDialog
import app.n_zik.android.components.dialog.artist.ChangeArtistTitleDialog
import app.n_zik.android.components.glass.GlassHorizontalSeparator
import app.n_zik.android.components.menu.ObserveMenuProfile
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.MenuBookmarkBadge
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.MenuSheetSurface
import app.n_zik.android.components.menu.PendingLibraryItemMenu
import app.n_zik.android.components.menu.OpenEntityMenuAction
import app.n_zik.android.components.menu.menuLikeActionDescription
import app.n_zik.android.components.menu.menuSheetSectionBackground
import app.n_zik.android.components.navigation.LibraryBrowseKind
import app.n_zik.android.components.navigation.rememberLibraryBrowseDestination
import app.n_zik.android.components.shuffleButtonIconRes
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.components.menu.withMenuClick
import app.n_zik.android.utils.ToastRelay
import app.n_zik.android.utils.coroutines.NzikDispatchers

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
class OnlineArtistItemMenu private constructor(
    private val navController: NavController,
    private val artist: Innertube.ArtistItem,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
) : Menu {

    private val ownerProfile = getActiveProfile(appContext())

    companion object {
        @Composable
        operator fun invoke(navController: NavController, artist: Innertube.ArtistItem): OnlineArtistItemMenu =
            OnlineArtistItemMenu(
                navController = navController,
                artist = artist,
                menuState = LocalMenuState.current,
                styleState = rememberPreference(menuStyleKey, MenuStyle.List)
            )
    }

    private lateinit var buttons: List<Button?>
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
        buttons.getOrNull(5)?.let { if (it is MenuIcon) it.ListMenuItem() }
    }

    @Composable
    override fun GridMenu() {
        GridMenu.Menu(title = null, showDragHandle = false) {
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
            buttons.getOrNull(5)?.let { item { if (it is MenuIcon) it.GridMenuItem() } }
        }
    }

    @Composable
    private fun ArtistItemDisplay(
        entity: Artist,
        subscribersCount: String?,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
        val showDisliked by rememberPreference(excludeDislikedArtistsKey, DislikeMode.Enabled)
        val likeState = when {
            entity.dislikedAt != null -> false
            entity.bookmarkedAt != null -> true
            else -> null
        }

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
                            thumbnailUrl = entity.thumbnailUrl,
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
                        text = entity.cleanName(),
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
                                Database.artistTable.insertIgnore(entity)
                                if (showDisliked.isEnabled) {
                                    Database.artistTable.rotateLikeState(entity.id)
                                } else {
                                    Database.artistTable.toggleBookmark(entity.id)
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
                            showFavoriteChange(likeState, newState, entity.cleanName())
                        },
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )

                    onlineArtistMenuBrowseId(entity.id)?.let { onlineId ->
                        IconButton(
                            icon = R.drawable.share_social,
                            contentDescription = stringResource(R.string.player_mini_button_share),
                            color = colorPalette().text,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/channel/$onlineId")
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
        if (!ObserveMenuProfile(ownerProfile, menuState::hide)) return
        val browseId = rememberLibraryBrowseDestination(artist.key, LibraryBrowseKind.Artist)?.browseId
        if (browseId == null) {
            PendingLibraryItemMenu()
            return
        }
        key(browseId) { ResolvedArtistMenu(browseId) }
    }

    @Composable
    private fun ResolvedArtistMenu(browseId: String) {
        val dbArtist by remember(browseId) { Database.artistTable.findById(browseId) }
            .collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
        val binder = LocalPlayerServiceBinder.current
        val offlineMode by OfflineMode.rememberEnabled()
        val savedSongs by remember(browseId) { Database.artistSongs(browseId) }
            .collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)
        val onlineId = onlineArtistMenuBrowseId(browseId)
        val useSavedSongs = onlineId == null || offlineMode
        var artistPage by remember { mutableStateOf<ArtistPage?>(null) }
        var isFetching by remember { mutableStateOf(onlineId != null && !offlineMode) }
        val listedArtist = artist.takeIf { it.matchesOnlineArtistMenuId(onlineId) }

        LaunchedEffect(onlineId, offlineMode) {
            isFetching = onlineId != null && !offlineMode
            if (onlineId != null && !offlineMode) {
                artistPage = withContext(NzikDispatchers.DATA) {
                    YtMusic.getArtistPage(onlineId).getOrNull()
                }
                isFetching = false
            }
        }

        val entity = onlineArtistMenuEntity(browseId, dbArtist, artist, artistPage?.artist)
        val artistProvider = { dbArtist ?: entity.copy(timestamp = System.currentTimeMillis()) }
        val changeTitle = ChangeArtistTitleDialog(artistProvider)
        val changeCover = ChangeArtistCoverDialog(artistProvider)
        val changeId = ChangeArtistBrowseIdDialog(menuState = menuState, getArtist = artistProvider)

        val radioSeed = artistPage?.sections.orEmpty().asSequence()
            .flatMap { it.items.asSequence() }
            .firstNotNullOfOrNull { item ->
                when (item) {
                    is Innertube.SongItem -> item.asMediaItem
                    is Innertube.VideoItem -> item.asMediaItem
                    else -> null
                }
            }
        val radioAction = Radio.fromMediaItem { radioSeed }
        val playRadio = radioAction.withMenuClick {
            when {
                radioAction.active || radioSeed != null -> radioAction.onShortClick()
                isFetching -> ToastRelay.loadingWarning()
                else -> Toaster.e(R.string.player_no_song_found)
            }
        }

        val shuffle = object : MenuIcon, Descriptive, Clickable {
            override val iconId: Int get() = shuffleButtonIconRes(binder)
            override val isEnabled: Boolean
                get() = !listenTogetherGuestLock.value && (!useSavedSongs || savedSongs.isNotEmpty())
            override val color: androidx.compose.ui.graphics.Color
                @Composable
                get() = colorPalette().text
            override val messageId: Int = R.string.player_action_shuffle
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() {
                if (!Shuffler.canStart()) return
                if (!useSavedSongs && isFetching) {
                    ToastRelay.loadingWarning()
                } else {
                    val allMediaItems = if (useSavedSongs) savedSongs.map { it.asMediaItem } else {
                        artistPage?.sections.orEmpty().flatMap { section ->
                            section.items.mapNotNull { item ->
                                when (item) {
                                    is Innertube.SongItem -> item.asSong?.asMediaItem
                                    is Innertube.VideoItem -> item.asMediaItem
                                    else -> null
                                }
                            }
                        }
                    }
                    if (allMediaItems.isNotEmpty()) {
                        val activeBinder = binder ?: return
                        Shuffler.play(activeBinder, allMediaItems, onQueueApplied = activeBinder::triggerShuffleOkFlash)
                        menuState.hide()
                    } else {
                        Toaster.e(R.string.player_no_song_found)
                    }
                }
            }
            override fun onLongClick() {}
        }

        buttons = listOf(
            playRadio.takeUnless { useSavedSongs },
            shuffle,
            changeTitle,
            changeCover,
            changeId,
            OpenEntityMenuAction(navController, menuState, NavRoutes.artist, entity.id, R.drawable.people, R.string.artist_action_open),
        )

        changeTitle.Render()
        changeCover.Render()
        changeId.Render()

        MenuSheetSurface {
            ArtistItemDisplay(
                entity = entity,
                subscribersCount = artistPage?.artist?.subscribersCountText ?: listedArtist?.subscribersCountText,
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
                .menuSectionHeading()
        )
    }
}

internal fun onlineArtistMenuBrowseId(id: String): String? =
    id.removePrefix(MODIFIED_PREFIX).takeUnless {
        it.isBlank() || it.startsWith("LOCAL_ARTIST_") || isDeviceArtistId(it)
    }

private fun Innertube.ArtistItem.matchesOnlineArtistMenuId(onlineId: String?): Boolean =
    onlineId != null && info?.endpoint?.browseId?.removePrefix(MODIFIED_PREFIX) == onlineId

internal fun onlineArtistMenuEntity(
    browseId: String,
    stored: Artist?,
    listed: Innertube.ArtistItem?,
    fetched: Innertube.ArtistItem?,
): Artist {
    val entity = stored?.takeIf { it.id == browseId } ?: Artist(id = browseId)
    val onlineId = onlineArtistMenuBrowseId(browseId)
    val exactListed = listed?.takeIf { it.matchesOnlineArtistMenuId(onlineId) }
    val exactFetched = fetched?.takeIf { it.matchesOnlineArtistMenuId(onlineId) }
    // A browse-ID edit copies the old row. Only explicitly edited fields can override the new page.
    val useStoredMetadata = exactListed != null || onlineId == null
    return entity.copy(
        name = PropUtils.retainIfModified(
            entity.name,
            entity.name.takeIf { useStoredMetadata && !it.isNullOrBlank() } ?: exactFetched?.title ?: exactListed?.title
        ),
        thumbnailUrl = PropUtils.retainIfModified(
            entity.thumbnailUrl,
            entity.thumbnailUrl.takeIf { useStoredMetadata && !it.isNullOrBlank() }
                ?: exactFetched?.thumbnail?.url ?: exactListed?.thumbnail?.url
        ),
    )
}

