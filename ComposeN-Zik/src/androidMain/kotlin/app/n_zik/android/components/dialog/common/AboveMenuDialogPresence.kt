package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.canHostGlassMenuSheet
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.ui.components.MenuState

@Composable
internal fun AboveMenuDialogPresence(
    menuState: MenuState,
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    DialogPresence(visible = visible) {
        val glass = rememberGlassSettings()
        val host = LocalGlassMenuHost.current
        val sameWindow = host?.dialogs?.windowRoot === LocalView.current.rootView
        val menuSharesActivityWindow = canHostGlassMenuSheet(glass, glassRenderer(glass), sameWindow && host?.backdrop != null)
        val requiresNativeDialog = LocalRequireNativeDialogWindow.current ||
            (menuState.isDisplayed && !menuSharesActivityWindow)
        val nativeDialog = remember { requiresNativeDialog }
        CompositionLocalProvider(LocalRequireNativeDialogWindow provides nativeDialog, content = content)
    }
}
