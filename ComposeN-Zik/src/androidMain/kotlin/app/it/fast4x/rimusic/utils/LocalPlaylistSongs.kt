package app.it.fast4x.rimusic.utils

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.themed.DeleteDialog

@SuppressLint("ComposableNaming")
@Composable
fun DeletePlaylist(
    activeState: MutableState<Boolean> = rememberSaveable { mutableStateOf( false ) },
    menuState: MenuState = LocalMenuState.current,
    onEvent: DeleteDialog.() -> Unit
): DeleteDialog = object : DeleteDialog( activeState, menuState ) {

    override val confirmationTitle: String
        @Composable get() = stringResource(R.string.dialog_delete_playlist)
    override val confirmationAction: String
        @Composable get() = stringResource(R.string.delete)

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.delete_playlist )

    override fun onConfirm() = onEvent()
}


