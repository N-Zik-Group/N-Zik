package app.n_zik.android.components.glass

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.searchBubbleShape
import app.n_zik.android.uiRoundnessShape

/** A section-heading action with its original native spacing. */
@Stable
internal data class GlassSectionAction(
    @DrawableRes val icon: Int,
    val contentDescription: String?,
    val nativeEndPadding: Dp = 0.dp,
    val iconModifier: Modifier = Modifier,
    val loading: Boolean = false,
    val separateBefore: Boolean = false,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/** One action forms a bubble; several share a capsule and its short separators. */
@Composable
internal fun GlassSectionActions(
    actions: List<GlassSectionAction>,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    nativeContent: (@Composable () -> Unit)? = null,
) {
    if (actions.isEmpty()) return
    if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        val singleAction = actions.singleOrNull()?.takeUnless { it.loading }
        if (singleAction != null) {
            val backgroundColor = colorPalette().background0
            val drawBackground: DrawScope.() -> Unit = remember(backgroundColor) { { drawRect(backgroundColor) } }
            // Section actions can be part of the page capture; sample an independent canvas.
            val backdrop = rememberCanvasBackdrop(drawBackground)
            Box(modifier) {
                LiquidButton(
                    onClick = singleAction.onClick,
                    onLongClick = singleAction.onLongClick,
                    backdrop = backdrop,
                    modifier = Modifier.size(GlassStandaloneActionSize),
                    settings = settings.forArea(GlassArea.ActionButtons),
                    contentHeight = GlassStandaloneActionSize,
                    contentPadding = 0.dp,
                    shape = searchBubbleShape(),
                ) {
                    Icon(
                        painterResource(singleAction.icon), singleAction.contentDescription,
                        tint = colorPalette().textSecondary,
                        modifier = Modifier.size(GlassActionIconSize).then(singleAction.iconModifier),
                    )
                }
            }
            return
        }
        GlassActionGroup(settings, GlassArea.ActionButtons, modifier = modifier,
            separatorBefore = actions.indices.filter { it > 0 && actions[it].separateBefore }.toSet()) {
            actions.forEach { action ->
                if (action.loading) {
                    Box(
                        Modifier.size(if (actions.size == 1) GlassStandaloneActionSize else GlassActionCellWidth, GlassActionCellHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = colorPalette().textSecondary, strokeWidth = 2.dp)
                    }
                } else {
                    GlassActionGroupItem(
                        onClick = action.onClick,
                        onLongClick = action.onLongClick,
                    ) {
                        Icon(
                            painterResource(action.icon), action.contentDescription,
                            tint = colorPalette().textSecondary,
                            modifier = Modifier.size(GlassActionIconSize).then(action.iconModifier),
                        )
                    }
                }
            }
        }
    } else {
        val shape = uiRoundnessShape()
        Row(
            modifier = modifier.glassAreaContour(settings, GlassArea.ActionButtons, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (nativeContent != null) {
                nativeContent()
            } else {
                actions.forEach { action ->
                    if (action.loading) {
                        CircularProgressIndicator(Modifier.padding(end = action.nativeEndPadding).size(18.dp),
                            color = colorPalette().textSecondary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painterResource(action.icon), action.contentDescription,
                            tint = colorPalette().textSecondary,
                            modifier = Modifier.padding(end = action.nativeEndPadding).then(action.iconModifier).clip(shape)
                                .clickable(onClick = action.onClick),
                        )
                    }
                }
            }
        }
    }
}
