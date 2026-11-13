package app.n_zik.android.extensions.lastfm

import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.BuildConfig
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.lastfm.LastFm
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Shared LastFM actions for UI menus.
 *
 * Menus live in the UI code path and cannot reach the scrobble manager
 * owned by PlayerServiceModern, so they call the static LastFm object
 * directly with the session key read from encrypted preferences.
 */
object LastFmActions {

    private const val TAG = "LastFmActions"

    // Own scope: menus hide themselves on the same click, which would cancel
    // a menu-scoped request mid-flight and toast a fake failure.
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)

    fun isConfigured(): Boolean =
        BuildConfig.LASTFM_API_KEY.isNotEmpty() &&
        BuildConfig.LASTFM_API_SECRET.isNotEmpty()

    fun isSessionAvailable(): Boolean {
        if (!isConfigured()) return false
        val sessionKey = appContext().encryptedPreferences.getString(lastfmSessionKey, null).orEmpty()
        return sessionKey.isNotEmpty()
    }

    fun setLoveStatus(artist: String, track: String, love: Boolean) {
        scope.launch {
            performLoveStatus(artist, track, love)
        }
    }

    internal suspend fun performLoveStatus(
        artist: String,
        track: String,
        love: Boolean,
        isConfigured: () -> Boolean = {
            BuildConfig.LASTFM_API_KEY.isNotEmpty() &&
                BuildConfig.LASTFM_API_SECRET.isNotEmpty()
        }
    ) {
        if (artist.isBlank() || track.isBlank()) return
        if (!isConfigured()) return
        val sessionKey = appContext().encryptedPreferences.getString(lastfmSessionKey, null).orEmpty()
        if (sessionKey.isEmpty()) return
        LastFm.initialize(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_API_SECRET)
        LastFm.sessionKey = sessionKey
        LastFm.setLoveStatus(artist, track, love)
            .onSuccess {
                Timber.tag(TAG).d(if (love) "Loved '$track' by '$artist'" else "Unloved '$track' by '$artist'")
                Toaster.s(if (love) R.string.lastfm_loved else R.string.lastfm_unloved)
            }
            .onFailure { e ->
                Timber.tag(TAG).e(e, "Love/unlove failed for '$track' by '$artist'")
                Toaster.e(R.string.lastfm_action_failed)
            }
    }
}
