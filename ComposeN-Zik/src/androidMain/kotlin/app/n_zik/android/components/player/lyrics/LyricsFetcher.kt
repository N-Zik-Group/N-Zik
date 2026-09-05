package app.n_zik.android.components.player.lyrics

import android.R.attr.duration
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.models.Lyrics
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.lyrics
import it.fast4x.kugou.KuGou
import it.fast4x.lrclib.LrcLib
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Duration.Companion.milliseconds
import timber.log.Timber

import app.n_zik.android.enums.lyrics.LyricsType
import com.metrolist.music.betterlyrics.BetterLyrics
import android.database.sqlite.SQLiteConstraintException
import android.content.Context

private const val TAG = "LyricsFetcher"

private var globalLastKaraokeAttemptMediaId: String? = null
private var globalLastSyncedAttemptMediaId: String? = null
private var globalLastUnSyncedAttemptMediaId: String? = null

fun resetGlobalAttemptForType(type: LyricsType) {
    when (type) {
        LyricsType.Karaoke -> globalLastKaraokeAttemptMediaId = null
        LyricsType.Synced -> globalLastSyncedAttemptMediaId = null
        LyricsType.Unsynced -> globalLastUnSyncedAttemptMediaId = null
        LyricsType.Auto -> {
            globalLastKaraokeAttemptMediaId = null
            globalLastSyncedAttemptMediaId = null
            globalLastUnSyncedAttemptMediaId = null
        }
    }
}

