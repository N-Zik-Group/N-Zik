package app.n_zik.android.components.menu.album

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.MenuMetadataText
import app.n_zik.android.components.menu.distinctMenuMetadataSubtitle
import app.n_zik.android.components.menu.menuMetadataValue
import app.n_zik.android.typography
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold

@Composable
internal fun AlbumMenuMetadata(
    title: String?,
    authors: String?,
    year: String?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val marquee = Modifier.conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
    val titleText = menuMetadataValue(title) ?: if (loading) null else stringResource(R.string.metadata_unknown_album)
    val artistText = menuMetadataValue(authors) ?: if (loading) null else stringResource(R.string.metadata_unknown_artist)
    val yearText = menuMetadataValue(year) ?: if (loading) null else stringResource(R.string.metadata_unknown_year)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        titleText?.let {
            MenuMetadataText(
                text = it,
                style = typography().xs.semiBold.copy(color = colorPalette().text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = marquee,
            )
        }
        distinctMenuMetadataSubtitle(titleText, artistText)?.let {
            MenuMetadataText(
                text = it,
                style = typography().xs.semiBold.secondary.copy(color = colorPalette().textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = marquee,
            )
        }
        yearText?.let {
            MenuMetadataText(
                text = it,
                style = typography().xxs.semiBold.secondary.copy(color = colorPalette().textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
