package app.n_zik.innertube

import app.n_zik.innertube.models.AccountInfo
import app.n_zik.innertube.utils.parseCookieString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class YouTubeSession(
    val cookie: String?,
    val visitorData: String?,
    val dataSyncId: String?,
    val authUser: String?,
) {
    override fun toString() = "YouTubeSession"
}

@ConsistentCopyVisibility
data class YouTubeChannel internal constructor(
    val name: String,
    val email: String?,
    val handle: String?,
    val thumbnailUrl: String?,
    val selected: Boolean,
    val channelId: String?,
    val dataSyncId: String?,
    val authUser: String?,
    val signInUrl: String?,
) {
    override fun toString() = "YouTubeChannel"
}

@ConsistentCopyVisibility
data class YouTubeAccountDetails internal constructor(
    val info: AccountInfo,
    val channelId: String?,
    val dataSyncId: String?,
) {
    override fun toString() = "YouTubeAccountDetails"
}

fun normalizeYouTubeDataSyncId(value: String?): String? = value?.trim()?.let {
    it.substringBefore("||").ifBlank { it.substringAfter("||", "").trim() }
        .takeIf { id -> id.isNotBlank() && id.none(Char::isWhitespace) }
}

fun normalizeYouTubeAuthUser(value: String?): String? =
    value?.takeIf { it.isNotEmpty() && it.all { digit -> digit in '0'..'9' } }

fun hasYouTubeAuthCookie(cookie: String?): Boolean {
    val values = cookie?.let(::parseCookieString) ?: return false
    return sequenceOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID")
        .any { !values[it].isNullOrBlank() }
}

fun parseYouTubeChannels(body: String): List<YouTubeChannel> {
    val root = Json.parseToJsonElement(body.trimStart().removePrefix(")]}'")) as? JsonObject ?: return emptyList()
    return root.accountItems().mapNotNull { (account, groupEmail) ->
        val endpoint = account.objects().firstNotNullOfOrNull { it.obj("selectActiveIdentityEndpoint") }
        val tokens = (endpoint?.get("supportedTokens") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        fun token(key: String, value: String) = tokens.firstNotNullOfOrNull { it.obj(key)?.value(value) }

        val handle = account.text("channelHandle") ?: account.text("accountByline")?.takeIf { it.startsWith('@') }
        val name = account.text("accountName") ?: handle ?: return@mapNotNull null
        YouTubeChannel(
            name = name,
            email = account.text("email") ?: groupEmail,
            handle = handle,
            thumbnailUrl = account.photo(),
            selected = account.value("isSelected") == "true",
            channelId = account.value("channelId") ?: endpoint?.value("channelId"),
            dataSyncId = normalizeYouTubeDataSyncId(token("datasyncIdToken", "datasyncIdToken"))
                ?: normalizeYouTubeDataSyncId(token("pageIdToken", "pageId")),
            authUser = normalizeYouTubeAuthUser(
                endpoint?.value("sessionIndex") ?: endpoint?.value("authUser")
                    ?: account.value("sessionIndex") ?: token("accountIndexToken", "accountIndex")
            ),
            signInUrl = token("accountSigninToken", "signinUrl") ?: endpoint?.value("signinUrl"),
        )
    }.toList()
}

internal fun List<YouTubeChannel>.channelEmail(channelId: String?, handle: String?): String? =
    filter { channel ->
        val sameId = channelId?.let { it == channel.channelId } == true
        val sameHandle = handle?.let { it.equals(channel.handle, ignoreCase = true) } == true
        sameId || sameHandle
    }.mapNotNull { it.email }.distinct().singleOrNull()

fun fillYouTubeChannelEmails(channels: List<YouTubeChannel>, accounts: List<YouTubeChannel>): List<YouTubeChannel> =
    channels.map { channel ->
        if (channel.email == null) channel.copy(email = accounts.channelEmail(channel.channelId, channel.handle))
        else channel
    }

private fun JsonElement.accountItems(email: String? = null): Sequence<Pair<JsonObject, String?>> = when (this) {
    is JsonObject -> {
        val groupEmail = obj("header")?.obj("googleAccountHeaderRenderer")?.text("email") ?: email
        val account = obj("accountItem") ?: obj("accountItemRenderer")
        if (account != null) sequenceOf(account to groupEmail)
        else values.asSequence().flatMap { it.accountItems(groupEmail) }
    }
    is JsonArray -> asSequence().flatMap { it.accountItems(email) }
    else -> emptySequence()
}

fun parseYouTubeAccountDetails(body: String): YouTubeAccountDetails? {
    val root = Json.parseToJsonElement(body) as? JsonObject ?: return null
    val context = root.obj("responseContext")?.obj("mainAppWebResponseContext")
    if (context?.value("loggedOut") == "true") return null
    val header = root.objects().firstNotNullOfOrNull { it.obj("activeAccountHeaderRenderer") } ?: return null
    val name = header.text("accountName") ?: return null
    return YouTubeAccountDetails(
        info = AccountInfo(
            name = name,
            email = header.text("email"),
            channelHandle = header.text("channelHandle"),
            thumbnailUrl = header.photo(),
        ),
        channelId = header.value("channelId") ?: header.value("channelExternalId"),
        dataSyncId = normalizeYouTubeDataSyncId(
            header.value("dataSyncId") ?: context?.value("datasyncId") ?: context?.value("dataSyncId")
        ),
    )
}

private fun JsonElement.objects(): Sequence<JsonObject> = when (this) {
    is JsonObject -> sequenceOf(this) + values.asSequence().flatMap { it.objects() }
    is JsonArray -> asSequence().flatMap { it.objects() }
    else -> emptySequence()
}

private fun JsonObject.obj(key: String) = this[key] as? JsonObject

private fun JsonObject.value(key: String) =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

private fun JsonObject.text(key: String): String? =
    value(key) ?: obj(key)?.let { text ->
        text.value("simpleText") ?: (text["runs"] as? JsonArray)
            ?.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }
            ?.joinToString("")
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

private fun JsonObject.photo(): String? =
    (obj("accountPhoto")?.get("thumbnails") as? JsonArray)
        ?.lastOrNull()?.let { (it as? JsonObject)?.value("url") }
