package app.n_zik.android.components.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial

/** A query suggestion keeps its native pencil or presents one independent Glass bubble. */
@Composable
fun SearchSuggestionEditAction(
    onClick: () -> Unit,
    nativeContent: @Composable () -> Unit,
) {
    val settings = rememberGlassSettings()
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        GlassSectionActions(
            actions = listOf(GlassSectionAction(
                icon = R.drawable.pencil,
                contentDescription = stringResource(R.string.search),
                onClick = onClick,
            )),
            settings = settings,
        )
    } else {
        nativeContent()
    }
}
