package app.n_zik.android.components.ui.screens.album

import app.n_zik.android.core.database.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.DualIcon
import app.it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Icon
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.themed.IDialog
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.syncPushAlbumBookmarkKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.getSyncDirection
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.enums.SyncDirection
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.utils.preferences
import app.kreate.android.me.knighthat.utils.Toaster
import it.fast4x.innertube.YtMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class AlbumModifier private constructor(
    private val activeState: MutableState<Boolean>,
    private val onAction: Database.(String) -> Unit,
    override val iconId: Int,
    override val messageId: Int,
    override var value: String,
): MenuIcon, Descriptive, IDialog {

    companion object {
        @JvmStatic
        @Composable
        fun init(
            onAction: Database.(String) -> Unit,
            iconId: Int,
            messageId: Int,
            value: String,
            placeholder: String
        ): AlbumModifier = AlbumModifier(
            remember { mutableStateOf(false) },
            onAction,
            iconId,
            messageId,
            value
        )
    }

    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isActive: Boolean = activeState.value
        set(value) {
            activeState.value = value
            field = value
        }
    override val dialogTitle: String
        @Composable
        get() = menuIconTitle

    override fun onShortClick() = super.onShortClick()

    override fun onSet(newValue: String) =
        Database.asyncTransaction { onAction(newValue) }
}

@Composable
fun AlbumBookmark(
    albumId: String
): MenuIcon = object : MenuIcon, Descriptive, DualIcon {
    val likeState by remember(albumId) {
        Database.albumTable.likeState( albumId )
            .distinctUntilChanged()
    }.collectAsState( null, Dispatchers.IO )

    val album by remember(albumId) {
        Database.albumTable.findById( albumId )
            .distinctUntilChanged()
    }.collectAsState( null, Dispatchers.IO )

    override val iconId: Int = when(likeState) {
        true -> R.drawable.bookmark
        false -> R.drawable.bookmark_slash
        null -> R.drawable.bookmark_outline
    }
    override val secondIconId: Int = R.drawable.bookmark_outline
    override var isFirstIcon: Boolean = likeState != null
    override val messageId: Int = R.string.info_bookmark_album
    override val color: Color
        @Composable
        get() = when(likeState) {
            true -> colorPalette().accent
            false -> colorPalette().red
            null -> colorPalette().text
        }
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        CoroutineScope( Dispatchers.IO ).launch {
            val pushAlbumBookmark = appContext().preferences.getBoolean(syncPushAlbumBookmarkKey, false)
            val syncDir = getSyncDirection()
            // Only sync to YouTube if NOT disliked (dislike is local only)
            if (likeState != false && isYouTubeSyncEnabled() && pushAlbumBookmark && syncDir != SyncDirection.YT_TO_APP && isNetworkConnected(appContext())) {
                val playlistId = album?.shareUrl
                    ?.substringAfter("list=")
                    ?.takeIf { it.isNotBlank() }
                if (playlistId != null) {
                    if (likeState == true) YtMusic.removelikePlaylistOrAlbum(playlistId)
                    else YtMusic.likePlaylistOrAlbum(playlistId)
                }
            }
            Database.albumTable.rotateLikeState( albumId )
            val newState = when(likeState) {
                true -> null  // bookmarked → neutral
                false -> true // disliked → bookmarked
                null -> false // neutral → disliked
            }
            val messageId = when(newState) {
                true -> R.string.added_to_favorites
                false -> R.string.added_to_dislikes
                null -> R.string.removed_from_favorites
            }
            val albumName = album?.title
            if( albumName != null )
                Toaster.s( messageId, "\"$albumName\"" )
            else
                Toaster.s( messageId )
        }
    }
}

class Translate private constructor(
    activeState: MutableState<Boolean>,
    private val onLongClickAction: () -> Unit
): DynamicColor, Icon, Descriptive, Clickable {

    companion object {
        @JvmStatic
        @Composable
        fun init(onLongClick: () -> Unit = {}) = Translate(
            rememberSaveable { mutableStateOf(false) },
            onLongClick
        )
    }

    var isActive by activeState

    override val iconId: Int = R.drawable.translate
    override val messageId: Int = R.string.info_translation

    override var isFirstColor by activeState

    override fun onShortClick() { isFirstColor = !isFirstColor }
    override fun onLongClick() { onLongClickAction() }
}

