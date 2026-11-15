package app.n_zik.android.extensions.youtubelogin

import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

import androidx.core.content.edit
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import app.n_zik.android.utils.ytAccountChannelHandleKey
import app.n_zik.android.utils.ytAccountEmailKey
import app.n_zik.android.utils.ytAccountNameKey
import app.n_zik.android.utils.ytCookieKey
import app.n_zik.innertube.YouTubeChannel
import app.n_zik.innertube.models.AccountInfo
import java.util.UUID

private const val BROWSER_ACCOUNTS_KEY = "ytBrowserAccounts"
private const val ACTIVE_BROWSER_PROFILE_KEY = "ytActiveBrowserProfile"
private const val PROFILE_PREFIX = "nzik_youtube_"
private val profilePattern = Regex("${PROFILE_PREFIX}[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")

/** A device-local WebView account. A null profile name identifies the legacy shared browser. */
internal data class YouTubeBrowserAccount(
    val profileName: String?,
    val label: String,
    val channels: List<YouTubeBrowserChannel> = emptyList(),
) {
    override fun toString(): String = "YouTubeBrowserAccount"
}

internal data class YouTubeBrowserChannel(val name: String, val handle: String?, val avatar: String?, val id: String?) {
    override fun toString(): String = "YouTubeBrowserChannel"

    fun matches(channel: YouTubeChannel): Boolean =
        matches(channel.channelId, channel.handle)

    fun matches(channel: YouTubeBrowserChannel): Boolean = matches(channel.id, channel.handle)

    private fun matches(otherId: String?, otherHandle: String?): Boolean =
        if (id != null && otherId != null) id == otherId
        else handle?.equals(otherHandle, ignoreCase = true) == true
}

private fun List<YouTubeBrowserAccount>.previousChannels(profileName: String?): List<YouTubeBrowserChannel> =
    takeWhile { it.profileName != profileName }.flatMap { it.channels }

/** YouTube may return a shared channel under more than one sign-in. Keep its first listed group. */
internal fun List<YouTubeBrowserAccount>.uniqueDiscoveredChannels(
    profileName: String?,
    channels: List<YouTubeChannel>,
): List<YouTubeChannel> {
    val previous = previousChannels(profileName)
    return channels.filterNot { channel -> previous.any { it.matches(channel) } }
}

internal fun List<YouTubeBrowserAccount>.uniqueSavedChannels(account: YouTubeBrowserAccount): List<YouTubeBrowserChannel> {
    val previous = previousChannels(account.profileName)
    return account.channels.filterNot { channel -> previous.any { it.matches(channel) } }
}

/** Returns the active named browser profile, or null for the legacy shared browser. */
internal fun readActiveYouTubeBrowserAccount(prefs: SharedPreferences): String? =
    prefs.getString(ACTIVE_BROWSER_PROFILE_KEY, null)?.takeIf(profilePattern::matches)

/** Reads the roster from the encrypted settings for one N-Zik profile. */
internal fun readYouTubeBrowserAccounts(prefs: SharedPreferences): List<YouTubeBrowserAccount> {
    val accounts = readSavedBrowserAccounts(prefs)
    if (accounts.any { it.profileName == null } || readActiveYouTubeBrowserAccount(prefs) != null ||
        prefs.getString(ytCookieKey, null).isNullOrBlank()
    ) return accounts

    val legacyLabel = sequenceOf(ytAccountEmailKey, ytAccountNameKey, ytAccountChannelHandleKey)
        .map { prefs.getString(it, null) }
        .firstOrNull { !it.isNullOrBlank() }
        .orEmpty()
    return listOf(YouTubeBrowserAccount(null, legacyLabel)) + accounts
}

/** Stages the verified browser identity alongside the existing YouTube session fields. */
internal fun SharedPreferences.Editor.putYouTubeBrowserAccount(
    profileName: String?,
    account: AccountInfo,
    currentRoster: List<YouTubeBrowserAccount>,
) {
    val label = sequenceOf(account.email, account.name, account.channelHandle)
        .firstOrNull { !it.isNullOrBlank() }
        .orEmpty()
    val entry = YouTubeBrowserAccount(profileName, label, currentRoster.firstOrNull { it.profileName == profileName }?.channels.orEmpty())
    val accounts = if (currentRoster.any { it.profileName == profileName }) {
        currentRoster.map { if (it.profileName == profileName) entry else it }
    } else {
        currentRoster + entry
    }
    putString(ACTIVE_BROWSER_PROFILE_KEY, profileName)
    putString(BROWSER_ACCOUNTS_KEY, encodeBrowserAccounts(accounts))
}

/** Registers discovered channels without changing which YouTube Music session is active. */
internal fun rememberYouTubeBrowserAccount(
    prefs: SharedPreferences,
    profileName: String?,
    channels: List<YouTubeChannel>,
): List<YouTubeBrowserAccount> {
    val roster = readYouTubeBrowserAccounts(prefs)
    if (channels.isEmpty()) return roster
    val activeChannel = channels.firstOrNull { it.selected } ?: channels.first()
    val entry = YouTubeBrowserAccount(
        profileName,
        activeChannel.email ?: roster.firstOrNull { it.profileName == profileName }?.label ?: activeChannel.name,
        roster.uniqueDiscoveredChannels(profileName, channels)
            .map { YouTubeBrowserChannel(it.name, it.handle, it.thumbnailUrl, it.channelId) },
    )
    val updated = if (roster.any { it.profileName == profileName }) {
        roster.map { if (it.profileName == profileName) entry else it }
    } else roster + entry
    if (updated != roster) prefs.edit { putString(BROWSER_ACCOUNTS_KEY, encodeBrowserAccounts(updated)) }
    return updated
}

