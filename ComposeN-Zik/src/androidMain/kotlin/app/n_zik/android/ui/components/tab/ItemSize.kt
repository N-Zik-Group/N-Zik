package app.n_zik.android.ui.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.enums.HomeItemSize
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.menu.SelectionMenu
import app.n_zik.android.utils.Preference

class ItemSize private constructor(
    val menuState: MenuState,
    private val sizeState: MutableState<HomeItemSize>
): MenuIcon, Descriptive {

    companion object {
        @JvmStatic
        @Composable
        fun init(key: Preference.Key<HomeItemSize>): ItemSize =
            ItemSize(
                LocalMenuState.current,
                Preference.remember(key)
            )
    }

    override val iconId: Int = R.drawable.resize
    override val messageId: Int = R.string.common_size_label
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.common_size_label )

    val size: HomeItemSize
        get() = sizeState.value

    override fun onShortClick() {
        menuState.display {
            SelectionMenu(
                title = stringResource(R.string.common_size_label),
                choices = HomeItemSize.entries,
                selectedChoice = size,
                label = { it.text },
                icon = { it.iconId },
                onSelected = { selected ->
                    sizeState.value = selected
                    menuState.hide()
                },
            )
        }
    }
}
