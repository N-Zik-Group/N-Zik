package app.n_zik.android.utils

import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.components.settings.accounts.isYouTubeSyncBuild
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.boundProcessStorageIdentity
import app.n_zik.android.core.profiles.isCurrentProcessStorageProfile
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.models.Artist
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.hasYouTubeAuthCookie
import app.n_zik.innertube.requests.LibraryContinuationPage
import app.n_zik.innertube.requests.LibraryPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private const val VerifiedKey = "youtubeArtistProvenanceVerified"
private val SessionKeys = setOf(enableYouTubeSyncKey, enableYouTubeLoginKey, ytCookieKey, ytDataSyncIdKey, ytAuthUserKey)

internal object YouTubeArtistProvenance {
    private val writeLock = Mutex()
    private val cleanupScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)
    private val visible = MutableStateFlow(false)
    val showOrigin = visible.asStateFlow()
    private val importEnabled = MutableStateFlow(false)
    val canImport = importEnabled.asStateFlow()

    @Volatile
    private var binding: Binding? = null

    private data class ImportSession(val generation: Long, val cookie: String?, val dataSyncId: String?, val authUser: String?)

    private class Binding(
        val context: Context,
        val owner: ProfileStorageIdentity,
        val plain: SharedPreferences,
        val secure: SharedPreferences,
    ) {
        private var generation = 0L
        private var cleanupRevision = 0L

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (prefs === secure && (key == null || key in SessionKeys) ||
                prefs === plain && (key == null || key == useLoginForBrowseKey)
            ) invalidate()
            else if (prefs === plain && key == syncDirectionKey) directionChanged()
        }

        fun enabled(): Boolean = isYouTubeSyncBuild() &&
            secure.getBoolean(enableYouTubeSyncKey, false) &&
            secure.getBoolean(enableYouTubeLoginKey, false) &&
            hasYouTubeAuthCookie(secure.getString(ytCookieKey, null)) &&
            plain.getBoolean(useLoginForBrowseKey, true)

        fun canImport(): Boolean = enabled() &&
            plain.getString(syncDirectionKey, null) != SyncDirection.APP_TO_YT.name

        @Synchronized
        private fun directionChanged() {
            generation++
            importEnabled.value = canImport()
        }

        fun ownsLibrary(): Boolean =
            isCurrentProcessStorageProfile(context, owner) &&
                Database.FILE_NAME == Database.fileNameForProfile(owner.id)

        @Synchronized
        fun start() {
            plain.registerOnSharedPreferenceChangeListener(listener)
            secure.registerOnSharedPreferenceChangeListener(listener)
            importEnabled.value = canImport()
            if (enabled() && plain.getBoolean(VerifiedKey, false)) visible.value = true
            else invalidate()
        }

        private fun session() = ImportSession(
            generation,
            secure.getString(ytCookieKey, null),
            secure.getString(ytDataSyncIdKey, null),
            secure.getString(ytAuthUserKey, null),
        )

        @Synchronized
        fun begin(): ImportSession? {
            if (!ownsLibrary() || !canImport()) return null
            generation++
            return session()
        }

        @Synchronized
        fun matches(expected: ImportSession): Boolean = expected == session() && ownsLibrary() && canImport()

        @Synchronized
        fun publish(expected: ImportSession): Boolean {
            if (!matches(expected)) return false
            plain.edit().putBoolean(VerifiedKey, true).apply()
            visible.value = true
            return true
        }

        @Synchronized
        fun invalidate(scheduleCleanup: Boolean = true): Long {
            generation++
            val revision = ++cleanupRevision
            visible.value = false
            clearArtistSyncCooldown()
            importEnabled.value = canImport()
            plain.edit().putBoolean(VerifiedKey, false).apply()
            if (scheduleCleanup) cleanupScope.launch {
                try {
                    clearMarkers(revision)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Timber.tag("YouTubeArtistProvenance").w(error, "Could not clear artist sync markers")
                }
            }
            return revision
        }

        suspend fun clearMarkers(revision: Long) {
            writeLock.withLock {
                val needsCleanup = synchronized(this) { revision == cleanupRevision && !visible.value }
                if (needsCleanup && ownsLibrary()) Database.artistTable.clearYouTubeArtistProvenance()
            }
        }
    }

    @Synchronized
    fun observe(context: Context) {
        if (binding != null) return
        val owner = boundProcessStorageIdentity() ?: return
        try {
            val applicationContext = context.applicationContext
            val secure = applicationContext.encryptedPreferences
            if (getActiveProfile(applicationContext) != owner.id) return
            binding = Binding(applicationContext, owner, profileDataPrefs(applicationContext, owner.id), secure)
                .also(Binding::start)
        } catch (error: Exception) {
            Timber.tag("YouTubeArtistProvenance").w(error, "Could not start artist sync provenance observation")
        }
    }

    fun canShowOrigin(): Boolean {
        val current = binding ?: return false
        return showOrigin.value && current.enabled() && getActiveProfile(current.context) == current.owner.id
    }

    suspend fun importSnapshot(load: suspend () -> List<Innertube.ArtistItem>?): Boolean {
        val current = binding ?: return false
        val session = current.begin() ?: return false
        val artists = load() ?: return false
        return writeLock.withLock {
            if (!current.matches(session)) return@withLock false
            try {
                Database.transaction {
                    if (!current.matches(session)) throw ObsoleteArtistImport()
                    artistTable.clearYouTubeArtistProvenance()
                    val now = System.currentTimeMillis()
                    for (remote in artists) {
                        val id = remote.info?.endpoint?.browseId ?: continue
                        val existing = artistTable.findByIdDirect(id)
                        if (existing == null) artistTable.insertIgnore(Artist(id = id, name = remote.title))
                        val thumbnail = PropUtils.retainIfModified(existing?.thumbnailUrl, remote.thumbnail?.url)
                        artistTable.markYouTubeArtistImported(id, now, thumbnail)
                    }
                    if (!current.matches(session)) throw ObsoleteArtistImport()
                }
                current.publish(session)
            } catch (_: ObsoleteArtistImport) {
                false
            }
        }
    }

    suspend fun reset() {
        val current = binding ?: return
        current.clearMarkers(current.invalidate(scheduleCleanup = false))
    }

    private class ObsoleteArtistImport : Exception()
}

/** Returns null when any page is missing, so a truncated response cannot remove provenance. */
internal suspend fun completeYouTubeArtistLibrary(
    first: LibraryPage,
    continuationPage: suspend (String) -> LibraryContinuationPage?,
): List<Innertube.ArtistItem>? {
    val items = first.items.filterIsInstance<Innertube.ArtistItem>().toMutableList()
    val visited = mutableSetOf<String>()
    var continuation = first.continuation
    while (continuation != null) {
        if (!visited.add(continuation)) return null
        val page = continuationPage(continuation) ?: return null
        items += page.items.filterIsInstance<Innertube.ArtistItem>()
        continuation = page.continuation
    }
    if (items.any { it.info?.endpoint?.browseId.isNullOrBlank() }) return null
    return items.distinctBy { it.info?.endpoint?.browseId }
}
