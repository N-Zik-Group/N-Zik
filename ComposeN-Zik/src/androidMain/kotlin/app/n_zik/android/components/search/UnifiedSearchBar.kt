package app.n_zik.android.components.search

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.searchBubbleShape
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

/** One round action bubble rendered on the right of the shrunken bar (voice, audio search, clear). */
data class SearchBarBubble(
    val iconRes: Int,
    val contentDescriptionRes: Int,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Unified app-wide search bar (design reference: Android settings search).
 *
 * Pill bar with a surface background ([surfaceColor], pitch black safe), search
 * icon on the left, placeholder left-aligned right after the icon (same
 * position as the typed text). On focus the bar shrinks and round action
 * bubbles slide in on its right: the clear (X) bubble is always present,
 * [actionBubbles] are optional host extras (only the online search screen
 * passes any). Host-agnostic: the caller owns the text state and the focus
 * policy; [onFocused] reports focus changes, [focusRequester] lets the host
 * request focus.
 */
@Composable
fun UnifiedSearchBar(
    input: TextFieldValue,
    onInputChanged: (TextFieldValue) -> Unit,
    placeholderRes: Int,
    modifier: Modifier = Modifier,
    baseSurface: Color? = null,
    actionBubbles: List<SearchBarBubble> = emptyList(),
    onClear: () -> Unit,
    onSearch: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (Boolean) -> Unit = {},
    textFieldTestTag: String? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // +1 = the clear (X) bubble; 48dp bubble + 8dp spacing, 12dp bar-to-bubble gap
    val bubbleCount = actionBubbles.size + 1
    val bubbleAreaTarget = 12.dp + bubbleCount * 48.dp + (bubbleCount - 1) * 8.dp
    // Choreographed resize: the bar opens first, then the bubbles enter on a
    // diagonal (off-screen right, just below the bar) and travel up to their
    // slot. Blurring plays the same trip in reverse to the same starting
    // spot, then the bar reclaims the space. The 48dp circle is fixed
    // throughout: only its position and its opacity animate, so its size and
    // shape never change mid-trip.
    // Two clean, non-overlapping phases in both directions (a concurrent bar
    // resize reads as the bubble being squashed against the bar's moving
    // edge): entry = the bar opens first (200ms), then the bubble swoops in
    // (250ms) fading in as it starts. Exit = the mirror: the bubble swoops
    // out first (250ms, fading during the last stretch so it disappears at
    // its starting spot), then the bar reclaims (200ms).
    val bubbleAreaWidth by animateDpAsState(
        targetValue = if (isFocused) bubbleAreaTarget else 0.dp,
        animationSpec = if (isFocused) tween(200) else tween(200, delayMillis = 250),
        label = "unifiedSearchBarBubbleArea"
    )
    // Starting spot (entry AND exit): one bubble area to the right (fully
    // off-screen) and 64dp below the final slot, just under the bar in the
    // top area of the screen.
    val bubbleEntryY = 64.dp
    val bubbleSlide by animateDpAsState(
        targetValue = if (isFocused) 0.dp else bubbleAreaTarget,
        animationSpec = if (isFocused) tween(250, delayMillis = 200) else tween(250),
        label = "unifiedSearchBarBubbleSlide"
    )
    val bubbleDrop by animateDpAsState(
        targetValue = if (isFocused) 0.dp else bubbleEntryY,
        animationSpec = if (isFocused) tween(250, delayMillis = 200) else tween(250),
        label = "unifiedSearchBarBubbleDrop"
    )
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        // Entry: fade in at the trip start (a late fade hides the ascent).
        // Exit: fade out over the last stretch, at the starting spot.
        animationSpec = if (isFocused) tween(100, delayMillis = 200) else tween(100, delayMillis = 150),
        label = "unifiedSearchBarBubbleAlpha"
    )

    Row(
        modifier = modifier.fillMaxWidth().zIndex(1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .shadow(
                    elevation = 8.dp,
                    shape = uiRoundnessShape(),
                    spotColor = colorPalette().accent.copy(alpha = 0.3f)
                )
                .background(
                    color = surfaceColor(baseSurface ?: colorPalette().background1),
                    shape = uiRoundnessShape()
                )
                .clip(uiRoundnessShape())
                // The whole pill (icon, spacer and padding included) focuses the
                // field on tap; without this, taps on the padding fall through
                // to whatever is behind the bar (e.g. the queue list, which
                // hides the bar on item click)
                .clickable { focusRequester.requestFocus() }
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("unified_search_bar"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = stringResource(R.string.search),
                    tint = colorPalette().favoritesIcon,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = input,
                    onValueChange = { newValue ->
                        onInputChanged(
                            newValue.copy(
                                text = newValue.text,
                                selection = newValue.selection
                            )
                        )
                    },
                    textStyle = typography().xs.semiBold,
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearch()
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                    }),
                    cursorBrush = SolidColor(colorPalette().text),
                    decorationBox = { innerTextField ->
                        if (input.text.isEmpty()) {
                            BasicText(
                                text = stringResource(placeholderRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = typography().xs.semiBold.secondary,
                            )
                        }

                        innerTextField()
                    },
                    modifier = (
                        if (textFieldTestTag != null) Modifier.weight(1f).testTag(textFieldTestTag)
                        else Modifier.weight(1f)
                        ).onFocusChanged { focused ->
                            isFocused = focused.isFocused
                            onFocused(focused.isFocused)
                        }
                        .focusRequester(focusRequester),
                )
            }
        }

        // The bubble area width is the single animated value driving the whole
        // resize (bar = weight 1f takes the remainder every frame), which keeps
        // the shrink fluid - no AnimatedVisibility size snapping, no layout
        // measurement feedback. The row keeps a fixed size and travels in / out
        // from the entry spot (off-screen right, just below the bar; no clip on
        // the box).
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .width(bubbleAreaWidth),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .offset { IntOffset(bubbleSlide.roundToPx(), bubbleDrop.roundToPx()) }
                    .graphicsLayer { alpha = bubbleAlpha }
                    .padding(start = 12.dp),
            ) {
                actionBubbles.forEach { bubble ->
                    SearchBarBubbleButton(bubble)
                }

                // X clears the query and closes the search: hides the keyboard and
                // drops focus so the bar returns to its full-width resting state.
                SearchBarBubbleButton(
                    SearchBarBubble(
                        iconRes = R.drawable.close,
                        contentDescriptionRes = R.string.clear,
                        onClick = {
                            onClear()
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                        },
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchBarBubbleButton(bubble: SearchBarBubble) {
    // clip before clickable so the ripple indication follows the shape instead
    // of staying a square; background2 surface keeps the pitch-black fallback.
    val shape = searchBubbleShape()
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                color = surfaceColor(colorPalette().background2),
                shape = shape
            )
            .clip(shape)
            .clickable(onClick = bubble.onClick)
            .testTag("unified_search_bubble_${bubble.iconRes}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(bubble.iconRes),
            contentDescription = stringResource(bubble.contentDescriptionRes),
            tint = if (bubble.active) colorPalette().text else colorPalette().favoritesIcon,
            modifier = Modifier.size(20.dp),
        )
    }
}
