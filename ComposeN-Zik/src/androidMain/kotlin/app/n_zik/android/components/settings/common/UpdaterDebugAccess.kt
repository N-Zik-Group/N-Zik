package app.n_zik.android.components.settings.common

internal const val DEBUG_UPDATER_ACCESS_KEY = "debugUpdaterLayoutAccess"

/** Opens the existing updater UI for debug inspection without enabling auto-update. */
internal fun canOpenUpdater(autoUpdate: Boolean, debugBuild: Boolean, debugAccess: Boolean): Boolean =
    autoUpdate || (debugBuild && debugAccess)
