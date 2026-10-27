package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState

class RetrySyncDialog(
    private val activeState: MutableState<Boolean>,
    val failedCount: Int,
    val onRetry: () -> Unit
) : ConfirmDialog {

    companion object {
        @Composable
        operator fun invoke(failedCount: Int, onRetry: () -> Unit) = RetrySyncDialog(
            activeState = remember { mutableStateOf(false) },
            failedCount = failedCount,
            onRetry = onRetry
        )
    }

    override var isActive: Boolean
        get() = activeState.value
        set(value) {
            activeState.value = value
        }

    override val confirmText: String
        @Composable get() = stringResource(R.string.retry)

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.sync_failed)

    @Composable
    override fun DialogBody() {
        BasicText(
            text = stringResource(R.string.retry_sync_failed_items, failedCount),
            style = typography().s.medium.copy(color = colorPalette().text)
        )
    }

    override fun onConfirm() {
        hideDialog()
        onRetry()
    }
}
