package app.n_zik.android.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.MainActivity
import app.n_zik.android.compose.persist.findActivityNullable
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.utils.shimmerEffect
import kotlinx.coroutines.flow.collectLatest

private const val SHIMMER_PERIOD_NANOS = 1_600_000_000L

internal fun shimmerPhaseAt(frameNanos: Long): Float =
    (frameNanos % SHIMMER_PERIOD_NANOS).toFloat() / SHIMMER_PERIOD_NANOS

@Stable
internal class ShimmerClock {
    var attached by mutableIntStateOf(0)
    var phase by mutableFloatStateOf(0f)
}

internal val LocalShimmerClock = staticCompositionLocalOf<ShimmerClock?> { null }
internal val LocalShimmerPalette = staticCompositionLocalOf<ColorPalette?> { null }

@Composable
internal fun rememberShimmerClock(): ShimmerClock {
    val clock = remember { ShimmerClock() }
    LaunchedEffect(clock) {
        snapshotFlow { clock.attached > 0 }.collectLatest { active ->
            if (active) {
                while (true) {
                    withInfiniteAnimationFrameNanos { clock.phase = shimmerPhaseAt(it) }
                }
            }
        }
    }
    return clock
}

@Composable
@PublishedApi
internal fun forceLoadingShimmerPreview(): Boolean =
    (LocalContext.current.findActivityNullable() as? MainActivity)?.forceLoadingShimmer == true

@Composable
internal fun ArtworkShimmerPlaceholder(modifier: Modifier = Modifier, aspectRatio: Float = 1f) {
    Spacer(modifier.fillMaxWidth().aspectRatio(aspectRatio).shimmerEffect())
}

@Composable
fun ShimmerHost(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    val clock = LocalShimmerClock.current ?: rememberShimmerClock()
    CompositionLocalProvider(LocalShimmerClock provides clock) {
        Column(modifier, verticalArrangement, horizontalAlignment, content)
    }
}


