package app.n_zik.android.core.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Scroll offsets belong to one destination; a new destination starts with its bars visible. */
internal class ScaffoldBarVisibilityState {
    val topOffset = mutableFloatStateOf(0f)
    val bottomOffset = mutableFloatStateOf(0f)
    val visible = mutableStateOf(true)
    val animationJob = mutableStateOf<Job?>(null)
    private var navigationRevealJob: Job? = null

    fun reveal() {
        animationJob.value?.cancel()
        animationJob.value = null
        topOffset.floatValue = 0f
        bottomOffset.floatValue = 0f
        visible.value = true
    }

    /** Restores both bars together; nested navigation signals share an in-flight reveal. */
    fun revealOnNavigation(scope: CoroutineScope) {
        if (navigationRevealJob?.isActive == true && animationJob.value === navigationRevealJob) return
        animationJob.value?.cancel()
        animationJob.value = null
        visible.value = true
        val startTop = topOffset.floatValue
        val startBottom = bottomOffset.floatValue
        if (startTop == 0f && startBottom == 0f) return

        val job = scope.launch(NzikDispatchers.UI, start = CoroutineStart.LAZY) {
            try {
                Animatable(0f).animateTo(1f, navigationChromeTween()) {
                    topOffset.floatValue = startTop * (1f - value)
                    bottomOffset.floatValue = startBottom * (1f - value)
                }
            } finally {
                val owner = coroutineContext[Job]
                if (animationJob.value === owner) animationJob.value = null
                if (navigationRevealJob === owner) navigationRevealJob = null
            }
        }
        navigationRevealJob = job
        animationJob.value = job
        job.start()
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
