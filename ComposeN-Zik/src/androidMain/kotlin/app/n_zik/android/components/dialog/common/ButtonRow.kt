package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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

enum class DialogButtonPlacement { Auto, Start, Center, End }

/** Pill appearance and callback, with placement independent of its colors and localized label. */
data class DialogButton(
    val text: String,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val icon: Int? = null,
    val textColor: Color? = null,
    val placement: DialogButtonPlacement = DialogButtonPlacement.Auto
)

/** Each row shares its width equally; the final row preserves semantic action order. */
@Composable
fun ButtonRow(
    buttons: List<DialogButton>,
    modifier: Modifier = Modifier
) {
    val lastPrimary = buttons.indexOfLast { it.primary }
    val placements = buttons.mapIndexed { index, button ->
        when {
            buttons.size == 1 -> DialogButtonPlacement.Center
            button.placement != DialogButtonPlacement.Auto -> button.placement
            button.primary && index == lastPrimary -> DialogButtonPlacement.End
            button.danger -> DialogButtonPlacement.Center
            else -> DialogButtonPlacement.Start
        }
    }
    val anchored = buttons.size > 1 && placements.any {
        it == DialogButtonPlacement.Center || it == DialogButtonPlacement.End
    }
    val finalIndices = if (anchored) listOf(
        placements.indexOfLast { it == DialogButtonPlacement.Start },
        placements.indexOfLast { it == DialogButtonPlacement.Center },
        placements.indexOfLast { it == DialogButtonPlacement.End },
    ) else emptyList()
    val upperButtons = if (anchored) buttons.filterIndexed { index, _ -> index !in finalIndices } else buttons
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        val gap = ((maxWidth - 144.dp) / 2).coerceIn(0.dp, 8.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            upperButtons.chunked(3).forEach { row ->
                DialogButtonBand(row, gap)
            }
            if (anchored) {
                DialogButtonBand(finalIndices.mapNotNull { buttons.getOrNull(it) }, gap)
            }
        }
    }
}

@Composable
private fun DialogButtonBand(buttons: List<DialogButton>, gap: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEach { button ->
            DialogPill(button, Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialogPill(button: DialogButton, modifier: Modifier) {
    val palette = colorPalette()
    val iconOnly = button.icon != null && button.text.isEmpty()
    val style = (if (button.primary) typography().xs.semiBold else typography().xs.medium)
        .copy(color = resolvedColor(button, palette), textAlign = TextAlign.Center)
    Row(
        modifier = pillModifier(button, palette, modifier),
        horizontalArrangement = Arrangement.Center,
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

/** The pill surface of every [DialogButton]: rounded clip, role fill, tap and padding. */
@Composable
private fun pillModifier(button: DialogButton, palette: ColorPalette, modifier: Modifier): Modifier {
    val presentation = LocalDialogInput.current ?: LocalDialogPresentation.current
    return modifier
        .fillMaxWidth()
        .clip(uiRoundnessShape())
        .background(
            if (button.primary) palette.accent
            else if (button.danger) palette.red
            else palette.background2
        )
        .clickable(enabled = button.enabled && dialogInputEnabled) {
            if (presentation?.interactive != false) button.onClick()
        }
        .padding(horizontal = 16.dp, vertical = 10.dp)
}

private fun resolvedColor(button: DialogButton, palette: ColorPalette): Color =
    button.textColor ?: when {
        !button.enabled -> palette.textDisabled
        button.primary -> palette.onAccent
        button.danger -> Color.White
        else -> palette.text
    }
