package app.n_zik.android.components.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.n_zik.android.LocalPlayerAwareWindowInsets

/** Use inside a scroll owner; its viewport must remain behind the app overlays. */
@Composable
internal fun rememberPageChromeContentPadding(
    base: PaddingValues = PaddingValues(0.dp),
    topInsetProvider: (() -> Dp)? = null,
    extraTopProvider: (() -> Dp)? = null,
): PaddingValues {
    val topInset = topInsetProvider ?: rememberTopContentInsetProvider(hasNavBar = false)
    val railInsets = LocalSideRailContentInsets.current.asPaddingValues()
    val playerInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    return remember(base, topInset, extraTopProvider, railInsets, playerInsets) {
        PageChromeContentPadding(base, topInset, railInsets, playerInsets, extraTopProvider)
    }
}

internal class PageChromeContentPadding(
    private val base: PaddingValues,
    private val topInset: () -> Dp,
    private val railInsets: PaddingValues,
    private val playerInsets: PaddingValues,
    private val extraTop: (() -> Dp)? = null,
) : PaddingValues {
    override fun calculateTopPadding(): Dp =
        base.calculateTopPadding() + topInset() + (extraTop?.invoke() ?: 0.dp)

    override fun calculateBottomPadding(): Dp =
        maxOf(base.calculateBottomPadding(), playerInsets.calculateBottomPadding())

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateLeftPadding(layoutDirection) + railInsets.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateRightPadding(layoutDirection) + railInsets.calculateRightPadding(layoutDirection)
}
