@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.n_zik.android.components

import app.n_zik.android.core.database.*
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.drawscope.Stroke
import app.it.fast4x.rimusic.utils.getDownloadProgress
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_SIZE_NORMAL
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_STROKE_WIDTH
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalDownloadStatesMap
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.components.themed.NowPlayingSongIndicator
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.ui.styling.favoritesOverlay
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.downloadedStateMedia
import app.it.fast4x.rimusic.utils.getDownloadState
import app.it.fast4x.rimusic.utils.getLikedIcon
import app.it.fast4x.rimusic.utils.currentMediaItemIdAsState
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.playlistindicatorKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.components.menu.song.SongItemMenu
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import app.it.fast4x.rimusic.MODIFIED_PREFIX


private interface SongIndicator: Icon {
    override val sizeDp: Dp
        get() = 18.dp
    override val color: Color
        @Composable
        get() = colorPalette().accent

    override fun onShortClick() { /* Does nothing */ }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun ToolBarButton() {
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = this.modifier
                .size(sizeDp)
                .clip(uiRoundnessShape())
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(
                        bounded = false,
                        radius = 20.dp
                    ),
                    enabled = isEnabled,
                    onClick = ::onShortClick,
                    onLongClick = {
                        if (this is Clickable)
                            this.onLongClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconId),
                contentDescription = null,
                modifier = Modifier.size(sizeDp),
                tint = if (isEnabled) color else color.copy(alpha = 0.5f)
            )
        }

        Spacer( Modifier.padding(horizontal = 3.dp) )
    }
}

@Composable
fun SongText(
    text: String,
    style: TextStyle,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    modifier: Modifier = Modifier
) = BasicText(
    text = text,
    style = style,
    maxLines = 1,
    overflow = overflow,
    modifier = modifier
)

/**
 *  Displays information of a song.
 *
 *  [onLongClick] overrides song's menu
 *
 *  @param song record from database
 *  @param itemSelector optional field to enable select box
 *  @param navController optional field to detect whether the
 *  current location is playlist to hide playlist indicator.
 *  @param isRecommended whether this song is selected by algorithm
 *  @param modifier applied to the outermost layer but not its content
 *  @param showThumbnail whether to fetch/show thumbnail of this song.
 *  [thumbnailOverlay] will still being shown regardless the state of this value.
 *  @param onLongClick what happens when user holds this item for a short while
 *  @param trailingContent content being placed to the rightmost of the card
 *  @param onClick what happens when user tap on this item
 */
