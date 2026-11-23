package app.n_zik.android.components.menu.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.components.ui.sliders.SliderControl
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.percentLabel
import app.n_zik.android.utils.semiBold

@Composable
internal fun VolumeRow(
    currentVolume: Float,
    maxVolume: Float,
    enabled: Boolean = true,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeComplete: () -> Unit = {}
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .graphicsLayer(alpha = alpha)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .iconBadgeBackground(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = colorPalette().accent,
                modifier = Modifier.size(18.dp)
            )
        }

        BasicText(
            text = stringResource(R.string.player_statistics_volume_label),
            style = typography().s.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().text
            )
        )

        BasicText(
            text = percentLabel(if (maxVolume > 0) kotlin.math.round(((currentVolume / maxVolume) * 100)).toInt() else 0),
            style = typography().xxs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().accent
            )
        )

        SliderControl(
            state = currentVolume,
            range = 0f..maxVolume,
            stepSize = 0f,
            drawValuePoints = false,
            showValue = false,
            onSlide = if (enabled) onVolumeChange else ({}),
            onSlideComplete = if (enabled) onVolumeChangeComplete else ({}),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun VolumeGridEntry(
    currentVolume: Float,
    maxVolume: Float,
    enabled: Boolean = true,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeComplete: () -> Unit = {}
) {
    var isShowingDialog by remember { mutableStateOf(false) }

    DialogPresence(visible = isShowingDialog && enabled) {
        VolumeDialog(
            currentVolume = currentVolume,
            maxVolume = maxVolume,
            onVolumeChange = onVolumeChange,
            onVolumeChangeComplete = onVolumeChangeComplete,
            onDismiss = { isShowingDialog = false }
        )
    }

    GridMenu.Entry(
        text = stringResource(R.string.player_statistics_volume_label),
        enabled = enabled,
        icon = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .iconBadgeBackground(
                        color = colorPalette().accent.copy(alpha = 0.1f),
                        shape = uiRoundnessShape()
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        subtitle = percentLabel(if (maxVolume > 0) kotlin.math.round(((currentVolume / maxVolume) * 100)).toInt() else 0),
        onClick = { isShowingDialog = true },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_forward),
                tint = colorPalette().textSecondary,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun VolumeDialog(
    currentVolume: Float,
    maxVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeComplete: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var dialogVolume by remember { mutableFloatStateOf(currentVolume) }

    DialogFrame(
        onDismiss = onDismiss,
        title = stringResource(R.string.player_statistics_volume_label),
        horizontalAlignment = Alignment.Start,
        footer = {
            ButtonRow(
                buttons = listOf(
                    DialogButton(
                        text = stringResource(R.string.common_action_close),
                        icon = R.drawable.close,
                        onClick = onDismiss, danger = true
                    )
                )
            )
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .iconBadgeBackground(
                        color = colorPalette().accent.copy(alpha = 0.1f),
                        shape = uiRoundnessShape()
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        BasicText(
            text = percentLabel(if (maxVolume > 0) kotlin.math.round(((dialogVolume / maxVolume) * 100)).toInt() else 0),
            style = typography().l.semiBold.copy(
                color = colorPalette().accent
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        SliderControl(
            state = dialogVolume,
            onSlide = { dialogVolume = it },
            onSlideComplete = {
                onVolumeChange(dialogVolume)
                onVolumeChangeComplete()
            },
            range = 0f..maxVolume,
            stepSize = 0f,
            drawValuePoints = false,
            showValue = false
        )
    }
}
