package app.n_zik.android.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.R

@Composable
internal fun artistMoreFromTitle(artistName: String?): String =
    stringResource(R.string.artist_more_from_format, cleanPrefix(artistName.orEmpty()))
