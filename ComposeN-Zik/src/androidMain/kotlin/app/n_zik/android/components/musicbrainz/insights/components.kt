package app.n_zik.android.components.musicbrainz.insights

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold
import kotlin.math.roundToInt

/**
 * Insights use the Settings card template, including its shared Glass surface.
 */
@Composable
fun InfoCard(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    SettingsSectionCard(
        title = title,
        icon = icon,
        modifier = modifier,
        content = { Column(content = content) },
    )
}

/**
 * Five-star rating display for the Insights screen.
 */
@Composable
fun RatingBar(
    rating: Float?,
    votes: Int?,
    modifier: Modifier = Modifier
) {
    if (rating == null) {
        Text(
            text = stringResource(R.string.mb_no_rating),
            style = typography().xxs,
            color = colorPalette().textSecondary,
            modifier = modifier
        )
        return
    }

    val normalizedRating = (rating / 5f).coerceIn(0f, 1f)
    val stars = (normalizedRating * 5).roundToInt()

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(stars) {
                Icon(
                    painter = painterResource(R.drawable.star_brilliant),
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(14.dp)
                )
            }
            repeat(5 - stars) {
                Icon(
                    painter = painterResource(R.drawable.star_brilliant),
                    contentDescription = null,
                    tint = colorPalette().textDisabled,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = String.format("%.1f", rating),
                style = typography().xs.semiBold
            )
        }
        if (votes != null && votes > 0) {
            Text(
                text = stringResource(R.string.mb_rating_votes_mb, votes),
                style = typography().xxs,
                color = colorPalette().textSecondary
            )
        }
    }
}

