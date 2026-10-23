package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.uiRoundnessShape

/**
 * One pill button for [ButtonRow]; neither-primary-nor-danger pills use the gray fill
 * ([ColorPalette.background2]). [primary] renders the n_zik accent pill with
 * [ColorPalette.onAccent] text, [icon] an optional fixed-size icon before the label,
 * [textColor] an explicit label color overriding the resolved one (e.g. dimmed reset).
 *
 * @param text Label on a single line, bounded to the cell, continuous marquee when it does not fit; may be empty for icon-only pills.
 * @param enabled Disabled pills stay visible ([ColorPalette.textDisabled] text) but do not fire [onClick].
 * @param danger Renders the filled red cancel pill (the cancel role).
 */
data class DialogButton(
    val text: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val icon: Int? = null,
    val textColor: Color? = null
)

/**
 * Overflow-safe row of pill buttons — the single source of all dialog buttons (issue #896).
 *
 * Every present button gets an equally weighted cell and every labelled pill fills its cell
 * (`weight(1f)`, the old ConfirmDialog distribution), so all pills stay visible and tappable
 * on any width and locale: a label wider than the pill is bounded to it and scrolled
 * (continuous marquee, same pattern as [CheckboxDialog]); an icon-only button renders a small
 * pill centered in its cell.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ButtonRow(
    buttons: List<DialogButton>,
    modifier: Modifier = Modifier
) {
    val palette = colorPalette()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        buttons.forEach { button ->
            val iconOnly = button.icon != null && button.text.isEmpty()
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = pillModifier(button, palette),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    button.icon?.let { iconRes ->
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = resolvedColor(button, palette),
                            modifier = Modifier.size(16.dp)
                        )
                        if (!iconOnly) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                    // The weighted label makes the pill fill its cell; without it
                    // (icon-only) the pill wraps its content and stays centered.
                    if (!iconOnly) {
                        BasicText(
                            text = button.text,
                            maxLines = 1,
                            style = (if (button.primary) typography().xs.semiBold else typography().xs.medium)
                                .copy(
                                    color = resolvedColor(button, palette),
                                    textAlign = TextAlign.Center
                                ),
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(iterations = Int.MAX_VALUE)
                        )
                    }
                }
            }
        }
    }
}

/** The pill surface of every [DialogButton]: rounded clip, role fill, tap and padding. */
@Composable
private fun pillModifier(button: DialogButton, palette: ColorPalette) =
    Modifier
        .clip(uiRoundnessShape())
        .background(
            if (button.primary) palette.accent
            else if (button.danger) palette.red
            else palette.background2
        )
        .clickable(enabled = button.enabled, onClick = button.onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp)

private fun resolvedColor(button: DialogButton, palette: ColorPalette): Color =
    button.textColor ?: when {
        !button.enabled -> palette.textDisabled
        button.primary -> palette.onAccent
        button.danger -> Color.White
        else -> palette.text
    }
