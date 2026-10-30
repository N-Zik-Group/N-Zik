package app.n_zik.android.components.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics

/** NavHost commits its incoming content after the controller has already changed target. */
internal class CommittedNavigationPresentation<T> {
    private var committedPageKey: String? = null

    data class Presentation<T>(val registration: T?, val interactive: Boolean)

    fun resolve(targetKey: String?, expectsTabs: Boolean, registration: (String?) -> T?): Presentation<T> {
        val incoming = registration(targetKey)
        if (incoming != null || !expectsTabs) {
            committedPageKey = targetKey
            return Presentation(incoming, true)
        }
        // This is the still-visible outgoing destination, never an arbitrary cached page.
        // Once the incoming destination commits, this key can no longer select its predecessor.
        return Presentation(registration(committedPageKey), committedPageKey == targetKey)
    }
}

/** A visible outgoing control must not dispatch an action to its old destination. */
internal fun Modifier.navigationPresentationInput(interactive: Boolean): Modifier =
    if (interactive) this else semantics { disabled() }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
