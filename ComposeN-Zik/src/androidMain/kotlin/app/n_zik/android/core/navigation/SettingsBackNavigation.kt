package app.n_zik.android.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Routes toolbar and app-level back actions to the active nested Settings host. */
class SettingsBackNavigation(private val currentRoute: () -> String?) {
    private var owner: Any? = null
    private var onBack: (() -> Unit)? = null
    private var canNavigateBack by mutableStateOf<() -> Boolean>({ false })

    /** Lets the nested NavHost own predictive gestures while it can pop a page. */
    val hasNestedBackStack: Boolean
        get() = isActive && canNavigateBack()

    /** Observes the outer route so an outgoing Settings host stops intercepting back. */
    val isActive: Boolean
        get() = currentRoute()?.let { it == "settings" || it.startsWith("settings?") } ?: false

    /** Registers the current host; an outgoing host cannot remove its replacement. */
    fun register(owner: Any, canNavigateBack: () -> Boolean = { false }, onBack: () -> Unit) {
        this.owner = owner
        this.onBack = onBack
        this.canNavigateBack = canNavigateBack
    }

    /** Releases callbacks owned by the disposed host. */
    fun unregister(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            onBack = null
            canNavigateBack = { false }
        }
    }

    /** Returns true only when the current Settings host consumed this back action. */
    fun handleBack(): Boolean {
        if (!isActive) return false
        val handler = onBack ?: return false
        handler()
        return true
    }
}

/** One back router per app navigation host, shared with its toolbar. */
val LocalSettingsBackNavigation = staticCompositionLocalOf<SettingsBackNavigation?> { null }
