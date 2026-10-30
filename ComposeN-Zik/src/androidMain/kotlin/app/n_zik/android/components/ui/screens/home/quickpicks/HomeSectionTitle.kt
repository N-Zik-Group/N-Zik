package app.n_zik.android.components.ui.screens.home.quickpicks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.navigation.LocalSideRailContentInsets

/** Home section navigation keeps its native heading click and single trailing action. */
@Composable
internal fun HomeSectionTitle(
    title: String,
    verticalPadding: Dp = 12.dp,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(LocalSideRailContentInsets.current.asPaddingValues())
            .clip(uiRoundnessShape()).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = verticalPadding),
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
