package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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
 * @param text Single-line label with marquee; may be empty for icon-only pills.
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

/** Content-sized buttons wrap between rows; each pill caps its width and retains its marquee. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ButtonRow(
    buttons: List<DialogButton>,
    modifier: Modifier = Modifier
) {
    val palette = colorPalette()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = if (buttons.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        buttons.forEach { button ->
            val iconOnly = button.icon != null && button.text.isEmpty()
            val style = (if (button.primary) typography().xs.semiBold else typography().xs.medium)
                .copy(color = resolvedColor(button, palette), textAlign = TextAlign.Center)
            val textWidth = with(density) {
                textMeasurer.measure(button.text, style, softWrap = false, maxLines = 1).size.width.toDp()
            }
            val buttonWidth = if (iconOnly) 48.dp else
                (textWidth + 32.dp + if (button.icon != null) 22.dp else 0.dp).coerceIn(48.dp, 180.dp)
            Row(
                modifier = pillModifier(button, palette, buttonWidth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                button.icon?.let { iconRes ->
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = resolvedColor(button, palette),
                        modifier = Modifier.size(16.dp),
                    )
                    if (!iconOnly) Spacer(Modifier.width(6.dp))
                }
                if (!iconOnly) {
                    BasicText(
                        text = button.text,
                        maxLines = 1,
                        style = style,
                        modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
            }
        }
    }
}
/** The pill surface of every [DialogButton]: rounded clip, role fill, tap and padding. */
@Composable
private fun pillModifier(button: DialogButton, palette: ColorPalette, width: Dp) =
    Modifier
        .width(width)
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
