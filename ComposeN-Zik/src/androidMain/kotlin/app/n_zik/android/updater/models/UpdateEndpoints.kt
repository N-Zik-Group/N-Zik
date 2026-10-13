package app.n_zik.android.updater.models

import app.n_zik.android.BuildConfig

/**
 * GitHub endpoints of the N-Zik repository: the releases API, the APK
 * download URLs, the changelog raw files (in-app updater) and the public
 * repo links (About screen).
 *
 * Migrated from the legacy `app.kreate.android.me.knighthat.utils.Repository`,
 * which has been removed.
 */
object UpdateEndpoints {

    const val GITHUB = "https://github.com"

    private const val GITHUB_API = "https://api.github.com"
    private const val GITHUB_RAW = "https://raw.githubusercontent.com"
    private const val OWNER = "N-Zik-Group"

    const val REAL_OWNER = "NEVARLeVrai"

    const val REPO = "$OWNER/${BuildConfig.APP_NAME}"

    const val REPO_URL = "$GITHUB/$REPO"

    const val RELEASES_API_URL = "$GITHUB_API/repos/$REPO/releases"

    const val RELEASE_DOWNLOAD_URL = "$REPO_URL/releases/download/v"

    const val CHANGELOGS_PATH = "Updater/changelogs"

    const val CHANGELOGS_URL = "$GITHUB_RAW/$REPO/main/$CHANGELOGS_PATH"
}
