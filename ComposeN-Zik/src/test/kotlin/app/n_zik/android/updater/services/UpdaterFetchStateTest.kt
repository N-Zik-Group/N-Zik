package app.n_zik.android.updater.services

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.BuildConfig
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.dialog.updater.NewUpdateAvailableDialog
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.updater.models.GithubRelease
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the [Updater.githubRelease] state machine around `fetchUpdate`:
 * a previously published release is cleared when a re-check fails (bad
 * network response, or a release whose APK cannot be extracted for the
 * user), and a release is only published once its APK resolved. That is
 * what keeps a failed or undeliverable update from staying visible in
 * the UI (review #2 fix, pinned here per review #3).
 *
 * Robolectric + plain [Application] (the repo idiom from
 * NewUpdateAvailableDialogLandscapeWidthTest) so SharedPreferences,
 * resources and toasts behave natively; the network is scripted through
 * [NetworkClientFactory]. JUnit 4 executed through the project's
 * junit-vintage-engine on the JUnit 5 platform.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UpdaterFetchStateTest {

    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        // Service-locator stub — the repo convention (MediaItemUtilsTest):
        // a mockk'd MainApplication over the running plain Application
        val appContext = ApplicationProvider.getApplicationContext<Application>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns appContext
        Dependencies.init(mainApplication)

        httpClient = mockk()
        mockkObject(NetworkClientFactory)
        every { NetworkClientFactory.getClient() } returns httpClient
        NewUpdateAvailableDialog.isCancelled = false
    }

    @After
    fun tearDown() {
        unmockkObject(NetworkClientFactory)
        Updater.githubRelease = null
        Updater.latestChangelog = null
        NewUpdateAvailableDialog.isCancelled = false
    }

    @Test
    fun `a failed fetch clears a previously published release`() {
        Updater.githubRelease = release("v8.5.0")
        scriptResponse(code = 404)

        Updater.checkForUpdate(isForced = true, showDialog = false)

        awaitUntil { Updater.githubRelease == null }
        assertNull(Updater.githubRelease)
    }

    @Test
    fun `a successful fetch publishes the release only after the asset extracted`() {
        // The test source set runs per build type — serve the asset this
        // variant's user can actually install, derived from the same pure

        // helpers the production code uses (dev variants live on the dated
        // dev channel, so they need the dated dev tag)
        val currentVersion = BuildConfig.VERSION_NAME
        val tag = if (Updater.isDevFamily(currentVersion)) "v9.0.0-dev-20260101" else "v9.0.0"
        val assets = Updater.targetBuildTypes(currentVersion, checkBetaUpdates = false)
            .map { "${BuildConfig.APP_NAME}-$it.apk" }
            .ifEmpty { listOf("${BuildConfig.APP_NAME}-${Updater.extractBuildType(currentVersion)}.apk") }
        scriptResponse(code = 200, body = releasesJson(tag, *assets.toTypedArray()))

        if (!Updater.isKnownFlavor(currentVersion)) {
            // Unrecognized flavors (e.g. debug) are targeted at the
            // non-existent "-unknown" tag — the served stable release with
            // the matching base asset is never published: a previously
            // published release is cleared and the no-update path completes
            // (isCancelled flips) with nothing republished
            Updater.githubRelease = release("v8.5.0")
            Updater.checkForUpdate(isForced = true, showDialog = false)
            awaitUntil { NewUpdateAvailableDialog.isCancelled }
            assertNull(Updater.githubRelease)
            return
        }

        Updater.checkForUpdate(isForced = true, showDialog = false)

        awaitUntil { Updater.githubRelease != null }
        assertEquals(tag, Updater.githubRelease?.tagName)
        assertEquals(assets.first(), Updater.build.name)
    }

    @Test
    fun `a release without an installable asset leaves no published release`() {
        Updater.githubRelease = release("v8.5.0")
        scriptResponse(code = 200, body = releasesJson("v9.0.0", "N-Zik-beta.apk"))

        Updater.checkForUpdate(isForced = true, showDialog = false)

        awaitUntil { Updater.githubRelease == null }
        assertNull(Updater.githubRelease)
    }

    /** Scripts every [OkHttpClient.newCall] to the same canned [Response] (the releases list; the changelog fetch reuses it harmlessly). */
    private fun scriptResponse(code: Int, body: String? = null) {
        val call = mockk<Call>()
        every { httpClient.newCall(any()) } returns call
        every { call.execute() } returns response(code, body)
    }

    private fun response(code: Int, body: String?): Response {
        val request = Request.Builder()
            .url("https://api.github.com/repos/N-Zik-Group/N-Zik/releases")
            .build()
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Failed")
        if (body != null) {
            builder.body(ResponseBody.create("application/json".toMediaType(), body))
        }
        return builder.build()
    }

    /** A single GitHub release carrying the given APK assets. */
    private fun releasesJson(tagName: String, vararg assetNames: String): String {
        val assets = assetNames.joinToString(",") { name ->
            """{"id":1,"url":"https://api.github.com/repos/N-Zik-Group/N-Zik/releases/assets/1","name":"$name","size":1048576,"created_at":"2026-01-01T00:00:00Z","browser_download_url":"https://github.com/N-Zik-Group/N-Zik/releases/download/$tagName/$name"}"""
        }
        return """[{"id":1,"tag_name":"$tagName","name":"$tagName","body":"","prerelease":false,"assets":[$assets]}]"""
    }

    private fun release(tagName: String): GithubRelease = GithubRelease(
        id = 1u,
        tagName = tagName,
        name = tagName,
        body = "",
        builds = emptyList()
    )

    /** `checkForUpdate` is fire-and-forget on a background dispatcher — poll until the asserted terminal state is reached (or fail loudly). */
    private fun awaitUntil(timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) {
                "condition not met within ${timeoutMs} ms"
            }
            Thread.sleep(50)
        }
    }
}
