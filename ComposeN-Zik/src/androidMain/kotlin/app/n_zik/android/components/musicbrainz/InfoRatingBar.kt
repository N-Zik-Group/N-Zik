package app.n_zik.android.components.musicbrainz

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.musicbrainz.utils.toFlagEmoji

/**
 * Shows the artist's begin year and country flag emoji.
 */
@Composable
fun InfoBar(year: String?, countryCode: String?) {
    if (year == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.calendar),
            contentDescription = stringResource(R.string.metadata_year),
            tint = colorPalette().text,
            modifier = Modifier.size(14.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = year,
            style = typography().xs.semiBold,
            color = colorPalette().text
        )

        if (countryCode != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = countryCode.toFlagEmoji(),
                style = typography().xxs.semiBold,
                color = colorPalette().text
            )
        }
    }
}

/**
 * Shows the MusicBrainz rating value and vote count.
 */
@Composable
fun RatingBar(rating: Float?, votesCount: Int?) {
    if (rating == null || rating == 0f) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.star_brilliant),
            contentDescription = stringResource(R.string.metadata_musicbrainz_rating),
            tint = Color(0xFFFFC107),
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = String.format("%.1f", rating),
            style = typography().xs.semiBold,
            color = colorPalette().text
        )

        if (votesCount != null && votesCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.metadata_musicbrainz_rating_votes, votesCount),
                style = typography().xxs.semiBold,
                color = colorPalette().text
            )
        }
    }
}
