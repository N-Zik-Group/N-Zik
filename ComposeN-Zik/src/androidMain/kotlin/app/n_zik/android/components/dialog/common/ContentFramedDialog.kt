package app.n_zik.android.components.dialog.common

import androidx.compose.runtime.Composable

/** Dialog whose body explicitly supplies its shared frame and footer slots. */
interface ContentFramedDialog : Dialog {
    @Composable
    override fun Render() {
        DialogPresence(visible = isActive, inherit = true) { DialogBody() }
    }
}
