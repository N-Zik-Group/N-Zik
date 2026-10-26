package app.n_zik.android.components.settings.common

import app.n_zik.android.BuildConfig

/**
 * Whether this build ships with auto-update (foss/debug builds do not). Single
 * seam over [BuildConfig] so the global page's Update row state stays
 * observable in unit tests.
 */
fun hasAutoUpdate(): Boolean = BuildConfig.IS_AUTOUPDATE
