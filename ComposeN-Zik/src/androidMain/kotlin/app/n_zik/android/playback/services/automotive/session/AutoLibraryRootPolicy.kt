package app.n_zik.android.playback.services.automotive.session

internal object AutoLibraryRootPolicy {
    private const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"

    fun useSuggestedRoot(
        packageName: String,
        isSuggested: Boolean,
        isRecent: Boolean,
        isOffline: Boolean,
    ): Boolean {
        if (isSuggested) return true
        if (isRecent || isOffline) return false
        // Observed For You host without suggested hints, not an official Android Auto contract:
        // https://github.com/androidx/media/issues/1731. This selects content, never permissions.
        return packageName == GOOGLE_SEARCH_PACKAGE
    }
}
