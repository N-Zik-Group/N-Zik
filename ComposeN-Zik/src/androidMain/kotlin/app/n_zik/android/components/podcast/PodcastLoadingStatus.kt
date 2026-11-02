package app.n_zik.android.components.podcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.OfflineAwareLoader
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.ui.screens.CollectionPageStatus

/** Displays terminal failures and empty feeds separately from an active request. */
@Composable
internal fun PodcastLoadingStatus(status: PodcastPageStatus, isOnline: Boolean, onRetry: () -> Unit) {
    CollectionPageStatus {
        when (status) {
            PodcastPageStatus.Loading -> OfflineAwareLoader(
                isOnline = isOnline,
                modifier = Modifier.fillMaxSize().testTag("podcast-loading"),
            )
            PodcastPageStatus.Empty -> PodcastEmptyEpisodes()
            PodcastPageStatus.Error -> Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        if (isOnline) R.string.error else R.string.error_no_internet
                    ),
                    color = colorPalette().text,
                    textAlign = TextAlign.Center,
                )
                ButtonRow(
                    buttons = listOf(
                        DialogButton(
                            text = stringResource(R.string.retry),
                            onClick = onRetry,
                            primary = true,
                        )
                    ),
                )
            }
            PodcastPageStatus.Content -> Unit
        }
    }
}

/** Keeps an empty episode shelf visible below the valid podcast metadata and actions. */
@Composable
internal fun PodcastEmptyEpisodes() {
    Text(
        text = stringResource(R.string.no_items),
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        color = colorPalette().text,
        textAlign = TextAlign.Center,
    )
}
