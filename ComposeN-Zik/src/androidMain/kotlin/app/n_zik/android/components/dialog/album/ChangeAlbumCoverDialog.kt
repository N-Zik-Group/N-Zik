package app.n_zik.android.components.dialog.album

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.models.Album
import app.n_zik.android.components.dialog.common.CoverEditTarget
import app.n_zik.android.components.dialog.common.StagedCoverDialog
import app.n_zik.android.components.image.saveOwnedImage
import kotlinx.coroutines.CoroutineScope

class ChangeAlbumCoverDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    context: Context,
    scope: CoroutineScope,
    updateTrackCovers: Boolean,
    getAlbum: () -> Album?,
) : StagedCoverDialog(
    activeState, valueState, context, scope,
    getTarget = {
        getAlbum()?.let { item ->
            CoverEditTarget(item.id, item.thumbnailUrl) { url ->
                albumTable.insertIgnore(item)
                albumTable.updateCover(item.id, url)
                if (updateTrackCovers) songTable.updateCoverForAlbum(item.id, url)
            }
        }
    },
    save = { context, uri, _, directory, name -> saveCoverArt(context, uri, directory, name) }
) {
    companion object {
        @Composable
        operator fun invoke(getAlbum: () -> Album?): ChangeAlbumCoverDialog =
            rememberDialog(updateTrackCovers = true, getAlbum = getAlbum)

        /** Uses the album details save behavior, preserving artwork set on individual tracks. */
        @Composable
        internal fun forAlbumDetails(getAlbum: () -> Album?): ChangeAlbumCoverDialog =
            rememberDialog(updateTrackCovers = false, getAlbum = getAlbum)

        @Composable
        private fun rememberDialog(
            updateTrackCovers: Boolean,
            getAlbum: () -> Album?,
        ): ChangeAlbumCoverDialog {
            val current = rememberUpdatedState(getAlbum)
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val dialog = remember(context, scope, updateTrackCovers) {
                ChangeAlbumCoverDialog(
                    mutableStateOf(false), mutableStateOf(TextFieldValue()), context, scope, updateTrackCovers,
                ) { current.value() }
            }
            DisposableEffect(dialog) { onDispose { dialog.hideDialog() } }
            return dialog
        }

        /** Saves only after confirmation; a failed decode keeps the existing cover. */
        internal fun saveCoverArt(context: Context, uri: Uri, dirPath: String, fileName: String): Uri? {
            return saveOwnedImage(context, uri, dirPath, fileName)
        }
    }
}
