package app.n_zik.android.components.settings



import androidx.compose.foundation.layout.WindowInsets

import app.n_zik.android.LocalPlayerAwareWindowInsets

import android.app.Application

import android.content.Context


import android.content.SharedPreferences

import androidx.compose.animation.ExperimentalAnimationApi

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.runtime.CompositionLocalProvider

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.semantics.SemanticsProperties

import androidx.compose.ui.test.SemanticsMatcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier

import androidx.compose.ui.test.junit4.createComposeRule

import androidx.compose.ui.test.onNodeWithTag

import androidx.compose.ui.test.onNodeWithText

import androidx.compose.ui.test.performClick

import androidx.compose.ui.test.performScrollTo

import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider

import app.n_zik.android.enums.FontType
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.components.settings.accounts.AccountsSettings
import app.n_zik.android.components.settings.accounts.AccountsDiscordCard
import app.n_zik.android.components.settings.accounts.AccountsSyncCards
import app.n_zik.android.components.settings.accounts.AccountsYouTubeCard
import app.n_zik.android.components.settings.accounts.isYouTubeSyncBuild
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import app.n_zik.android.components.tab.Search
import app.n_zik.android.ui.styling.Appearance

import app.n_zik.android.ui.styling.DefaultDarkColorPalette

import app.n_zik.android.ui.styling.LocalAppearance

import app.n_zik.android.ui.styling.typographyOf

import app.n_zik.android.utils.enableYouTubeLoginKey
import app.n_zik.android.utils.enableYouTubeSyncKey
import app.n_zik.android.utils.cancelAllSyncs
import app.n_zik.android.utils.syncImportHistoryKey
import app.n_zik.android.utils.useLoginForBrowseKey

import app.n_zik.android.utils.encryptedPreferences

import app.n_zik.android.utils.preferences

import app.n_zik.android.utils.ytCookieKey

import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication

import app.n_zik.android.R
import app.n_zik.android.listentogether.ListenTogetherManager
import app.n_zik.android.listentogether.RoomRole


import app.n_zik.innertube.Innertube

import io.mockk.every
import io.mockk.just
import io.mockk.runs


import io.mockk.mockk


import io.mockk.mockkObject
import io.mockk.mockkStatic

import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow

import org.junit.After

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

import org.junit.Rule

import org.junit.Test

import org.junit.runner.RunWith

import org.robolectric.RobolectricTestRunner

import org.robolectric.annotation.Config




/**

 * Wiring test for the EMBEDDING call site: the "Use account for searches" row must be

 * reachable through the real legacy [AccountsSettings] screen (the

 * [YtSearchAccountToggle] call right after the "Login for Browse" block) and must

 * behave through it exactly like standalone:

 *

 * - YouTube login enabled + logged in (SAPISID cookie): the row title is present;

 * - YouTube login enabled + logged out (no cookie): the connect-first hint is shown and a

 *   click on the greyed-out row writes nothing;

 * - logged in: a click on the row persists `useLoginForSearchKey = false` AND sets the

 *   in-memory [Innertube.useLoginForSearch] flag.

 *

 * The host supplies the authenticated state independently of the asynchronous library startup.
 * Its keystore-backed preferences are stubbed because this wiring test exercises the real
 * Accounts screen without the device keystore.
 *
 */

@RunWith(RobolectricTestRunner::class)

@Config(sdk = [33], application = Application::class)

class AccountsSettingsEmbeddingWiringTest {



    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // The screen navigates through it on the "Open" rows (the Profiles / Listen Together
    // tests below stub and verify it); a bare mock is enough for the render assertions
    private val navController = mockk<NavController>()



    private val title = context.getString(R.string.settings_youtube_authenticated_search_label)

    private val connectFirst = context.getString(R.string.settings_youtube_connection_required_description)



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



    private fun seedEnabledYouTubeSync(): SharedPreferences {
        mockEncryptedPrefs(cookie = "SAPISID=test", loginEnabled = true)
        val secure = context.getSharedPreferences("youtube-sync-wiring", Context.MODE_PRIVATE)
        secure.edit().putString(ytCookieKey, "SAPISID=test")
            .putBoolean(enableYouTubeLoginKey, true)
            .putBoolean(enableYouTubeSyncKey, true)
            .apply()
        context.preferences.edit().putBoolean(useLoginForBrowseKey, true)
            .putBoolean(syncImportHistoryKey, true)
            .apply()
        every { any<Context>().encryptedPreferences } returns secure
        mockkStatic(::cancelAllSyncs)
        every { cancelAllSyncs() } answers {
            assertFalse(secure.getBoolean(enableYouTubeSyncKey, true))
            assertFalse(context.preferences.getBoolean(syncImportHistoryKey, true))
        }
        return secure
    }

