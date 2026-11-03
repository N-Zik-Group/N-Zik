package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.it.fast4x.rimusic.enums.QueueType
import app.it.fast4x.rimusic.utils.queueTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.components.glass.rememberGlassSettings

/** Optical budget is independent of whether visible player decorations may animate. */
@Composable
internal fun playerQueueOpticsEnabled(inheritedEnabled: Boolean, queueVisible: Boolean): Boolean =
    inheritedEnabled && (!queueVisible || !rememberGlassSettings().optimizationEnabled)

/** Keeps any uncovered player content live while the queue opens or closes. */
@Composable
internal fun playerQueueUnderlayCovered(
    inheritedCovered: Boolean,
    queueVisible: Boolean,
    fullyCovering: Boolean = false,
): Boolean {
    val queueType by rememberPreference(queueTypeKey, QueueType.Modern)
    return playerQueueUnderlayCovered(inheritedCovered, queueVisible, queueType, fullyCovering)
}

internal fun playerQueueUnderlayCovered(
    inheritedCovered: Boolean,
    queueVisible: Boolean,
    queueType: QueueType,
    fullyCovering: Boolean = false,
): Boolean = inheritedCovered || (queueVisible && fullyCovering && queueType == QueueType.Essential)

/** The status-bar inset is outside the visible player; allow one pixel of layout rounding. */
internal fun queuePanelCoversPlayer(panelHeight: Int, viewportHeight: Int, topInset: Int, alpha: Float): Boolean =
    viewportHeight > 0 && panelHeight > 0 && alpha >= 1f &&
        panelHeight >= (viewportHeight - topInset.coerceAtLeast(0) - 1).coerceAtLeast(1)

/** Match the actual resizable panel endpoint, including configuration/window inset differences. */
internal fun queuePanelAtExpandedEndpoint(panelHeight: Int, viewportHeight: Int, maxFraction: Float, alpha: Float): Boolean =
    maxFraction.isFinite() && maxFraction > 0f && maxFraction <= 1f &&
        queuePanelCoversPlayer(panelHeight, (viewportHeight * maxFraction).toInt(), 0, alpha)
