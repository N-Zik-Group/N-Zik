package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class CreatorGitHubPublisherTest {
    @Test
    fun installationLookupRequiresTheCreatorsActualThemeStoreFork() = runBlocking {
        fun http(status: Int, body: String) = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("/repos/creator/N-Zik-Theme-Store", request.url.encodedPath)
            Response.Builder().code(status).request(request).protocol(Protocol.HTTP_1_1).message("Stub")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()

        val fork = """{"owner":{"id":123},"parent":{"full_name":"N-Zik-Group/N-Zik-Theme-Store"}}"""
        assertTrue(creatorThemeStoreForkExists(123, "creator", null, http(200, fork)))
        assertFalse(creatorThemeStoreForkExists(456, "creator", null, http(200, fork)))
        assertFalse(creatorThemeStoreForkExists(123, "creator", null, http(404, "{}")))
        assertFalse(creatorThemeStoreForkExists(123, "creator", null, http(200, fork.replace(upstream, "other/catalog"))))
    }

    @Test
    fun creatorSetupOnlyCreatesTheForkWhenItDoesNotExist() = runBlocking {
        for (exists in listOf(false, true)) {
            var forkReady = exists
            var creations = 0
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val (status, body) = when (request.url.encodedPath) {
                    "/repos/creator/N-Zik-Theme-Store" -> if (forkReady) {
                        200 to """{"parent":{"full_name":"N-Zik-Group/N-Zik-Theme-Store"}}"""
                    } else 404 to "{}"
                    "/repos/$upstream/forks" -> {
                        assertEquals("POST", request.method)
                        creations++
                        forkReady = true
                        202 to "{}"
                    }
                    else -> error("Unexpected request")
                }
                Response.Builder().code(status).request(request).protocol(Protocol.HTTP_1_1).message("Stub")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
            prepareCreatorThemeStoreFork("creator", "test-token", http)
            assertEquals(if (exists) 0 else 1, creations)
        }
    }

    @Test
    fun submissionHistoryReadsAllPagesAndDeduplicatesPulls() = runBlocking {
        fun pull(number: Int, account: Long = 123) = """
            {"number":$number,"user":{"id":$account},"head":{"ref":"nzik/creator-profile-123"},
             "title":"Profile $number","state":"closed","labels":[{"name":"merged"}]}
        """.trimIndent()
        val pages = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val page = requireNotNull(request.url.queryParameter("page"))
            pages += page
            assertEquals("Bearer history-token", request.header("Authorization"))
            val response = Response.Builder().code(200).request(request).protocol(Protocol.HTTP_1_1).message("Stub")
            val body = when (page) {
                "1" -> {
                    response.header("Link", "<https://api.github.com/repos/$upstream/pulls?page=2>; rel=\"next\"")
                    "[${pull(102)},${pull(101, 456)}]"
                }
                "2" -> "[${pull(102)},${pull(1)}]"
                else -> error("Unexpected history page $page")
            }
            response.body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()

        assertEquals(listOf(102, 1), creatorSubmissions(123, "creator", "history-token", http).map { it.number })
        assertEquals(listOf("1", "2"), pages)
    }

    @Test
    fun `unpublished creators cannot submit themes or updates before any fork or pull request access`() = runTest {
        for (revision in listOf(1, 2)) {
            for (creators in listOf("[]", "[{\"githubId\":456}]")) {
                val fork = OutdatedFork(creatorIndex = """{"schemaVersion":1,"creators":$creators}""")
                val theme = StoreTheme(
                    1, themeId, revision, "Preview", "A test theme", StoreAuthor(123, "creator"), emptyList(),
                    StoreCompatibility(null), StoreFile("appearance.json", "a".repeat(64)), emptyList(),
                )
                val result = runCatching {
                    submitCreatorProfile("test-token", 123, emptyMap(), theme,
                        requestClient = fork.client, contributionBlock = { null })
                }

                assertTrue(result.exceptionOrNull() is CreatorPublicationProfileRequired)
                assertEquals(listOf("/user", "/repos/$upstream/contents/catalog/creators/index.json"), fork.calls)
                assertEquals(null, fork.pull)
                assertEquals(null, fork.commit)
            }
        }
    }

    @Test
    fun `creator index failures never allow theme publication`() = runTest {
        for ((status, index) in listOf(503 to "{}", 404 to "{}", 200 to "invalid", 200 to """{"schemaVersion":2,"creators":[{"githubId":123}]}""")) {
            val fork = OutdatedFork(creatorIndex = index, creatorIndexStatus = status)
            val theme = StoreTheme(
                1, themeId, 1, "Preview", "A test theme", StoreAuthor(123, "creator"), emptyList(),
                StoreCompatibility(null), StoreFile("appearance.json", "a".repeat(64)), emptyList(),
            )
            val result = runCatching {
                submitCreatorProfile("test-token", 123, emptyMap(), theme,
                    requestClient = fork.client, contributionBlock = { null })
            }

            assertTrue(result.isFailure)
            assertEquals(listOf("/user", "/repos/$upstream/contents/catalog/creators/index.json"), fork.calls)
            assertEquals(null, fork.pull)
        }
    }

    @Test
    fun allSubmissionKindsUseUpstreamBaseWithoutSynchronizingTheFork() = runBlocking {
        val author = StoreAuthor(123, "creator")
        val theme = StoreTheme(
            schemaVersion = 1,
            themeId = themeId,
            revision = 1,
            title = "Preview",
            description = "A test theme",
            author = author,
            tags = emptyList(),
            compatibility = StoreCompatibility(null),
            appearance = StoreFile("appearance.json", "a".repeat(64)),
            previews = emptyList(),
        )
        val updated = theme.copy(revision = 2)
        val upgraded = updated.copy(schemaVersion = 2, compatibility = StoreCompatibility(99))
        val current = upgraded.copy(schemaVersion = 3, revision = 3, compatibility = StoreCompatibility(101))
        val scenarios = listOf(
            Submission("nzik/creator-profile-123", files = mapOf(
                "catalog/creators/123/profile.json" to """{"githubId":123,"displayName":"Creator","bio":""}""".encodeToByteArray(),
            )),
            Submission("nzik/theme-$themeId", theme = theme, alreadyPublished = false, files = mapOf(
                theme.directory + "manifest.json" to """{"author":{"githubId":123,"login":"creator"}}""".encodeToByteArray(),
            )),
            Submission("nzik/theme-$themeId", theme = updated, files = mapOf(
                updated.directory + "manifest.json" to """{"author":{"githubId":123,"login":"creator"}}""".encodeToByteArray(),
            )),
            Submission("nzik/theme-$themeId", theme = upgraded, hasV2Index = true, files = mapOf(
                upgraded.directory + "manifest.json" to """{"author":{"githubId":123,"login":"creator"}}""".encodeToByteArray(),
            )),
            Submission("nzik/theme-$themeId", theme = current, hasV2Index = true, v2Revision = 2, hasV3Index = true,
                files = mapOf(current.directory + "manifest.json" to """{"author":{"githubId":123,"login":"creator"}}""".encodeToByteArray())),
            Submission("nzik/withdraw-$themeId", withdrawal = upgraded, hasV2Index = true, v2Revision = 2),
            Submission("nzik/withdraw-$themeId", withdrawal = current, hasV2Index = true, v2Revision = 2, hasV3Index = true, v3Revision = 3),
            Submission("nzik/review-$themeId-1-123", review = ThemeReviewSubmission(theme, 5, "Test")),
            Submission("nzik/withdraw-$themeId", withdrawal = theme),
        )
        for (scenario in scenarios) {
            val fork = OutdatedFork(
                scenario.alreadyPublished, hasV2Index = scenario.hasV2Index, v2Revision = scenario.v2Revision,
                hasV3Index = scenario.hasV3Index, v3Revision = scenario.v3Revision,
            )
            val actual = submitCreatorProfile(
                "test-token", author.githubId, scenario.files, scenario.theme, scenario.review, scenario.withdrawal,
                requestClient = fork.client,
                contributionBlock = { null },
            )
            assertEquals("https://github.com/$upstream/pull/400", actual)
            assertEquals("refs/heads/${scenario.branch}", fork.ref?.getString("ref"))
            assertEquals("creator:${scenario.branch}", fork.pull?.getString("head"))
            assertEquals("main", fork.pull?.getString("base"))
            assertEquals(mainSha, fork.commit?.getJSONArray("parents")?.getString(0))
            assertEquals(mainTree, fork.tree?.getString("base_tree"))
            assertFalse(fork.calls.any { it.contains("merge-upstream") || it.contains("/git/refs/heads/main") })
            val entries = checkNotNull(fork.tree).getJSONArray("tree")
            val paths = (0 until entries.length()).map { entries.getJSONObject(it).getString("path") }.toSet()
            val published = scenario.theme
            if (published != null && published.schemaVersion >= ThemeAppearanceV2) {
                val indexPath = themeCatalogIndexPath(published.schemaVersion)
                assertEquals(setOf(published.directory + "manifest.json", indexPath), paths)
                val blob = (0 until entries.length()).map(entries::getJSONObject)
                    .single { it.getString("path") == indexPath }.getString("sha")
                val index = JSONObject(fork.blobs.getValue(blob).decodeToString())
                assertEquals(80, index.getInt("previewLimit"))
                assertEquals(98, index.getInt("legacyMaxVersionCode"))
                assertEquals(published.revision, index.getJSONArray("themes").getJSONObject(0).getInt("revision"))
            }
            if (scenario.withdrawal != null) {
                assertEquals(
                    buildSet {
                        add(ThemeCatalogV1Path)
                        if (scenario.v2Revision != null) add(ThemeCatalogV2Path)
                        if (scenario.v3Revision != null) add(ThemeCatalogV3Path)
                    },
                    paths,
                )
                assertTrue(checkNotNull(fork.pull).getString("title").startsWith("Withdraw theme:"))
            }
        }
    }

    @Test
    fun submissionOpensNewPullWhenExistingPullClosesDuringUpdate() = runBlocking {
        val fork = OutdatedFork(alreadyPublished = false, closingPull = true)
        val files = mapOf(
            "catalog/creators/123/profile.json" to """{"githubId":123,"displayName":"Creator","bio":""}""".encodeToByteArray(),
        )

        val result = submitCreatorProfile("test-token", 123, files, requestClient = fork.client, contributionBlock = { null })

        assertTrue(fork.calls.contains("/repos/$upstream/pulls/399"))
        assertEquals("https://github.com/$upstream/pull/400", result)
        assertEquals("creator:nzik/creator-profile-123", fork.pull?.getString("head"))
    }

    @Test
    fun expiredHistoryTokenFallsBackToPublicRead() = runBlocking {
        val headers = mutableListOf<String?>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val token = request.header("Authorization")
            headers += token
            Response.Builder().code(if (token == null) 200 else 401)
                .request(request).protocol(Protocol.HTTP_1_1).message("Stub")
                .body("[]".toResponseBody("application/json".toMediaType())).build()
        }.build()

        assertTrue(creatorSubmissions(123, "creator", "old-token", http).isEmpty())
        assertEquals(listOf("Bearer old-token", null), headers)
    }

    @Test
    fun missingWorkflowScopeStopsBeforeOpeningAPullRequest() = runBlocking {
        val fork = OutdatedFork(alreadyPublished = false, refStatus = 404)
        val files = mapOf(
            "catalog/creators/123/profile.json" to """{"githubId":123,"displayName":"Creator","bio":""}""".encodeToByteArray(),
        )
        val result = runCatching {
            submitCreatorProfile("test-token", 123, files, requestClient = fork.client, contributionBlock = { null })
        }
        assertTrue(result.exceptionOrNull() is CreatorPublicationWorkflowPermission)
        assertEquals(null, fork.pull)
    }

    @Test
    fun renamedAccountUpdatesAuthoredDocumentsBeforeTheirIndexHashesAreCalculated() = runTest {
        val theme = StoreTheme(
            1, themeId, 1, "Preview", "A test theme", StoreAuthor(123, "old-login"), emptyList(),
            StoreCompatibility(null), StoreFile("appearance.json", "a".repeat(64)), emptyList(),
        )
        for (submittedTheme in listOf(null, theme)) {
            val fork = OutdatedFork(alreadyPublished = false)
            val path = submittedTheme?.let { it.directory + "manifest.json" } ?: "catalog/creators/123/profile.json"
            val document = if (submittedTheme == null) {
                """{"githubId":123,"login":"old-login","displayName":"Creator","bio":""}"""
            } else """{"author":{"githubId":123,"login":"old-login"}}"""
            submitCreatorProfile("test-token", 123, mapOf(path to document.encodeToByteArray()), submittedTheme,
                requestClient = fork.client, contributionBlock = { null })

            val entries = checkNotNull(fork.tree).getJSONArray("tree")
            fun content(file: String): ByteArray {
                val entry = (0 until entries.length()).map(entries::getJSONObject).single { it.getString("path") == file }
                return fork.blobs.getValue(entry.getString("sha"))
            }
            val bytes = content(path)
            val authored = JSONObject(bytes.decodeToString()).let { if (submittedTheme == null) it else it.getJSONObject("author") }
            assertEquals("creator", authored.getString("login"))
            val indexPath = if (submittedTheme == null) "catalog/creators/index.json" else "catalog/themes/index.json"
            val indexKey = if (submittedTheme == null) "creators" else "themes"
            val hashKey = if (submittedTheme == null) "profileSha256" else "manifestSha256"
            assertEquals(bytes.sha256(), JSONObject(content(indexPath).decodeToString()).getJSONArray(indexKey).getJSONObject(0).getString(hashKey))
        }
    }

    @Test
    fun renamedPublishedProfileCanBeSubmittedWithoutOtherChanges() = runTest {
        val before = """{"githubId":123,"login":"old-login","displayName":"Creator","bio":""}"""
        val fork = OutdatedFork(alreadyPublished = false, publishedProfile = before)
        val path = "catalog/creators/123/profile.json"

        val url = submitCreatorProfile("test-token", 123, mapOf(path to before.encodeToByteArray()),
            requestClient = fork.client, contributionBlock = { null })

        assertEquals("https://github.com/$upstream/pull/400", url)
        assertEquals("## Creator profile update\n\n- GitHub username updated.", checkNotNull(fork.pull).getString("body"))
        val entries = checkNotNull(fork.tree).getJSONArray("tree")
        val profile = (0 until entries.length()).map(entries::getJSONObject).single { it.getString("path") == path }
        assertEquals("creator", JSONObject(fork.blobs.getValue(profile.getString("sha")).decodeToString()).getString("login"))
    }

    @Test
    fun unchangedPublishedProfileStillDoesNotOpenAPull() = runTest {
        val before = """{"githubId":123,"login":"creator","displayName":"Creator","bio":""}"""
        val fork = OutdatedFork(alreadyPublished = false, publishedProfile = before)

        val result = runCatching {
            submitCreatorProfile("test-token", 123, mapOf("catalog/creators/123/profile.json" to before.encodeToByteArray()),
                requestClient = fork.client, contributionBlock = { null })
        }

        assertTrue(result.exceptionOrNull() is CreatorPublicationUnchanged)
        assertEquals(null, fork.pull)
    }

    @Test
    fun profileEditorEnablesPublishingWhenOnlyTheGitHubLoginChanged() {
        val published = CreatorProfile(1, 123, "old-login", "Creator", "Bio", CreatorMedia("github"), CreatorMedia("generated"))

        assertFalse(hasCreatorPublicationChanges(published, "old-login", "Creator", "Bio", null, null))
        assertTrue(hasCreatorPublicationChanges(published, "creator", "Creator", "Bio", null, null))
    }

    @Test
    fun refreshingAHandleNeverReattributesAnotherAccountsDocument() = runTest {
        val fork = OutdatedFork(alreadyPublished = false)
        val result = runCatching {
            submitCreatorProfile("test-token", 123, mapOf("catalog/creators/123/profile.json" to
                """{"githubId":456,"login":"old-login"}""".encodeToByteArray()),
                requestClient = fork.client, contributionBlock = { null })
        }
        assertTrue(result.exceptionOrNull() is CreatorPublicationAccountMismatch)
        assertTrue(fork.blobs.isEmpty())
        assertEquals(null, fork.pull)
    }

    private data class Submission(
        val branch: String,
        val files: Map<String, ByteArray> = emptyMap(),
        val theme: StoreTheme? = null,
        val review: ThemeReviewSubmission? = null,
        val withdrawal: StoreTheme? = null,
        val alreadyPublished: Boolean = true,
        val hasV2Index: Boolean = false,
        val v2Revision: Int? = null,
        val hasV3Index: Boolean = false,
        val v3Revision: Int? = null,
    )

    private class OutdatedFork(
        private val alreadyPublished: Boolean = false,
        private val creatorIndex: String = """{"schemaVersion":1,"creators":[{"githubId":123}]}""",
        private val creatorIndexStatus: Int = 200,
        private val refStatus: Int = 201,
        private val closingPull: Boolean = false,
        private val publishedProfile: String? = null,
        private val hasV2Index: Boolean = false,
        private val v2Revision: Int? = null,
        private val hasV3Index: Boolean = false,
        private val v3Revision: Int? = null,
    ) {
        val calls = mutableListOf<String>()
        var ref: JSONObject? = null
        var pull: JSONObject? = null
        var commit: JSONObject? = null
        var tree: JSONObject? = null
        val blobs = mutableMapOf<String, ByteArray>()

        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            calls += path
            val body = request.body?.let {
                val buffer = Buffer()
                it.writeTo(buffer)
                JSONObject(buffer.readUtf8())
            }
            val (status, response) = when {
                path == "/user" -> 200 to """{"id":123,"login":"creator"}"""
                path == "/repos/$upstream/pulls" && request.method == "GET" -> 200 to
                    if (closingPull) """[{"number":399}]""" else "[]"
                path == "/repos/$upstream/pulls/399" && request.method == "PATCH" ->
                    200 to """{"number":399,"state":"closed"}"""
                path == "/repos/creator/N-Zik-Theme-Store" -> 200 to """{"parent":{"full_name":"$upstream"}}"""
                path == "/repos/$upstream/branches/main" -> 200 to
                    """{"commit":{"sha":"$mainSha","commit":{"tree":{"sha":"$mainTree"}}}}"""
                path == "/repos/creator/N-Zik-Theme-Store/git/commits/$mainSha" -> 200 to "{}"
                path.endsWith("/creators/123/profile.json") -> publishedProfile?.let { 200 to contents(it) } ?: (404 to "{}")
                path.endsWith("/creators/index.json") -> creatorIndexStatus to contents(creatorIndex)
                path.endsWith("/themes/index.json") -> 200 to contents(
                    if (alreadyPublished) """{"themes":[${publishedEntry(1)}]}""" else """{"themes":[]}""",
                )
                path.endsWith("/themes/index-v2.json") -> if (hasV2Index) {
                    val entry = v2Revision?.let(::publishedEntry).orEmpty()
                    200 to contents("""{"schemaVersion":1,"legacyMaxVersionCode":98,"previewLimit":80,"themes":[$entry]}""")
                } else {
                    404 to "{}"
                }
                path.endsWith("/themes/index-v3.json") -> if (hasV3Index) {
                    val entry = v3Revision?.let(::publishedEntry).orEmpty()
                    200 to contents("""{"schemaVersion":1,"legacyMaxVersionCode":98,"previewLimit":80,"themes":[$entry]}""")
                } else {
                    404 to "{}"
                }
                path.endsWith("/manifest.json") -> 200 to contents(publishedManifest(path.substringBeforeLast('/').substringAfterLast('/').toInt()))
                path.endsWith("/reviews.json") -> 404 to "{}"
                path.contains("/git/ref/heads/") -> 404 to "{}"
                path.endsWith("/git/blobs") -> {
                    val sha = "blob-${blobs.size}"
                    blobs[sha] = Base64.decode(checkNotNull(body).getString("content"), Base64.DEFAULT)
                    201 to """{"sha":"$sha"}"""
                }
                path.endsWith("/git/trees") -> {
                    tree = body
                    201 to """{"sha":"new-tree"}"""
                }
                path.endsWith("/git/commits") -> {
                    commit = body
                    201 to """{"sha":"new-commit"}"""
                }
                path.endsWith("/git/refs") -> {
                    ref = body
                    refStatus to "{}"
                }
                path == "/repos/$upstream/pulls" && request.method == "POST" -> {
                    pull = body
                    201 to """{"number":400,"state":"open","head":{"sha":"new-commit"}}"""
                }
                else -> error("Unexpected request ${request.method} $path")
            }
            Response.Builder().code(status).request(request).protocol(Protocol.HTTP_1_1)
                .message("Stub").header("X-OAuth-Scopes", "public_repo").body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
    }

    private companion object {
        const val upstream = "N-Zik-Group/N-Zik-Theme-Store"
        const val themeId = "99edc843-6a9b-5753-8e76-cc11fe9b3295"
        const val mainSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val mainTree = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        fun publishedManifest(revision: Int): String =
            """{"themeId":"$themeId","revision":$revision,"author":{"githubId":123}}"""

        fun publishedEntry(revision: Int): String {
            val hash = publishedManifest(revision).encodeToByteArray().sha256()
            return """{"themeId":"$themeId","revision":$revision,"manifestSha256":"$hash"}"""
        }

        fun contents(json: String): String = JSONObject()
            .put("content", Base64.encodeToString(json.encodeToByteArray(), Base64.NO_WRAP)).toString()
    }
}
