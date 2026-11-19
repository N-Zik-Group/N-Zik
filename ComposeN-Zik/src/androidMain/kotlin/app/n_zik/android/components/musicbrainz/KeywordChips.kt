package app.n_zik.android.components.musicbrainz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.shimmerEffect
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassSuggestionChip
import app.n_zik.android.typography
import app.n_zik.android.utils.semiBold

/**
 * Displays genre/tag keywords as suggestion chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordChips(
    keywords: List<String>?,
    isLoading: Boolean = false,
    onKeywordClick: (String) -> Unit = {}
) {
    if (keywords.isNullOrEmpty()) return

    val forceShimmer = forceLoadingShimmerPreview() || isLoading
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        keywords.forEach { keyword ->
            val title = keyword.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            GlassSuggestionChip(
                onClick = { onKeywordClick(keyword) },
                enabled = !forceShimmer,
                label = {
                    Text(
                        text = title,
                        style = typography().xxs.semiBold,
                        modifier = if (forceShimmer) Modifier
                            .clip(uiRoundnessShape())
                            .shimmerEffect()
                            .drawWithContent {}
                            .clearAndSetSemantics {}
                        else Modifier,
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = colorPalette().background1,
                    labelColor = colorPalette().text
                ),
                border = BorderStroke(1.dp, colorPalette().text)
            )
        }
    }
}
