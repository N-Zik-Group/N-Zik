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
 * One pill button for [ButtonRow].
 *
 * @param text label rendered on a single line; when it does not fit in
 *        the cell it is bounded to the cell and scrolled with a continuous
 *        marquee. May be empty for icon-only pills.
 * @param primary renders the n_zik accent pill with [ColorPalette.onAccent] text.
 * @param danger renders the filled red cancel pill (the n_zik cancel role).
 * @param enabled disabled pills stay visible ([ColorPalette.textDisabled] text)
 *        but do not fire [onClick].
 *
 * Every pill that is neither primary nor danger uses the n_zik gray
 * ([ColorPalette.background2]) fill, matching the legacy n_zik button
 * language (gray secondary + accent confirm).
 * @param icon optional fixed-size icon drawn before the label.
 * @param textColor explicit label color overriding the resolved one
 *        (e.g. dimmed reset, red cancel).
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
 * Overflow-safe row of pill buttons — the single source of all dialog
 * buttons (issue #896).
 *
 * Every present button gets an equally weighted cell, so all pills stay
 * visible and tappable on any screen width and any locale: a short label
 * is a small pill centered in its cell, a label wider than the cell is
 * bounded to it and scrolled (continuous marquee, same pattern as
 * [CheckboxDialog]).
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
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(uiRoundnessShape())
                        .background(
                            if (button.primary) palette.accent
                            else if (button.danger) palette.red
                            else palette.background2
                        )
                        .clickable(enabled = button.enabled, onClick = button.onClick)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    button.icon?.let { iconRes ->
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = resolvedColor(button, palette),
                            modifier = Modifier.size(16.dp)
                        )
                        if (button.text.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
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

private fun resolvedColor(button: DialogButton, palette: ColorPalette): Color =
    button.textColor ?: when {
        !button.enabled -> palette.textDisabled
        button.primary -> palette.onAccent
        button.danger -> Color.White
        else -> palette.text
    }
