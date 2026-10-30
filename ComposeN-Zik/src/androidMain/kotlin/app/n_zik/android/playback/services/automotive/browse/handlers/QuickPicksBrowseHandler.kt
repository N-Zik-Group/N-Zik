package app.n_zik.android.playback.services.automotive.browse.handlers

import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.enums.LocalRecommandationsNumber
import app.it.fast4x.rimusic.enums.PlayEventsType
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.playEventsTypeKey
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.requests.relatedPage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

/** Mirrors the phone Quick Picks assembly while discovery is available online. */
class QuickPicksBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == AutoSessionConstants.ID_QUICK_PICKS

    private companion object {
        /** Same all-time window the app uses for the MostPlayed trending. */
        val ALL_TIME_FROM_MS = 18250.days.inWholeMilliseconds

        /** Same default related-page seed the app uses when there is no local history. */
        const val DEFAULT_RELATED_SEED = "4NRXx6U8ABQ"
    }

    // Bound each provider attempt so a slow car network cannot stall discovery.
    private suspend fun <T> fetchSection( label: String, attemptTimeout: Long = 5_000, block: suspend () -> T ): T? {
        val first = withTimeoutOrNull(attemptTimeout) { runCatching { block() }.getOrNull() }
        if (first != null) return first
        Timber.tag("QuickPicksBrowseHandler").w("$label empty or timed out after ${attemptTimeout}ms — retrying once")
        delay(1_000)
        return withTimeoutOrNull(attemptTimeout) { runCatching { block() }.getOrNull() }
    }

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val access = AutoAccessPolicy(context, database, downloadHelper, binder)
        if (!access.online) return emptyList()
        val luckyItem = MediaItem.Builder()
            .setMediaId(AutoSessionConstants.ID_LUCKY_SHUFFLE)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(context.getString(R.string.lucky_shuffle))
                    .setArtworkUri(drawableUri(context, R.drawable.smart_shuffle))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

        // Same settings the in-app row reads
        val prefs = appContext().preferences
        val playEventType = prefs.getEnum(playEventsTypeKey, PlayEventsType.MostPlayed)
        val parentalControlEnabled = prefs.getBoolean(parentalControlEnabledKey, false)
        val localCount = prefs.getEnum("LocalRecommandationsNumber", LocalRecommandationsNumber.SixQ).value
        val useLogin = isYouTubeLoggedIn() && Innertube.useLoginForBrowse

        // 1. Local trending — same queries, filters and count as the app
        val trendingList = when (playEventType) {
            PlayEventsType.MostPlayed ->
                database.eventTable.findSongsMostPlayedBetween( from = ALL_TIME_FROM_MS, limit = localCount ).first()
            PlayEventsType.LastPlayed ->
                database.eventTable.findSongsLastPlayed( limit = localCount ).first()
            PlayEventsType.CasualPlayed ->
                database.eventTable.findSongsMostPlayedBetween( from = 0, limit = 100 ).first()
        }.let { songs ->
            val distinct = songs.distinctBy { it.id }
                .filter { !parentalControlEnabled || !it.title.startsWith( EXPLICIT_PREFIX, true ) }
            if (playEventType == PlayEventsType.CasualPlayed) distinct.shuffled().take(localCount)
            else distinct.take(localCount)
        }

        // Run bounded provider sections in parallel, using the phone's fallback seed.
        val (ytmQuickPicks, relatedRaw) = coroutineScope {
            val ytmDeferred = if (access.online && isYouTubeLoggedIn() && Innertube.useLoginForBrowse) {
                async {
                    fetchSection("ytm quick picks") {
                        if (!access.online) emptyList() else YtMusic.getQuickPicks(setLogin = true).getOrNull()?.map { it.asSong } ?: emptyList()
                    } ?: emptyList()
                }
            } else null
            val relatedDeferred = async {
                fetchSection("related page") {
                    if (!access.online) emptyList() else Innertube.relatedPage(
                        videoId = trendingList.firstOrNull()?.id ?: DEFAULT_RELATED_SEED,
                        setLogin = useLogin
                    )?.getOrNull()?.songs ?: emptyList()
                } ?: emptyList()
            }
            ytmDeferred?.await().orEmpty() to relatedDeferred.await()
        }

        val relatedSongsSource = relatedRaw
            .map { it.asSong }
            .filter { !parentalControlEnabled || !it.title.startsWith( EXPLICIT_PREFIX, true ) }
            .distinctBy { it.id }

        // 3. Assembly — exact copy of the app's recommendations logic
        val seed = (trendingList.joinToString { it.id } + relatedRaw.joinToString { it.key }).hashCode()
        val random = Random(seed)
        val candidateList = if (playEventType == PlayEventsType.MostPlayed || playEventType == PlayEventsType.LastPlayed) {
            val first = trendingList.firstOrNull()
            val others = trendingList.drop(1)
            val pool = (others + ytmQuickPicks + relatedSongsSource).distinctBy { it.id }
            listOfNotNull(first) + pool.shuffled(random)
        } else {
            val locals = trendingList.take(localCount)
            val pool = (locals + ytmQuickPicks + relatedSongsSource).distinctBy { it.id }
            pool.shuffled(random)
        }

        Timber.tag("QuickPicksBrowseHandler").d(
            "Quick picks loaded -> local: ${trendingList.size}, ytb: ${ytmQuickPicks.size}, " +
                "related: ${relatedSongsSource.size}, total: ${candidateList.size}, ytLogin: ${isYouTubeLoggedIn()}"
        )

        if (!access.online) return emptyList()
        val items = candidateList.map { song -> CancellableAutoMediaItemMapper.mapSongToMediaItem(song, parentId, loadArtwork = song.isLocal) }
        if (!access.online) return emptyList()
        return (listOf(luckyItem) + items).distinctBy { it.mediaId }
    }
}
