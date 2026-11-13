package app.n_zik.android.ui.components.themed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.ConfirmDialog
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

abstract class DeleteDialog protected constructor(
    protected val activeState: MutableState<Boolean>,
    protected val menuState: MenuState
): ConfirmDialog, MenuIcon, Descriptive {

    override val iconId: Int = R.drawable.trash
    override val messageId: Int = R.string.common_action_delete
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var isActive: Boolean = activeState.value
        set(value) {
            activeState.value = value
            field = value
        }

    override fun onShortClick() = super.onShortClick()

    override fun onDismiss() {
        super.onDismiss()
        menuState.hide()
    }
}


