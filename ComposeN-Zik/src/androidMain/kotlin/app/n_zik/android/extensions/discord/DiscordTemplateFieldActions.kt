package app.n_zik.android.extensions.discord

import androidx.compose.runtime.MutableState
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R

/**
 * Draft-state behavior of the Discord template field dialog — the toolbar settings
 * dialogs' button contract (see ToggleListDialog): Reset restores the built-in
 * default (empties the draft so the default template applies again) WITHOUT saving —
 * the dialog stays open; Cancel discards the draft; OK persists the draft and
 * confirms with the "preference saved" toast.
 */
class DiscordTemplateFieldActions(private val textState: MutableState<String>) {

    /**
     * Reset the draft to the built-in default (the empty value — the default template
     * applies again, shown as the field placeholder). No save, no dismiss: the dialog
     * stays open so the user confirms with OK or discards with Cancel.
     */
    fun reset() {
        textState.value = ""
    }

    /** OK: persist the draft, confirm with the "preference saved" toast, dismiss the dialog. */
    fun confirm(onSave: (String) -> Unit, onDismiss: () -> Unit) {
        onSave(textState.value)
        Toaster.s(R.string.toast_preference_saved)
        onDismiss()
    }

    /** Cancel: dismiss the dialog, discarding the draft (no save, no toast). */
    fun cancel(onDismiss: () -> Unit) {
        onDismiss()
    }
}
