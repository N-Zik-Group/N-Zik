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
     * The update channel of the current build: "beta" when the version name
     * carries a beta suffix ("-beta64"/"-beta32"), "stable" otherwise.
     */
    fun getCurrentBuildType(): String {
        val version = BuildConfig.VERSION_NAME.lowercase()
        return if (version.contains(UpdaterConstants.SUFFIX_BETA64) ||
            version.contains(UpdaterConstants.SUFFIX_BETA32)
        ) {
            UpdaterConstants.TYPE_BETA
        } else {
            UpdaterConstants.TYPE_STABLE
        }
    }

    /** Determines if a build transition warning should be shown. Returns: - "stable-to-beta" - "beta-to-stable" - null if no transition or same build type */
    fun getTransitionType(lastBuildType: String?): String? {
        if (lastBuildType.isNullOrEmpty()) return null
        val currentBuildType = getCurrentBuildType()
        
        if (lastBuildType == UpdaterConstants.TYPE_STABLE && currentBuildType == UpdaterConstants.TYPE_BETA) return "${UpdaterConstants.TYPE_STABLE}-to-${UpdaterConstants.TYPE_BETA}"
        if (lastBuildType == UpdaterConstants.TYPE_BETA && currentBuildType == UpdaterConstants.TYPE_STABLE) return "${UpdaterConstants.TYPE_BETA}-to-${UpdaterConstants.TYPE_STABLE}"
        
        return null
    }
}
