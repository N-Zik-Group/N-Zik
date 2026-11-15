package app.n_zik.android.extensions.youtubelogin

import app.n_zik.innertube.YouTubeAccountDetails
import app.n_zik.innertube.YouTubeChannel
import app.n_zik.innertube.YouTubeSession
import app.n_zik.innertube.hasYouTubeAuthCookie
import app.n_zik.innertube.normalizeYouTubeAuthUser
import app.n_zik.innertube.normalizeYouTubeDataSyncId
import app.n_zik.innertube.utils.parseCookieString
import java.net.URI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val MUSIC_HOME = "https://music.youtube.com/"
private val musicUri = URI(MUSIC_HOME)

internal fun isYouTubeMusicPage(url: String): Boolean =
    parseLoginUri(url)?.host.equals("music.youtube.com", ignoreCase = true)

internal fun isGoogleDevicePrompt(url: String): Boolean {
    val uri = parseLoginUri(url) ?: return false
    return uri.host.equals("accounts.google.com", ignoreCase = true) &&
        uri.path.trimEnd('/').endsWith("/signin/challenge/dp")
}

internal fun googleSessionCookies(cookie: String?): Map<String, String> =
    parseCookieString(cookie.orEmpty()).filter { (key, value) ->
        key in setOf("SID", "__Secure-1PSID", "__Secure-3PSID") && value.isNotBlank()
    }

internal fun hasNewGoogleSession(before: Map<String, String>, current: Map<String, String>): Boolean =
    current.any { (key, value) -> before[key] != value }

private fun parseLoginUri(url: String): URI? = runCatching { URI(url) }.getOrNull()
    ?.takeIf { it.scheme.equals("https", ignoreCase = true) && it.rawUserInfo == null && it.port in setOf(-1, 443) }

internal fun isYouTubeLoginPage(url: String): Boolean = when (parseLoginUri(url)?.host?.lowercase()) {
    "music.youtube.com", "www.youtube.com", "accounts.youtube.com", "youtube.com",
    "accounts.google.com", "consent.google.com", "consent.youtube.com" -> true
    else -> false
}

internal fun YouTubeChannel.navigationUrl(): String? = signInUrl?.let { target ->
    runCatching { musicUri.resolve(target).toString() }.getOrNull()?.takeIf(::isYouTubeLoginPage)
}

internal fun YouTubeChannel.sessionIndex(): String? = authUser ?: navigationUrl()?.let { url ->
    val query = parseLoginUri(url)?.rawQuery.orEmpty().split('&')
    query.firstNotNullOfOrNull { parameter ->
        if (parameter.substringBefore('=').equals("authuser", ignoreCase = true)) {
            normalizeYouTubeAuthUser(parameter.substringAfter('=', ""))
        } else null
    }
}

private fun YouTubeChannel.routeKey(session: YouTubeSession): Pair<String?, String>? {
    val route = channelId ?: handle?.lowercase() ?: dataSyncId ?: navigationUrl()
        ?: session.dataSyncId?.takeIf { selected }
    return route?.let { (sessionIndex() ?: session.authUser ?: "0") to it }
}

internal fun selectableYouTubeChannels(channels: List<YouTubeChannel>, session: YouTubeSession): List<YouTubeChannel> =
    channels.mapNotNull { channel -> channel.routeKey(session)?.let { it to channel } }
        .distinctBy { (key, _) -> key }
        .map { (_, channel) -> channel }

internal fun YouTubeChannel.currentOrDirectSession(session: YouTubeSession): YouTubeSession? {
    val expectedIndex = sessionIndex()
    val currentIndex = session.authUser ?: "0"
    if (selected && (expectedIndex == null || expectedIndex == currentIndex) &&
        (dataSyncId == null || dataSyncId == session.dataSyncId)
    ) {
        return session
    }
    if (expectedIndex != null && expectedIndex != currentIndex) return null
    return dataSyncId?.let { session.copy(dataSyncId = it, authUser = expectedIndex ?: session.authUser) }
}

internal fun YouTubeChannel.matchesStoredSession(session: YouTubeSession): Boolean =
    dataSyncId != null && dataSyncId == session.dataSyncId &&
        (sessionIndex() ?: session.authUser ?: "0") == (session.authUser ?: "0")

internal suspend fun verifyYouTubeChoice(
    session: YouTubeSession,
    channel: YouTubeChannel?,
    freshlyCaptured: Boolean,
    isCurrent: () -> Boolean,
    fetchAccount: suspend (YouTubeSession) -> YouTubeAccountDetails?,
    fetchChoices: suspend (YouTubeSession) -> List<YouTubeChannel>,
): YouTubeAccountDetails? {
    if (!isCurrent()) return null
    if (channel?.sessionIndex()?.let { it != (session.authUser ?: "0") } == true) return null
    val account = fetchAccount(session) ?: return null
    currentCoroutineContext().ensureActive()
    if (!isCurrent()) return null
    if (channel == null) return account

    val channelIdMatches = channel.channelId?.let { expected -> account.channelId?.let { it == expected } }
    val handleMatches = channel.handle?.let { expected ->
        account.info.channelHandle?.let { it.equals(expected, ignoreCase = true) }
    }
    if (channelIdMatches == false || handleMatches == false) return null
    val routeMatches = channel.dataSyncId?.let { expected -> account.dataSyncId?.let { it == expected } }
    if (freshlyCaptured && channel.dataSyncId != null && session.dataSyncId != null &&
        channel.dataSyncId != session.dataSyncId
    ) return null
    if (routeMatches == false) return null
    if (channelIdMatches == true || handleMatches == true || routeMatches == true) return account

    val route = channel.routeKey(session) ?: return null
    val observed = fetchChoices(session).any { it.selected && it.routeKey(session) == route }
    currentCoroutineContext().ensureActive()
    return account.takeIf { observed && isCurrent() }
}

internal fun parseYouTubePageSession(
    raw: String?,
    pageUrl: String,
    cookie: String?,
    requireRoutingFields: Boolean = true,
): YouTubeSession? {
    if (!isYouTubeMusicPage(pageUrl) || !hasYouTubeAuthCookie(cookie)) return null
    val config = raw?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
    fun value(key: String) = (config[key] as? JsonPrimitive)?.contentOrNull
    if (value("url") != pageUrl || value("loggedIn") == "false") return null
    val visitor = value("visitorData")?.takeIf { it.isNotBlank() && it.none(Char::isWhitespace) } ?: return null
    val rawDataSyncId = value("dataSyncId")?.takeIf(String::isNotBlank)
    val rawAuthUser = value("authUser")?.takeIf(String::isNotBlank)
    val dataSyncId = normalizeYouTubeDataSyncId(rawDataSyncId)
    val authUser = normalizeYouTubeAuthUser(rawAuthUser)
    if ((dataSyncId == null && (requireRoutingFields || rawDataSyncId != null)) ||
        (authUser == null && (requireRoutingFields || rawAuthUser != null))
    ) return null
    return YouTubeSession(cookie, visitor, dataSyncId, authUser)
}

internal class YouTubeLoginPageGate {
    var revision = 0L
        private set

    fun invalidate() {
        revision++
    }

    fun isCurrent(ticket: Long) = revision == ticket
}
