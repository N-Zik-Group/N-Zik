package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.screens.settings.AccountsSettings
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.listentogether.ListenTogetherPlayerBridge
import it.fast4x.innertube.Innertube
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

/**
 * Wiring test for the EMBEDDING call site: the "Use account for searches" row must be
 * reachable through the real legacy [AccountsSettings] screen (the
 * [YtSearchAccountToggle] call right after the "Login for Browse" block) and must
 * behave through it exactly like standalone:
 *
 * - YouTube login enabled + logged in (SAPISID cookie): the row title is present;
 * - YouTube login enabled + logged out (no cookie): the connect-first hint is shown
 *   and a click on the greyed-out row writes nothing;
 * - logged in: a click on the row persists `useLoginForSearchKey = false` AND sets
 *   the in-memory [Innertube.useLoginForSearch] flag.
 *
 * Harness: the real [MainApplication] booted under Robolectric (the
 * [StartupSearchRestoreWiringTest] / `MaintenanceBootChainTest.launchApp` template —
 * it executes the real `onCreate()`, pins the process name, calls the protected
 * `attachBaseContext` reflectively, and swallows the post-restore WorkManager
 * failure of the test harness) so `appContext()` / `Dependencies` resolve; the
 * keystore-backed [encryptedPreferences] is stubbed (a JVM has no keystore) with
 * the cookie / login-enabled values per test — `onCreate()` reads the cookie
 * through it to settle [MainApplication.cookieStatus], and the screen reads its
 * login state the same way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AccountsSettingsEmbeddingWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val title = context.getString(R.string.use_account_for_searches)
    private val connectFirst = context.getString(R.string.youtube_connect_first)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @After
    fun tearDown() {
        unmockkAll()
        Innertube.useLoginForSearch = true
        MainApplication.cookieStatus = MainApplication.CookieStatus.NOT_LOGGED_IN
    }

    /**
     * Stubs the keystore-backed prefs: [cookie] under [ytCookieKey] ("" = logged
     * out), [loginEnabled] under [enableYouTubeLoginKey], everything else absent
     * (visitorData, dataSyncId, sync toggle, …).
     */
    private fun mockEncryptedPrefs(cookie: String, loginEnabled: Boolean) {
        val secure = mockk<SharedPreferences>(relaxed = true)
        every { secure.getString(any(), any()) } answers {
            if (firstArg<String>() == ytCookieKey && cookie.isNotEmpty()) cookie else null
        }
        every { secure.getBoolean(any(), any()) } answers {
            firstArg<String>() == enableYouTubeLoginKey && loginEnabled
        }
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns secure
    }

    /** Boots the real MainApplication (see StartupSearchRestoreWiringTest.launchApp). */
    private fun launchApp() {
        // onCreate initializes the Listen Together manager, which binds the player
        // service. Robolectric's bindService shadow later delivers onServiceConnected
        // with a null ComponentName, which the bridge's non-null parameter turns into
        // an NPE the first time the main looper idles (composeRule). The bridge is
        // irrelevant to this wiring test — stub the bind out.
        mockkObject(ListenTogetherPlayerBridge)
        every { ListenTogetherPlayerBridge.ensureBound(any()) } just runs
        // encryptedPreferences is already stubbed by mockEncryptedPrefs():
        // onCreate reads the cookie through it to settle MainApplication.cookieStatus
        // (SAPISID present + not expired -> VALID, absent -> NOT_LOGGED_IN).
        ShadowProcess.setProcessName(context.packageName)
        val app = MainApplication()
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        try {
            app.onCreate()
        } catch (e: Exception) {
            // Expected under Robolectric: post-restore init fails (WorkManager not
            // initialized). Everything the assertions below depend on (cookie
            // status, the useLoginForSearch restore) lands before that point.
        }
    }

    // AccountsSettings is @ExperimentalAnimationApi (its section fades).
    @OptIn(ExperimentalAnimationApi::class)
    private fun showScreen() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AccountsSettings()
            }
        }
    }

    @Test
    fun `the row is present when YouTube login is enabled and the user is logged in`() {
        mockEncryptedPrefs(cookie = "SAPISID=test", loginEnabled = true)
        launchApp()
        showScreen()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertExists()
    }

    @Test
    fun `a logged-out row shows the connect-first hint and a click writes nothing`() {
        mockEncryptedPrefs(cookie = "", loginEnabled = true)
        launchApp()
        showScreen()
        composeRule.waitForIdle()

        // The "Login for Browse" row above shows the SAME connect-first hint when
        // logged out, so a bare text match is ambiguous: select MY row by its exact
        // merged text content — title + connect-first (the greyed-out state).
        val greyedOutRow = SemanticsMatcher.expectValue(
            SemanticsProperties.Text,
            listOf(AnnotatedString(title), AnnotatedString(connectFirst)),
        )
        composeRule.onNode(greyedOutRow).performScrollTo()
        composeRule.onNode(greyedOutRow).assertIsDisplayed()

        composeRule.onNodeWithText(title).performClick()
        composeRule.waitForIdle()

        assertFalse(
            "a click on the greyed-out row must not write the preference",
            context.preferences.contains(useLoginForSearchKey)
        )
    }

    @Test
    fun `clicking the row while logged in persists guest searches and sets the flag`() {
        mockEncryptedPrefs(cookie = "SAPISID=test", loginEnabled = true)
        launchApp()
        showScreen()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(title)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertFalse(
            "the toggle must persist OFF through the real screen",
            context.preferences.getBoolean(useLoginForSearchKey, true)
        )
        assertFalse("the in-memory flag must follow the toggle", Innertube.useLoginForSearch)
    }
}
