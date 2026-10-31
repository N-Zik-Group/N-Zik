package app.n_zik.android.updater.models

object UpdaterConstants {
    // Version name suffixes (BuildConfig.VERSION_NAME markers), per build type —
    // spec-ffmpeg-extension-module build-matrix rename
    const val SUFFIX_RELEASE64 = "-r64"
    const val SUFFIX_RELEASE32 = "-r32"
    const val SUFFIX_MINIFIED64 = "-m64"
    const val SUFFIX_MINIFIED32 = "-m32"
    const val SUFFIX_BETA64 = "-beta64"
    const val SUFFIX_BETA32 = "-beta32"
    const val SUFFIX_DEV64 = "-dev64"
    const val SUFFIX_DEV32 = "-dev32"

    // Suffix characters (the part after the version's "-")
    const val SUFFIX_CHAR_RELEASE64 = "r64"
    const val SUFFIX_CHAR_RELEASE32 = "r32"
    const val SUFFIX_CHAR_MINIFIED64 = "m64"
    const val SUFFIX_CHAR_MINIFIED32 = "m32"
    const val SUFFIX_CHAR_BETA64 = "beta64"
    const val SUFFIX_CHAR_BETA32 = "beta32"
    const val SUFFIX_CHAR_DEV64 = "dev64"
    const val SUFFIX_CHAR_DEV32 = "dev32"

    // Release tag suffixes — tags are shared per channel and UNCHANGED by the
    // rename: stable "v8.4.5", beta "v8.4.5-beta", dev "v8.4.5-dev-<date>".
    // Parsed from tag names, so the updater must recognize them alongside the
    // version-name suffixes above.
    const val SUFFIX_BETA_TAG = "-beta"
    const val SUFFIX_CHAR_BETA_TAG = "beta"
    const val SUFFIX_DEV_TAG = "-dev-"
    const val SUFFIX_CHAR_DEV_TAG = "dev"

    // Tag targeted by unrecognized flavors — it never exists, so the
    // updater reports a clean "no update" instead of offering a stable APK
    const val SUFFIX_UNKNOWN = "-unknown"
    const val SUFFIX_CHAR_UNKNOWN = "unknown"

    // Build type names — they double as the APK asset-name bases
    // ("N-Zik-<type>.apk", via outputFileName in build.gradle.kts)
    const val TYPE_RELEASE64 = "release64"
    const val TYPE_RELEASE32 = "release32"
    const val TYPE_MINIFIED64 = "minified64"
    const val TYPE_MINIFIED32 = "minified32"
    const val TYPE_BETA64 = "beta64"
    const val TYPE_BETA32 = "beta32"
    const val TYPE_DEV64 = "dev64"
    const val TYPE_DEV32 = "dev32"

    // Channel names (MajorUpdateConfig, stable↔beta transition warnings)
    const val TYPE_BETA = "beta"
    const val TYPE_STABLE = "stable"

    const val PREFIX_VERSION = "v"

    const val CHANGELOG_ADDED = "added"
    const val CHANGELOG_CHANGED = "changed"
    const val CHANGELOG_IMPROVED = "improved"
    const val CHANGELOG_FIXED = "fixed"
    const val CHANGELOG_REFACTOR = "refactor"
    const val CHANGELOG_OTHER = "other"
    const val CHANGELOG_REMOVED = "removed"
    const val CHANGELOG_DEPRECATED = "deprecated"
    const val CHANGELOG_DEV = "dev"
}
