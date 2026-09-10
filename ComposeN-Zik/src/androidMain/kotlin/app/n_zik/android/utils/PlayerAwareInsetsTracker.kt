package app.n_zik.android.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn

/**
 * Resolves the player-aware bottom [WindowInsets] for the current sheet position,
 * caching the produced instance so a stable clamped bottom always returns the
 * same instance.
 *
 * The sheet position changes every frame while the mini-player is dragged, but
 * the clamped bottom is constant except while approaching the dismissed bound.
 * Returning the same instance keeps a wrapping [androidx.compose.runtime.derivedStateOf]
 * value unchanged, so `LocalPlayerAwareWindowInsets` consumers are not invalidated
 * (and recomposed) on every drag frame.
 */
class PlayerAwareInsetsTracker(
    private val baseInsets: WindowInsets,
    private val lowerBound: Dp,
    private val upperBound: Dp,
) {
    private var cachedBottom: Dp? = null
    private var cachedInsets: WindowInsets? = null

    fun resolve(sheetValue: Dp): WindowInsets {
        val bottom = sheetValue.coerceIn(lowerBound, upperBound)
        cachedInsets?.let { insets ->
            if (cachedBottom == bottom) return insets
        }
        val insets = baseInsets.add(WindowInsets(bottom = bottom))
        cachedBottom = bottom
        cachedInsets = insets
        return insets
    }
}
