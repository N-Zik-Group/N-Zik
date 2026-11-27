package app.n_zik.android.components.tab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.isGlassMotionAllowed
import app.n_zik.android.components.menu.withMenuClick
import app.n_zik.android.components.search.SearchHeaderVisibility
import app.n_zik.android.components.search.SearchMotionDurationMillis
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.ui.components.LocalMenuEntry
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

class Search private constructor(
    inputState: MutableState<TextFieldValue>,
    visibleState: MutableState<Boolean>,
    focusState: MutableState<Boolean>,
    private val lazyListState: LazyListState?,
    private val lazyGridState: LazyGridState?,
    private val persistent: Boolean = false,
    private val canConsumeBack: (() -> Boolean)? = null,
): MenuIcon, Descriptive{

    companion object {
        @Composable
        operator fun invoke( lazyListState: LazyListState? = null ) = Search(
            remember { mutableStateOf( TextFieldValue("") ) },
            remember { mutableStateOf( false ) },
            remember { mutableStateOf( false ) },
            lazyListState,
            null
        )

        @Composable
        operator fun invoke( lazyGridState: LazyGridState? = null ) = Search(
            remember { mutableStateOf( TextFieldValue("") ) },
            remember { mutableStateOf( false ) },
            remember { mutableStateOf( false ) },
            null,
            lazyGridState
        )

        @Composable
        operator fun invoke() = Search(
            remember { mutableStateOf( TextFieldValue("") ) },
            remember { mutableStateOf( false ) },
            remember { mutableStateOf( false ) },
            null,
            null
        )

        @Composable
        fun forHome() = Search(
            rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) },
            rememberSaveable { mutableStateOf(false) },
            remember { mutableStateOf(false) },
            null,
            null,
        )

        @Composable
        internal fun detail() = forHome()

        @Composable
        internal fun retained(
            input: MutableState<TextFieldValue>,
            visible: MutableState<Boolean>,
            menuState: MenuState,
        ): Search {
            val entry = LocalMenuEntry.current
            val ownsBack: (() -> Boolean)? = entry?.let {
                { menuState.isDisplayed && menuState.presentationState.second === entry }
            }
            return Search(
                input, visible, remember { mutableStateOf(false) }, null, null,
                canConsumeBack = ownsBack,
            )
        }

        /** Hosts may retain input across nested destinations without restoring keyboard focus. */
        @Composable
        operator fun invoke(
            initialVisible: Boolean,
            inputState: MutableState<TextFieldValue> = remember { mutableStateOf(TextFieldValue("")) },
        ) = Search(
            inputState,
            remember { mutableStateOf( initialVisible ) },
            remember { mutableStateOf( false ) },
            null,
            null,
            persistent = initialVisible,
        )

        /** Variant for hosts that keep the bar visible from the first frame and still scroll the list to the top on input. */
        @Composable
        operator fun invoke( lazyListState: LazyListState, initialVisible: Boolean ) = Search(
            remember { mutableStateOf( TextFieldValue("") ) },
            remember { mutableStateOf( initialVisible ) },
            remember { mutableStateOf( false ) },
            lazyListState,
            null,
            persistent = initialVisible,
        )
    }

    val inputValue: String
        get() = input.text
    override val iconId: Int = R.drawable.search_circle
    override val messageId: Int = R.string.search_title
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    /** Keeps the cursor position when the query changes. */
    var input: TextFieldValue by inputState
    var isVisible: Boolean by visibleState

    val filterQuery: String
        get() = if (isVisible) inputValue.trim() else ""
    var isFocused: Boolean by focusState

    override fun onShortClick() {
        isVisible = !isVisible
        isFocused = isVisible
    }

    @Composable
    override fun ListMenuItem() = overflowEntry().ListMenuItem()

    @Composable
    override fun GridMenuItem() = overflowEntry().GridMenuItem()

    @Composable
    private fun overflowEntry(): MenuIcon {
        val menu = LocalMenuState.current
        return withMenuClick {
            onShortClick()
            menu.hide()
        }
    }

    internal fun clearAndClose() {
        isFocused = false
        isVisible = false
        input = TextFieldValue("")
    }

    /** Hides an empty bar or removes focus from a populated one. */
    fun hideIfEmpty() {
        if ( isVisible )
            if ( inputValue.isBlank() )
                isVisible = false
            else
                isFocused = false
    }

    @Composable
    internal fun HeaderContent(content: @Composable () -> Unit) {
        SearchHeaderVisibility(!isFocused, content)
    }

    @Composable
    fun SearchBar(
        columnScope: ColumnScope,
        barPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        placeholderRes: Int = R.string.search_title,
        onSearch: () -> Unit = {},
    ) {
        // Auto focus on search bar when it's visible
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val visibility = remember { MutableTransitionState(isVisible) }
        val motion = isGlassMotionAllowed()
        val duration = if (motion) SearchMotionDurationMillis else 0
        val collapseDelay = if (motion) SearchMotionDurationMillis else 0
        visibility.targetState = isVisible
        val isFirstRun = remember { mutableStateOf(true) }
        LaunchedEffect(input.text) {
            if (isFirstRun.value) {
                isFirstRun.value = false
                return@LaunchedEffect
            }
            lazyListState?.takeIf { it.firstVisibleItemIndex > 0 || it.firstVisibleItemScrollOffset > 0 }?.let {
                if (motion) it.animateScrollToItem(0) else it.scrollToItem(0)
            }
            lazyGridState?.takeIf { it.firstVisibleItemIndex > 0 || it.firstVisibleItemScrollOffset > 0 }?.let {
                if (motion) it.animateScrollToItem(0) else it.scrollToItem(0)
            }
        }

        // Reserve animated height without clipping the bubbles' downward travel.
        with(columnScope) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
            ) {
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = expandVertically(
                        tween(duration, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                        clip = false
                    ) + fadeIn(tween(duration)),
                    exit = shrinkVertically(
                        tween(duration, delayMillis = collapseDelay, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                        clip = false
                    ) + fadeOut(tween(duration, delayMillis = collapseDelay)),
                ) {
                    var fieldFocused by remember { mutableStateOf(false) }
                    // Request only after AnimatedVisibility has attached the field. Its initial
                    // unfocused notification must not cancel a toolbar's pending focus request.
                    LaunchedEffect(isVisible, isFocused, fieldFocused) {
                        if (isVisible && isFocused && !fieldFocused) {
                            snapshotFlow { visibility.isIdle && visibility.currentState }.first { it }
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        } else if ((!isVisible || !isFocused) && fieldFocused) {
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                        }
                    }
                    Box(modifier = Modifier.padding(barPadding)) {
                        UnifiedSearchBar(
                            input = input,
                            onInputChanged = { input = it },
                            placeholderRes = placeholderRes,
                            baseSurface = colorPalette().background1,
                            onClear = {
                                input = TextFieldValue("")
                                isFocused = true
                            },
                            onSearch = {
                                onSearch()
                                isVisible = persistent || inputValue.isNotBlank()
                                isFocused = false
                                keyboardController?.hide()
                            },
                            onFocused = { focused ->
                                if (focused || fieldFocused) isFocused = focused
                                fieldFocused = focused
                            },
                            focusRequester = focusRequester,
                            backSessionActive = isVisible && (!persistent || isFocused),
                            onBackDismiss = {
                                isFocused = false
                                if (!persistent) isVisible = false
                            },
                            canConsumeBack = canConsumeBack,
                        )
                    }
                }
            }
        }
    }
}
