package app.it.fast4x.rimusic.ui.screens.artist

import app.n_zik.android.core.database.*

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFirstOrNull
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.screens.artist.ArtistDetails
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.ArtistPage
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.enums.TransitionEffect
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.transitionEffectKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

import app.kreate.android.me.knighthat.utils.PropUtils
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.ui.components.themed.Loader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.n_zik.android.core.coil.ImageCacheFactory


import it.fast4x.innertube.requests.queue
import app.it.fast4x.rimusic.MODIFIED_PREFIX

@OptIn(ExperimentalAnimationApi::class, ExperimentalTextApi::class)
@UnstableApi
@ExperimentalFoundationApi
@Composable
fun ArtistScreenModern(
    navController: NavController,
    browseId: String,
    miniPlayer: @Composable () -> Unit = {},
) {
    // Essentials
    val saveableStateHolder = rememberSaveableStateHolder()

    // Settings
    val transitionEffect by rememberPreference( transitionEffectKey, TransitionEffect.Fade )
    val playerPosition by rememberPreference( playerPositionKey, PlayerPosition.Bottom )


    var selectedTabIndex by remember { mutableStateOf(0) }

    var localArtist: Artist? by remember { mutableStateOf( null ) }
    LaunchedEffect( Unit ) {
        Database.artistTable
                .findById( browseId )
                .flowOn( Dispatchers.IO )
                .collect { localArtist = it }
    }
    var artistPage: ArtistPage? by remember { mutableStateOf( null ) }

    LaunchedEffect( Unit ) {
        YtMusic.getArtistPage( browseId.removePrefix(MODIFIED_PREFIX) )
               .onSuccess { online ->
                   artistPage = online

                   Database.asyncTransaction {
                         val onlineArtist = Artist(
                             id = browseId,
                             name =  PropUtils.retainIfModified( localArtist?.name, online.artist.title ),
                             thumbnailUrl = PropUtils.retainIfModified( localArtist?.thumbnailUrl, online.artist.thumbnail?.url ),
                             timestamp = localArtist?.timestamp ?: System.currentTimeMillis(),
                             bookmarkedAt = localArtist?.bookmarkedAt,
                             isYoutubeArtist = localArtist?.isYoutubeArtist == true,
                             position = localArtist?.position ?: -1
                         )
                       artistTable.upsert( onlineArtist )

                       online.sections
                             .fastFirstOrNull { section ->
                                 section.items.fastAll { it is Innertube.SongItem }
                             }
                             ?.items
                             ?.map { (it as Innertube.SongItem).asMediaItem }
                             ?.also {
                                 mapIgnore( onlineArtist, *it.toTypedArray() )
                             }
                   }

                   // Batch fetch durations for videos and songs missing it
                   val itemsToFetch = online.sections.flatMap { it.items }
                       .filter { (it is Innertube.VideoItem && it.durationText == null) || (it is Innertube.SongItem && it.durationText == null) }
                       .map { it.key }
                       .distinct()

                   if (itemsToFetch.isNotEmpty()) {
                                               Innertube.queue(videoIds = itemsToFetch)?.onSuccess { queueItems ->
                           val durationsMap = queueItems?.associate { it.key to it.durationText } ?: emptyMap()
                           artistPage = artistPage?.withUpdatedVideoDurations(durationsMap)
                       }
                   }
               }
    }

    val thumbnailPainter = ImageCacheFactory.Painter( localArtist?.thumbnailUrl )

    Skeleton(
        navController = navController,
        tabIndex = selectedTabIndex,
        onTabChanged = { index -> selectedTabIndex = index },
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.overview), R.drawable.artist)
            item(1, stringResource(R.string.library), R.drawable.library)
        }
    ) { currentTabIndex ->
        when (currentTabIndex) {
            0 -> {
                if (artistPage == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Loader()
                    }
                } else {
                    ArtistDetails(navController, localArtist, artistPage, thumbnailPainter)
                }
            }
            1 -> {
                if (localArtist == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Loader()
                    }
                } else {
                    ArtistLocalSongs(navController, localArtist, artistPage, thumbnailPainter)
                }
            }
        }
    }

}


