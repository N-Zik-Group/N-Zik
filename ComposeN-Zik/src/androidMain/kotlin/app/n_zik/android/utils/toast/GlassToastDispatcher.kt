package app.n_zik.android.utils.toast

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.annotation.MainThread

/** UI delivery only. The legacy entry point remains the sole owner of the PC relay. */
internal data class GlassToastMessage(
    val text: String,
    val duration: Int,
    val icon: Drawable?,
    val background: Int,
    val foreground: Int,
    val id: Any = Any(),
)

internal fun toastDurationMillis(duration: Int): Long =
    if (duration == Toast.LENGTH_LONG) 3500L else 2000L

/** A host accepts synchronously, so the caller can immediately use the native fallback. */
object GlassToastDispatcher {
    private val hosts = linkedMapOf<Any, (GlassToastMessage) -> Boolean>()

    @MainThread
    internal fun register(owner: Any, accept: (GlassToastMessage) -> Boolean) { hosts[owner] = accept }

    @MainThread
    internal fun unregister(owner: Any) { hosts.remove(owner) }

    @MainThread
    fun tryShow(message: String, duration: Int, icon: Drawable?, background: Int, foreground: Int): Boolean {
        val toast = GlassToastMessage(message, duration, icon, background, foreground)
        return hosts.values.toList().asReversed().any { it(toast) }
    }
}

/** Keep the active toast stable and only the four most recent distinct pending messages. */
internal fun enqueueGlassToast(queue: MutableList<GlassToastMessage>, message: GlassToastMessage) {
    val duplicate = queue.indexOfFirst { pending ->
        pending.text == message.text && pending.duration == message.duration &&
            pending.icon === message.icon && pending.background == message.background &&
            pending.foreground == message.foreground
    }
    if (duplicate >= 0) return
    if (queue.size >= 5) queue.removeAt(1)
    queue.add(message)
}
