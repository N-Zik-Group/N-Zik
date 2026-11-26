package app.n_zik.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
            this.transitionKey++
        } else {
            hide()
        }
    }

    fun hide() {
        isDisplayed = false
    }
}

