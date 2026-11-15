package app.n_zik.android.components.settings.appearance.preview

import android.util.Base64
import app.n_zik.android.components.settings.accounts.githubContributionBlock
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private const val repo = "N-Zik-Group/N-Zik-Theme-Store"
private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()

internal suspend fun creatorThemeStoreForkExists(
    accountId: Long,
    login: String,
    token: String?,
    http: OkHttpClient = client,
): Boolean = withContext(NzikDispatchers.DATA) {
    require(login.matches(Regex("[A-Za-z0-9-]{1,39}")))
    val request = Request.Builder()
        .url("https://api.github.com/repos/$login/N-Zik-Theme-Store")
        .header("Accept", "application/vnd.github+json")
        .apply { if (token != null) header("Authorization", "Bearer $token") }
        .build()
    http.newCall(request).execute().use { response ->
        if (response.code == 404) return@use false
        check(response.isSuccessful) { "GitHub fork lookup HTTP ${response.code}" }
        val fork = JSONObject(checkNotNull(response.body).string())
        fork.optJSONObject("owner")?.optLong("id") == accountId &&
            fork.optJSONObject("parent")?.optString("full_name").equals(repo, ignoreCase = true)
    }
}

// The caller supplies the login and token already verified by the current OAuth attempt.
internal suspend fun prepareCreatorThemeStoreFork(
    login: String,
    token: String,
    http: OkHttpClient = client,
) = withContext(NzikDispatchers.DATA) {
    fun request(path: String, body: JSONObject?, missing: Boolean): String? {
        val request = Request.Builder().url("https://api.github.com/$path")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .apply { if (body != null) post(body.toString().toRequestBody("application/json".toMediaType())) }
            .build()
        return http.newCall(request).execute().use { response ->
            if (missing && response.code == 404) return@use null
            if (!response.isSuccessful) throw CreatorPublicationHttpError(response.code, "prepare Theme Store fork")
            checkNotNull(response.body).string()
        }
    }
    check(login.matches(Regex("[A-Za-z0-9-]{1,39}")))
    creatorPublicationMutex.withLock {
        ensureCreatorThemeStoreFork(login, ::request)
    }
}

private suspend fun ensureCreatorThemeStoreFork(
    login: String,
    request: (String, JSONObject?, Boolean) -> String?,
) {
    val fork = "repos/$login/N-Zik-Theme-Store"
    var forkInfo = request(fork, null, true)
    val creatingFork = forkInfo == null
    if (creatingFork) {
        try {
            request("repos/$repo/forks", JSONObject().put("default_branch_only", true), false)
            forkInfo = request(fork, null, true)
        } catch (error: CreatorPublicationHttpError) {
            if (error.status !in setOf(409, 422)) throw error
            forkInfo = request(fork, null, true)
            if (forkInfo == null && error.status == 422) throw error
        }
    }
    for (attempt in 0 until 14) {
        if (forkInfo != null) break
        delay(2000)
        forkInfo = request(fork, null, true)
    }
    check(JSONObject(checkNotNull(forkInfo)).getJSONObject("parent").getString("full_name").equals(repo, true))
}

internal data class CreatorSubmission(val number: Int, val title: String, val state: String, val reason: String? = null, val stateDate: String = "")

