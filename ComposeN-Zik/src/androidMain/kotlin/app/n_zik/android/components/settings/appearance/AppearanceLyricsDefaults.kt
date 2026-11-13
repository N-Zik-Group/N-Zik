package app.n_zik.android.components.settings.appearance

import android.content.SharedPreferences
import app.n_zik.android.utils.karaokeRespectAgentPositionKey
import app.n_zik.android.utils.landscapeControlsKey
import app.n_zik.android.utils.lyricsAlignmentKey
import app.n_zik.android.utils.lyricsBackgroundKey
import app.n_zik.android.utils.lyricsHighlightKey
import app.n_zik.android.utils.lyricsIntervalIndicatorKey
import app.n_zik.android.utils.lyricsOutlineKey
import app.n_zik.android.utils.lyricsSizeAnimateKey
import app.n_zik.android.utils.lyricsSizeKey
import app.n_zik.android.utils.lyricsSizeLKey
import app.n_zik.android.utils.lyricsTypeKey
import app.n_zik.android.utils.otherLanguageAppLyricsKey
import app.n_zik.android.utils.romanizationEnabledKey
import app.n_zik.android.utils.showSecondLineKey
import app.n_zik.android.components.player.lyrics.LyricsProvider
import app.n_zik.android.components.player.lyrics.karaokeClipRectsDebugKey
import app.n_zik.android.enums.lyrics.LyricsType

/** Restore menu-only controls through their first-run readers without touching saved lyrics. */
internal fun SharedPreferences.Editor.resetLyricsMenuDefaults() {
    listOf(
        landscapeControlsKey, lyricsAlignmentKey, lyricsOutlineKey, romanizationEnabledKey,
        lyricsIntervalIndicatorKey, showSecondLineKey, lyricsSizeAnimateKey, lyricsHighlightKey,
        lyricsBackgroundKey, lyricsTypeKey, karaokeRespectAgentPositionKey,
        otherLanguageAppLyricsKey, karaokeClipRectsDebugKey, lyricsSizeKey, lyricsSizeLKey,
    ).forEach { key -> remove(key) }
    LyricsType.entries.forEach { mode -> remove(LyricsProvider.preferenceKey(mode)) }
}