@Composable
fun LyricsFetcher(
    mediaId: String,
    lyricsType: LyricsType,
    checkLyrics: Boolean,
    artistName: String?,
    title: String?,
    mediaMetadata: MediaMetadata,
    durationProvider: () -> Long,
    coroutineScope: CoroutineScope,
    playerEnableLyricsPopupMessage: Boolean,
    onLyricsUpdated: (Lyrics?) -> Unit,
    onErrorUpdated: (Boolean) -> Unit,
    onCheckedLrcUpdated: (Boolean) -> Unit,
    onCheckedKugouUpdated: (Boolean) -> Unit,
    onCheckedInnertubeUpdated: (Boolean) -> Unit,
    onFetchingStateChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var previousLyricsType by remember { mutableStateOf(lyricsType) }
    var previousCheckLyrics by remember { mutableStateOf(checkLyrics) }
    LaunchedEffect(mediaId, lyricsType, checkLyrics, mediaMetadata.title, mediaMetadata.artist) {
        if (checkLyrics != previousCheckLyrics) {
            globalLastSyncedAttemptMediaId = null
            globalLastKaraokeAttemptMediaId = null
            globalLastUnSyncedAttemptMediaId = null
            previousCheckLyrics = checkLyrics
            onLyricsUpdated(null)
        }
        
        // Mode switch
        if (lyricsType != previousLyricsType) {
            globalLastSyncedAttemptMediaId = null
            globalLastKaraokeAttemptMediaId = null
            globalLastUnSyncedAttemptMediaId = null
            previousLyricsType = lyricsType
            onLyricsUpdated(null)
        }
        Database.lyricsTable
            .findAllBySongId(mediaId)
            .collect { allLyrics ->
                val fetchNeeds = LyricsDecisionMaker.evaluateFetchNeeds(
                    mediaId = mediaId,
                    lyricsType = lyricsType,
                    allLyrics = allLyrics,
                    globalLastKaraokeAttemptMediaId = globalLastKaraokeAttemptMediaId,
                    globalLastSyncedAttemptMediaId = globalLastSyncedAttemptMediaId,
                    globalLastUnSyncedAttemptMediaId = globalLastUnSyncedAttemptMediaId
                )

                val isAuto = lyricsType == LyricsType.Auto
                val wantSynced = lyricsType == LyricsType.Synced || isAuto
                val wantKaraoke = lyricsType == LyricsType.Karaoke || lyricsType == LyricsType.Synced || isAuto

                val currentLyrics = fetchNeeds.currentLyrics
                val needKaraokeFetch = fetchNeeds.needKaraokeFetch
                val needSyncedFetch = fetchNeeds.needSyncedFetch
                val needUnsyncedFetch = fetchNeeds.needUnsyncedFetch

                if (needKaraokeFetch || needSyncedFetch || needUnsyncedFetch) {
                    onFetchingStateChanged(true)
                    try {

                    if (needKaraokeFetch) {
                        globalLastKaraokeAttemptMediaId = mediaId
                    }
                    if (needSyncedFetch) {
                        globalLastSyncedAttemptMediaId = mediaId
                    }
                    if (needUnsyncedFetch) {
                        globalLastUnSyncedAttemptMediaId = mediaId
                    }

                    var duration = withContext(Dispatchers.Main) {
                        durationProvider()
                    }

                    while (duration == C.TIME_UNSET) {
                        delay(100)
                        duration = withContext(Dispatchers.Main) {
                            durationProvider()
                        }
                    }

                    // Unsync mode → skip synced/karaoke fetch, go directly to unsynced
                    if (lyricsType == LyricsType.Unsynced && needUnsyncedFetch) {
                        var foundUnsynced = false
                        kotlin.runCatching {
                            LrcLib.lyricsUnsynced(
                                artist = artistName ?: "",
                                title = title ?: "",
                                duration = duration.milliseconds
                            )?.onSuccess {
                                if (it?.text?.isNotEmpty() == true && playerEnableLyricsPopupMessage)
                                    coroutineScope.launch { Toaster.s(R.string.info_lyrics_found_on_s, context.getString(R.string.source_lrclib_unsynced)) }
                                if (it?.text?.isNotEmpty() != true)
                                    if (playerEnableLyricsPopupMessage) coroutineScope.launch { Toaster.e(R.string.info_lyrics_not_found_on_s, context.getString(R.string.source_lrclib_unsynced)) }
                                onErrorUpdated(it?.text?.isNotEmpty() != true)
                                onCheckedLrcUpdated(true)
                                saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = it?.text))
                                onLyricsUpdated(currentLyrics)
                                foundUnsynced = true
                            }?.onFailure {
                                Timber.tag(TAG).e("→ LrcLib UNSYNCED ERROR: ${it.stackTraceToString()}")
                            }
                        }
                        if (!foundUnsynced) {
                            tryYouTubeUnsynced(mediaId, mediaMetadata, coroutineScope, playerEnableLyricsPopupMessage, onErrorUpdated, onCheckedInnertubeUpdated, onLyricsUpdated, currentLyrics, context)
                        }
                    } else {

                    val fetchLrcLibAndKugou: suspend () -> Unit = {
                        if (currentLyrics?.data.isNullOrEmpty() || needSyncedFetch) {
                                kotlin.runCatching {
                                LrcLib.lyrics(
                                    artist = artistName ?: "",
                                    title = title ?: "",
                                    duration = duration.milliseconds,
                                    album = mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                                )?.onSuccess {
                                    if ((it?.text?.isNotEmpty() == true || it?.sentences?.isNotEmpty() == true)
                                        && playerEnableLyricsPopupMessage
                                    )
                                        coroutineScope.launch {
                                            Toaster.s(
                                                R.string.info_lyrics_found_on_s,
                                                context.getString(R.string.source_lrclib_synced)
                                            )
                                        }
                                    else
                                        if (playerEnableLyricsPopupMessage)
                                            coroutineScope.launch {
                                                Toaster.e(
                                                    R.string.info_lyrics_not_found_on_s,
                                                    context.getString(R.string.source_lrclib_synced),
                                                    duration = Toast.LENGTH_LONG
                                                )
                                            }

                                    onErrorUpdated(false)
                                    onCheckedLrcUpdated(true)

                                    saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = it?.text.orEmpty()))
                                }?.onFailure {
                                    if (playerEnableLyricsPopupMessage)
                                        coroutineScope.launch {
                                            Toaster.e(
                                                R.string.info_lyrics_not_found_on_s_try_on_s,
                                                context.getString(R.string.source_lrclib_synced), context.getString(R.string.source_kugou_synced),
                                                duration = Toast.LENGTH_LONG
                                            )
                                        }

                                    onCheckedLrcUpdated(true)

                                    kotlin.runCatching {
                                        KuGou.lyrics(
                                            artist = mediaMetadata.artist?.toString() ?: "",
                                            title = cleanPrefix(mediaMetadata.title?.toString() ?: ""),
                                            duration = duration / 1000
                                        )?.onSuccess {
                                            val hasContent = it?.value?.isNotEmpty() == true || it?.sentences?.isNotEmpty() == true
                                            if (hasContent) {
                                                if (playerEnableLyricsPopupMessage)
                                                    coroutineScope.launch {
                                                        Toaster.s(
                                                            R.string.info_lyrics_found_on_s,
                                                            context.getString(R.string.source_kugou_synced)
                                                        )
                                                    }
                                                onErrorUpdated(false)
                                                onCheckedKugouUpdated(true)
                                                saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = it?.value.orEmpty()))
                                            } else {
                                                if (playerEnableLyricsPopupMessage)
                                                    coroutineScope.launch {
                                                        Toaster.e(
                                                            R.string.info_lyrics_not_found_on_s_try_on_s,
                                                            context.getString(R.string.source_kugou_synced),
                                                            context.getString(R.string.source_lrclib_unsynced)
                                                        )
                                                    }
                                                onCheckedKugouUpdated(true)
                                                kotlin.runCatching {
                                                    LrcLib.lyricsUnsynced(
                                                        artist = artistName ?: "",
                                                        title = title ?: "",
                                                        duration = duration.milliseconds,
                                                        album = mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                                                    )?.onSuccess {
                                                        val hasContent = it?.plainText?.isNotEmpty() == true
                                                        if (hasContent) {
                                                            if (playerEnableLyricsPopupMessage)
                                                                coroutineScope.launch {
                                                                    Toaster.s(
                                                                        R.string.info_lyrics_found_on_s,
                                                                        context.getString(R.string.source_lrclib_unsynced)
                                                                    )
                                                                }
                                                            onErrorUpdated(false)
                                                            onCheckedLrcUpdated(true)
                                                            saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = it?.plainText.orEmpty()))
                                                        } else {
                                                            onCheckedLrcUpdated(true)
                                                            tryYouTubeUnsynced(mediaId, mediaMetadata, coroutineScope, playerEnableLyricsPopupMessage, onErrorUpdated, onCheckedInnertubeUpdated, onLyricsUpdated, currentLyrics, context)
                                                        }
                                                    }?.onFailure {
                                                        onCheckedLrcUpdated(true)
                                                        tryYouTubeUnsynced(mediaId, mediaMetadata, coroutineScope, playerEnableLyricsPopupMessage, onErrorUpdated, onCheckedInnertubeUpdated, onLyricsUpdated, currentLyrics, context)
                                                    }
                                                }.onFailure {
                                                    Timber.tag(TAG).e("→ LrcLib(U) ERROR: ${it.stackTraceToString()}")
                                                    onCheckedLrcUpdated(true)
                                                    tryYouTubeUnsynced(mediaId, mediaMetadata, coroutineScope, playerEnableLyricsPopupMessage, onErrorUpdated, onCheckedInnertubeUpdated, onLyricsUpdated, currentLyrics, context)
                                                }
                                            }
                                        }?.onFailure {
                                            if (playerEnableLyricsPopupMessage)
                                                coroutineScope.launch {
                                                    Toaster.e(
                                                        R.string.info_lyrics_not_found_on_s_try_on_s,
                                                        context.getString(R.string.source_kugou_synced),
                                                        context.getString(R.string.source_lrclib_unsynced)
                                                    )
                                                }

                                            kotlin.runCatching {
                                                LrcLib.lyricsUnsynced(
                                                    artist = artistName ?: "",
                                                    title = title ?: "",
                                                    duration = duration.milliseconds,
                                                    album = mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                                                )?.onSuccess {
                                                    val hasContent = it?.plainText?.isNotEmpty() == true
                                                    if (hasContent) {
                                                        if (playerEnableLyricsPopupMessage)
                                                            coroutineScope.launch {
                                                                Toaster.s(
                                                                    R.string.info_lyrics_found_on_s,
                                                                    context.getString(R.string.source_lrclib_unsynced)
                                                                )
                                                            }
                                                        onErrorUpdated(false)
                                                        onCheckedLrcUpdated(true)
                                                        saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = it?.plainText.orEmpty()))
                                                    } else {
                                                        onCheckedLrcUpdated(true)
                                                        tryYouTubeUnsynced(mediaId, mediaMetadata, coroutineScope, playerEnableLyricsPopupMessage, onErrorUpdated, onCheckedInnertubeUpdated, onLyricsUpdated, currentLyrics, context)
                                                    }
                                                }?.onFailure {
                                                    if (playerEnableLyricsPopupMessage)
                                                        coroutineScope.launch {
                                                            Toaster.e(
                                                                R.string.info_lyrics_not_found_on_s_try_on_s,
                                                                context.getString(R.string.source_lrclib_unsynced),
                                                                context.getString(R.string.source_youtube_unsynced)
                                                            )
                                                        }
                                                    onCheckedLrcUpdated(true)
                                                    tryYouTubeUnsynced(mediaId, mediaMetadata, coroutineScope, playerEnableLyricsPopupMessage, onErrorUpdated, onCheckedInnertubeUpdated, onLyricsUpdated, currentLyrics, context)
                                                }
                                            }.onFailure {
                                                Timber.tag(TAG).e("→ LrcLib(U) ERROR: ${it.stackTraceToString()}")
                                                if (playerEnableLyricsPopupMessage)
                                                    coroutineScope.launch {
                                                        Toaster.e(
                                                            R.string.info_lyrics_not_found_on_s_try_on_s,
                                                            context.getString(R.string.source_lrclib_unsynced),
                                                            context.getString(R.string.source_youtube_unsynced)
                                                        )
                                                    }
                                                onCheckedLrcUpdated(true)
                                                tryYouTubeUnsynced(mediaId, mediaMetadata, coroutineScope, playerEnableLyricsPopupMessage, onErrorUpdated, onCheckedInnertubeUpdated, onLyricsUpdated, currentLyrics, context)
                                            }
                                        }
                                    }.onFailure {
                                        Timber.tag(TAG).e("→ KuGou ERROR: ${it.stackTraceToString()}")
                                        if (!currentLyrics?.data.isNullOrEmpty()) {
                                            onLyricsUpdated(currentLyrics)
                                        }
                                    }
                                }
                            }.onFailure {
                                Timber.tag(TAG).e("→ LrcLib ERROR: ${it.stackTraceToString()}")
                                if (!currentLyrics?.data.isNullOrEmpty()) {
                                    onLyricsUpdated(currentLyrics)
                                }
                            }
                        }
                    }

                    if (wantKaraoke) {
                        kotlin.runCatching {
                            BetterLyrics.getLyrics(
                                title = cleanPrefix(title ?: ""),
                                artist = artistName ?: "",
                                duration = duration.milliseconds.inWholeSeconds.toInt(),
                                album = mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                            ).onSuccess { ttmlStr ->
                                val hasKaraokeTimings = ttmlStr.lines().any { it.trim().startsWith("<") && it.contains(":") && it.contains(">") }
                                if (ttmlStr.isNotEmpty()) {
                                    if (hasKaraokeTimings) {
                                        if (lyricsType == LyricsType.Synced) {
                                            // The user explicitly requested Synced, not Karaoke. 
                                            // BetterLyrics returned Karaoke timings, so we reject it and fallback.
                                            fetchLrcLibAndKugou()
                                        } else {
                                            if (playerEnableLyricsPopupMessage) {
                                                Toaster.s(
                                                    R.string.info_lyrics_found_on_s,
                                                    context.getString(R.string.source_betterlyrics_karaoke)
                                                )
                                            }
                                            onErrorUpdated(false)
                                            onCheckedLrcUpdated(true)
                                            saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = ttmlStr))
                                        }
                                    } else {
                                        // BetterLyrics found synced lyrics (no word timings)
                                        if (lyricsType == LyricsType.Synced) {
                                            if (playerEnableLyricsPopupMessage) {
                                                Toaster.s(
                                                    R.string.info_lyrics_found_on_s,
                                                    context.getString(R.string.source_betterlyrics_synced)
                                                )
                                            }
                                            onErrorUpdated(false)
                                            onCheckedLrcUpdated(true)
                                            saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Synced.name, data = ttmlStr))
                                        } else {
                                            if (playerEnableLyricsPopupMessage) {
                                                Toaster.w(R.string.info_karaoke_not_found_showing_sync, context.getString(R.string.source_betterlyrics_karaoke), context.getString(R.string.source_betterlyrics_synced))
                                            }
                                            onErrorUpdated(false)
                                            onCheckedLrcUpdated(true)
                                            saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Karaoke.name, data = ttmlStr))
                                        }
                                    }
                                } else {
                                    if (playerEnableLyricsPopupMessage) {
                                        Toaster.e(
                                            R.string.info_lyrics_not_found_on_s_try_on_s,
                                            context.getString(R.string.source_betterlyrics_karaoke),
                                            context.getString(R.string.source_betterlyrics_synced)
                                        )
                                    }
                                    if (isAuto || lyricsType == LyricsType.Synced) {
                                        fetchLrcLibAndKugou()
                                    } else {
                                        onCheckedLrcUpdated(true)
                                        onCheckedKugouUpdated(true)
                                        onCheckedInnertubeUpdated(true)
                                        onErrorUpdated(true)
                                    }
                                }
                            }.onFailure {
                                if (playerEnableLyricsPopupMessage) {
                                    Toaster.e(
                                        R.string.info_lyrics_not_found_on_s_try_on_s,
                                        context.getString(R.string.source_betterlyrics_karaoke),
                                        context.getString(R.string.source_betterlyrics_synced)
                                    )
                                }
                                if (isAuto || lyricsType == LyricsType.Synced) {
                                    fetchLrcLibAndKugou()
                                } else {
                                    onCheckedLrcUpdated(true)
                                    onCheckedKugouUpdated(true)
                                    onCheckedInnertubeUpdated(true)
                                    onErrorUpdated(true)
                                }
                            }
                        }.onFailure {
                            Timber.tag(TAG).e("→ BetterLyrics KARAOKE ERROR: ${it.stackTraceToString()}")
                            if (playerEnableLyricsPopupMessage) {
                                Toaster.e(
                                    R.string.info_lyrics_not_found_on_s_try_on_s,
                                    context.getString(R.string.source_betterlyrics_karaoke),
                                    context.getString(R.string.source_betterlyrics_synced)
                                )
                            }
                            if (isAuto || lyricsType == LyricsType.Synced) {
                                fetchLrcLibAndKugou()
                            } else {
                                onCheckedLrcUpdated(true)
                                onCheckedKugouUpdated(true)
                                onCheckedInnertubeUpdated(true)
                                onErrorUpdated(true)
                            }
                        }
                    } else {
                        fetchLrcLibAndKugou()
                    }
                    } // end else (not needUnsyncedFetch)
                    } finally {
                        onFetchingStateChanged(false)
                    }

                } else if (!currentLyrics?.data.isNullOrEmpty()) {
                    onFetchingStateChanged(false)
                    // No fetch needed — just update UI with current lyrics
                    onLyricsUpdated(currentLyrics)
                } else {
                    onFetchingStateChanged(false)
                }

                if (lyricsType == LyricsType.Unsynced && currentLyrics?.data == null && globalLastUnSyncedAttemptMediaId != mediaId) {
                    globalLastUnSyncedAttemptMediaId = mediaId
                    onErrorUpdated(false)
                    onLyricsUpdated(null)

                    var foundUnsynced = false

                    kotlin.runCatching {
                        LrcLib.lyricsUnsynced(
                            artist = artistName ?: "",
                            title = title ?: "",
                            duration = duration.milliseconds,
                            album = mediaMetadata.albumTitle?.toString() ?: Database.albumTable.findBySongId(mediaId).firstOrNull()?.title
                        )?.onSuccess {
                            val hasContent = it?.plainText?.isNotEmpty() == true
                            if (hasContent) {
                                if (playerEnableLyricsPopupMessage)
                                    coroutineScope.launch {
                                        Toaster.s(
                                            R.string.info_lyrics_found_on_s,
                                            context.getString(R.string.source_lrclib_unsynced)
                                        )
                                    }
                                foundUnsynced = true
                                saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = it?.plainText.orEmpty()))
                            }
                        }?.onFailure {
                            if (playerEnableLyricsPopupMessage)
                                coroutineScope.launch {
                                    Toaster.e(
                                        R.string.info_lyrics_not_found_on_s_try_on_s,
                                        context.getString(R.string.source_lrclib_unsynced),
                                        context.getString(R.string.source_youtube_unsynced)
                                    )
                                }
                        }
                    }.onFailure {
                        Timber.tag(TAG).e("→ LrcLib(U) ERROR: ${it.stackTraceToString()}")
                        if (playerEnableLyricsPopupMessage)
                            coroutineScope.launch {
                                Toaster.e(
                                    R.string.info_lyrics_not_found_on_s_try_on_s,
                                    context.getString(R.string.source_lrclib_unsynced),
                                    context.getString(R.string.source_youtube_unsynced)
                                )
                            }
                    }

                    if (!foundUnsynced) {
                        kotlin.runCatching {
                            Innertube.lyrics(videoId = mediaId)
                                ?.onSuccess { fixedLyrics ->
                                    if (fixedLyrics?.isNotEmpty() == true && playerEnableLyricsPopupMessage) {
                                        coroutineScope.launch {
                                            Toaster.s(
                                                R.string.info_lyrics_found_on_s,
                                                context.getString(R.string.source_youtube_unsynced)
                                            )
                                        }
                                    } else if (playerEnableLyricsPopupMessage) {
                                        coroutineScope.launch {
                                            Toaster.e(
                                                R.string.info_lyrics_not_found_on_s,
                                                context.getString(R.string.source_youtube_unsynced),
                                                duration = Toast.LENGTH_LONG
                                            )
                                        }
                                    }
                                    if (!fixedLyrics.isNullOrEmpty()) {
                                        saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = fixedLyrics))
                                    } else {
                                        onErrorUpdated(true)
                                    }
                                }?.onFailure {
                                    onErrorUpdated(true)
                                }
                        }.onFailure {
                            Timber.tag(TAG).e("→ YouTube(U) ERROR: ${it.stackTraceToString()}")
                        }
                    }
                    onCheckedLrcUpdated(true)
                    onCheckedKugouUpdated(true)
                    onCheckedInnertubeUpdated(true)
                } else {
                    onLyricsUpdated(currentLyrics)
                }
            }
    }
}

