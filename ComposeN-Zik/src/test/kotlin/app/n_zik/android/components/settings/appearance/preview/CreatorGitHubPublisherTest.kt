package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.util.Base64
import kotlinx.coroutines.runBlocking
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

        assertEquals(listOf(102, 1), creatorSubmissions(123, "creator", null, http).map { it.number })
        assertEquals(listOf("1", "2"), pages)
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
        val scenarios = listOf(
            Submission("nzik/creator-profile-123", files = mapOf(
                "catalog/creators/123/profile.json" to """{"githubId":123,"displayName":"Creator","bio":""}""".encodeToByteArray(),
            )),
            Submission("nzik/theme-$themeId", theme = theme, alreadyPublished = false, files = mapOf(
                theme.directory + "manifest.json" to "{}".encodeToByteArray(),
            )),
            Submission("nzik/theme-$themeId", theme = updated, files = mapOf(
                updated.directory + "manifest.json" to "{}".encodeToByteArray(),
            )),
            Submission("nzik/review-$themeId-1-123", review = ThemeReviewSubmission(theme, 5, "Test")),
            Submission("nzik/withdraw-$themeId", withdrawal = theme),
        )
        for (scenario in scenarios) {
            val fork = OutdatedFork(scenario.alreadyPublished)
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
            if (scenario.withdrawal != null) {
                val entries = checkNotNull(fork.tree).getJSONArray("tree")
                assertEquals(1, entries.length())
                assertEquals("catalog/themes/index.json", entries.getJSONObject(0).getString("path"))
                assertTrue(checkNotNull(fork.pull).getString("title").startsWith("Withdraw theme:"))
            }
        }
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

    private data class Submission(
        val branch: String,
        val files: Map<String, ByteArray> = emptyMap(),
        val theme: StoreTheme? = null,
        val review: ThemeReviewSubmission? = null,
        val withdrawal: StoreTheme? = null,
        val alreadyPublished: Boolean = true,
    )

    private class OutdatedFork(private val alreadyPublished: Boolean, private val refStatus: Int = 201) {
        val calls = mutableListOf<String>()
        var ref: JSONObject? = null
        var pull: JSONObject? = null
        var commit: JSONObject? = null
        var tree: JSONObject? = null

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
                path == "/repos/$upstream/pulls" && request.method == "GET" -> 200 to "[]"
                path == "/repos/creator/N-Zik-Theme-Store" -> 200 to """{"parent":{"full_name":"$upstream"}}"""
                path == "/repos/$upstream/branches/main" -> 200 to
                    """{"commit":{"sha":"$mainSha","commit":{"tree":{"sha":"$mainTree"}}}}"""
                path == "/repos/creator/N-Zik-Theme-Store/git/commits/$mainSha" -> 200 to "{}"
                path.endsWith("/creators/123/profile.json") -> 404 to "{}"
                path.endsWith("/creators/index.json") -> 200 to contents("""{"creators":[]}""")
                path.endsWith("/themes/index.json") -> 200 to contents(
                    if (alreadyPublished) """{"themes":[{"themeId":"$themeId","revision":1}]}""" else """{"themes":[]}""",
                )
                path.endsWith("/manifest.json") -> 200 to contents("""{"author":{"githubId":123}}""")
                path.endsWith("/reviews.json") -> 404 to "{}"
                path.contains("/git/ref/heads/") -> 404 to "{}"
                path.endsWith("/git/blobs") -> 201 to """{"sha":"blob"}"""
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
                    201 to """{"number":400}"""
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

        fun contents(json: String): String = JSONObject()
            .put("content", Base64.encodeToString(json.encodeToByteArray(), Base64.NO_WRAP)).toString()
    }
}
