package app.n_zik.android.components.musicbrainz.insights

import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.themed.TextPlaceholder
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.shimmerEffect
import app.n_zik.android.colorPalette
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.typography
import app.n_zik.android.utils.semiBold
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
        InsightsText(
            text = stringResource(R.string.metadata_musicbrainz_no_rating),
            style = typography().xxs,
            color = colorPalette().textSecondary,
            modifier = modifier
        )
        return
    }

    val forceShimmer = forceLoadingShimmerPreview()
    val normalizedRating = (rating / 5f).coerceIn(0f, 1f)
    val stars = (normalizedRating * 5).roundToInt()

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (forceShimmer) {
                repeat(5) { Spacer(Modifier.size(14.dp).clip(uiRoundnessShape()).shimmerEffect()) }
            } else repeat(stars) {
                Icon(
                    painter = painterResource(R.drawable.star_brilliant),
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(14.dp)
                )
            }
            if (!forceShimmer) repeat(5 - stars) {
                Icon(
                    painter = painterResource(R.drawable.star_brilliant),
                    contentDescription = null,
                    tint = colorPalette().textDisabled,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            InsightsText(
                text = String.format("%.1f", rating),
                style = typography().xs.semiBold
            )
        }
        if (votes != null && votes > 0) {
            InsightsText(
                text = pluralStringResource(R.plurals.metadata_musicbrainz_rating_vote_count_attribution, votes, votes),
                style = typography().xxs,
                color = colorPalette().textSecondary
            )
        }
    }
}


@Composable
internal fun InsightsText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    if (forceLoadingShimmerPreview()) {
        val measuredStyle = if (textAlign == null) style else style.copy(textAlign = textAlign)
        MeasuredMetadataPlaceholder(text, measuredStyle, modifier, maxLines, overflow)
    } else {
        Text(text, modifier = modifier, style = style, color = color, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
    }
}

@Composable
internal fun InsightsThumbnail(thumbnailUrl: String?, shape: Shape, modifier: Modifier) {
    if (forceLoadingShimmerPreview()) {
        Spacer(modifier.clip(shape).shimmerEffect())
    } else {
        ImageCacheFactory.Thumbnail(thumbnailUrl = thumbnailUrl, shape = shape, modifier = modifier.clip(shape))
    }
}

@Composable
internal fun InsightsLoadingHeader(size: Dp, shape: Shape) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.size(size).clip(shape).shimmerEffect())
        TextPlaceholder(Modifier.width(160.dp))
    }
}
