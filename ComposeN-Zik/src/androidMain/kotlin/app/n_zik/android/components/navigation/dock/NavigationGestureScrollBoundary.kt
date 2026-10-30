package app.n_zik.android.components.navigation.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/** A drag on the tabs must not be mistaken for a page scroll by the chrome owner. */
@Composable
internal fun Modifier.navigationGestureScrollBoundary(): Modifier {
    val boundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(0f, available.y)
        }
    }
    return nestedScroll(boundary)
}
