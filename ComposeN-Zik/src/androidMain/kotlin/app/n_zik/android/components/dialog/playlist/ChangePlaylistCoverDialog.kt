package app.n_zik.android.components.dialog.playlist

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import app.n_zik.android.components.dialog.common.CoverEditTarget
import app.n_zik.android.components.dialog.common.StagedCoverDialog
import app.n_zik.android.utils.playlist.savePlaylistThumbnail
import kotlinx.coroutines.CoroutineScope

class ChangePlaylistCoverDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    context: Context,
    scope: CoroutineScope,
    getPlaylistId: () -> Long?,
    getThumbnail: () -> String?,
    setThumbnail: (String) -> Unit,
) : StagedCoverDialog(
    activeState, valueState, context, scope,
    getTarget = { getPlaylistId()?.let { CoverEditTarget(it.toString(), getThumbnail()) } },
    savesOwnedArtwork = true,
    applyDraft = { target, uri, isCurrent ->
        val saved = savePlaylistThumbnail(context, uri, "playlist_${target.id}", isCurrent)
        if (saved != null && isCurrent()) {
            setThumbnail(saved.toString())
            true
        } else {
            false
        }
    },
) {
    companion object {
        @Composable
        operator fun invoke(
            getPlaylistId: () -> Long?,
            getThumbnail: () -> String?,
            setThumbnail: (String) -> Unit,
        ): ChangePlaylistCoverDialog {
            val currentId = rememberUpdatedState(getPlaylistId)
            val currentThumbnail = rememberUpdatedState(getThumbnail)
            val currentSetThumbnail = rememberUpdatedState(setThumbnail)
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val dialog = remember(context, scope) {
                ChangePlaylistCoverDialog(
                    mutableStateOf(false), mutableStateOf(TextFieldValue()), context, scope,
                    { currentId.value() }, { currentThumbnail.value() }, { currentSetThumbnail.value(it) },
                )
            }
            DisposableEffect(dialog) { onDispose { dialog.hideDialog() } }
            return dialog
        }
    }
}