private fun tryYouTubeUnsynced(
    mediaId: String,
    mediaMetadata: MediaMetadata,
    coroutineScope: CoroutineScope,
    playerEnableLyricsPopupMessage: Boolean,
    onErrorUpdated: (Boolean) -> Unit,
    onCheckedInnertubeUpdated: (Boolean) -> Unit,
    onLyricsUpdated: (Lyrics?) -> Unit,
    currentLyrics: Lyrics?,
    context: Context
) {
    coroutineScope.launch {
        kotlin.runCatching {
            Innertube.lyrics(videoId = mediaId)
                ?.onSuccess { fixedLyrics ->
                    if (fixedLyrics?.isNotEmpty() == true && playerEnableLyricsPopupMessage) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            Toaster.s(
                                R.string.info_lyrics_found_on_s,
                                context.getString(R.string.source_youtube_unsynced)
                            )
                        }
                    } else if (playerEnableLyricsPopupMessage) {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            Toaster.e(
                                R.string.info_lyrics_not_found_on_s,
                                context.getString(R.string.source_youtube_unsynced),
                                duration = Toast.LENGTH_LONG
                            )
                        }
                    }
                    onCheckedInnertubeUpdated(true)
                    if (!fixedLyrics.isNullOrEmpty()) {
                        saveLyricsSafe(Lyrics(songId = mediaId, type = LyricsType.Unsynced.name, data = fixedLyrics))
                    } else {
                        onErrorUpdated(true)
                    }
                }?.onFailure {
                    onCheckedInnertubeUpdated(true)
                    onErrorUpdated(true)
                    if (!currentLyrics?.data.isNullOrEmpty()) {
                        onLyricsUpdated(currentLyrics)
                    }
                }
        }.onFailure {
            Timber.tag(TAG).e("→ YouTube(U) ERROR: ${it.stackTraceToString()}")
            onCheckedInnertubeUpdated(true)
            onErrorUpdated(true)
            if (!currentLyrics?.data.isNullOrEmpty()) {
                onLyricsUpdated(currentLyrics)
            }
        }
    }
}

private fun saveLyricsSafe(lyrics: Lyrics) {
    Database.asyncTransaction {
        try {
            lyricsTable.upsert(lyrics)
        } catch (e: SQLiteConstraintException) {
            Timber.tag("LyricsFetcher").w("Foreign key constraint failed for songId ${lyrics.songId}. Retrying in 5 seconds...")
            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(5000)
                try {
                    Database.asyncTransaction {
                        lyricsTable.upsert(lyrics)
                    }
                } catch (e2: Exception) {
                    Timber.tag("LyricsFetcher").e("Failed to save lyrics even after delay: ${e2.message}")
                }
            }
        } catch (e: Exception) {
            Timber.tag("LyricsFetcher").e("Error saving lyrics: ${e.message}")
        }
    }
}
