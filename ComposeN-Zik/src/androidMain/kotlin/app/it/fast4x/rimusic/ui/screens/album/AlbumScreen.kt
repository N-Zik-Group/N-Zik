package app.it.fast4x.rimusic.ui.screens.album

import app.n_zik.android.core.database.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastMapNotNull
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.core.coil.ImageCacheFactory
import app.it.fast4x.compose.persist.PersistMapCleanup
import app.it.fast4x.compose.persist.persist
import app.it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import app.n_zik.android.core.database.Database
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.parseArtists
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.transitionEffectKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

import app.kreate.android.me.knighthat.utils.PropUtils
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import timber.log.Timber


@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun AlbumScreen(
    navController: NavController,
    browseId: String,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit = {}
) {
    PersistMapCleanup( tagPrefix = "album/$browseId/" )

    // Essentials
    val saveableStateHolder = rememberSaveableStateHolder()

    // Settings
    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Fade)
    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)

    var album by persist<Album?>("album/$browseId")
    LaunchedEffect(Unit) {
        Database.albumTable
                .findById( browseId )
                .flowOn( Dispatchers.IO )
                .distinctUntilChanged()
                .collect { album = it }
    }

    var alternatives by persistList<Innertube.AlbumItem>( "album/$browseId/alternatives" )
    var description by rememberSaveable { mutableStateOf("") }
    var loadedSongsCount by remember { mutableIntStateOf(0) }
    LaunchedEffect( Unit ) {
        Timber.tag("ALBUM-DETAILS-DEBUG").w("========== ALBUM DETAILS [GET ALBUM] ==========")
        Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] browseId from navigation: $browseId")
        Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] MODIFIED_PREFIX: ${app.it.fast4x.rimusic.MODIFIED_PREFIX}")
        val cleanedBrowseId = browseId.removePrefix(MODIFIED_PREFIX)
        Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Cleaned browseId: $cleanedBrowseId")
        Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Was prefix removed? ${browseId != cleanedBrowseId}")

        YtMusic.getAlbum( browseId.removePrefix(MODIFIED_PREFIX), true, onProgress = { loadedSongsCount = it } )
               .onSuccess { online ->
                   val onlineAlbum = online.album
                    val authorsText: String? = onlineAlbum.authors.parseArtists().joinToString(", ")

                   Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Result: SUCCESS")
                   Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Album title: ${onlineAlbum.title}")
                   Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Album year: ${onlineAlbum.year}")
                   Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Album authors: ${onlineAlbum.authors?.map { it.name }}")
                   Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Album thumbnail: ${onlineAlbum.thumbnail?.url}")
                   Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Songs count: ${online.songs.size}")
                   Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Album URL: ${online.url}")
                   online.songs.forEachIndexed { index, song ->
                       Timber.tag("ALBUM-DETAILS-DEBUG").w("[GET ALBUM] Song[$index]: ${song.info?.name} (${song.info?.endpoint?.videoId})")
                   }
                   Timber.tag("ALBUM-DETAILS-DEBUG").w("========== END ALBUM DETAILS [GET ALBUM] ==========")

                    Database.asyncTransaction {
                         val now = System.currentTimeMillis()
                         val inserted = albumTable.insertMetadata(
                             id = browseId,
                             title = onlineAlbum.title,
                             thumbnailUrl = onlineAlbum.thumbnail?.url,
                             year = onlineAlbum.year,
                             authorsText = authorsText,
                             shareUrl = online.url,
                             timestamp = now,
                             isYoutubeAlbum = false,
                             position = -1,
                             lastFetch = now
                         )
                         if (inserted == -1L) {
                             albumTable.updateMetadata(
                                 id = browseId,
                                 title = PropUtils.retainIfModified(album?.title, onlineAlbum.title),
                                 thumbnailUrl = PropUtils.retainIfModified(album?.thumbnailUrl, onlineAlbum.thumbnail?.url),
                                 year = onlineAlbum.year,
                                 authorsText = PropUtils.retainIfModified(album?.authorsText, authorsText),
                                 shareUrl = online.url,
                                 isYoutubeAlbum = album?.isYoutubeAlbum == true,
                                 position = album?.position ?: -1,
                                 lastFetch = now
                             )
                         }

                         songAlbumMapTable.clear(browseId)

                         online.songs
                              .map( Innertube.SongItem::asMediaItem )
                              .onEach( ::insertIgnore )
                              .mapIndexed { position, mediaItem ->
                                  SongAlbumMap(
                                      songId = mediaItem.mediaId,
                                      albumId = browseId,
                                      position = position
                                  )
                              }
                              .also( songAlbumMapTable::upsert )
                   }

                   alternatives = online.otherVersions
                   description = online.description ?: ""
               }
    }

    val thumbnailPainter = ImageCacheFactory.Painter( album?.thumbnailUrl )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            .navigationBarsPadding()
    ) {

        Row(
            modifier = Modifier.fillMaxSize()
                //.background( colorPalette().background0 )
        ) {
            val topPadding = if ( UiType.ViMusic.isCurrent() ) 30.dp else 0.dp

            AnimatedContent(
                    targetState = 0,
                    transitionSpec = {
                        when (transitionEffect) {
                            TransitionEffect.None -> EnterTransition.None togetherWith ExitTransition.None
                            TransitionEffect.Expand -> expandIn(
                                animationSpec = tween(
                                    350,
                                    easing = LinearOutSlowInEasing
                                ), expandFrom = Alignment.BottomStart
                            ).togetherWith(
                                shrinkOut(
                                    animationSpec = tween(
                                        350,
                                        easing = FastOutSlowInEasing
                                    ), shrinkTowards = Alignment.CenterStart
                                )
                            )

                            TransitionEffect.Fade -> fadeIn(animationSpec = tween(350)).togetherWith(
                                fadeOut(animationSpec = tween(350))
                            )

                            TransitionEffect.Scale -> scaleIn(animationSpec = tween(350)).togetherWith(
                                scaleOut(animationSpec = tween(350))
                            )

                            TransitionEffect.SlideHorizontal, TransitionEffect.SlideVertical -> {
                                val slideDirection = when (targetState > initialState) {
                                    true -> {
                                        if (transitionEffect == TransitionEffect.SlideHorizontal)
                                            AnimatedContentTransitionScope.SlideDirection.Left
                                        else AnimatedContentTransitionScope.SlideDirection.Up
                                    }

                                    false -> {
                                        if (transitionEffect == TransitionEffect.SlideHorizontal)
                                            AnimatedContentTransitionScope.SlideDirection.Right
                                        else AnimatedContentTransitionScope.SlideDirection.Down
                                    }
                                }

                                val animationSpec = spring(
                                    dampingRatio = 0.9f,
                                    stiffness = Spring.StiffnessLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold
                                )

                                slideIntoContainer(
                                    slideDirection,
                                    animationSpec
                                ) togetherWith
                                        slideOutOfContainer(slideDirection, animationSpec)
                            }
                        }
                    },
                    label = "",
                    modifier = Modifier.fillMaxHeight()
                        .padding( top = topPadding )
                ) { currentTabIndex ->
                    saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                        when (currentTabIndex) {
                            0 -> AlbumDetails(
                                navController = navController,
                                browseId = browseId,
                                album = album,
                                thumbnailPainter = thumbnailPainter,
                                alternatives = alternatives,
                                description = description,
                                loadedSongsCount = loadedSongsCount,
                                onSearchClick = {
                                    navController.navigate(NavRoutes.search.name)
                                },
                                onSettingsClick = {
                                    navController.navigate(NavRoutes.settings.name)
                                }
                            )
                        }
                    }
            }
        }

        //**
        Box(
            modifier = modifier.padding( vertical = 5.dp ).align(
                if( playerPosition == PlayerPosition.Top ) Alignment.TopCenter else Alignment.BottomCenter
            )
        ) {
            miniPlayer.invoke()
        }
    }
}



