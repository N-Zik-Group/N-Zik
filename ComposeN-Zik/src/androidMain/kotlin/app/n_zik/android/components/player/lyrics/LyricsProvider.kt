package app.n_zik.android.components.player.lyrics

import app.n_zik.android.enums.lyrics.LyricsType

/** Network sources selectable independently for each automatic lyrics mode. */
enum class LyricsProvider {
    BetterLyrics,
    LrcLib,
    KuGou,
    YouTube;

    companion object {
        fun availableFor(mode: LyricsType): List<LyricsProvider> = when (mode) {
            LyricsType.Auto -> entries
            LyricsType.Karaoke -> listOf(BetterLyrics)
            LyricsType.Synced -> listOf(BetterLyrics, LrcLib, KuGou)
            LyricsType.Unsynced -> listOf(LrcLib, YouTube)
        }

        fun preferenceKey(mode: LyricsType): String = "lyricsProviders.${mode.name}"

        fun decode(mode: LyricsType, stored: String): Set<LyricsProvider> {
            val available = availableFor(mode)
            val storedNames = stored.split(',').toSet()
            return available.filterTo(linkedSetOf()) { it.name in storedNames }
                .ifEmpty { available.toSet() }
        }

        fun encode(providers: Set<LyricsProvider>): String =
            entries.filter { it in providers }.joinToString(",") { it.name }
    }
}