@UnstableApi
@ExperimentalFoundationApi
@Composable
fun SongItem(
    song: Song,
    itemSelector: ItemSelector<Song>? = null,
    navController: NavController? = null,
    isRecommended: Boolean = false,
    isLiked: Boolean? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorPalette().background0,
    showThumbnail: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    thumbnailOverlay: @Composable BoxScope.() -> Unit = {},
    onClick: () -> Unit = {}
) {
    // Essentials
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val disableScrollingText by rememberPreference( disableScrollingTextKey, false )
    val hapticFeedback = LocalHapticFeedback.current

    val colorPalette = colorPalette()
    val currentMediaId by (binder?.player?.currentMediaItemIdAsState() ?: remember { mutableStateOf<String?>(null) })
    val isPlaying = currentMediaId == song.id

    val displaySong = song

    val menu = if( navController != null && onLongClick == null ) SongItemMenu( navController, displaySong ) else null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy( 12.dp ),
        modifier = modifier.fillMaxWidth()
                           .clip( uiRoundnessShape() )
                           .background( backgroundColor )
                           .conditional( isPlaying ) {
                               background( colorPalette.favoritesOverlay )
                           }
                           // This component must be placed before padding to prevent
                           // ripple effect to only highlight padded area
                           .clip(uiRoundnessShape()).combinedClickable(
                               onClick = onClick,
                               onLongClick = {
                                   hapticFeedback.performHapticFeedback( HapticFeedbackType.LongPress )

                                   onLongClick?.invoke()
                                   menu?.openMenu()
                               }
                           )
                           .padding(
                               vertical = Dimensions.itemsVerticalPadding,
                               horizontal = 16.dp
                           )
    )
 {
        // Song's thumbnail
        Box(
            Modifier.size( Dimensions.thumbnails.song )
        ) {
            // Actual thumbnail (from cache or fetch from url)
            if( showThumbnail ) {
                // Check if the image is in local cache
                val isCached = remember(displaySong.thumbnailUrl) {
                    ImageCacheFactory.isImageCached(displaySong.thumbnailUrl)
                }
                
                val isCustomImage = displaySong.thumbnailUrl?.let {
                    it.startsWith("file://") || it.contains("app_covers") || it.startsWith(MODIFIED_PREFIX)
                } == true

                ImageCacheFactory.Thumbnail(
                    thumbnailUrl = displaySong.thumbnailUrl,
                    contentScale = if (isCustomImage) ContentScale.Crop else ContentScale.FillHeight
                )
            }

            if( isPlaying )
                NowPlayingSongIndicator(
                    mediaId = displaySong.id,
                    player = binder?.player
                )

            thumbnailOverlay()

            val showLiked = isLiked ?: (displaySong.likedAt != null)
            if( showLiked )
                HeaderIconButton(
                    onClick = {},
                    icon = getLikedIcon(),
                    color = colorPalette().favoritesIcon,
                    iconSize = 12.dp,
                    modifier = Modifier.align( Alignment.BottomStart )
                                       .absoluteOffset( x = (-8).dp )
                )
        }

        // Song's information
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy( 4.dp ),
            modifier = Modifier.weight( 1f )
        ) {
            Row( verticalAlignment = Alignment.CenterVertically ) {
                // Show icon if song is recommended by the algorithm
                if( isRecommended )
                    object: SongIndicator {
                        override val iconId: Int = R.drawable.smart_shuffle
                    }.ToolBarButton()

                val showInPlaylistIndicator by rememberPreference( playlistindicatorKey,false )
                val isInPlaylistScreen = navController != null && NavRoutes.localPlaylist.isHere( navController )
                // Show icon if song belongs to a playlist,
                // except when in playlist.
                if( showInPlaylistIndicator && !isInPlaylistScreen ) {

                    val isExistedInAPlaylist by remember( showInPlaylistIndicator ) {
                        Database.songPlaylistMapTable.isMapped( displaySong.id )
                    }.collectAsStateWithLifecycle(initialValue = false)

                    if( isExistedInAPlaylist )
                        object: SongIndicator {
                            override val iconId: Int = R.drawable.playlist
                        }.ToolBarButton()
                }

                if( displaySong.title.hasExplicitPrefix() )
                    object: SongIndicator {
                        override val iconId: Int = R.drawable.explicit
                    }.ToolBarButton()

                val safeTitle = if (displaySong.title.isBlank() || displaySong.title == "null") stringResource(R.string.unknown_title) else displaySong.title
                // Song's name
                SongText(
                    text = cleanPrefix( safeTitle ),
                    style = typography().xs.semiBold,
                    modifier = Modifier.weight( 1f )
                                       .conditional( !disableScrollingText ) {
                                           basicMarquee( iterations = Int.MAX_VALUE )
                                       }
                )
            }

            Row( verticalAlignment = Alignment.CenterVertically ) {
                val artistsText = displaySong.cleanArtistsText()
                val safeArtists = if (artistsText.isBlank() || artistsText == "null") stringResource(R.string.unknown_artist) else artistsText
                // Song's author
                SongText(
                    text = safeArtists,
                    style = typography().xs.semiBold.secondary,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight( 1f )
                                       .conditional( !disableScrollingText ) {
                                           basicMarquee( iterations = Int.MAX_VALUE )
                                       }
                )

                /*
                    Song's duration
                    If it's "null", show --:-- instead of leaving it empty
                 */
                SongText(
                    text = displaySong.durationText ?: "--:--",
                    style = typography().xxs.secondary.medium,
                    modifier = Modifier.padding( top = 4.dp, start = 5.dp  )
                )

                Spacer( Modifier.padding(horizontal = 4.dp) )

                // Show download icon when song is NOT local
                if( !song.isLocal ) {
                    val downloadStatesMap = LocalDownloadStatesMap.current
                    val cacheState = downloadStatesMap[song.id] ?: downloadedStateMedia( song.id )
                    val downloadState = getDownloadState( song.id )

                    val color = when( cacheState ) {
                        DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED -> colorPalette().textDisabled
                        else                                          -> colorPalette().text
                    }

                    if (downloadState == Download.STATE_DOWNLOADING) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(uiRoundnessShape()).clickable {
                                    binder?.cache?.removeResource( song.id )
                                    Database.asyncTransaction {
                                        formatTable.deleteBySongId( song.id )
                                    }
                                    MyDownloadHelper.handleDownload( context, song, true )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val progress = getDownloadProgress(song.id)
                            if (progress > 0.01f && downloadState == Download.STATE_DOWNLOADING) {
                                CircularWavyProgressIndicator(
                                    progress = { progress },
                                    color = colorPalette().accent,
                                    trackColor = colorPalette().textDisabled,
                                    modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp),
                                    stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                                    trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
                                )
                            } else {
                                CircularWavyProgressIndicator(
                                    color = colorPalette().accent,
                                    trackColor = colorPalette().textDisabled,
                                    modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp),
                                    stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                                    trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
                                )
                            }
                        }
                    } else {
                        val icon = when( downloadState ) {
                            Download.STATE_REMOVING     -> R.drawable.download
                            Download.STATE_QUEUED       -> R.drawable.download_progress
                            Download.STATE_RESTARTING   -> R.drawable.download_progress
                            else                        -> cacheState.iconId
                        }
                        IconButton(
                            icon = icon,
                            color = color,
                            modifier = Modifier.size( 20.dp ),
                            onClick = {
                                binder?.cache?.removeResource( song.id )
                                Database.asyncTransaction {
                                    formatTable.deleteBySongId( song.id )
                                }
                                MyDownloadHelper.handleDownload( context, song, true )
                            }
                        )
                    }
                }
            }
        }

        if( itemSelector != null ) {
            // It must watch for [selectedItems.size] for changes
            // Otherwise, state will stay the same
            val checkedState = remember( itemSelector.size ) {
                mutableStateOf( song in itemSelector )
            }

            if( itemSelector.isActive )
                Icon(
                    painter = painterResource(if (checkedState.value) R.drawable.checked_filled else R.drawable.unchecked_outline),
                    contentDescription = null,
                    tint = if (checkedState.value) colorPalette().accent else colorPalette().text,
                    modifier = Modifier
                        .scale( 0.8f )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            val newValue = !checkedState.value
                            checkedState.value = newValue
                            if (newValue) itemSelector.add(song) else itemSelector.remove(song)
                        }
                        .padding(8.dp)
                )
        }

        trailingContent?.invoke( this )
    }
}







