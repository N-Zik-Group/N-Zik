package app.n_zik.android.components.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassFloatingActionIcon
import app.n_zik.android.components.glass.GlassMaterialContainerScope
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassScope
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassGlobalIndication
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.isLandscape

/** Extra room around the 40 dp button is reserved only by the material plate. */
fun playerWhatsNextGlassExtraHeight(settings: GlassSettings): Dp =
    if (settings.inScope(GlassScope.PlayerActions).usesGlassMaterial(GlassArea.ActionButtons)) 12.dp else 0.dp

/** Passive upcoming-song plate; the existing row owns its measurement and pager gestures. */
@Composable
fun PlayerWhatsNextGlassSurface(
    nativeBackground: Color,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerActions),
    landscape: Boolean = isLandscape,
    horizontalInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    renderSlot: @Composable (Modifier) -> Unit,
) {
    val scoped = settings.inScope(GlassScope.PlayerActions)
    val material = scoped.usesGlassMaterial(GlassArea.ActionButtons)
    val parent = LocalGlassContainerBackdrop.current
    val shape = uiRoundnessShape()
    Box(Modifier.fillMaxWidth().windowInsetsPadding(horizontalInsets), contentAlignment = Alignment.Center) {
        GlassMaterialContainerScope(
            modifier = Modifier.playerActionToolbarWidth(landscape).testTag("player.whatsNext.surface"),
            includeParent = true,
            sectionFilm = if (material) null else nativeBackground,
            material = if (material) ({ exported ->
                Modifier.glassMaterial(scoped.forArea(GlassArea.ActionButtons), shape,
                    ownBackdrop = parent, separateWindow = parent == null, exportedBackdrop = exported)
            }) else null,
        ) { surface ->
            CompositionLocalProvider(LocalGlassScope provides GlassScope.PlayerActions) {
                renderSlot(if (material) surface.padding(horizontal = 6.dp, vertical = playerWhatsNextGlassExtraHeight(scoped) / 2)
                    else surface.background(nativeBackground)
                    .glassAreaContour(scoped, GlassArea.ActionButtons, shape))
            }
        }
    }
}

/** Reserves the full material button and its gap; native weighting and padding remain unchanged. */
@Composable
fun RowScope.PlayerWhatsNextNavigation(
    isCurrent: Boolean,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerActions),
    content: @Composable RowScope.() -> Unit,
) {
    val material = settings.inScope(GlassScope.PlayerActions).usesGlassMaterial(GlassArea.ActionButtons)
    val slot = if (material) Modifier.width(46.dp).padding(end = 6.dp)
        else Modifier.padding(vertical = 7.5.dp).weight(.07f)
            .then(if (isCurrent) Modifier.padding(horizontal = 3.dp) else Modifier)
    Row(slot, content = content)
}

/** Material owns a full 40 dp slot; the native navigation target retains its original 25 dp. */
@Composable
fun PlayerWhatsNextAction(
    @DrawableRes icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerActions),
) {
    val scoped = settings.inScope(GlassScope.PlayerActions)
    val material = scoped.usesGlassMaterial(GlassArea.ActionButtons)
    Box(modifier.size(if (material) 40.dp else 25.dp).testTag("player.whatsNext.action")) {
        val canvas = Modifier.testTag("player.whatsNext.action.canvas")
        if (material) {
            GlassFloatingActionIcon(painterResource(icon), onClick, modifier = canvas,
                size = 40.dp, iconSize = 25.dp, tint = tint, settings = scoped)
        } else {
            Icon(painterResource(icon), null,
                canvas.size(25.dp).glassAreaContour(scoped, GlassArea.ActionButtons, uiRoundnessShape())
                    .clip(uiRoundnessShape()).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = glassGlobalIndication(null), onClick = onClick),
                tint = tint)
        }
    }
}
