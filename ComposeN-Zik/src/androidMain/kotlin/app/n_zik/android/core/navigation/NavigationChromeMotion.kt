package app.n_zik.android.core.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/** Shared pacing for navigation-driven header and mini-player geometry, in both directions. */
const val NAVIGATION_CHROME_ANIMATION_MS = 350

/** Creates the common navigation component motion without changing unrelated animations. */
fun <T> navigationChromeTween(): TweenSpec<T> = tween(
    durationMillis = NAVIGATION_CHROME_ANIMATION_MS,
    easing = FastOutSlowInEasing,
)
