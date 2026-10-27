package app.n_zik.android.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import kotlinx.coroutines.Job

/** Scroll offsets belong to one destination; a new destination starts with its bars visible. */
internal class ScaffoldBarVisibilityState {
    val topOffset = mutableFloatStateOf(0f)
    val bottomOffset = mutableFloatStateOf(0f)
    val visible = mutableStateOf(true)
    val animationJob = mutableStateOf<Job?>(null)

    fun reveal() {
        animationJob.value?.cancel()
        animationJob.value = null
        topOffset.floatValue = 0f
        bottomOffset.floatValue = 0f
        visible.value = true
    }
}

val LocalRevealScaffold = staticCompositionLocalOf<() -> Unit> { {} }

/** Includes argument changes, repeated route instances and back navigation, not recomposition. */
@Composable
fun RevealScaffoldOnNavigation(
    navController: NavController,
    reveal: () -> Unit = LocalRevealScaffold.current,
) {
    val currentReveal by rememberUpdatedState(reveal)
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ -> currentReveal() }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

/** Tabs inside a destination are pages too; a stable selection must not override scroll hiding. */
@Composable
fun RevealScaffoldOnPageSelection(selection: Any?, reveal: () -> Unit = LocalRevealScaffold.current) {
    val currentReveal by rememberUpdatedState(reveal)
    LaunchedEffect(selection) { currentReveal() }
}
