package app.n_zik.android.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateListOf

val LocalMenuState = staticCompositionLocalOf { MenuState() }

@Stable
class MenuState {
    var isDisplayed by mutableStateOf(false)
        private set

    var transitionKey by mutableStateOf(0)
        private set

    private val contentStack = mutableStateListOf<@Composable () -> Unit>()

    val contentState: Pair<Int, @Composable () -> Unit>
        get() = transitionKey to (contentStack.lastOrNull() ?: {})

    val hasPrevious: Boolean
        get() = contentStack.size > 1

    fun display(content: @Composable () -> Unit) {
        if (!isDisplayed) {
            contentStack.clear()
        }
        contentStack.add(content)
        this.transitionKey++
        isDisplayed = true
    }

    fun pop() {
        if (contentStack.size > 1) {
            contentStack.removeLast()
            this.transitionKey--
        } else {
            hide()
        }
    }

    fun hide() {
        isDisplayed = false
    }
}

@Composable
fun BottomSheetMenu(
    state: MenuState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.isDisplayed,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        BackHandler(onBack = state::pop)

        Spacer(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures {
                        state.hide()
                    }
                }
                .background(Color.Black.copy(alpha = 0.5f))
                .fillMaxSize()
        )
    }

    AnimatedVisibility(
        visible = state.isDisplayed,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = state.contentState,
            transitionSpec = {
                slideInHorizontally(animationSpec = tween(300)) { width -> width / 2 } + fadeIn(animationSpec = tween(300)) togetherWith 
                slideOutHorizontally(animationSpec = tween(300)) { width -> -width / 2 } + fadeOut(animationSpec = tween(300))
            },
            label = "MenuContentTransition"
        ) { target ->
            target.second()
        }
    }
}



