package app.n_zik.android.components.player

import kotlinx.coroutines.delay

/** Preserves the sheet when the activity unbinds and waits out transient empty queues. */
internal suspend fun resolvePlayerSheetMediaPresence(readPresence: () -> Boolean?): Boolean? {
    val presence = readPresence()
    if (presence != false) return presence

    // Listen Together can briefly clear the current item while replacing its queue.
    delay(400)
    return readPresence()
}

/** Keeps the sheet mounted through dismissal, even when playback has already cleared its media. */
internal fun shouldComposePlayerSheet(mediaPresent: Boolean, isDismissed: Boolean): Boolean =
    mediaPresent || !isDismissed