/** Removes one named browser account from the owning N-Zik profile's roster. */
internal fun removeYouTubeBrowserAccount(prefs: SharedPreferences, profileName: String) {
    if (!profilePattern.matches(profileName)) return
    val accounts = readSavedBrowserAccounts(prefs).filterNot { it.profileName == profileName }
    prefs.edit {
        putString(BROWSER_ACCOUNTS_KEY, encodeBrowserAccounts(accounts))
        if (readActiveYouTubeBrowserAccount(prefs) == profileName) remove(ACTIVE_BROWSER_PROFILE_KEY)
    }
}

/** Removes the current N-Zik profile's local browser account references. */
internal fun SharedPreferences.Editor.removeAllYouTubeBrowserAccounts() {
    remove(BROWSER_ACCOUNTS_KEY)
    remove(ACTIVE_BROWSER_PROFILE_KEY)
}

/** Reports whether the current Android System WebView can isolate browser accounts. */
internal fun supportsYouTubeBrowserProfiles(): Boolean =
    WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

/** Allocates a device-local identifier without putting the Google identity in the profile name. */
internal fun newYouTubeBrowserProfileName(): String = PROFILE_PREFIX + UUID.randomUUID()

/** Assigns a named profile before the WebView loads a page or accesses its settings. */
internal fun WebView.setYouTubeBrowserProfile(profileName: String?) {
    if (profileName == null) return
    require(profilePattern.matches(profileName))
    check(supportsYouTubeBrowserProfiles())
    WebViewCompat.setProfile(this, profileName)
}

/** Returns the cookie manager for this WebView's assigned profile. */
internal fun WebView.youtubeLoginCookieManager(): CookieManager =
    if (supportsYouTubeBrowserProfiles()) WebViewCompat.getProfile(this).cookieManager
    else CookieManager.getInstance()

/** Clears cookies and site storage for one isolated profile without deleting a loaded profile. */
internal fun clearYouTubeBrowserProfile(profileName: String) {
    if (!profilePattern.matches(profileName) || !supportsYouTubeBrowserProfiles()) return
    val profile = ProfileStore.getInstance().getProfile(profileName) ?: return
    clearBrowserData(profile.cookieManager, profile.webStorage)
}

/** Clears all YouTube browser profiles, including the legacy shared browser, after global logout. */
internal fun clearAllYouTubeBrowserProfiles() {
    if (supportsYouTubeBrowserProfiles()) {
        ProfileStore.getInstance().allProfileNames.filter(profilePattern::matches).forEach(::clearYouTubeBrowserProfile)
    }
    clearBrowserData(CookieManager.getInstance(), WebStorage.getInstance())
}

private fun clearBrowserData(cookies: CookieManager, storage: WebStorage) {
    cookies.removeAllCookies { cookies.flush() }
    storage.deleteAllData()
}

private fun readSavedBrowserAccounts(prefs: SharedPreferences): List<YouTubeBrowserAccount> {
    val raw = prefs.getString(BROWSER_ACCOUNTS_KEY, null) ?: return emptyList()
    val entries = try {
        JSONArray(raw)
    } catch (_: JSONException) {
        return emptyList()
    }
    return (0 until entries.length()).mapNotNull { index ->
        val entry = entries.optJSONObject(index) ?: return@mapNotNull null
        if (!entry.has("profile")) return@mapNotNull null
        val profile = if (entry.isNull("profile")) null else {
            entry.optString("profile").takeIf(profilePattern::matches) ?: return@mapNotNull null
        }
        val channels = entry.optJSONArray("channels")
        val decoded = if (channels == null) emptyList() else (0 until channels.length()).mapNotNull { row ->
            val channel = channels.optJSONObject(row) ?: return@mapNotNull null
            val name = channel.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            YouTubeBrowserChannel(name, channel.optionalText("handle"), channel.optionalText("avatar"), channel.optionalText("id"))
        }
        YouTubeBrowserAccount(profile, entry.optString("label"), decoded)
    }.distinctBy { it.profileName }
}

private fun encodeBrowserAccounts(accounts: List<YouTubeBrowserAccount>): String =
    JSONArray().apply {
        accounts.forEach { account ->
            put(JSONObject().apply {
                put("profile", account.profileName ?: JSONObject.NULL)
                put("label", account.label)
                put("channels", JSONArray().apply {
                    account.channels.forEach { channel ->
                        put(JSONObject().apply {
                            put("name", channel.name)
                            put("handle", channel.handle)
                            put("avatar", channel.avatar)
                            put("id", channel.id)
                        })
                    }
                })
            })
        }
    }.toString()

private fun JSONObject.optionalText(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