internal suspend fun creatorSubmissions(
    id: Long,
    login: String,
    publicationToken: String?,
    http: OkHttpClient = client,
): List<CreatorSubmission> = withContext(NzikDispatchers.DATA) {
    check(login.matches(Regex("[A-Za-z0-9-]{1,39}")))
    val seenPulls = mutableSetOf<Int>()
    buildList {
        var page = 1
        var hasNext: Boolean
        do {
            currentCoroutineContext().ensureActive()
            val url = "https://api.github.com/repos/$repo/pulls?state=all&sort=created&direction=desc&per_page=100&base=main&page=$page"
            val list = creatorHistoryRequest(http, url, publicationToken).use {
                check(it.isSuccessful) { "GitHub submission history HTTP ${it.code}" }
                hasNext = it.header("Link").orEmpty().split(',').any { link -> link.contains("rel=\"next\"") }
                JSONArray(checkNotNull(it.body).string())
            }
            for (index in 0 until list.length()) {
                val pull = list.getJSONObject(index)
                if (!seenPulls.add(pull.getInt("number"))) continue
                if (pull.optJSONObject("user")?.optLong("id") != id) continue
                val labels = pull.optJSONArray("labels") ?: JSONArray()
                val names = (0 until labels.length()).mapNotNull { labels.optJSONObject(it)?.optString("name") }
                val branch = pull.optJSONObject("head")?.optString("ref")
                if (branch != "nzik/creator-profile-$id" && branch?.startsWith("nzik/theme-") != true &&
                    branch?.startsWith("nzik/review-") != true && branch?.startsWith("nzik/withdraw-") != true &&
                    "new theme" !in names && "theme update" !in names) continue
                val reason = names.singleOrNull { it.startsWith("reason: ") }?.removePrefix("reason: ")
                val state = when {
                    pull.optString("state") == "open" -> "open"
                    "ban" in names -> "banned"
                    "refused" in names -> "refused"
                    "merged" in names -> "accepted"
                    creatorPullMerged(http, pull.getInt("number"), publicationToken) -> "accepted"
                    else -> "closed"
                }
                val timestamp = if (state == "open") pull.optString("updated_at") else pull.optString("closed_at")
                add(CreatorSubmission(
                    pull.getInt("number"), pull.getString("title"), state,
                    reason.takeIf { state == "refused" || state == "banned" }, timestamp,
                ))
            }
            page++
        } while (hasNext)
    }
}

internal fun creatorPullUrl(number: Int) = "https://github.com/$repo/pull/$number"

private val reportMutex = Mutex()

internal data class ThemeReportResult(val number: Int, val updated: Boolean, val pending: Boolean)

internal suspend fun submitThemeReport(
    token: String, accountId: Long, theme: StoreTheme, review: StoreReview?, allReviews: Boolean, reason: String,
): ThemeReportResult = withContext(NzikDispatchers.DATA) {
    reportMutex.withLock {
        require(reason.isNotBlank() && reason.themeTextLength <= 2000)
        fun api(path: String, payload: JSONObject? = null): String {
            val request = Request.Builder().url("https://api.github.com/$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
            if (payload != null) request.post(payload.toString().toRequestBody("application/json".toMediaType()))
            return client.newCall(request.build()).execute().use {
                if (it.code == 401) throw CreatorPublicationSessionExpired()
                check(it.isSuccessful) { "GitHub report HTTP ${it.code}" }
                checkNotNull(it.body).string()
            }
        }
        val user = JSONObject(api("user"))
        if (user.getLong("id") != accountId) throw CreatorPublicationAccountMismatch()
        githubContributionBlock(accountId)?.let { throw CreatorPublicationBlocked(it) }
        val login = user.getString("login")
        check(login.matches(Regex("[A-Za-z0-9-]{1,39}")))
        val target = when {
            review != null -> "review:${theme.revision}:${review.author.githubId}"
            allReviews -> "reviews:${theme.revision}"
            else -> "theme"
        }
        val marker = "<!-- nzik-report:${theme.themeId}:$target -->"
        val endpoint = "repos/$repo/issues"
        var page = 1
        while (true) {
            val issues = JSONArray(api("$endpoint?state=all&creator=$login&per_page=100&page=$page"))
            for (index in 0 until issues.length()) {
                val issue = issues.getJSONObject(index)
                if (issue.has("pull_request") || issue.optJSONObject("user")?.optLong("id") != accountId) continue
                if (issue.optString("body").lineSequence().firstOrNull() != marker) continue
                val number = issue.getInt("number")
                api("$endpoint/$number/comments", JSONObject().put("body", "Follow-up for revision ${theme.revision}:\n\n${reason.trim()}"))
                return@withLock ThemeReportResult(number, updated = true, pending = issue.optString("state") == "open")
            }
            if (issues.length() < 100) break
            page++
        }
        val body = buildString {
            appendLine(marker)
            appendLine("Theme: ${theme.themeId}")
            appendLine("Revision: ${theme.revision}")
            appendLine("Catalog: https://github.com/$repo/tree/main/${theme.directory.trimEnd('/')}")
            appendLine("N-Zik: `nzik://theme/${theme.themeId}/${theme.revision}`")
            review?.let { appendLine("Reviewer: ${it.author.login} (GitHub ID ${it.author.githubId})") }
            appendLine()
            appendLine("Reason:")
            append(reason.trim())
        }
        val title = when {
            review != null -> "[REPORT] : Review report"
            allReviews -> "[REPORT] : All reviews report"
            else -> "[REPORT] : Theme report"
        }
        val result = JSONObject(api(endpoint, JSONObject().put("title", title).put("body", body)))
        ThemeReportResult(result.getInt("number"), updated = false, pending = true)
    }
}

internal suspend fun pendingThemeStoreReports(accountId: Long, login: String): Set<String> =
    withContext(NzikDispatchers.DATA) {
        require(login.matches(Regex("[A-Za-z0-9-]{1,39}")))
        val prefix = "<!-- nzik-report:"
        buildSet {
            var page = 1
            while (true) {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$repo/issues?state=open&creator=$login&per_page=100&page=$page")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                val issues = client.newCall(request).execute().use {
                    check(it.isSuccessful) { "GitHub report status HTTP ${it.code}" }
                    JSONArray(checkNotNull(it.body).string())
                }
                for (index in 0 until issues.length()) {
                    val issue = issues.getJSONObject(index)
                    if (issue.has("pull_request") || issue.optJSONObject("user")?.optLong("id") != accountId) continue
                    val marker = issue.optString("body").lineSequence().firstOrNull() ?: continue
                    if (marker.startsWith(prefix) && marker.endsWith(" -->")) {
                        add(marker.removePrefix(prefix).removeSuffix(" -->"))
                    }
                }
                if (issues.length() < 100) break
                page++
            }
        }
    }

