package app.n_zik.android.enums.lyrics

enum class LyricsFontSize {
    Light,
    Medium,
    Heavy,
    Large,
    Custom;

    companion object {
        /**
         * Single shared default for the lyrics font size preference.
         * The lyrics screen (rendering) and the settings menu (display) both
         * read the same preference key; they must fall back to this same
         * default, or the menu shows a size different from the one actually
         * rendered on a fresh install (no stored value yet).
         */
        val DEFAULT: LyricsFontSize = Large
    }
}



