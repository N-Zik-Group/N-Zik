package app.n_zik.android.components.settings.appearance

import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.karaokeRespectAgentPositionKey
import app.it.fast4x.rimusic.utils.landscapeControlsKey
import app.it.fast4x.rimusic.utils.lyricsAlignmentKey
import app.it.fast4x.rimusic.utils.lyricsBackgroundKey
import app.it.fast4x.rimusic.utils.lyricsHighlightKey
import app.it.fast4x.rimusic.utils.lyricsIntervalIndicatorKey
import app.it.fast4x.rimusic.utils.lyricsOutlineKey
import app.it.fast4x.rimusic.utils.lyricsSizeAnimateKey
import app.it.fast4x.rimusic.utils.lyricsSizeKey
import app.it.fast4x.rimusic.utils.lyricsSizeLKey
import app.it.fast4x.rimusic.utils.lyricsTypeKey
import app.it.fast4x.rimusic.utils.otherLanguageAppLyricsKey
import app.it.fast4x.rimusic.utils.romanizationEnabledKey
import app.it.fast4x.rimusic.utils.showSecondLineKey
import app.n_zik.android.components.player.lyrics.karaokeClipRectsDebugKey

/** Restore menu-only controls through their first-run readers without touching saved lyrics. */
internal fun SharedPreferences.Editor.resetLyricsMenuDefaults() {
    listOf(
        landscapeControlsKey, lyricsAlignmentKey, lyricsOutlineKey, romanizationEnabledKey,
        lyricsIntervalIndicatorKey, showSecondLineKey, lyricsSizeAnimateKey, lyricsHighlightKey,
        lyricsBackgroundKey, lyricsTypeKey, karaokeRespectAgentPositionKey,
        otherLanguageAppLyricsKey, karaokeClipRectsDebugKey, lyricsSizeKey, lyricsSizeLKey,
    ).forEach { key -> remove(key) }
}
