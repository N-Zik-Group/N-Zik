package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
            actions = listOf(GlassSectionAction(R.drawable.arrow_forward, title, onClick = onClick)),
            nativeContent = {
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    tint = colorPalette().text,
                )
            },
        )
    }
}