private fun creatorHistoryRequest(http: OkHttpClient, url: String, token: String?): Response {
    val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json")
    if (token == null) return http.newCall(request.build()).execute()
    val response = http.newCall(request.header("Authorization", "Bearer $token").build()).execute()
    if (response.code != 401) return response
    response.close()
    return http.newCall(request.removeHeader("Authorization").build()).execute()
}

private fun creatorPullMerged(http: OkHttpClient, number: Int, token: String?): Boolean =
    creatorHistoryRequest(http, "https://api.github.com/repos/$repo/pulls/$number", token).use {
        check(it.isSuccessful)
        JSONObject(checkNotNull(it.body).string()).getBoolean("merged")
    }

internal class CreatorPublicationBlocked(val publicMessage: String) : Exception()
internal class CreatorPublicationAccountMismatch : Exception()
internal class CreatorPublicationSessionExpired : Exception()
internal class CreatorPublicationUnchanged : Exception()
internal class CreatorPublicationWorkflowPermission : Exception()

private val creatorPublicationMutex = Mutex()
private class CreatorPublicationHttpError(val status: Int, operation: String) :
    IllegalStateException("GitHub HTTP $status during $operation")

internal data class ThemeReviewSubmission(val theme: StoreTheme, val rating: Int, val comment: String)

