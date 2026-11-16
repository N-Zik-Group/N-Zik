package app.n_zik.android.components.player

/** Keeps the sheet mounted through dismissal, even when playback has already cleared its media. */
internal fun shouldComposePlayerSheet(mediaPresent: Boolean, isDismissed: Boolean): Boolean =
    mediaPresent || !isDismissed
