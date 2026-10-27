package app.n_zik.android.easter

import app.n_zik.android.updater.models.UpdateEndpoints

/**
 * A hidden easter egg image served from the N-Zik repository
 * (spec-remote-easter-eggs), identified by its raw asset file name.
 */
enum class EasterEgg(val fileName: String) {
    PACMAN("easter1.jpg"),
    SNAKE("easter2.jpg");

    /** GitHub raw URL of the asset, same shape as the [UpdateEndpoints] changelog URL. */
    val rawUrl: String = "https://raw.githubusercontent.com/${UpdateEndpoints.REPO}/main/assets/easter/$fileName"
}
