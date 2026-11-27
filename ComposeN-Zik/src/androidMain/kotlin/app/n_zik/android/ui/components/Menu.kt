package app.n_zik.android.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

val LocalMenuState = staticCompositionLocalOf { MenuState() }

internal val LocalMenuScrollState = staticCompositionLocalOf<MenuScrollState?> { null }

internal class MenuScrollState {
    private val listPosition = MenuScrollPosition(0)
    private val gridPosition = MenuScrollPosition(0 to 0)
    private val pages by lazy { mutableMapOf<Enum<*>, MenuScrollState>() }

    fun forPage(page: Enum<*>): MenuScrollState = pages.getOrPut(page, ::MenuScrollState)

    @Composable
    fun rememberListState(): ScrollState {
        val state = remember(this) { ScrollState(listPosition.current()) }
        listPosition.Track(state) { state.value }
        return state
    }

    @Composable
    fun rememberGridState(): LazyGridState {
        val state = remember(this) {
            val (index, offset) = gridPosition.current()
            LazyGridState(index, offset)
        }
        gridPosition.Track(state) { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        return state
    }
}

private class MenuScrollPosition<T>(private var position: T) {
    private var readActive: (() -> T)? = null

    fun current(): T = readActive?.invoke() ?: position

    @Composable
    fun Track(state: Any, readPosition: () -> T) {
        DisposableEffect(state) {
            readActive = readPosition
            onDispose {
                // A quick return can attach a new viewport while the previous one animates out.
                if (readActive === readPosition) {
                    position = readPosition()
                    readActive = null
                }
            }
        }
    }
}

@Stable
class MenuState {
    var isDisplayed by mutableStateOf(false)
        private set

    var transitionKey by mutableStateOf(0)
        private set

    internal class Entry(
        val content: @Composable () -> Unit,
        val onDestinationOpened: (() -> Unit)?,
    ) {
        val scrollState = MenuScrollState()
    }

    private val contentStack = mutableStateListOf<Entry>()

    val contentState: Pair<Int, @Composable () -> Unit>
        get() = transitionKey to (contentStack.lastOrNull()?.content ?: {})

    internal val presentationState: Pair<Int, Entry?>
        get() = transitionKey to contentStack.lastOrNull()

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

