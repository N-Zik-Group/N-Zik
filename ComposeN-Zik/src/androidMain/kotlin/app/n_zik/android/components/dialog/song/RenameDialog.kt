package app.n_zik.android.components.dialog.song

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.dialog.common.InputDialogConstraints
import app.n_zik.android.components.dialog.common.TextInputDialog
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R

abstract class RenameDialog(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>
): TextInputDialog(InputDialogConstraints.ALL), MenuIcon, Descriptive {

    override var isActive: Boolean by activeState
    override var value: TextFieldValue by valueState

    override fun onShortClick() = showDialog()

    @Composable
    override fun LeadingIcon() = Icon(
        painter = icon,
        tint = colorPalette().text,
        contentDescription = stringResource(R.string.accessibility_rename_dialog_text_box_icon),
        modifier = Modifier.size( 20.dp )
    )
}




