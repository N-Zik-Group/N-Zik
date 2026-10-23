package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.models.Info
import app.it.fast4x.rimusic.utils.ExternalUris

/** "Listen on" dialog: opens the current media on an external app (YouTube / YouTube Music / Invidious) via the shared [ValueSelectorDialog]. */
@Composable
fun ListenOnDialog(
    mediaId: String,
    onDismiss: () -> Unit,
    onPlayOnUrl: (String) -> Unit
) {
    ValueSelectorDialog(
        title = stringResource(R.string.listen_on),
        onDismiss = onDismiss,
        selectedValue = Info("none", null),
        values = listOf(
            Info(
                ExternalUris.youtube(mediaId),
                stringResource(R.string.listen_on_youtube)
            ),
            Info(
                ExternalUris.youtubeMusic(mediaId),
                stringResource(R.string.listen_on_youtube_music)
            ),

            Info(
                ExternalUris.invidious(mediaId),
                stringResource(R.string.listen_on_invidious)
            )
        ),
        onValueSelected = {
            onPlayOnUrl(it.id)
        },
        valueText = { it.name ?: "" }
    )
}
