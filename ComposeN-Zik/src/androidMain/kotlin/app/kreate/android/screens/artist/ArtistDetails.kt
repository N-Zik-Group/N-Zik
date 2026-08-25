package app.kreate.android.screens.artist

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastMap
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import androidx.compose.ui.graphics.painter.Painter

import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.ArtistPage
import it.fast4x.innertube.requests.ArtistSection
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.n_zik.android.appContext

import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.themed.AutoResizeText
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.FontSizeRange
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.items.ArtistItem
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.items.VideoItem
import app.n_zik.android.components.menu.video.VideoItemMenu
import app.n_zik.android.components.menu.album.OnlineAlbumItemMenu
import app.it.fast4x.rimusic.ui.styling.Dimensions

import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.align
import app.it.fast4x.rimusic.utils.asAlbum
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.fadingEdge
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.utils.otherLanguageAppArtistKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.artist.FollowButton
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.playback.utils.Shuffler
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.ui.screens.DynamicOrientationLayout
import app.n_zik.android.components.ui.screens.album.Translate
import kotlinx.coroutines.launch
import timber.log.Timber
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import it.fast4x.innertube.YtMusic
import androidx.media3.common.MediaItem
import androidx.compose.material3.CircularProgressIndicator
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.rememberCoroutineScope
import java.util.Locale

