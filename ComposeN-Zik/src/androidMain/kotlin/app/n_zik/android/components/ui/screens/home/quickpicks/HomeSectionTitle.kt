package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.components.glass.GlassTitleActionRow
import app.n_zik.android.typography
import app.n_zik.android.components.navigation.LocalSideRailContentInsets

/** Home section navigation keeps its native heading click and single trailing action. */
@Composable
internal fun HomeSectionTitle(
    title: String,
    verticalPadding: Dp = 12.dp,
    onClick: () -> Unit,
) {
    val arrowScaleX = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    GlassTitleActionRow(
        modifier = Modifier.fillMaxWidth()
            .padding(LocalSideRailContentInsets.current.asPaddingValues()),
        verticalPadding = verticalPadding,
        hasTrailingActions = true,
        onClick = onClick,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontSize = typography().l.semiBold.fontSize,
                fontWeight = typography().l.semiBold.fontWeight,
                color = colorPalette().text,
                textAlign = TextAlign.Start,
            ),
            modifier = Modifier.weight(1f),
        )
        GlassSectionActions(
            actions = listOf(
                GlassSectionAction(
                    icon = R.drawable.arrow_forward,
                    contentDescription = title,
                    iconModifier = Modifier.scale(scaleX = arrowScaleX, scaleY = 1f),
                    nativeColor = colorPalette().text,
                    onClick = onClick,
                ),
            ),
        )
    }
}
