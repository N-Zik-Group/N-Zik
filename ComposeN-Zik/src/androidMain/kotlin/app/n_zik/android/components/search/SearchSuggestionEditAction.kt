package app.n_zik.android.components.search

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassGlobalIndication
import app.n_zik.android.components.navigation.header.HeaderActionCanvasSize
import app.n_zik.android.components.ui.nativeControlIndication
import app.n_zik.android.components.ui.reservedIconActionCanvas
import app.n_zik.android.uiRoundnessShape

/** A query suggestion keeps its native pencil or presents one independent Glass bubble. */
@Composable
fun SearchSuggestionEditAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    nativeContent: @Composable () -> Unit,
) {
    val settings = rememberGlassSettings()
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        GlassSectionActions(
            actions = listOf(GlassSectionAction(
                icon = R.drawable.pencil,
                contentDescription = stringResource(R.string.search_title),
                enabled = enabled,
                onClick = onClick,
            )),
            settings = settings,
        )
    } else {
        val shape = uiRoundnessShape()
        Box(Modifier.size(HeaderActionCanvasSize)
            .glassAreaContour(settings, GlassArea.ActionButtons, shape)
            .reservedIconActionCanvas(shape, remember { MutableInteractionSource() },
                glassGlobalIndication(nativeControlIndication()), enabled, onClick),
            contentAlignment = Alignment.Center,
        ) { nativeContent() }
    }
}