@ExperimentalFoundationApi
@UnstableApi
@Composable
fun ArtistDetails(
    navController: NavController,
    localArtist: Artist?,
    artistPage: ArtistPage?,
    thumbnailPainter: Painter,
) {
    artistPage ?: return

    // Essentials

    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()


    // Settings
    val disableScrollingText by rememberPreference( disableScrollingTextKey, false )
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val sectionTextModifier = Modifier
        .padding( horizontal = 16.dp )
        .padding( top = 24.dp, bottom = 8.dp )
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px

    var isGlobalLoading by remember { mutableStateOf(false) }
    var sectionLoadingId by remember { mutableStateOf<String?>(null) }

    val songs = remember(parentalControlEnabled) {
        artistPage.sections
                  .fastFirstOrNull { section ->
                      section.items.fastAll { it is Innertube.SongItem }
                  }
                  ?.items
                  ?.mapNotNull {
                      (it as? Innertube.SongItem)?.asSong
                  }
                  ?.filter { !parentalControlEnabled || it.title.startsWith(EXPLICIT_PREFIX, true) != true }
                  .orEmpty()
    }

    //<editor-fold defaultstate="collapsed" desc="Buttons">
    val itemSelector = ItemSelector<Song>()
    val scope = rememberCoroutineScope()

    suspend fun fetchAllArtistSongs(): List<Song> {
        val songsSection = artistPage.sections.fastFirstOrNull { section ->
            section.items.fastAll { it is Innertube.SongItem } && section.moreEndpoint?.browseId != null
        }
        if (songsSection != null) {
            val fetched = YtMusic.getPlaylist(songsSection.moreEndpoint!!.browseId!!)
                .getOrNull()?.songs?.mapNotNull { it.asSong }
            if (!fetched.isNullOrEmpty()) return fetched
        }
        return songs
    }

    fun getSongs() = itemSelector.ifEmpty { songs }
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val followButton = localArtist?.let { artist -> FollowButton { artist } }
    val shuffler = object: MenuIcon, Descriptive {
        override val iconId: Int = R.drawable.shuffle
        override val messageId: Int = R.string.info_shuffle
        override val menuIconTitle: String
            @Composable get() = stringResource(R.string.shuffle)

        override fun onShortClick() {
            scope.launch {
                isGlobalLoading = true
                try {
                    val allSongs = getSongs()
                    if (allSongs.isNotEmpty()) {
                        binder?.let { Shuffler.play(it, allSongs) }
                    } else {
                        Toaster.i(R.string.no_song_to_shuffle)
                    }
                } finally {
                    isGlobalLoading = false
                }
            }
        }
    }

    val downloadAllDialog = DownloadAllSongsDialog(::getSongs)
    val deleteAllDownloadsDialog = DeleteAllDownloadedSongsDialog(::getSongs)
    val radio = Radio(::getSongs)
    val playNext = PlayNext {
        scope.launch {
            isGlobalLoading = true
            try {
                val allSongs = getSongs()
                val mediaItems = allSongs.map(Song::asMediaItem)
                binder?.player?.addNext(mediaItems, appContext())
                itemSelector.isActive = false
            } finally {
                isGlobalLoading = false
            }
        }
    }
    val enqueue = Enqueue {
        scope.launch {
            isGlobalLoading = true
            try {
                val allSongs = getSongs()
                val mediaItems = allSongs.map(Song::asMediaItem)
                binder?.player?.enqueue(mediaItems, appContext())
                itemSelector.isActive = false
            } finally {
                isGlobalLoading = false
            }
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Translator">
    val translate = Translate.init()
    val translator = Translator(NetworkClientFactory.getTranslatorClient())
    val otherLanguageApp by rememberPreference(otherLanguageAppArtistKey, Languages.System)
    val appLang = Locale.getDefault().language
    val activeTranslateLang = remember(otherLanguageApp, appLang) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else Languages.entries.firstOrNull { it.code == appLang } ?: Languages.English
    }
    val languageDestination = activeTranslateLang.translatorLanguage
    //</editor-fold>

    //<editor-fold desc="Dialog renders">
    downloadAllDialog.Render()
    deleteAllDownloadsDialog.Render()
    //</editor-fold>

    DynamicOrientationLayout( thumbnailPainter ) {
        LazyColumn(
            state = lazyListState,
            userScrollEnabled = artistPage.sections.isNotEmpty(),
            contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
        ) {
            item( "header" ) {
                Box( Modifier.fillMaxWidth() ) {
                    if ( !isLandscape )
                        Image(
                            painter = thumbnailPainter,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.aspectRatio( 4f / 3 )      // Limit height
                                               .fillMaxWidth()
                                               .align( Alignment.Center )
                                               .fadingEdge(
                                                   top = WindowInsets.systemBars
                                                       .asPaddingValues()
                                                       .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                                   bottom = Dimensions.fadeSpacingBottom
                                               )
                        )

                    Column( Modifier.align( Alignment.BottomCenter ) ) {
                        AutoResizeText(
                            text = cleanPrefix( localArtist?.name ?: artistPage.artist.title ?: "..." ),
                            style = typography().l.semiBold,
                            fontSizeRange = FontSizeRange(32.sp, 38.sp),
                            fontWeight = typography().l.semiBold.fontWeight,
                            fontFamily = typography().l.semiBold.fontFamily,
                            color = typography().l.semiBold.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding( horizontal = 30.dp )
                                               .conditional( !disableScrollingText ) {
                                                   basicMarquee( iterations = Int.MAX_VALUE )
                                               }
                                               .align( Alignment.CenterHorizontally )
                        )


                        val statsText = buildString {
                            val subs = artistPage.subscribers
                            val listeners = artistPage.listeners
                            if (!subs.isNullOrBlank()) append(subs)
                            if (!subs.isNullOrBlank() && !listeners.isNullOrBlank()) append(" • ")
                            if (!listeners.isNullOrBlank()) append(listeners)
                        }
                        if (statsText.isNotBlank()) {
                            BasicText(
                                text = statsText,
                                style = typography().s.copy( colorPalette().textSecondary ),
                                modifier = Modifier.align( Alignment.CenterHorizontally )
                            )
                        }
                    }


                    HeaderIconButton(
                        icon = R.drawable.share_social,
                        color = colorPalette().text,
                        iconSize = 24.dp,
                        modifier = Modifier.align( Alignment.TopEnd )
                                           .padding( top = 5.dp, end = 5.dp ),
                        onClick = {
                            val url = "https://music.youtube.com/channel/${localArtist?.id ?: artistPage.artist.key}"
                            val sendIntent = Intent().apply {

                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }

                            context.startActivity(
                                Intent.createChooser( sendIntent, null )
                            )
                        }
                    )
                }
            }

            item( "action_buttons") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        followButton?.ToolBarButton()

                        Spacer( Modifier.width( 5.dp ) )

                        if (isGlobalLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = colorPalette().accent
                            )
                        } else {
                            TabToolBar.Buttons(
                                shuffler,
                                playNext,
                                enqueue,
                                radio,
                                itemSelector,
                                downloadAllDialog,
                                deleteAllDownloadsDialog,
                                modifier = Modifier.fillMaxWidth( .8f )
                            )
                        }
                    }
                }
            }
            if (!artistPage.description.isNullOrBlank()) {
                item("description") {
                    var isDescriptionExpanded by remember { mutableStateOf(false) }
                    val description = artistPage.description.orEmpty()
                    val attributionsIndex = description.lastIndexOf("\n\nFrom Wikipedia")
                    val nonTranslatedText by remember {
                        mutableStateOf(
                            if (attributionsIndex == -1) description
                            else description.substring(0, attributionsIndex)
                        )
                    }

                    var translatedText by remember { mutableStateOf("") }
                    if (translate.isActive) {
                        LaunchedEffect(Unit) {
                            val result = withContext(Dispatchers.IO) {
                                try {
                                    translator.translate(
                                        nonTranslatedText,
                                        languageDestination,
                                        Language.AUTO
                                    ).translatedText
                                } catch (e: Exception) {
                                    Timber.tag("ArtistDetails").e(e, "Failed to translate text")
                                }
                            }
                            translatedText = if (result.toString() == "kotlin.Unit") "" else result.toString()
                        }
                    } else translatedText = nonTranslatedText
                    var showMoreButton by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateContentSize()
                            .clip(uiRoundnessShape())
                            .clickable(enabled = showMoreButton || isDescriptionExpanded) { isDescriptionExpanded = !isDescriptionExpanded }
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            translate.ToolBarButton()
                            
                            BasicText(
                                text = "“",
                                style = typography().xxl.semiBold,
                                modifier = Modifier.offset(y = (-8).dp)
                            )
                            
                            BasicText(
                                text = translatedText,
                                style = typography().xs.secondary.align(TextAlign.Justify),
                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                                onTextLayout = { textLayoutResult ->
                                    if (textLayoutResult.hasVisualOverflow) {
                                        showMoreButton = true
                                    } else if (!isDescriptionExpanded) {
                                        showMoreButton = false
                                    }
                                }
                            )

                            BasicText(
                                text = "”",
                                style = typography().xxl.semiBold,
                                modifier = Modifier.offset(y = 4.dp).align(Alignment.Bottom)
                            )
                        }

                        if (showMoreButton || isDescriptionExpanded) {
                            BasicText(
                                text = if (isDescriptionExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
                                style = typography().xs.semiBold.copy(colorPalette().text),
                                modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                            )
                        }

                        if (isDescriptionExpanded && attributionsIndex != -1) {
                            BasicText(
                                text = stringResource(R.string.from_wikipedia_cca),
                                style = typography().xxs
                                    .color(colorPalette().textDisabled)
                                    .align(TextAlign.Start),
                                modifier = Modifier.padding(top = 8.dp, start = 8.dp)
                            )
                        }
                    }
                }
            }

            items(
                items = artistPage.sections,
                key = ArtistSection::title
            ) { section ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = sectionTextModifier.fillMaxWidth()
                ) {
                    Text(
                        text = section.title,
                        style = typography().m.semiBold,
                        modifier = Modifier.weight( 1f )
                    )

                    if (!section.items.fastAll { it is Innertube.ArtistItem }) {
                        val context = LocalContext.current
                        val sectionId = section.title
                        val isSectionLoading = sectionLoadingId == sectionId

                        if (isSectionLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                                strokeWidth = 2.dp,
                                color = colorPalette().textSecondary
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.dice),
                                contentDescription = null,
                                tint = colorPalette().textSecondary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clip(uiRoundnessShape()).clickable {
                                        scope.launch(Dispatchers.IO) {
                                            sectionLoadingId = sectionId
                                            try {
                                                val allMediaItems = mutableListOf<MediaItem>()
                                                if (section.items.fastAll { it is Innertube.SongItem } && section.moreEndpoint?.browseId != null) {
                                                    YtMusic.getPlaylist(section.moreEndpoint!!.browseId!!).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                }
                                                if (allMediaItems.isEmpty()) {
                                                    section.items.forEach { item ->
                                                        when (item) {
                                                            is Innertube.SongItem -> item.asSong?.asMediaItem?.let { allMediaItems.add(it) }
                                                            is Innertube.VideoItem -> allMediaItems.add(item.asMediaItem)
                                                            is Innertube.AlbumItem -> YtMusic.getAlbum(item.key).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                            is Innertube.PlaylistItem -> YtMusic.getPlaylist(item.key).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                            else -> {}
                                                        }
                                                    }
                                                }
                                                if (allMediaItems.isNotEmpty()) {
                                                    binder?.let { Shuffler.play(it, allMediaItems) }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toaster.e(R.string.no_song_found)
                                                    }
                                                }
                                            } finally {
                                                sectionLoadingId = null
                                            }
                                        }
                                    }
                            )

                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                tint = colorPalette().textSecondary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clip(uiRoundnessShape()).clickable {
                                        scope.launch(Dispatchers.IO) {
                                            sectionLoadingId = sectionId
                                            try {
                                                val allMediaItems = mutableListOf<MediaItem>()
                                                if (section.items.fastAll { it is Innertube.SongItem } && section.moreEndpoint?.browseId != null) {
                                                    YtMusic.getPlaylist(section.moreEndpoint!!.browseId!!).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                }
                                                if (allMediaItems.isEmpty()) {
                                                    section.items.forEach { item ->
                                                        when (item) {
                                                            is Innertube.SongItem -> item.asSong?.asMediaItem?.let { allMediaItems.add(it) }
                                                            is Innertube.VideoItem -> allMediaItems.add(item.asMediaItem)
                                                            is Innertube.AlbumItem -> YtMusic.getAlbum(item.key).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                            is Innertube.PlaylistItem -> YtMusic.getPlaylist(item.key).getOrNull()?.songs?.map { it.asMediaItem }?.let { allMediaItems.addAll(it) }
                                                            else -> {}
                                                        }
                                                    }
                                                }
                                                if (allMediaItems.isNotEmpty()) {
                                                    withContext(Dispatchers.Main) {
                                                        binder?.stopRadio()
                                                        binder?.player?.forcePlay(allMediaItems.first())
                                                        binder?.player?.addMediaItems(allMediaItems.drop(1))
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toaster.e(R.string.no_song_found)
                                                    }
                                                }
                                            } finally {
                                                sectionLoadingId = null
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    section.moreEndpoint?.browseId?.let { browseId ->
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colorPalette().textSecondary,
                            modifier = Modifier.clip(uiRoundnessShape()).clickable {
                                val path = "$browseId?params=${section.moreEndpoint?.params}"

                                val route: NavRoutes = if( section.items.fastAll { it is Innertube.SongItem } )
                                    NavRoutes.playlist
                                else if( section.items.fastAll { it is Innertube.AlbumItem } )
                                    NavRoutes.artistAlbums
                                else if( section.items.fastAll { it is Innertube.VideoItem } )
                                    NavRoutes.artistVideos
                                else if( section.items.fastAll { it is Innertube.PlaylistItem } )
                                    NavRoutes.artistPlaylists
                                else
                                    return@clickable

                                route.navigateHere( navController, path )
                            }
                        )
                    }
                }

                if( section.items.fastAll { it is Innertube.SongItem } )
                    songs.forEachIndexed { index, song ->
                        SwipeablePlaylistItem(
                            mediaItem = song.asMediaItem,
                            onPlayNext = {
                                binder?.player?.addNext( song.asMediaItem )
                            }
                        ) {
                            SongItem(
                                song = song,
                                itemSelector = itemSelector,
                                navController = navController,
                                showThumbnail = true,
                                modifier = Modifier.background(colorPalette().background0),
                                onClick = {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(
                                        songs.map( Song::asMediaItem ),
                                        index
                                    )

                                    /*
                                        Due to the small size of checkboxes,
                                        we shouldn't disable [itemSelector]
                                     */
                                }
                            )
                        }
                    }

                // Works on both Albums and Single/EPs
                if( section.items.fastAll { it is Innertube.AlbumItem } )
                    LazyRow {
                        items(
                            items = section.items.fastMap { it as Innertube.AlbumItem },
                            key = Innertube.AlbumItem::key
                        ) { album ->
                            AlbumItem(
                                album = album,
                                alternative = true,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = {
                                        navController.navigate("${NavRoutes.album.name}/${album.key}")
                                    },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.display {
                                            OnlineAlbumItemMenu(
                                                navController = navController,
                                                album = album
                                            ).MenuComponent()
                                        }
                                    }
                                )
                            )
                        }
                    }

                if( section.items.fastAll { it is Innertube.PlaylistItem } )
                    LazyRow {
                        items(
                            items = section.items.fastMap { it as Innertube.PlaylistItem },
                            key = Innertube.PlaylistItem::key
                        ) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                alternative = true,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { navController.navigate("${NavRoutes.playlist.name}/${playlist.key}") },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.display {
                                            OnlinePlaylistItemMenu(
                                                navController = navController,
                                                playlist = playlist
                                            ).MenuComponent()
                                        }
                                    }
                                )
                            )
                        }
                    }


                if( section.items.fastAll { it is Innertube.VideoItem } )
                    LazyRow {
                        items(
                            items = section.items.fastMap { it as Innertube.VideoItem },
                            key = Innertube.VideoItem::key
                        ) { video ->
                            VideoItem(
                                video = video,
                                thumbnailHeightDp = 72.dp,
                                thumbnailWidthDp = 128.dp,
                                alternative = true,
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.display {
                                            VideoItemMenu(
                                                navController = navController,
                                                song = video.asMediaItem.asSong
                                            ).MenuComponent()
                                        }
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlay(video.asMediaItem)
                                    }
                                )
                            )

                        }
                    }

                if( section.items.fastAll { it is Innertube.ArtistItem } )
                    LazyRow {
                        items(
                            items = section.items.fastMap { it as Innertube.ArtistItem },
                            key = Innertube.ArtistItem::key
                        ) { artist ->
                            ArtistItem(
                                artist = artist,
                                alternative = true,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                disableScrollingText = disableScrollingText,
                                modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                    onClick = { navController.navigate("${NavRoutes.artist.name}/${artist.key}") },
                                    onLongClick = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.display {
                                            OnlineArtistItemMenu(
                                                navController = navController,
                                                artist = artist
                                            ).MenuComponent()
                                        }
                                    }
                                )
                            )
                        }
                    }
            }
        }
    }
}





