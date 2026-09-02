package app.n_zik.android.updater.models

import app.n_zik.android.BuildConfig

object MajorUpdateConfig {
    /**
     * The version code that triggers the major update warning.
     * Change this whenever you have a new major update that requires a warning.
     */
    const val TARGET_MAJOR_VERSION_CODE = 33

    /**
     * Determines if a major update warning should be shown.
     * @param lastVersionCode The version code saved from the previous run.
     * @param hasSeenAnyChangelog Whether the user has seen any changelogs (used to detect upgrades from untracked versions).
     */
    fun shouldShowWarning(lastVersionCode: Int, hasSeenAnyChangelog: Boolean): Boolean {
        val currentCode = BuildConfig.VERSION_CODE
        
        // Don't show if we are already past the target or if we haven't reached it yet
        if (currentCode < TARGET_MAJOR_VERSION_CODE) return false
        
        // If lastVersionCode is 0, it's either a fresh install or an upgrade from an old version that didn't track it.
        // We use hasSeenAnyChangelog to distinguish.
        val isUpgradeFromUntracked = lastVersionCode == 0 && hasSeenAnyChangelog
        val isUpgradeFromTracked = lastVersionCode in 1 until TARGET_MAJOR_VERSION_CODE
        
        return isUpgradeFromUntracked || isUpgradeFromTracked
    }

    /**
     * Extracts the build type from version name.
     * - Returns "beta" if version name contains "-b" (Beta).
     * - Returns "stable" if version name contains "-f" (Stable Full) or "-m" (Stable Minimal).
     * Default fallback is "stable".
     */
    fun getCurrentBuildType(): String {
        val version = BuildConfig.VERSION_NAME.lowercase()
        return when {
            version.contains(UpdaterConstants.SUFFIX_BETA) -> UpdaterConstants.TYPE_BETA
            version.contains(UpdaterConstants.SUFFIX_MINIFIED) || version.contains(UpdaterConstants.SUFFIX_FULL) -> UpdaterConstants.TYPE_STABLE
            else -> UpdaterConstants.TYPE_STABLE
        }
    }

    /**
     * Determines if a build transition warning should be shown.
     * Returns:
     * - "stable-to-beta"
     * - "beta-to-stable"
     * - null if no transition or same build type
     */
    fun getTransitionType(lastBuildType: String?): String? {
        if (lastBuildType.isNullOrEmpty()) return null
        val currentBuildType = getCurrentBuildType()
        
        if (lastBuildType == UpdaterConstants.TYPE_STABLE && currentBuildType == UpdaterConstants.TYPE_BETA) return "${UpdaterConstants.TYPE_STABLE}-to-${UpdaterConstants.TYPE_BETA}"
        if (lastBuildType == UpdaterConstants.TYPE_BETA && currentBuildType == UpdaterConstants.TYPE_STABLE) return "${UpdaterConstants.TYPE_BETA}-to-${UpdaterConstants.TYPE_STABLE}"
        
        return null
    }
}
