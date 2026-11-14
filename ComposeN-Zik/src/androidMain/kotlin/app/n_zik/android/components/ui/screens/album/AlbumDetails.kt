package app.n_zik.android.components.ui.screens.album

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.core.database.*

import app.n_zik.android.components.glass.GlassDescriptionTranslate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.n_zik.android.ui.components.tab.toolbar.Clickable
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.DualIcon
import app.n_zik.android.ui.components.tab.toolbar.DynamicColor
import app.n_zik.android.ui.components.tab.toolbar.Icon
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.IDialog
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.utils.syncPushAlbumBookmarkKey
import app.n_zik.android.utils.syncDirectionKey
import app.n_zik.android.utils.getSyncDirection
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.showDislikedAlbumKey
import app.n_zik.android.utils.excludeDislikedAlbumsKey
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.appContext
import app.n_zik.android.utils.preferences
import app.n_zik.android.core.utils.Toaster
import app.n_zik.innertube.YtMusic
import app.n_zik.android.utils.coroutines.NzikDispatchers
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
    albumId: String,
    localOnly: Boolean = false,
): MenuIcon = object : MenuIcon, Descriptive, DualIcon {
    val preview = LocalThemePreview.current
    val likeState by remember(albumId, preview) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(null) else Database.albumTable.likeState( albumId )
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

    val album by remember(albumId, preview) {
        if (preview != null) kotlinx.coroutines.flow.flowOf(null) else Database.albumTable.findById( albumId )
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

    val showDisliked = rememberPreference(excludeDislikedAlbumsKey, DislikeMode.Enabled).value.isEnabled

    override val iconId: Int = when(likeState) {
        true -> R.drawable.bookmark
        false -> R.drawable.bookmark_slash
        null -> R.drawable.bookmark_outline
    }
    override val secondIconId: Int = R.drawable.bookmark_outline
    override var isFirstIcon: Boolean = likeState != null
    override val messageId: Int = R.string.album_action_bookmark
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
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            val pushAlbumBookmark = appContext().preferences.getBoolean(syncPushAlbumBookmarkKey, false)
            val syncDir = getSyncDirection()
            // Only sync to YouTube if NOT disliked (dislike is local only)
            if (!localOnly && likeState != false && isYouTubeSyncEnabled() && pushAlbumBookmark && syncDir != SyncDirection.YT_TO_APP && isNetworkConnected(appContext())) {
                val playlistId = album?.shareUrl
                    ?.substringAfter("list=")
                    ?.takeIf { it.isNotBlank() }
                if (playlistId != null) {
                    if (likeState == true) YtMusic.removelikePlaylistOrAlbum(playlistId)
                    else YtMusic.likePlaylistOrAlbum(playlistId)
                }
            }
            if (showDisliked) {
                Database.albumTable.rotateLikeState( albumId )
            } else {
                Database.albumTable.toggleBookmark( albumId )
            }
            val newState = if (showDisliked) {
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
            val messageId = if (showDisliked) {
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
    override val messageId: Int = R.string.common_translation_title

    override var isFirstColor by activeState

    @Composable
    override fun ToolBarButton() {
        GlassDescriptionTranslate(color, ::onShortClick, ::onLongClick) { super<Icon>.ToolBarButton() }
    }

    override fun onShortClick() { isFirstColor = !isFirstColor }
    override fun onLongClick() { onLongClickAction() }
}

