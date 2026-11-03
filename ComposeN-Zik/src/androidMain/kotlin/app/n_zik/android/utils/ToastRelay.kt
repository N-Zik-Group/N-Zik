package app.n_zik.android.utils

import androidx.annotation.StringRes
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.appContext
import java.util.concurrent.CopyOnWriteArrayList
import timber.log.Timber

/** Relays every legacy [Toaster] toast once, preserving resource keys for the PC bridge. */
object ToastRelay {

    /** Wire type of a relayed toast: the phone's [Toaster.Type] in lower case. */
    enum class Kind(val wire: String) { NORMAL("normal"), SUCCESS("success"), INFO("info"), WARNING("warning"), ERROR("error") }

    /** A relayed toast: the string resource name (empty for a plain-string toast), its format arguments, its type and the text the phone showed. */
    data class Relayed(val key: String, val args: List<String>, val kind: Kind, val message: String)

    fun interface Listener {
        fun onToast(toast: Relayed)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    /** The key and arguments of the resource toast being built on this thread (see [legacyResource]). */
    private val pendingResource = ThreadLocal<Pair<String, List<String>>?>()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    /** Formats a resource toast and remembers its identity for the next [onLegacyToast]. */
    fun legacyResource(@StringRes messageId: Int, formatArgs: Array<out Any?>): String {
        // A stale key left by a toast that threw before its hook never tags the next one
        pendingResource.remove()
        val resources = appContext().resources
        val message = resources.getString(messageId, *formatArgs)
        if (listeners.isNotEmpty()) {
            val key = runCatching { resources.getResourceEntryName(messageId) }
                .onFailure { Timber.tag("ToastRelay").w(it, "Could not resolve a toast resource key") }
                .getOrNull()
            pendingResource.set(key?.let { it to formatArgs.map(Any?::toString) })
        }
        return message
    }

    /** Called by the legacy [Toaster]'s base `toast(message, …)` for every toast it shows. */
    fun onLegacyToast(message: String, typeName: String) {
        val pending = try {
            pendingResource.get()
        } finally {
            pendingResource.remove()
        }
        if (listeners.isEmpty()) return
        val relayed = runCatching { relayedOf(message, typeName, pending) }
            .onFailure { Timber.tag("ToastRelay").w(it, "Could not relay a toast") }
            .getOrNull() ?: return
        listeners.forEach { it.onToast(relayed) }
    }

    /** The relayed form of a toast: with its resource key when one is pending, as text otherwise. */
    internal fun relayedOf(message: String, typeName: String, pending: Pair<String, List<String>>?): Relayed {
        val kind = Kind.entries.firstOrNull { it.name == typeName } ?: Kind.NORMAL
        return Relayed(
            pending?.first?.let(StringResourceIdentity::legacyName).orEmpty(),
            pending?.second.orEmpty(),
            kind,
            message,
        )
    }

    fun s(@StringRes messageId: Int, vararg formatArgs: Any?) = show(Kind.SUCCESS, messageId, formatArgs)
    fun i(@StringRes messageId: Int, vararg formatArgs: Any?) = show(Kind.INFO, messageId, formatArgs)
    fun w(@StringRes messageId: Int, vararg formatArgs: Any?) = show(Kind.WARNING, messageId, formatArgs)
    fun e(@StringRes messageId: Int, vararg formatArgs: Any?) = show(Kind.ERROR, messageId, formatArgs)
    fun n(@StringRes messageId: Int, vararg formatArgs: Any?) = show(Kind.NORMAL, messageId, formatArgs)

    /** Shows through [Toaster] only: its hook relays the toast (never twice). */
    private fun show(kind: Kind, @StringRes messageId: Int, formatArgs: Array<out Any?>) {
        // Same overloads as the callers used before the wrapper (no-argument ones when there is no argument)
        if (formatArgs.isEmpty()) {
            when (kind) {
                Kind.NORMAL -> Toaster.n(messageId)
                Kind.SUCCESS -> Toaster.s(messageId)
                Kind.INFO -> Toaster.i(messageId)
                Kind.WARNING -> Toaster.w(messageId)
                Kind.ERROR -> Toaster.e(messageId)
            }
        } else {
            when (kind) {
                Kind.NORMAL -> Toaster.n(messageId, *formatArgs)
                Kind.SUCCESS -> Toaster.s(messageId, formatArgs = *formatArgs)
                Kind.INFO -> Toaster.i(messageId, formatArgs = *formatArgs)
                Kind.WARNING -> Toaster.w(messageId, formatArgs = *formatArgs)
                Kind.ERROR -> Toaster.e(messageId, formatArgs = *formatArgs)
            }
        }
    }
}
