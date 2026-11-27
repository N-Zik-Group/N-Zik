package app.n_zik.android.components.dialog.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.AboveMenuDialogPresence
import app.n_zik.android.components.dialog.common.DialogActionList
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.ui.components.LocalMenuState

internal class ImportPlaylistFormatDialog(
    private val onImportNzik: () -> Unit,
    private val onImportSpotify: () -> Unit,
    private val onImportRiplay: () -> Unit,
    private val onImportYoutubeLink: () -> Unit,
    private val networkOptionsEnabled: () -> Boolean = { true },
) {
    var isOpen by mutableStateOf(false)
        private set
    private var onAccepted: (() -> Unit)? = null

    fun open(onAccepted: () -> Unit) {
        this.onAccepted = onAccepted
        isOpen = true
    }

    private fun dismiss() {
        isOpen = false
        onAccepted = null
    }

    @Composable
    fun Render() {
        val menuState = LocalMenuState.current
        AboveMenuDialogPresence(menuState = menuState, visible = isOpen) {
            fun select(action: () -> Unit) {
                if (!isOpen) return
                val accepted = onAccepted
                dismiss()
                accepted?.invoke()
                menuState.hide()
                action()
            }

            @Composable
            fun format(label: Int, icon: Int, action: () -> Unit) = DialogButton(
                text = stringResource(label),
                icon = icon,
                iconColor = Color.Unspecified,
                wrapLabel = true,
                onClick = { select(action) },
            )
            DialogActionList(
                title = stringResource(R.string.playlist_action_import),
                onDismiss = ::dismiss,
                actions = listOfNotNull(
                    format(R.string.playlist_import_format_nzik_csv, R.drawable.ic_launcher, onImportNzik),
                    format(R.string.playlist_import_format_exportify_csv, R.drawable.spotify, onImportSpotify),
                    format(R.string.playlist_import_format_riplay_csv, R.drawable.riplay, onImportRiplay),
                    if (networkOptionsEnabled()) format(R.string.settings_youtube_music_title, R.drawable.ytmusic) {
                        if (networkOptionsEnabled()) onImportYoutubeLink()
                    } else null,
                ),
            )
        }
    }
}
