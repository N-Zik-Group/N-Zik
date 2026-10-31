@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.it.fast4x.rimusic.ui.components.themed

import app.n_zik.android.components.glass.glassGlobalIndication

import app.n_zik.android.components.ui.nativeControlIndication
import app.n_zik.android.components.ui.reservedIconActionCanvas

import app.n_zik.android.uiRoundnessShape

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.utils.getDownloadProgress
import androidx.compose.ui.graphics.drawscope.Stroke
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_SIZE_NORMAL
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_STROKE_WIDTH
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box

@UnstableApi
@Composable
fun DownloadStateIconButton(
    onClick: () -> Unit,
    onCancelButtonClicked: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    downloadState: Int,
    mediaId: String? = null
) {
    val progress = if (mediaId != null) getDownloadProgress(mediaId) else 0f

    if (downloadState == Download.STATE_DOWNLOADING
                || downloadState == Download.STATE_QUEUED
                || downloadState == Download.STATE_RESTARTING
                ){
        Box(
            modifier = modifier.reservedIconActionCanvas(
                    shape = uiRoundnessShape(),
                    indication = glassGlobalIndication(indication ?: nativeControlIndication()),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onCancelButtonClicked,
                ),
            contentAlignment = Alignment.Center
        ) {
            if (progress > 0.01f && downloadState == Download.STATE_DOWNLOADING) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    color = colorPalette().accent,
                    trackColor = colorPalette().textDisabled,
                    modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp),
                    stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                    trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
                )
            } else {
                CircularWavyProgressIndicator(
                    color = colorPalette().accent,
                    trackColor = colorPalette().textDisabled,
                    modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp),
                    stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                    trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
                )
            }
        }
    } else {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = modifier.reservedIconActionCanvas(
                    shape = uiRoundnessShape(),
                    indication = glassGlobalIndication(indication ?: nativeControlIndication()),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onClick,
                )
        )
    }
}
