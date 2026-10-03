package app.n_zik.android.playback.services

/**
 * Pure (Android-free) rules behind the unknown-metadata placeholders published
 * through the session ([SessionArtworkPlayer]) and the Android Auto item mappers
 * ([app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper]).
 * Kept as plain string logic so they stay unit-testable off device.
 *
 * Why: tracks whose title or artist is missing (untagged local files, streams
 * whose metadata did not resolve) carried the raw empty string into the session
 * metadata, so the Android Auto surfaces and the media notification rendered a
 * blank label while the phone lists already show the localized placeholders
 * (`R.string.unknown_title` / `R.string.unknown_artist`) through their own
 * display-layer fallbacks. The placeholder strings themselves stay in the
 * resource layer — this object only decides WHEN a field counts as missing.
 */
object SessionMetadataFallbacks {

    /**
     * True when [text] is missing enough that the surface would render a blank
     * label: null, blank, or the literal "null" some persistence paths store
     * for an absent tag (the same contract as the `*WithFallback` display
     * helpers in `app.n_zik.android.utils.MediaItemUtils` and the legacy
     * `asMediaItem`).
     */
    fun needsFallback(text: CharSequence?): Boolean =
        text.isNullOrBlank() || text.toString() == "null"
}
