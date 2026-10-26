package app.n_zik.android.components.search

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Animates entry even when a lazy history item is first composed while already requested. */
@Composable
fun rememberSearchHistoryVisibility(visible: Boolean): MutableTransitionState<Boolean> =
    remember { MutableTransitionState(false) }.apply { targetState = visible }
