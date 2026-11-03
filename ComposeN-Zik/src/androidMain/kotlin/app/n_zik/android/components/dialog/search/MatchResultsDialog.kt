package app.n_zik.android.components.dialog.search

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/**
 * Centralized match results dialog: matched/failed counts and the list of failed songs
 * (replaces the inline implementations in HomeSongsScreen and LocalPlaylistSongs).
 *
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
        bodyPadding = PaddingValues(0.dp),
        title = stringResource(R.string.library_matching_results_title),
        footer = {
            ButtonRow(
                buttons = buildList {
                    if (failed > 0 && onRetry != null) {
                        add(
                            DialogButton(
                                text = stringResource(R.string.library_matching_action_retry),
                                onClick = onRetry
                            )
                        )
                    }
                    add(
                        DialogButton(
                            text = stringResource(R.string.common_action_close),
                            onClick = onDismiss,
                            primary = true
                        )
                    )
                }
            )
        }
    ) {
        Column(Modifier.weight(1f, false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            if (failed == 0) {
                BasicText(
                    text = stringResource(R.string.library_matching_all_success_message),
                    style = typography().s.copy(color = colorPalette().text),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                BasicText(
                    text = stringResource(R.string.library_matching_success_failure_count_format, matched, failed),
                    style = typography().s.copy(color = colorPalette().text),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (merged > 0) {
                BasicText(
                    text = stringResource(R.string.library_matching_merged_count_format, merged),
                    style = typography().xs.copy(color = colorPalette().textSecondary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (failed > 0) {
                BasicText(
                    text = stringResource(R.string.library_matching_failed_songs_description),
                    style = typography().xs.copy(color = colorPalette().textSecondary),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth()) {
                    failedSongs.forEach { song ->
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
        }
    }
}
