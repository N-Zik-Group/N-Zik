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
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.search.UnifiedSearchBar
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

        /** Variant for hosts that keep the bar visible from the first frame (no appearance animation). */
        @Composable
        operator fun invoke( initialVisible: Boolean ) = Search(
            remember { mutableStateOf( TextFieldValue("") ) },
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
    override val messageId: Int = R.string.search
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    /** Keeps the cursor position when the query changes. */
    var input: TextFieldValue by inputState
    var isVisible: Boolean by visibleState
    var isFocused: Boolean by focusState

    override fun onShortClick() {
        isVisible = !isVisible
        isFocused = isVisible
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
    fun SearchBar(
        columnScope: ColumnScope,
        barPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        onSearch: () -> Unit = {},
    ) {
        // Auto focus on search bar when it's visible
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val visibility = remember { MutableTransitionState(isVisible) }
        visibility.targetState = isVisible
        val isFirstRun = remember { mutableStateOf(true) }
        LaunchedEffect( input ) {
            if (isFirstRun.value) {
                isFirstRun.value = false
                return@LaunchedEffect
            }
            lazyListState?.scrollToItem( 0, 0 )
            lazyGridState?.scrollToItem( 0, 0 )
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
                        tween(200, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                        clip = false
                    ) + fadeIn(tween(200)),
                    exit = shrinkVertically(
                        tween(200, delayMillis = 250, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                        clip = false
                    ) + fadeOut(tween(200, delayMillis = 250)),
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
                            placeholderRes = R.string.search,
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
                        )
                    }
                }
            }
        }
    }
}
