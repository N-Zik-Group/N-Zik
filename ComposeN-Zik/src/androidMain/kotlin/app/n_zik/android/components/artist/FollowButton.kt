package app.n_zik.android.components.artist

import app.n_zik.android.core.database.*
import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import it.fast4x.innertube.YtMusic
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.showDislikedArtistKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.syncPushArtistFollowKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.getSyncDirection
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.enums.SyncDirection
import app.n_zik.android.appContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.uiRoundnessShape

class FollowButton private constructor(
    private val getArtist: () -> Artist
): Button, Descriptive {

    companion object {
        @Composable
        operator fun invoke( getArtist: () -> Artist ): FollowButton =
            FollowButton(getArtist)
    }

    override val messageId: Int = R.string.follow

    override fun onShortClick() {
        if ( isYouTubeSyncEnabled() && !isNetworkConnected( appContext() ) ){
            Toaster.noInternet()
        } else {
            val artist = getArtist()
            val currentState = artist.let {
                when {
                    it.dislikedAt != null -> false // disliked
                    it.bookmarkedAt != null -> true // followed
                    else -> null // neutral
                }
            }

            val showDisliked = appContext().preferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() }?.isEnabled ?: true

            Database.asyncTransaction {
                if (showDisliked) {
                    artistTable.rotateLikeState( artist.id )
                } else {
                    artistTable.toggleBookmark( artist.id )
                }
            }

            val newState = if (showDisliked) {
                when(currentState) {
                    true -> false // followed → disliked
                    false -> null // disliked → neutral
                    null -> true  // neutral → followed
                }
            } else {
                when(currentState) {
                    true -> null  // followed → neutral
                    false -> true // disliked → followed
                    null -> true  // neutral → followed
                }
            }
            val messageId = if (showDisliked) {
                when(newState) {
                    true -> R.string.added_to_favorites
                    false -> R.string.added_to_dislikes
                    null -> R.string.removed_from_favorites
                }
            } else {
                when(newState) {
                    true -> R.string.added_to_favorites
                    null -> R.string.removed_from_favorites
                    else -> R.string.added_to_favorites
                }
            }
            with( artist ) {
                if( name != null )
                    Toaster.s( messageId, "\"$name\"" )
                else
                    Toaster.s( messageId )
            }

            // Only sync to YouTube if NOT disliked (dislike is local only)
            if (newState != false) {
                CoroutineScope( Dispatchers.IO ).launch {
                    if( !isYouTubeSyncEnabled() ) return@launch

                    val pushArtistFollow = appContext().preferences.getBoolean(syncPushArtistFollowKey, false)
                    if( !pushArtistFollow ) return@launch

                    val syncDirection = getSyncDirection()
                    if( syncDirection == SyncDirection.YT_TO_APP ) return@launch
                    if( !isNetworkConnected(appContext()) ) return@launch

                    if ( newState == null ) // unfollow
                        YtMusic.unsubscribeChannel( artist.id )
                    else // follow
                        YtMusic.subscribeChannel( artist.id )
                }
            }
        }
    }

    @Composable
    override fun ToolBarButton() {
        val likeState by remember {
            Database.artistTable
                    .likeState( getArtist().id )
        }.collectAsState( null, Dispatchers.IO )
        val colorPalette = colorPalette()

        val buttonProps: Triple<Int, Color, Color> = remember( likeState ) {
            val text = when(likeState) {
                true -> R.string.following
                false -> R.string.disliked
                null -> R.string.follow
            }
            val background = when(likeState) {
                true -> colorPalette.accent
                false -> colorPalette.red
                null -> colorPalette.background2
            }
            val foreground = when(likeState) {
                true -> colorPalette.onAccent
                false -> colorPalette.onAccent
                null -> colorPalette.text
            }

            Triple(text, background, foreground)
        }

        Box(
            modifier = Modifier.requiredSize(
                                   width = 100.dp,
                                   height = TabToolBar.TOOLBAR_ICON_SIZE
                               )
                               .clip( uiRoundnessShape() )
                               .background( buttonProps.second )
                               .clip(uiRoundnessShape()).clickable( onClick = ::onShortClick ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = stringResource( buttonProps.first ),
                style = typography().s.copy( color = buttonProps.third ),
            )
        }
    }
}




