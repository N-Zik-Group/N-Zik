package app.n_zik.android.components.menu

import android.content.ActivityNotFoundException
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.ListenOnDialog
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import timber.log.Timber

@Composable
internal fun rememberListenOnMenuAction(
    mediaId: String,
    menuState: MenuState,
    onOpened: () -> Unit = {},
): MenuIcon {
    var showDialog by remember(mediaId) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    DialogPresence(visible = showDialog) {
        ListenOnDialog(
            mediaId = mediaId,
            onDismiss = { showDialog = false },
            onPlayOnUrl = { url ->
                if (openExternalMenuLink { uriHandler.openUri(url) }) {
                    onOpened()
                    showDialog = false
                    menuState.hide()
                }
            },
        )
    }
    return remember(mediaId) {
        object : MenuIcon, Descriptive {
            override val iconId = R.drawable.play
            override val messageId = R.string.player_listen_on_title
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { showDialog = true }
        }
    }
}

internal fun openExternalMenuLink(open: () -> Unit): Boolean {
    val failure = try {
        open()
        return true
    } catch (error: ActivityNotFoundException) {
        error
    } catch (error: IllegalArgumentException) {
        error
    }
    Timber.tag("ExternalMenuLink").w("Unable to open link (%s)", failure.javaClass.simpleName)
    Toaster.e(R.string.common_error_title)
    return false
}