    @Test
    fun disablingTheSyncMasterCancelsQueuedAndRunningSync() {
        val secure = seedEnabledYouTubeSync()
        mockkStatic(::isYouTubeSyncBuild)
        every { isYouTubeSyncBuild(any()) } returns true
        val registry = SettingsTargetRegistry()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsTargets provides registry) {
                AccountsSyncCards(selectedCards = setOf(R.string.sync_data_with_ytm_account))
            }
        }

        composeRule.onNodeWithTag("settings.target.accounts.sync_data_with_ytm_account.sync_data_with_ytm_account")
            .performClick()

        verify(exactly = 1) { cancelAllSyncs() }
        assertTrue(secure.getBoolean(enableYouTubeLoginKey, false))
        assertEquals("SAPISID=test", secure.getString(ytCookieKey, null))
    }

    @Test
    fun disablingTheLoginMasterCancelsSyncButKeepsTheAccount() {
        val secure = seedEnabledYouTubeSync()
        val registry = SettingsTargetRegistry()
        val previousSession = Innertube.youtubeSessionSnapshot()
        val previousBrowse = Innertube.useLoginForBrowse
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsTargets provides registry) {
                    AccountsYouTubeCard(search = Search(initialVisible = false))
                }
            }

            composeRule.onNodeWithTag("settings.target.accounts.youtube_music.enable_youtube_music_login")
                .performClick()

            verify(exactly = 1) { cancelAllSyncs() }
            assertFalse(secure.getBoolean(enableYouTubeLoginKey, true))
            assertEquals("SAPISID=test", secure.getString(ytCookieKey, null))
        } finally {
            Innertube.replaceYouTubeSession(previousSession)
            Innertube.useLoginForBrowse = previousBrowse
        }
    }

    @Test
    fun disablingAuthenticatedBrowsingCancelsSyncButKeepsTheAccount() {
        val secure = seedEnabledYouTubeSync()
        val registry = SettingsTargetRegistry()
        val previousBrowse = Innertube.useLoginForBrowse
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsTargets provides registry) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        AccountsYouTubeCard(search = Search(initialVisible = false))
                    }
                }
            }

            composeRule.onNodeWithTag("settings.target.accounts.youtube_music.login_for_browse")
                .performScrollTo()
                .assertIsOn()
                .assertIsEnabled()
                .performClick()
            composeRule.waitForIdle()

            verify(exactly = 1) { cancelAllSyncs() }
            assertFalse(context.preferences.getBoolean(useLoginForBrowseKey, true))
            assertTrue(secure.getBoolean(enableYouTubeLoginKey, false))
            assertEquals("SAPISID=test", secure.getString(ytCookieKey, null))
        } finally {
            Innertube.useLoginForBrowse = previousBrowse
        }
    }

    /** Stubs the keystore-backed prefs: [cookie] under [ytCookieKey] ("" = logged out), [loginEnabled] under [enableYouTubeLoginKey], everything else absent (visitorData, dataSyncId, sync toggle, …). */
    private fun mockEncryptedPrefs(cookie: String, loginEnabled: Boolean) {

        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        MainApplication.cookieStatus = if (cookie.isBlank()) MainApplication.CookieStatus.NOT_LOGGED_IN
            else MainApplication.CookieStatus.VALID
        val secure = mockk<SharedPreferences>(relaxed = true)

        every { secure.getString(any(), any()) } answers {

            if (firstArg<String>() == ytCookieKey && cookie.isNotEmpty()) cookie else null

        }

        every { secure.getBoolean(any(), any()) } answers {

            firstArg<String>() == enableYouTubeLoginKey && loginEnabled

        }

        mockkStatic("app.n_zik.android.utils.EncryptedPreferencesKt")

        every { any<Context>().encryptedPreferences } returns secure

    }



    // AccountsSettings is @ExperimentalAnimationApi (its section fades).

    @OptIn(ExperimentalAnimationApi::class)

    private fun showScreen() {

        composeRule.setContent {

            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {

                AccountsSettings(navController = navController)

            }

        }

    }



    @Test

    fun `Discord card presents its beta label as a separate badge`() {
        mockEncryptedPrefs(cookie = "", loginEnabled = false)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AccountsDiscordCard(search = Search(initialVisible = false))
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.settings_discord_title), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("settings.discord.beta", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test

    fun `the row is present when YouTube login is enabled and the user is logged in`() {

        mockEncryptedPrefs(cookie = "SAPISID=test", loginEnabled = true)

        showScreen()

        composeRule.waitForIdle()



        composeRule.onNodeWithText(title).assertExists()

    }



    @Test

    fun `a logged-out row shows the connect-first hint and a click writes nothing`() {

        mockEncryptedPrefs(cookie = "", loginEnabled = true)

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

    // The Accounts screen carries two "Open" rows (the burger deep links' target cards);
    // they are disambiguated by their unique description texts, not by the shared
    // "Open" title.
    @Test
    fun theProfilesOpenRowNavigatesToTheProfilesScreen() {
        mockEncryptedPrefs(cookie = "SAPISID=test", loginEnabled = true)
        showScreen()
        composeRule.waitForIdle()

        every { navController.navigate(any<String>()) } just runs
        composeRule
            .onNodeWithText(context.getString(R.string.profile_open_desc))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        verify { navController.navigate(NavRoutes.profiles.name) }
    }

    @Test
    fun theListenTogetherOpenRowNavigatesToTheListenTogetherScreen() {
        mockEncryptedPrefs(cookie = "SAPISID=test", loginEnabled = true)
        val manager = mockk<ListenTogetherManager>()
        mockkObject(ListenTogetherManager.Companion)
        every { ListenTogetherManager.getInstance() } returns manager
        every { manager.roomState } returns MutableStateFlow(null)
        every { manager.role } returns MutableStateFlow(RoomRole.NONE)
        every { manager.logs } returns MutableStateFlow(emptyList())
        every { manager.blockedUsernames } returns MutableStateFlow(emptySet())
        every { manager.syncHostVolumeEnabled } returns MutableStateFlow(true)
        showScreen()
        composeRule.waitForIdle()

        every { navController.navigate(any<String>()) } just runs
        composeRule
            .onNodeWithText(context.getString(R.string.listen_together_open_description))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        verify { navController.navigate(NavRoutes.listenTogether.name) }
    }
}
