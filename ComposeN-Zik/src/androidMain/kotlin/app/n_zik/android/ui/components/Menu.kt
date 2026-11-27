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

    private class Entry(
        val content: @Composable () -> Unit,
        val onDestinationOpened: (() -> Unit)?,
    )

    private val contentStack = mutableStateListOf<Entry>()

    val contentState: Pair<Int, @Composable () -> Unit>
        get() = transitionKey to (contentStack.lastOrNull()?.content ?: {})

    val hasPrevious: Boolean
        get() = contentStack.size > 1

    internal val onDestinationOpened: (() -> Unit)?
        get() = if (isDisplayed) contentStack.lastOrNull()?.onDestinationOpened else null

    fun display(content: @Composable () -> Unit) = display(null, content)

    internal fun display(onDestinationOpened: (() -> Unit)?, content: @Composable () -> Unit) {
        if (!isDisplayed) {
            contentStack.clear()
        }
        contentStack.add(Entry(content, onDestinationOpened ?: this.onDestinationOpened))
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

