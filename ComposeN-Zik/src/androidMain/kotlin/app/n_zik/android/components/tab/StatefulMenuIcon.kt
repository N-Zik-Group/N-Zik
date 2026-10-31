package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.colorPalette

/** Live stateful actions keep their menu open unless a one-shot action explicitly dismisses it. */
open class StatefulMenuIcon(
    private val menuState: MenuState,
    override val messageId: Int,
    private val readActive: () -> Boolean,
    private val readIcon: () -> Int,
    private val readTitle: () -> Int,
    private val performAction: () -> Unit,
    private val closeMenuAfterAction: Boolean = false,
) : MenuIcon, Descriptive {
    val active: Boolean get() = readActive()
    override val iconId: Int get() = readIcon()
    override val color: Color
        @Composable get() = if (active) colorPalette().accent else colorPalette().text
    override val menuIconTitle: String
        @Composable get() = stringResource(readTitle())

    override fun onShortClick() {
        performAction()
        if (closeMenuAfterAction) menuState.hide()
    }

    override fun onLongClick() = Unit
}
