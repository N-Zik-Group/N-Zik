package app.n_zik.android.enums

internal enum class PlayerOpeningMode {
    Audio,
    Video,
    RememberLast;

    fun opensInVideo(lastModeWasVideo: Boolean): Boolean = when (this) {
        Audio -> false
        Video -> true
        RememberLast -> lastModeWasVideo
    }
}
