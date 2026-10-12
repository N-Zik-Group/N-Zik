package app.n_zik.android.components.dialog.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.typography
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.semiBold

/**
 * Centralized match results dialog showing matched/failed counts
 * and the list of failed songs. Replaces inline implementations
 * in HomeSongsScreen and LocalPlaylistSongs.
 *
 * @param matched Number of successfully matched songs
 * @param failed Number of failed songs
 * @param failedSongs List of songs that failed to match
 * @param onRetry Called when user taps Retry (only shown if failed > 0)
 * @param onDismiss Called when user taps OK or dismisses the dialog
 */
@Composable
fun MatchResultsDialog(
    matched: Int,
    failed: Int,
    merged: Int = 0,
    failedSongs: List<Song>,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    DialogFrame(
        onDismiss = onDismiss,
        title = stringResource(R.string.match_results_title)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        if (failed == 0) {
            BasicText(
                text = stringResource(R.string.match_results_all_matched),
                style = typography().s.copy(color = colorPalette().text),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            BasicText(
                text = stringResource(R.string.match_results_some_failed, matched, failed),
                style = typography().s.copy(color = colorPalette().text),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (merged > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = stringResource(R.string.match_results_merged, merged),
                style = typography().xs.copy(color = colorPalette().textSecondary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (failed > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = stringResource(R.string.match_results_failed_songs),
                style = typography().xs.copy(color = colorPalette().textSecondary),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
            ) {
                items(failedSongs.size) { index ->
                    val song = failedSongs[index]
                    BasicText(
                        text = "${song.title} - ${song.artistsText ?: ""}",
                        style = typography().xs.copy(color = colorPalette().text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        ButtonRow(
            buttons = buildList {
                if (failed > 0 && onRetry != null) {
                    add(
                        DialogButton(
                            text = stringResource(R.string.retry_match),
                            onClick = onRetry
                        )
                    )
                }
                add(
                    DialogButton(
                        text = stringResource(R.string.confirm),
                        onClick = onDismiss,
                        primary = true
                    )
                )
            }
        )
    }
}
