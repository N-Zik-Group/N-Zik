package app.n_zik.android.components.settings.common

import app.n_zik.android.BuildConfig

/**
 * Whether this build ships with auto-update.
 */
fun hasAutoUpdate(): Boolean = BuildConfig.IS_AUTOUPDATE
