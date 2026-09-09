package app.it.fast4x.rimusic.ui.screens.album

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.core.database.*

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import androidx.compose.ui.graphics.painter.Painter

import app.it.fast4x.compose.persist.PersistMapCleanup
import it.fast4x.innertube.Innertube
import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.themed.AutoResizeText
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.FontSizeRange
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.ItemsList
import app.it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.items.AlbumItemPlaceholder
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.ui.items.SongItemPlaceholder

import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.align
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.fadingEdge
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.formatAsTime
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.utils.otherLanguageAppAlbumKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import app.n_zik.android.components.SongItem
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.components.album.AlbumModifier
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.tab.Radio
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.components.ui.screens.DynamicOrientationLayout
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.download.utils.MyDownloadHelper
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.components.ui.screens.album.AlbumBookmark
import app.n_zik.android.components.ui.screens.album.Translate
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import timber.log.Timber
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun AlbumDetails(
    navController: NavController,
    browseId: String,
    album: Album?,
    thumbnailPainter: Painter,
    alternatives: List<Innertube.AlbumItem>,

    description: String,
    loadedSongsCount: Int = 0,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    PersistMapCleanup( "album/${browseId}/songs" )

    // Essentials
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val lazyListState = rememberLazyListState()

    // Settings
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val rawItems by remember {
        Database.songAlbumMapTable
                .allSongsOf( browseId )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )

    val items = remember(rawItems, parentalControlEnabled) {
        rawItems.filter { !parentalControlEnabled || it.title.startsWith(EXPLICIT_PREFIX, true) != true }
    }

    val itemSelector = ItemSelector<Song>()

    fun getSongs() = itemSelector.ifEmpty { items }
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val bookmark = AlbumBookmark( browseId )
    
    LaunchedEffect(items) {
        if (items.isEmpty() && album == null) {
            // Force fetch or similar if needed, but usually AlbumDetails is called with a pre-fetched album
        }
    }

    val deleteAllDownloadsDialog = DeleteAllDownloadedSongsDialog( ::getSongs )
    val downloadALlDialog = DownloadAllSongsDialog( ::getSongs )
    val shuffle = SongShuffler {
        getMediaItems().map( MediaItem::asSong )
    }
    val radio = Radio( ::getSongs )
    val locator = Locator( lazyListState, ::getSongs )
    val playNext = PlayNext {
        getMediaItems().let {
            binder?.player?.addNext( it, appContext() )

            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    }
    val enqueue = Enqueue {
        getMediaItems().let {
            binder?.player?.enqueue( it, appContext() )

            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    }
    val addToPlaylist = PlaylistsMenu.init(
        navController,
        { getMediaItems() },
        { throwable, preview ->
            Timber.tag("AlbumDetails").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        {
            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    )
    //<editor-fold defaultstate="collapsed" desc="Album modifiers">
    val changeTitle = AlbumModifier(
        iconId = R.drawable.title_edit,
        messageId = R.string.update_title,
        getDefaultValue = { album?.cleanTitle() ?: "" },
    ) {
        updateTitle( browseId, "$MODIFIED_PREFIX$it" )
    }
    val changeAuthors = AlbumModifier(
        iconId = R.drawable.artists_edit,
        messageId = R.string.update_authors,
        getDefaultValue = { album?.cleanAuthorsText() ?: "" },
    ) {
        updateAuthors( browseId, "$MODIFIED_PREFIX$it" )
    }
    val changeCover = AlbumModifier(
        iconId = R.drawable.cover_edit,
        messageId = R.string.update_cover,
        getDefaultValue = { album?.thumbnailUrl ?: "" },
    ) {
        updateCover( browseId, "$MODIFIED_PREFIX$it" )
    }
    //</editor-fold>
    ///<editor-fold defaultstate="collapsed" desc="Translator">
    var showTranslateLanguageDialog by remember { mutableStateOf(false) }
    val translate = Translate.init(onLongClick = { showTranslateLanguageDialog = true })
    val translator = Translator(NetworkClientFactory.getTranslatorClient())
    var otherLanguageApp by rememberPreference(otherLanguageAppAlbumKey, Languages.System)
    val appLangAlbum = Locale.getDefault().language
    val activeTranslateLang = remember(otherLanguageApp, appLangAlbum) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else Languages.entries.firstOrNull { it.code == appLangAlbum } ?: Languages.English
    }
    val languageDestination = activeTranslateLang.translatorLanguage
    //</editor-fold>

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailAlbumSizeDp = Dimensions.thumbnails.album

    val thumbnailAlbumSizePx = thumbnailAlbumSizeDp.px

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    downloadALlDialog.Render()
    deleteAllDownloadsDialog.Render()
    changeTitle.Render()
    changeAuthors.Render()
    changeCover.Render()

    DynamicOrientationLayout( thumbnailPainter ) {
        Box(
            Modifier.fillMaxSize()
                .background( colorPalette().background0 ),
            contentAlignment = Alignment.Center
        ) {
            if (items.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Loader()
                    if (loadedSongsCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        BasicText(
                            text = stringResource(R.string.loading_songs_count, loadedSongsCount),
                            style = typography().xxs.copy(
                                color = colorPalette().textDisabled
                            )
                        )
                    }
                }
            } else {
                val albumSongIds = remember(items) { items.map { it.id } }
                val likeStatesMap by remember(albumSongIds) {
                    LikeStateManager.getLikeStates(albumSongIds)
                }.collectAsState(emptyMap(), Dispatchers.IO)

                val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle()
                val downloadedIds by remember {
                    derivedStateOf {
                        downloadsMapState.values
                            .filter { it.state == Download.STATE_COMPLETED }
                            .mapTo(HashSet()) { it.request.id }
                    }
                }
                val downloadStatesMap by remember {
                    derivedStateOf {
                        items.associate { song ->
                            song.id to if (song.id in downloadedIds) DownloadedStateMedia.DOWNLOADED else DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                        }
                    }
                }

                CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
                LazyColumn(
                    state = lazyListState,
                    userScrollEnabled = items.isNotEmpty(),
                    contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
                    modifier = Modifier.fillMaxSize()
                        .background( colorPalette().background0 )
                ) {
                    item( "header" ) {
                    Box( Modifier.fillMaxWidth() ) {
                        if (!isLandscape)
                            Image(
                                painter = thumbnailPainter,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.aspectRatio( 4f / 3 )      // Limit height
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                                    .fadingEdge(
                                        top = WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                        bottom = Dimensions.fadeSpacingBottom
                                    )
                            )

                        AutoResizeText(
                            text = cleanPrefix( album?.title ?: "..." ),
                            style = typography().l.semiBold,
                            fontSizeRange = FontSizeRange(32.sp, 38.sp),
                            fontWeight = typography().l.semiBold.fontWeight,
                            fontFamily = typography().l.semiBold.fontFamily,
                            color = typography().l.semiBold.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align( Alignment.BottomCenter )
                                .padding( horizontal = 30.dp )
                                .conditional( !disableScrollingText ) {
                                    basicMarquee(iterations = Int.MAX_VALUE)
                                }
                        )

                        HeaderIconButton(
                            icon = R.drawable.share_social,
                            color = colorPalette().text,
                            iconSize = 24.dp,
                            modifier = Modifier.align( Alignment.TopEnd )
                                .padding( top = 5.dp, end = 5.dp ),
                            onClick = {
                                album?.shareUrl?.let { url ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }

                                    context.startActivity(
                                        Intent.createChooser( sendIntent, null )
                                    )
                                }
                            }
                        )
                    }
                }

                item( "album_details" ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val year = album?.year ?: "0000"
                        val songCount = "${items.size} ${stringResource( R.string.songs )}"
                        val totalDuration = items.sumOf {
                            durationTextToMillis(it.durationText ?: "")
                        }.let( ::formatAsTime )

                        BasicText(
                            text = "$year - $songCount - $totalDuration",
                            style = typography().xs.medium,
                            maxLines = 1
                        )
                    }
                }

                item( "action_buttons" ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Place this button alone so we can space it further from other buttons
                        bookmark.ToolBarButton()

                        Spacer( Modifier.width(15.dp) )

                        TabToolBar.Buttons(
                            downloadALlDialog,
                            deleteAllDownloadsDialog,
                            shuffle,
                            radio,
                            locator,
                            itemSelector,
                            changeTitle,
                            changeAuthors,
                            changeCover,
                            playNext,
                            enqueue,
                            addToPlaylist,
                            modifier = Modifier.fillMaxWidth( .8f )
                        )
                    }
                }

                if (description.isNotBlank()) {
                    item("description") {
                        var isDescriptionExpanded by remember { mutableStateOf(false) }
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
                                        Timber.tag("AlbumDetails").e(e, "Failed to translate text")
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
                                
                                if (showTranslateLanguageDialog) {
                                    ValueSelectorDialog(
                                        title = stringResource(R.string.info_translation),
                                        selectedValue = otherLanguageApp,
                                        onValueSelected = {
                                            otherLanguageApp = it
                                            translate.isActive = it.translatorLanguage != Language.ENGLISH
                                            showTranslateLanguageDialog = false
                                        },
                                        valueText = { it.text },
                                        values = Languages.entries.toList(),
                                        onDismiss = { showTranslateLanguageDialog = false }
                                    )
                                }
                                
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


                item( "songsTitle" ) {
                    BasicText(
                        text = stringResource(R.string.songs),
                        style = typography().m.semiBold.align(TextAlign.Start),
                        modifier = sectionTextModifier.fillMaxWidth()
                    )
                }


                itemsIndexed(
                    items = items,
                    key = { _, song -> song.id }
                ) { index, song ->

                    SwipeablePlaylistItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(song.asMediaItem)
                        }
                    ) {
                        SongItem(
                            song = song,
                            isLiked = likeStatesMap[song.id],
                            itemSelector = itemSelector,
                            navController = navController,
                            showThumbnail = false,
                            modifier = Modifier,

                            thumbnailOverlay = {
                                BasicText(
                                    text = "${index + 1}",
                                    style = typography().s
                                        .semiBold
                                        .center
                                        .color(
                                            colorPalette()
                                                .textDisabled
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .width(thumbnailSizeDp)
                                        .align(Alignment.Center)
                                )
                            },
                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    getMediaItems(),
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

                if( alternatives.isNotEmpty() )
                    item( "alternatives" ) {
                        // Section text
                        BasicText(
                            text = stringResource( R.string.album_alternative_versions ),
                            style = typography().m.semiBold,
                            maxLines = 1,
                            modifier = Modifier.padding( all = 16.dp )
                        )

                        val altAlbumKeys = remember(alternatives) { alternatives.map { it.key } }
                        val altBookmarkStatesMap by remember(altAlbumKeys) {
                            BookmarkStateManager.getAlbumBookmarkStates(altAlbumKeys)
                        }.collectAsStateWithLifecycle(emptyMap())

                        // List all alternatives
                        ItemsList(
                            tag = "album/$browseId/alternatives_list",
                            headerContent = {},
                            initialPlaceholderCount = 1,
                            continuationPlaceholderCount = 1,
                            emptyItemsText = stringResource( R.string.album_no_alternative_version ),
                            itemsPageProvider = {
                                Result.success(
                                    Innertube.ItemsPage( alternatives, null )
                                )
                            },
                            itemContent = { album ->
                                AlbumItem(
                                    alternative = true,
                                    album = album,
                                    thumbnailSizePx = thumbnailAlbumSizePx,
                                    thumbnailSizeDp = thumbnailAlbumSizeDp,
                                    bookmarkState = altBookmarkStatesMap[album.key],
                                    modifier = Modifier
                                        .clip(uiRoundnessShape()).clickable {
                                            navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                                        },
                                    disableScrollingText = disableScrollingText
                                )
                            },
                            itemPlaceholderContent = {
                                AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                            }
                        )
                    }


            }
                }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if ( showFloatingIcon )
                MultiFloatingActionsContainer(
                    iconId = R.drawable.shuffle,
                    onClick = shuffle::onShortClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )
            }
        }
    }
}



