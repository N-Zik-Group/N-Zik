package app.n_zik.android.components.dialog.common

import android.app.Activity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.intent
import kotlin.system.exitProcess

object RestartAppDialog: ConfirmDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.title_restart_required )

    override var isActive: Boolean by mutableStateOf(false)

    override fun hideDialog() = onConfirm()

    override fun onConfirm() {
        appContext().stopService( appContext().intent<PlayerServiceModern>() )
        appContext().stopService( appContext().intent<MyDownloadService>() )

        // Close other activities
        (appContext() as? Activity)?.finishAffinity()

        // Close app with exit 0 notify that no problem occurred
        exitProcess( 0 )
    }

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.confirm),
                onClick = ::onConfirm,
                primary = true
            )
        )
    )

    @Composable
    override fun DialogBody() {
        BasicText(
            text = stringResource( R.string.restart_dialog_body, BuildConfig.APP_NAME ),
            style = typography().xs.copy( color = colorPalette().text ),
            modifier = Modifier.padding( vertical = 20.dp )
        )
    }
}




