package app.n_zik.android.components.player.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.it.fast4x.rimusic.utils.textCopyToClipboard

/**
 * Copies [text] to the clipboard exactly once per tap.
 *
 * The copy lives in a [LaunchedEffect] keyed on [copyTrigger] instead of in the composition
 * body: it only runs when the trigger value changes, and [onConsumed] is invoked right after
 * the write so the trigger can be reset to false. A plain `if (copyTrigger)` in the
 * composition body with a never-reset flag re-ran the copy on every recomposition of the
 * lyrics screen (constant recomposition from preferences, auto-scroll and rotation).
 */
@Composable
internal fun LyricsCopyEffect(
    copyTrigger: Boolean,
    text: String?,
    onConsumed: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(copyTrigger) {
        if (copyTrigger) {
            text?.let { textCopyToClipboard(it, context) }
            onConsumed()
        }
    }
}