internal suspend fun submitCreatorProfile(
    token: String,
    id: Long,
    files: Map<String, ByteArray>,
    theme: StoreTheme? = null,
    review: ThemeReviewSubmission? = null,
    withdrawal: StoreTheme? = null,
    requestClient: OkHttpClient = client,
    contributionBlock: suspend (Long) -> String? = ::githubContributionBlock,
): String = withContext(NzikDispatchers.DATA) {
    creatorPublicationMutex.withLock {
        var tokenScopes = emptySet<String>()
        fun api(path: String, body: JSONObject? = null, missing: Boolean = false, patch: Boolean = false): String? {
            val request = Request.Builder().url("https://api.github.com/$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
            if (body != null) {
                val payload = body.toString().toRequestBody("application/json".toMediaType())
                if (patch) request.patch(payload) else request.post(payload)
            }
            return requestClient.newCall(request.build()).execute().use {
                if (it.code == 401) throw CreatorPublicationSessionExpired()
                if (path == "user") {
                    tokenScopes = it.header("X-OAuth-Scopes").orEmpty().split(',').map(String::trim).toSet()
                }
                if (body != null && "/git/refs" in path && it.code in setOf(403, 404) &&
                    "public_repo" in tokenScopes && "workflow" !in tokenScopes
                ) {
                    throw CreatorPublicationWorkflowPermission()
                }
                if (missing && it.code == 404) return@use null
                if (!it.isSuccessful) {
                    val operation = path.substringBefore('?').substringAfter("/N-Zik-Theme-Store/", path.substringBefore('?'))
                    throw CreatorPublicationHttpError(it.code, operation)
                }
                checkNotNull(it.body).string()
            }
        }
        fun obj(path: String, body: JSONObject? = null) = JSONObject(checkNotNull(api(path, body)))
        val user = obj("user")
        if (user.getLong("id") != id) throw CreatorPublicationAccountMismatch()
        contributionBlock(id)?.let { throw CreatorPublicationBlocked(it) }
        val login = user.getString("login")
        check(login.matches(Regex("[A-Za-z0-9-]{1,39}")))
        val upstream = "repos/$repo"
        require(withdrawal == null || (theme == null && review == null && files.isEmpty()))
        val submittedFiles = if (review == null && withdrawal == null) {
            authenticatedCreatorFiles(files, theme, id, login)
        } else files
        val branch = when {
            withdrawal != null -> "nzik/withdraw-${withdrawal.themeId}"
            review != null -> "nzik/review-${review.theme.themeId}-${review.theme.revision}-$id"
            theme != null -> "nzik/theme-${theme.themeId}"
            else -> "nzik/creator-profile-$id"
        }
        val head = "$login:$branch"
        fun findOpen(): JSONObject? = JSONArray(checkNotNull(api(
            "$upstream/pulls?state=open&base=main&head=$login%3A${branch.replace("/", "%2F")}"
        ))).optJSONObject(0)
        val existing = findOpen()
        val fork = "repos/$login/N-Zik-Theme-Store"
        ensureCreatorThemeStoreFork(login) { path, body, missing -> api(path, body, missing) }
        val latest = obj("$upstream/branches/main").getJSONObject("commit")
        val baseSha = latest.getString("sha")
        // The fork network exposes upstream Git objects even when the fork's main has
        // diverged. Build the submission directly on upstream, leaving the user's main alone.
        val commitPath = "$fork/git/commits/$baseSha"
        var baseAvailable = api(commitPath, missing = true) != null
        for (attempt in 0 until 5) {
            if (baseAvailable) break
            delay(1000)
            baseAvailable = api(commitPath, missing = true) != null
        }
        check(baseAvailable) {
            "The current Theme Store commit is not yet available in the fork"
        }
        val profilePath = "catalog/creators/$id/profile.json"
        val published = if (theme == null && review == null && withdrawal == null) api("$upstream/contents/$profilePath?ref=$baseSha", missing = true)?.let {
            JSONObject(Base64.decode(JSONObject(it).getString("content"), Base64.DEFAULT).decodeToString())
        } else null
        val withdrawalTitle = withdrawal?.let { "Withdraw theme: ${it.title}" }
        val body = if (withdrawal != null) {
            "The creator requested removal of this theme and all its published revisions from the Theme Store catalog."
        } else if (review != null) "Review submitted from N-Zik for revision ${review.theme.revision}." else if (theme != null) "Theme submission from N-Zik. Revision ${theme.revision}." else creatorProfilePullBody(published, JSONObject(submittedFiles.getValue(profilePath).decodeToString()))
            ?: if (existing == null) throw CreatorPublicationUnchanged()
            else "## Creator profile update\n\nThe profile fields match the currently published version."
        val contributionFiles = if (withdrawal != null) {
            val path = "catalog/themes/index.json"
            val indexFile = obj("$upstream/contents/$path?ref=$baseSha")
            val index = JSONObject(Base64.decode(indexFile.getString("content"), Base64.DEFAULT).decodeToString())
            val original = index.getJSONArray("themes")
            val remaining = JSONArray()
            var found = false
            for (position in 0 until original.length()) {
                val entry = original.getJSONObject(position)
                if (entry.getString("themeId") == withdrawal.themeId) {
                    check(!found)
                    val revision = entry.getInt("revision")
                    val manifest = obj("$upstream/contents/catalog/themes/${withdrawal.themeId}/$revision/manifest.json?ref=$baseSha")
                    val publishedTheme = JSONObject(Base64.decode(manifest.getString("content"), Base64.DEFAULT).decodeToString())
                    if (publishedTheme.getJSONObject("author").getLong("githubId") != id) {
                        throw CreatorPublicationAccountMismatch()
                    }
                    found = true
                } else {
                    remaining.put(entry)
                }
            }
            if (!found) throw CreatorPublicationUnchanged()
            index.put("themes", remaining)
            mapOf(path to (index.toString(2) + "\n").encodeToByteArray())
        } else if (review != null) {
            val manifestPath = review.theme.directory + "manifest.json"
            obj("$upstream/contents/$manifestPath?ref=$baseSha")
            val path = review.theme.directory + "reviews.json"
            val before = api("$upstream/contents/$path?ref=$baseSha", missing = true)?.let {
                val bytes = Base64.decode(JSONObject(it).getString("content"), Base64.DEFAULT)
                parseStoreReviews(bytes.decodeToString()).comments
            }.orEmpty()
            val previous = before.firstOrNull { it.author.githubId == id }
            val entry = StoreReview(StoreAuthor(id, login), previous?.kind ?: "community", review.rating, review.comment.trim())
            if (entry == previous && existing == null) throw CreatorPublicationUnchanged()
            val updated = before.filterNot { it.author.githubId == id } + entry
            val source = Json.encodeToString(StoreReviewDocument.serializer(), StoreReviewDocument(1, updated))
            parseStoreReviews(source)
            mapOf(path to (source + "\n").encodeToByteArray())
        } else {
            val indexPath = if (theme == null) "catalog/creators/index.json" else "catalog/themes/index.json"
            val indexFile = obj("$upstream/contents/$indexPath?ref=$baseSha")
            val index = JSONObject(Base64.decode(indexFile.getString("content"), Base64.DEFAULT).decodeToString())
            val indexKey = if (theme == null) "creators" else "themes"
            val original = index.getJSONArray(indexKey)
            val creators = JSONArray()
            val updatedEntry = if (theme == null) JSONObject().put("githubId", id).put("profileSha256", submittedFiles.getValue(profilePath).sha256())
            else JSONObject().put("themeId", theme.themeId).put("revision", theme.revision)
                .put("manifestSha256", submittedFiles.getValue(theme.directory + "manifest.json").sha256())
            var indexed = false
            for (position in 0 until original.length()) {
                val entry = original.getJSONObject(position)
                if (if (theme == null) entry.getLong("githubId") == id else entry.getString("themeId") == theme.themeId) {
                    if (theme != null) {
                        val revision = entry.getInt("revision")
                        check(theme.revision == revision + 1)
                        val manifest = obj("$upstream/contents/catalog/themes/${theme.themeId}/$revision/manifest.json?ref=$baseSha")
                        val previous = JSONObject(Base64.decode(manifest.getString("content"), Base64.DEFAULT).decodeToString())
                        check(previous.getJSONObject("author").getLong("githubId") == id)
                    } else {
                        creators.put(updatedEntry)
                    }
                    indexed = true
                } else {
                    creators.put(entry)
                }
            }
            if (!indexed) check(theme == null || theme.revision == 1)
            if (theme != null || !indexed) creators.put(updatedEntry)
            index.put(indexKey, creators)
            submittedFiles + (indexPath to (index.toString(2) + "\n").encodeToByteArray())
        }
        // Each submission replaces the generated branch with one commit on current main.
        val baseTree = latest.getJSONObject("commit").getJSONObject("tree").getString("sha")
        val entries = JSONArray()
        for ((path, bytes) in contributionFiles) {
            val blob = obj("$fork/git/blobs", JSONObject().put("encoding", "base64")
                .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            entries.put(JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("sha", blob.getString("sha")))
        }
        for (path in supersededCreatorAssets(published, id, submittedFiles.keys)) {
            entries.put(JSONObject().put("path", path).put("mode", "100644")
                .put("type", "blob").put("sha", JSONObject.NULL))
        }
        val tree = obj("$fork/git/trees", JSONObject().put("base_tree", baseTree).put("tree", entries)).getString("sha")
        val ref = api("$fork/git/ref/heads/$branch", missing = true)
        if (tree == baseTree && ref == null) throw CreatorPublicationUnchanged()
        val message = withdrawalTitle ?: when {
            review != null -> "Submit theme review"
            theme != null -> "Submit theme ${theme.themeId}"
            else -> "Update creator profile for @$login"
        }
        val commit = obj("$fork/git/commits", JSONObject().put("message", message)
            .put("tree", tree).put("parents", JSONArray().put(baseSha))).getString("sha")
        fun updateGeneratedRef() {
            api("$fork/git/refs/heads/$branch", JSONObject().put("sha", commit).put("force", true), patch = true)
        }
        if (ref != null) {
            updateGeneratedRef()
        } else {
            val newRef = JSONObject().put("ref", "refs/heads/$branch").put("sha", commit)
            try {
                obj("$fork/git/refs", newRef)
            } catch (error: CreatorPublicationHttpError) {
                if (error.status !in setOf(409, 422) || api("$fork/git/ref/heads/$branch", missing = true) == null) throw error
                updateGeneratedRef()
            }
        }
        val title = when {
            withdrawalTitle != null -> withdrawalTitle
            review != null -> "Review: ${review.theme.title} · revision ${review.theme.revision}"
            theme != null -> "Theme: ${theme.title}"
            else -> "Creator profile: @$login"
        }
        fun updatePull(open: JSONObject): JSONObject = JSONObject(checkNotNull(api(
            "$upstream/pulls/${open.getInt("number")}",
            JSONObject().put("body", body).put("title", title),
            patch = true,
        )))
        val open = findOpen()
        val updated = open?.let(::updatePull)
        val pull = if (updated?.optString("state") == "open") {
            updated
        } else {
            try {
                obj("$upstream/pulls", JSONObject().put("head", head).put("base", "main").put("title", title).put("body", body))
            } catch (error: CreatorPublicationHttpError) {
                if (error.status != 422) throw error
                val raced = findOpen() ?: throw error
                updatePull(raced)
            }
        }
        val number = pull.getInt("number")
        var confirmed = pull
        repeat(3) {
            if (confirmed.optString("state") == "open" &&
                confirmed.optJSONObject("head")?.optString("sha") == commit
            ) return@withLock creatorPullUrl(number)
            if (confirmed.optString("state") == "closed") {
                error("GitHub closed submission #$number before it was confirmed")
            }
            delay(500)
            confirmed = obj("$upstream/pulls/$number")
        }
        check(confirmed.optString("state") == "open" && confirmed.optJSONObject("head")?.optString("sha") == commit) {
            "GitHub did not confirm the submitted commit on open pull request #$number"
        }
        creatorPullUrl(number)
    }
}

/** Account handles can change while an OAuth token and an editor draft remain valid. */
private fun authenticatedCreatorFiles(files: Map<String, ByteArray>, theme: StoreTheme?, id: Long, login: String): Map<String, ByteArray> {
    val path = theme?.let { it.directory + "manifest.json" } ?: "catalog/creators/$id/profile.json"
    val document = JSONObject(files.getValue(path).decodeToString())
    val author = if (theme == null) document else document.getJSONObject("author")
    if (author.getLong("githubId") != id || (theme != null && theme.author.githubId != id)) {
        throw CreatorPublicationAccountMismatch()
    }
    if (author.optString("login") == login) return files
    author.put("login", login)
    return files + (path to document.toString(2).encodeToByteArray())
}

private fun supersededCreatorAssets(profile: JSONObject?, id: Long, submittedPaths: Set<String>): Set<String> {
    val validFilename = Regex("[a-z0-9][a-z0-9_-]{0,63}\\.(webp|png|jpg)")
    return listOf("avatar", "banner").mapNotNull { field ->
        profile?.optJSONObject(field)?.optJSONObject("asset")?.optString("path")
    }.filter(validFilename::matches).map { "catalog/creators/$id/$it" }.toSet() - submittedPaths
}

private fun creatorProfilePullBody(before: JSONObject?, after: JSONObject): String? {
    fun media(profile: JSONObject?, field: String): Pair<String, String>? =
        profile?.optJSONObject(field)?.let { it.optString("source") to it.optJSONObject("asset")?.optString("sha256").orEmpty() }
    fun source(value: Pair<String, String>?) = when (value?.first) {
        "github" -> "GitHub photo"
        "generated" -> "generated banner"
        "custom" -> "custom image"
        else -> "none"
    }
    val changes = buildList {
        if (before != null && before.optString("login") != after.optString("login")) add("- GitHub username updated.")
        if (before?.optString("displayName") != after.getString("displayName")) add("- Public name updated.")
        if (before?.optString("bio").orEmpty() != after.getString("bio")) add("- Bio updated.")
        for ((field, label) in listOf("avatar" to "Profile photo", "banner" to "Banner")) {
            val previous = media(before, field)
            val next = media(after, field)
            if (previous != next) {
                add("- $label: ${source(previous)} → ${source(next)}" + if (previous?.first == "custom" && next?.first == "custom") " (replaced)." else ".")
            }
        }
    }
    if (changes.isEmpty()) return null
    return (listOf(if (before == null) "## New creator profile" else "## Creator profile update", "") + changes).joinToString("\n")
}
