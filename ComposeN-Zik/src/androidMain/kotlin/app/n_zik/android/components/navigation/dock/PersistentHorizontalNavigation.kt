package app.n_zik.android.components.navigation.dock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.utils.LANDSCAPE_BARS_ANIMATION_MS
import app.n_zik.android.colorPalette
import app.n_zik.android.components.navigation.CommittedNavigationPresentation
import app.n_zik.android.components.navigation.navigationPresentationInput
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.navigation.header.UnifiedTopNavigationGlass
import app.n_zik.android.surfaceColor
import kotlin.math.roundToInt

/** The presentation stays outside NavHost; only the current entry's registered tabs change. */
@Composable
internal fun PersistentHorizontalNavigation(
    position: NavigationBarPosition,
    pageKey: () -> String?,
    expectsTabs: () -> Boolean,
    registration: (String?) -> FloatingDockState.Registration?,
    hidden: Boolean,
    topPx: () -> Int,
    bottomOffsetPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    // Destination registrations publish in SideEffect. Resolve them after NavHost has
    // applied composition, in the same frame's layout, rather than displaying a null
    // registration sampled before an incoming destination was composed.
    val committed = remember { CommittedNavigationPresentation<FloatingDockState.Registration>() }
    SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
        val presentation = committed.resolve(pageKey(), expectsTabs(), registration)
        val navigation = subcompose(Unit) {
            HorizontalNavigationPresentation(position, presentation.registration, hidden, topPx, bottomOffsetPx, presentation.interactive)
        }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            navigation.forEach { it.placeRelative(0, 0) }
        }
    }
}

@Composable
private fun HorizontalNavigationPresentation(
    position: NavigationBarPosition,
    registration: FloatingDockState.Registration?,
    hidden: Boolean,
    topPx: () -> Int,
    bottomOffsetPx: () -> Float,
    interactive: Boolean,
) {
    val top = position == NavigationBarPosition.Top
    val imeLiftPx = rememberBottomNavigationImeLift(position)
    val settings = rememberGlassSettings()
    val backdrop = LocalGlassBackdrop.current
    Box(Modifier.fillMaxSize().navigationPresentationInput(interactive)
        .offset { IntOffset(0, if (top) 0 else bottomOffsetPx().roundToInt() - imeLiftPx()) }
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
        // Destination changes must never restart a visibility/placement animation.
        AnimatedVisibility(
            visible = !hidden,
            enter = if (top) expandVertically(tween(LANDSCAPE_BARS_ANIMATION_MS, easing = FastOutSlowInEasing), clip = false) + fadeIn()
                else slideInVertically(tween(LANDSCAPE_BARS_ANIMATION_MS, easing = FastOutSlowInEasing)) { it } + fadeIn(),
            exit = if (top) shrinkVertically(tween(LANDSCAPE_BARS_ANIMATION_MS, easing = FastOutSlowInEasing), clip = false) + fadeOut()
                else slideOutVertically(tween(LANDSCAPE_BARS_ANIMATION_MS, easing = FastOutSlowInEasing)) { it } + fadeOut(),
            modifier = Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
                .offset { IntOffset(0, if (top) topPx() else 0) },
        ) {
            if (top && settings.usesGlassMaterial(GlassArea.Nav) && registration?.present == true) {
                UnifiedTopNavigationGlass(settings, surfaceColor(colorPalette().background1), backdrop, topPx) {
                    registration.body?.invoke()
                }
            } else registration?.body?.invoke()
        }
    }
}

/** Preserve page-local owners while presenting its controls at the stable navigation root. */
@Composable
internal fun RegisterDockNavigation(
    pageKey: String?,
    buttonCount: Int,
    visible: Boolean,
    body: @Composable () -> Unit,
) {
    val state = LocalFloatingDock.current?.state
    val owner = remember { Any() }
    val context = rememberUpdatedState(currentCompositionLocalContext)
    val latestBody = rememberUpdatedState(body)
    val presentation: @Composable () -> Unit = remember(owner) {
        { CompositionLocalProvider(context.value) { latestBody.value() } }
    }
    SideEffect {
        if (pageKey != null) state?.register(pageKey, owner, buttonCount, visible, presentation)
    }
    DisposableEffect(state, pageKey, owner) {
        onDispose { if (pageKey != null) state?.unregister(pageKey, owner) }
    }
}
