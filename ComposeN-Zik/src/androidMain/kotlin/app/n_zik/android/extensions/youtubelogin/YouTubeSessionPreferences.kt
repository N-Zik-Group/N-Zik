package app.n_zik.android.extensions.youtubelogin

import android.content.SharedPreferences

import androidx.core.content.edit

import app.n_zik.android.core.network.client.Store
import app.n_zik.android.playback.services.clearStreamCaches
import app.n_zik.android.utils.cancelAllSyncs
import app.n_zik.android.utils.enableYouTubeLoginKey
import app.n_zik.android.utils.enableYouTubeSyncKey
import app.n_zik.android.utils.encryptedPreferencesUpdateTrigger
import app.n_zik.android.utils.ytAccountChannelHandleKey
import app.n_zik.android.utils.ytAccountEmailKey
import app.n_zik.android.utils.ytAccountNameKey
import app.n_zik.android.utils.ytAccountThumbnailKey
import app.n_zik.android.utils.ytAuthUserKey
import app.n_zik.android.utils.ytCookieKey
import app.n_zik.android.utils.ytDataSyncIdKey
import app.n_zik.android.utils.ytVisitorDataKey
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YouTubeSession
import app.n_zik.innertube.models.AccountInfo
import app.n_zik.innertube.normalizeYouTubeAuthUser

internal fun SharedPreferences.readYouTubeSession(): YouTubeSession {
    val cookie = getString(ytCookieKey, null)?.takeIf(String::isNotBlank)
    return YouTubeSession(
        cookie = cookie,
        visitorData = getString(ytVisitorDataKey, null)?.takeIf(String::isNotBlank),
        dataSyncId = cookie?.let { getString(ytDataSyncIdKey, null)?.takeIf(String::isNotBlank) },
        authUser = cookie?.let { normalizeYouTubeAuthUser(getString(ytAuthUserKey, null)) },
    )
}

internal fun SharedPreferences.Editor.putYouTubeSession(session: YouTubeSession) {
    putString(ytCookieKey, session.cookie)
    putString(ytVisitorDataKey, session.visitorData)
    putString(ytDataSyncIdKey, session.dataSyncId)
    putString(ytAuthUserKey, session.authUser)
}

internal fun saveVerifiedYouTubeSession(
    prefs: SharedPreferences,
    session: YouTubeSession,
    account: AccountInfo,
    webProfileName: String?,
    knownAccounts: List<YouTubeBrowserAccount>,
) {
    val previous = prefs.readYouTubeSession()
    val changedAccount = previous.cookie != null && (
        previous.dataSyncId != session.dataSyncId ||
            (previous.authUser ?: "0") != (session.authUser ?: "0") ||
            previous.dataSyncId == null && previous.cookie != session.cookie
        )
    cancelAllSyncs()
    prefs.edit {
        putYouTubeSession(session)
        putYouTubeBrowserAccount(webProfileName, account, knownAccounts)
        putString(ytAccountNameKey, account.name)
        putString(ytAccountEmailKey, account.email)
        putString(ytAccountChannelHandleKey, account.channelHandle)
        putString(ytAccountThumbnailKey, account.thumbnailUrl)
        putBoolean(enableYouTubeLoginKey, true)
        if (changedAccount) putBoolean(enableYouTubeSyncKey, false)
    }
    Store.setSession(session)
    clearStreamCaches()
    encryptedPreferencesUpdateTrigger++
}

internal fun clearSavedYouTubeSession(prefs: SharedPreferences, clearWebSignIn: Boolean = false) {
    val guest = YouTubeSession(null, null, null, null)
    cancelAllSyncs()
    prefs.edit {
        putYouTubeSession(guest)
        remove(ytAccountNameKey)
        remove(ytAccountEmailKey)
        remove(ytAccountChannelHandleKey)
        remove(ytAccountThumbnailKey)
        putBoolean(enableYouTubeSyncKey, false)
        if (clearWebSignIn) removeAllYouTubeBrowserAccounts()
    }
    Store.setSession(guest)
    Innertube.useLoginForBrowse = false
    clearStreamCaches()
    encryptedPreferencesUpdateTrigger++
    if (clearWebSignIn) clearAllYouTubeBrowserProfiles()
}
