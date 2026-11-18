package app.n_zik.android.components.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import app.n_zik.android.MainActivity
import app.n_zik.android.components.glass.neutralForeground
import app.n_zik.android.components.player.PlayerBackdropSampling
import app.n_zik.android.compose.persist.findActivityNullable

/** Dark means a dark background requiring light system icons. */
internal data class SystemBarAppearance(val statusIsDark: Boolean, val navigationIsDark: Boolean)

private class PlayerBarSample {
    var settled: SystemBarAppearance? = null
}

internal fun resolvePlayerSystemBarAppearance(
    statusSurface: Color?,
    navigationSurface: Color?,
    fallbackSurface: Color,
    samplingFinished: Boolean,
    retained: SystemBarAppearance?,
): SystemBarAppearance {
    fun dark(surface: Color?, previous: Boolean?): Boolean = when {
        surface != null -> neutralForeground(surface) == Color.White
        !samplingFinished && previous != null -> previous
        else -> neutralForeground(fallbackSurface) == Color.White
    }
    return SystemBarAppearance(dark(statusSurface, retained?.statusIsDark),
        dark(navigationSurface, retained?.navigationIsDark))
}

/** The foreground player owns both bars while page updates remain available for restoration. */
internal class SystemBarAppearanceOwner {
    private var page = SystemBarAppearance(true, true)
    private var player: SystemBarAppearance? = null
    private var applied: SystemBarAppearance? = null

    fun updatePage(isDark: Boolean): SystemBarAppearance? {
        page = SystemBarAppearance(isDark, isDark)
        return changed()
    }

    fun updatePlayer(appearance: SystemBarAppearance?): SystemBarAppearance? {
        player = appearance
        return changed()
    }

    private fun changed(): SystemBarAppearance? {
        val current = player ?: page
        if (current == applied) return null
        applied = current
        return current
    }
}

/** Uses the existing tiny background recording, never captures the player or video foreground. */
@Composable
internal fun PlayerSystemBarAppearance(
    active: Boolean,
    sampling: PlayerBackdropSampling,
    fallbackSurface: Color,
    navigationSurfaceOverride: Color? = null,
) {
    val activity = LocalContext.current.findActivityNullable() as? MainActivity
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val top = WindowInsets.statusBars.getTop(density).coerceAtLeast(1)
    val bottom = WindowInsets.navigationBars.getBottom(density)
    val left = WindowInsets.navigationBars.getLeft(density, direction)
    val right = WindowInsets.navigationBars.getRight(density, direction)
    val last = remember(activity) { PlayerBarSample() }
    // Observe completion even though coordinates and the recording generation are plain fields.
    val version = sampling.measurementVersion
    val sampledSurface = sampling.foregroundSurface
    val coordinates = sampling.coordinates?.takeIf { it.isAttached }
    val origin = coordinates?.positionInWindow()
    val size = coordinates?.size
    val colors = remember(active, sampling, version, sampledSurface, origin, size, top, bottom, left, right) {
        if (!active || version == 0 || sampledSurface == null || origin == null || size == null) return@remember null
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        // Avoid rounded screen corners, whose transparent pixels are outside the displayed pane.
        val status = sampling.colorAt(Rect(origin.x + width * .05f, origin.y,
            origin.x + width * .95f, origin.y + top.coerceAtMost(height.toInt())), textFilm = Color.Transparent)
        val navigationRegion = when {
            bottom > 0 -> Rect(origin.x + width * .25f, origin.y + (height - bottom).coerceAtLeast(0f), origin.x + width * .75f, origin.y + height)
            right > 0 -> Rect(origin.x + (width - right).coerceAtLeast(0f), origin.y + height * .25f, origin.x + width, origin.y + height * .75f)
            left > 0 -> Rect(origin.x, origin.y + height * .25f, origin.x + left.coerceAtMost(width.toInt()), origin.y + height * .75f)
            else -> Rect(origin.x + width * .25f, origin.y + (height - top).coerceAtLeast(0f), origin.x + width * .75f, origin.y + height)
        }
        (status ?: sampledSurface) to (sampling.colorAt(navigationRegion, textFilm = Color.Transparent) ?: sampledSurface)
    }
    val hasProvisional = sampling.hasProvisionalSurface
    val visibleFallback = if (hasProvisional) sampling.foregroundSurface ?: fallbackSurface else fallbackSurface
    val backgroundAppearance = if (active) resolvePlayerSystemBarAppearance(colors?.first, colors?.second,
        visibleFallback, sampling.finished, last.settled.takeUnless { hasProvisional }) else null
    val appearance = if (navigationSurfaceOverride == null) backgroundAppearance else backgroundAppearance?.copy(
        navigationIsDark = neutralForeground(navigationSurfaceOverride) == Color.White,
    )
    SideEffect {
        // The queue can come and go while a cover is loading. Retain the sampled bar colors.
        if (!active || sampling.finished) last.settled = backgroundAppearance
        activity?.setPlayerSystemBarAppearance(appearance)
    }
    DisposableEffect(activity) {
        onDispose { activity?.setPlayerSystemBarAppearance(null) }
    }
}
