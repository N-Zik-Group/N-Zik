package app.n_zik.android.components.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.core.database.CollectionFavorites
import app.n_zik.android.core.database.CollectionFavoritesChange
import app.n_zik.android.core.database.CollectionFavoritesSelection
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.collectionFavoriteSongId
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.coroutines.NzikDispatchers

internal enum class CollectionFavoritesKind {
    Playlist, Album, Podcast,
}

/** Builds an inline or overflow toolbar action from complete collection membership. */
@Composable
internal fun rememberCollectionFavoritesButton(
    mediaItems: List<MediaItem>,
    kind: CollectionFavoritesKind,
    enabled: Boolean = true,
    collectionKey: Any? = null,
    likeStates: Map<String, Boolean?>? = null,
): Button {
    val context = LocalContext.current.applicationContext
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val itemsBySongId = remember(mediaItems) {
        buildMap {
            for (item in mediaItems) {
                val songId = collectionFavoriteSongId(item.mediaId)
                if (songId.isNotBlank() && songId !in this) {
                    put(songId, if (songId == item.mediaId) item else item.buildUpon().setMediaId(songId).build())
                }
            }
        }
    }
    val songIds = itemsBySongId.keys
    val observedStates = if (likeStates == null && enabled && songIds.isNotEmpty()) {
        val flow = remember(collectionKey, songIds) { LikeStateManager.getLikeStates(songIds.toList()) }
        key(flow) {
            flow.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA).value
        }
    } else likeStates
    val filled = remember(songIds, observedStates) {
        songIds.isNotEmpty() && songIds.all { observedStates?.get(it) == true }
    }
    var pending by remember(collectionKey, kind, songIds, enabled) {
        mutableStateOf<CollectionFavoritesSelection?>(null)
    }
    var busy by remember(collectionKey, kind) { mutableStateOf(false) }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Timber.tag("CollectionFavorites").e(error, "Unable to update collection favorites")
                Toaster.e(R.string.library_collection_favorites_update_failed)
            } finally {
                busy = false
            }
        }
    }

    val canAct = enabled && songIds.isNotEmpty() && observedStates != null && !busy && pending == null
    val onOpen by rememberUpdatedState<() -> Unit> {
        if (canAct) {
            menuState.hide()
            runAction {
                pending = withContext(NzikDispatchers.DATA) { CollectionFavorites.resolve(context, songIds) }
            }
        }
    }

    DialogPresence(target = pending) { selection ->
        CollectionFavoritesDialog(
            kind = kind,
            change = selection.change,
            onDismiss = { pending = null },
            onConfirm = {
                if (pending == selection && !busy) {
                    pending = null
                    runAction {
                        val changed = withContext(NzikDispatchers.DATA) {
                            CollectionFavorites.apply(context, itemsBySongId, selection)
                        }
                        if (changed > 0) {
                            Toaster.s(
                                if (selection.change.like) R.string.library_favorites_added
                                else R.string.library_favorites_removed
                            )
                        }
                    }
                }
            },
        )
    }

    return remember(canAct, filled) { CollectionFavoritesButton(canAct, filled) { onOpen() } }
}

private class CollectionFavoritesButton(
    override val isEnabled: Boolean,
    private val filled: Boolean,
    private val onOpen: () -> Unit,
) : MenuIcon {
    @get:DrawableRes
    override val iconId: Int = if (filled) R.drawable.heart else R.drawable.heart_outline

    override val color: Color
        @Composable get() = if (isEnabled) colorPalette().text else colorPalette().textDisabled

    override val menuIconTitle: String
        @Composable get() = stringResource(collectionFavoritesAction(like = !filled))

    override fun onShortClick() = onOpen()

    @Composable
    override fun ToolBarButton() {
        val title = menuIconTitle
        val tint = color
        if (GlassToolbarIcon(
                icon = painterResource(iconId),
                tint = tint,
                size = sizeDp,
                enabled = isEnabled,
                onClick = ::onShortClick,
                contentDescription = title,
            )
        ) return
        TabToolBar.Icon(
            iconId = iconId,
            tint = tint,
            enabled = isEnabled,
            modifier = Modifier.semantics { contentDescription = title },
            onClick = ::onShortClick,
        )
    }
}

@Composable
private fun CollectionFavoritesDialog(
    kind: CollectionFavoritesKind,
    change: CollectionFavoritesChange,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val count = change.songIds.size
    val message = when (kind) {
        CollectionFavoritesKind.Playlist ->
            if (change.like) R.plurals.library_collection_favorites_add_playlist_tracks
            else R.plurals.library_collection_favorites_remove_playlist_tracks
        CollectionFavoritesKind.Album ->
            if (change.like) R.plurals.library_collection_favorites_add_album_tracks
            else R.plurals.library_collection_favorites_remove_album_tracks
        CollectionFavoritesKind.Podcast ->
            if (change.like) R.plurals.library_collection_favorites_add_episodes
            else R.plurals.library_collection_favorites_remove_episodes
    }
    ConfirmationDialog(
        title = stringResource(R.string.library_favorites_title),
        text = pluralStringResource(message, count, count),
        confirmText = stringResource(collectionFavoritesAction(change.like)),
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        dismissOnConfirm = false,
    )
}

private fun collectionFavoritesAction(like: Boolean): Int =
    if (like) R.string.library_favorites_action_add else R.string.library_collection_favorites_action_remove
