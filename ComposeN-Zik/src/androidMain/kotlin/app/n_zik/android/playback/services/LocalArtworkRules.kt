package app.n_zik.android.playback.services

/**
 * Pure (Android-free) rules behind the signed local-artwork channel served by
 * [ArtworkContentProvider]. Kept as plain string logic so they stay unit-testable
 * off device.
 *
 * Why the channel exists: the Android Auto home media card is drawn by the
 * Android Auto process (`com.google.android.projection.gearhead`), which loads the
 * session artwork URI with its own Glide — not the embedded artwork bytes. A raw
 * MediaStore `content://media/.../albumart/{id}` URI comes back as a non-seekable
 * stream there (ESPIPE "Illegal seek"), and an `android.resource://` / `file://`
 * URI is not resolvable either, so the card stays empty. Every local track is
 * therefore published with an URI on the app's exported provider, which always
 * answers with a seekable square JPEG (custom cover, then MediaStore art, then the
 * app placeholder).
 */
object LocalArtworkRules {

    /** Android Auto projection process (phone-side renderer of every AA surface). */
    const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

    /**
     * Callers allowed to read MediaStore album art through the provider. The provider
     * is exported (Android Auto must open it), so MediaStore art is only relayed to
     * the car renderers and the system media surfaces — never to arbitrary apps that
     * do not hold the media read permission themselves.
     */
    private val TRUSTED_EXTERNAL_CALLERS = setOf(
        ANDROID_AUTO_PACKAGE,
        // Android Automotive OS media center
        "com.android.car.media",
        // System media controls (notification shade / lockscreen)
        "com.android.systemui",
    )

    /** Song id of a player/browse media id (`path/local:123` or `local:123`). */
    fun songIdOf(mediaId: String): String = mediaId.substringAfterLast('/')

    /** MediaStore audio id of a local song id (`local:123` → 123), null otherwise. */
    fun mediaStoreAudioIdOf(songId: String): Long? =
        songId.takeIf { it.startsWith(LOCAL_KEY_PREFIX) }
            ?.removePrefix(LOCAL_KEY_PREFIX)
            ?.toLongOrNull()

    /**
     * True when an item's artwork URI must be swapped for the provider URI.
     * Custom covers (`file://` in the app's private files, for local tracks AND
     * streams) are never readable by the Android Auto process. For local tracks,
     * anything else it cannot decode on its own (MediaStore, resource or missing
     * URI) is swapped too. A URI already on [providerAuthority] and remote http(s)
     * artwork are kept untouched.
     */
    fun needsProviderArtwork(mediaId: String, artworkUri: String?, providerAuthority: String): Boolean {
        if (artworkUri != null && (artworkUri.startsWith("file://") || artworkUri.startsWith("/"))) return true
        if (mediaStoreAudioIdOf(songIdOf(mediaId)) == null) return false
        if (artworkUri.isNullOrBlank()) return true
        if (artworkUri.startsWith("content://$providerAuthority/")) return false
        if (artworkUri.startsWith("http://") || artworkUri.startsWith("https://")) return false
        return true
    }

    /** Whether [callingPackage] may receive MediaStore album art through the provider. */
    fun isTrustedCaller(callingPackage: String?, ownPackage: String): Boolean =
        callingPackage != null &&
            (callingPackage == ownPackage || callingPackage in TRUSTED_EXTERNAL_CALLERS)
}
