// Adapted from Square LiquidBottomTab and BarTab, GPL-3.0; see NOTICE-square.txt.
// Backdrop catalog interaction/layout, Apache-2.0; see LICENSE-backdrop.txt.
package app.n_zik.android.components.navigation.nav

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape

/** A single semantic tab plus its noninteractive, magnified drawing replica. */
@Composable
internal fun GlassNavigationTab(
    text: String,
    iconId: Int,
    selected: Boolean,
    showLabel: Boolean,
    color: Color,
    sidePosition: NavigationBarPosition? = null,
    labelAbove: Boolean = false,
    onClick: () -> Unit,
) {
    val nativeIndication = if (!rememberGlassSettings().usesGlassMaterial(GlassArea.Nav)) LocalIndication.current else null
    val replica = LocalGlassTabReplica.current
    val scale = LocalGlassTabScale.current
    val ink = if (replica) colorPalette().text else color
    // The material owns its mask; magnified Glass glyphs need room beyond their resting slot.
    val tabModifier = (if (nativeIndication != null) Modifier.clip(uiRoundnessShape()) else Modifier)
            .then(if (replica) Modifier else Modifier
                .semantics { contentDescription = text; this.selected = selected }
                .clickable(interactionSource = null, indication = nativeIndication, role = Role.Tab, onClick = onClick))
            .graphicsLayer { scaleX = scale(); scaleY = scale() }
    val label: @Composable (Modifier) -> Unit = { modifier ->
        BasicText(
            text = text,
            modifier = modifier,
            maxLines = 1,
            style = TextStyle(
                color = ink,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
    if (showLabel && (sidePosition == NavigationBarPosition.Left || sidePosition == NavigationBarPosition.Right)) {
        Layout(
            modifier = tabModifier,
            content = {
                Box(Modifier.testTag("sideNavigationTabIcon")) {
                    Button(iconId, ink, 0.dp, 26.dp).Draw()
                }
                Box(Modifier.testTag("sideNavigationTabLabel")) {
                    label(Modifier.layout { measurable, constraints ->
                        // The word's width becomes its vertical extent inside the rail.
                        val word = measurable.measure(Constraints(
                            minWidth = constraints.minHeight, maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth, maxHeight = constraints.maxWidth,
                        ))
                        layout(word.height, word.width) {
                            word.placeWithLayer((word.height - word.width) / 2, (word.width - word.height) / 2) {
                                rotationZ = if (sidePosition == NavigationBarPosition.Right) 90f else -90f
                            }
                        }
                    })
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val icon = measurables[0].measure(loose)
            val gap = 2.dp.roundToPx()
            val word = measurables[1].measure(loose.copy(maxWidth = (loose.maxWidth - icon.width - gap).coerceAtLeast(0)))
            val width = constraints.constrainWidth(icon.width + gap + word.width)
            val height = constraints.constrainHeight(maxOf(icon.height, word.height))
            val start = (width - icon.width - gap - word.width) / 2
            layout(width, height) {
                // Absolute placement keeps physical Left/Right rails mirrored in RTL too.
                val iconX = if (sidePosition == NavigationBarPosition.Right) start + word.width + gap else start
                val wordX = if (sidePosition == NavigationBarPosition.Right) start else start + icon.width + gap
                icon.place(iconX, (height - icon.height) / 2)
                word.place(wordX, (height - word.height) / 2)
            }
        }
        return
    }
    Column(
        modifier = tabModifier,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showLabel && labelAbove) label(Modifier)
        Box(Modifier.testTag("horizontalNavigationTabIcon")) {
            Button(iconId, ink, 0.dp, 26.dp).Draw()
        }
        if (showLabel && !labelAbove) label(Modifier)
    }
}
