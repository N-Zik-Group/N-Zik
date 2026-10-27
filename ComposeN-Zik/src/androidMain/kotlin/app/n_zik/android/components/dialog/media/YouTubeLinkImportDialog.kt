package app.n_zik.android.components.dialog.media

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.InputDialog
import androidx.core.net.toUri

/**
 * Centralized YouTube link import dialog.
 * Takes a URL and calls [onImport] with the extracted playlist ID.
 *
 * @param onImport Called with the extracted playlist ID (e.g. "PLxxx...")
 * @param onDismiss Called when the dialog is dismissed
 */
@Composable
fun YouTubeLinkImportDialog(
    onImport: (playlistId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var youTubeUrl by remember { mutableStateOf("") }

    DialogFrame(
        onDismiss = onDismiss,
        title = stringResource(R.string.import_via_youtube_link),
        footer = {
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        danger = true
                    ),
                    DialogButton(
                        text = stringResource(R.string.import_button),
                        onClick = {
                            val playlistId = extractYouTubePlaylistId(youTubeUrl)
                            if (playlistId != null) {
                                onImport(playlistId)
                                onDismiss()
                            }
                        },
                        primary = true
                    )
                )
            )
        }
    ) {
        TextField(
            value = youTubeUrl,
            onValueChange = { youTubeUrl = it },
            placeholder = { BasicText("https://youtube.com/playlist?list=...", style = typography().xs.semiBold.copy(color = colorPalette().textDisabled)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                val playlistId = extractYouTubePlaylistId(youTubeUrl)
                if (playlistId != null) {
                    onImport(playlistId)
                    onDismiss()
                }
            }),
            colors = InputDialog.defaultTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun extractYouTubePlaylistId(url: String): String? {
    val prefixes = listOf(
        "https://www.youtube.com/playlist?",
        "https://youtube.com/playlist?",
        "https://music.youtube.com/playlist?",
        "https://m.youtube.com/playlist?"
    )
    return prefixes.find { url.startsWith(it) }
        ?.let { url.trim().toUri().getQueryParameter("list") }
}
