package app.n_zik.android.components.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/** Marks a rail dispatch without consuming its scroll or forwarding it as page travel. */
internal class SideRailScrollOrigin {
    private var railDispatch = false

    fun mark() { railDispatch = true }

    fun take(): Boolean = railDispatch.also { railDispatch = false }
}

internal val LocalSideRailScrollOrigin = staticCompositionLocalOf<SideRailScrollOrigin?> { null }

@Composable
internal fun Modifier.sideRailScrollOrigin(): Modifier {
    val origin = LocalSideRailScrollOrigin.current ?: return this
    val connection = remember(origin) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Post-scroll visits the rail before the root chrome observer, including flings.
                origin.mark()
                return Offset.Zero
            }
        }
    }
    return nestedScroll(connection)
}
