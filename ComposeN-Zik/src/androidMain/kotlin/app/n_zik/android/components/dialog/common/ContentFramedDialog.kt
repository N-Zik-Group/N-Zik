package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable
import app.n_zik.android.components.settings.common.SettingsDialogSearchScope

/** Dialog whose body explicitly supplies its shared frame and footer slots. */
interface ContentFramedDialog : Dialog {
    @Composable
    override fun Render() {
        SettingsDialogSearchScope(this) {
            DialogPresence(visible = isActive, inherit = true) { DialogBody() }
        }
    }
}
