package app.n_zik.android.components.ui.screens.home

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Home publishes only overlay geometry; the list keeps its original, fixed content reserve. */
internal class HomeTopMiniPlayerAnchorState(
    private val baseTopInset: State<(() -> Dp)?>,
    private val topGap: State<Dp>,
    private val miniBeforeHeaderHeight: State<Dp?> = mutableStateOf(null),
) {
    private class Header(
        val owner: Any,
        val measuredHeight: (() -> Dp)?,
        val scrollOffset: (() -> Dp)?,
        val overlayTopInset: (() -> Dp)?,
        val visibleHeight: () -> Dp,
    )

    private var header by mutableStateOf<Header?>(null)

    fun attach(
        owner: Any,
        measuredHeight: (() -> Dp)? = null,
        scrollOffset: (() -> Dp)? = null,
        overlayTopInset: (() -> Dp)? = null,
        visibleHeight: () -> Dp,
    ) {
        header = Header(owner, measuredHeight, scrollOffset, overlayTopInset, visibleHeight)
    }

    // Reattachment or late measurement changes placement; scrolling never changes this key.
    fun placementKey(): Pair<Any, Dp?>? = header?.let { it to it.measuredHeight?.invoke() }

    /** Capture measured geometry alongside its key, while keeping scroll reads live. */
    fun miniTopInsetProvider(panelGap: Dp = 0.dp): () -> Dp? {
        val activeHeader = header
        val measuredHeight = activeHeader?.measuredHeight?.invoke()?.coerceAtLeast(0.dp)
        return {
            val base = baseTopInset.value?.invoke()
            if (activeHeader == null || base == null) null else {
                val visibleHeight = if (measuredHeight != null && activeHeader.scrollOffset != null) {
                    (measuredHeight + activeHeader.scrollOffset.invoke()).coerceIn(0.dp, measuredHeight)
                } else activeHeader.visibleHeight().coerceAtLeast(0.dp)
                base + visibleHeight + panelGap
            }
        }
    }

    fun detach(owner: Any) {
        if (header?.owner === owner) header = null
    }

    fun miniPrecedesHeader(): Boolean = miniBeforeHeaderHeight.value != null

    /** Live visual geometry, independent of the list's fixed initial reserve. */
    fun headerOverlayBottom(): Dp? = header?.let { active ->
        active.overlayTopInset?.invoke()?.let { top -> top + active.visibleHeight().coerceAtLeast(0.dp) }
    }

    fun headerTopInset(): Dp? = baseTopInset.value?.invoke()?.let { base ->
        miniBeforeHeaderHeight.value?.let { base + it + topGap.value }
            ?: (base - topGap.value).coerceAtLeast(0.dp)
    }

    fun miniTopInset(): Dp? {
        val activeHeader = header ?: return null
        val base = baseTopInset.value?.invoke() ?: return null
        return base + activeHeader.visibleHeight().coerceAtLeast(0.dp)
    }
}

internal val LocalHomeTopMiniPlayerAnchor = staticCompositionLocalOf<HomeTopMiniPlayerAnchorState?> { null }

internal fun miniPrecedesHomeHeader(retainTopNavigation: Boolean, landscapeBarsHidden: Boolean): Boolean =
    retainTopNavigation || landscapeBarsHidden

internal fun visibleHomeHeaderHeightPx(height: Int, offset: Float): Float =
    (height + offset).coerceIn(0f, height.coerceAtLeast(0).toFloat())
