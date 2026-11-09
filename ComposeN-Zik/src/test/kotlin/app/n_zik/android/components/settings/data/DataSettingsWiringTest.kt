package app.n_zik.android.components.settings.data

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import app.n_zik.android.LocalPlayerAwareWindowInsets

import app.n_zik.android.components.settings.common.LocalSettingsSearchMessage
import java.time.Duration
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.datasource.cache.Cache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.pauseListenHistoryKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.components.dialog.backup.ImportBackupDialog
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.dialog.export.ExportBackupDialog
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.core.profiles.waveformsDir
import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.playback.services.PlayerServiceModern
import java.util.concurrent.atomic.AtomicInteger

/** Behavioral wiring for the migrated Data page, using semantic selectors. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DataSettingsWiringTest {
    @get:Rule val composeRule = createComposeRule()
    private val searchMessages = mutableListOf<String>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )
    private val cardIds = listOf("storage", "cache", "backup", "auto-backup", "history", "reset")

    @Before fun setUp() {
        searchMessages.clear()
        setActiveProfile("default", context)
        listOf("preferences", "app_settings").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        mockkObject(Database, ImageCacheFactory)
        val events = mockk<EventTable>()
        every { events.countAll() } returns flowOf(0L)
        every { Database.eventTable } returns events
        every { ImageCacheFactory.getDiskCache() } returns mockk { every { size } returns 0L }
        every { ImageCacheFactory.getCacheSize() } returns 0L
        ExportBackupDialog.hideDialog()
        ImportBackupDialog.hideDialog()
    }

    @After fun tearDown() {
        ExportBackupDialog.hideDialog()
        ImportBackupDialog.hideDialog()
        // The real dismiss action terminates the process; reset only the singleton test state.
        RestartAppDialog.isActive = false
        unmockkObject(Database, ImageCacheFactory)
    }

    private fun show(focus: String = "") {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance, LocalSettingsSearchMessage provides { searchMessages += it }) {
                DataSettings(navController = mockk(relaxed = true), focus = focus)
            }
        }
        if (composeRule.mainClock.autoAdvance) composeRule.waitForIdle()
    }

    @Test fun `unfiltered cards retain order and opening writes no preferences`() {
        show()
        val cards = composeRule.onAllNodes(SemanticsMatcher("Data card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("data.card.") == true
        }, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(cardIds.map { "data.card.$it" }, cards.map { it.config[SemanticsProperties.TestTag] })
        assertTrue("Cards must retain their vertical display order", cards.zipWithNext().all { (first, second) ->
            first.positionInRoot.y < second.positionInRoot.y
        })
        assertTrue(context.getSharedPreferences("preferences", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test fun `history records only explicit submission on the Data page`() {
        show()
        val query = context.getString(R.string.search_history_action_clear)
        val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        composeRule.onNode(isEditable()).performTextInput(query)
        composeRule.waitForIdle()
        assertTrue(SearchHistoryStore.all(preferences, SearchHistoryStore.PAGE_SETTINGS_DATA).isEmpty())
        composeRule.onNode(isEditable()).performImeAction()
        composeRule.waitForIdle()
        assertEquals(listOf(query), SearchHistoryStore.all(preferences, SearchHistoryStore.PAGE_SETTINGS_DATA))
        assertTrue(SearchHistoryStore.all(preferences, SearchHistoryStore.PAGE_SETTINGS_GENERAL).isEmpty())
    }

    @Test fun `confirmed storage actions use their own cleanup target`() {
        val binder = mockk<PlayerServiceModern.Binder>()
        val cache = mockk<Cache>(relaxed = true)
        every { binder.cache } returns cache
        every { cache.keys } returns setOf("first", "second")
        every { ImageCacheFactory.clearImageCache() } answers { }
        mockkStatic(DownloadService::class)
        every { DownloadService.sendRemoveAllDownloads(any(), MyDownloadService::class.java, false) } answers { }

        val activeDirectory = waveformsDir(context, "default").apply { mkdirs() }
        val inactiveDirectory = waveformsDir(context, "inactive").apply { mkdirs() }
        val generated = activeDirectory.resolve("generated.json").apply { writeText("{}") }
        val unrelated = activeDirectory.resolve("keep.txt").apply { writeText("keep") }
        val inactive = inactiveDirectory.resolve("inactive.json").apply { writeText("{}") }
        val completed = AtomicInteger()
        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance, LocalPlayerServiceBinder provides binder) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DataStorageCleanup(onCleanupComplete = { completed.incrementAndGet() })
                    }
                }
            }

            fun confirm(action: String, label: Int, expectedCompletions: Int) {
                composeRule.onNodeWithTag("data.storage.cleanup.$action").performScrollTo().performClick()
                composeRule.onNodeWithText(context.getString(label)).performClick()
                composeRule.waitUntil(10_000) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    completed.get() == expectedCompletions
                }
            }

            confirm("images", R.string.common_action_clear, 1)
            verify(exactly = 1) { ImageCacheFactory.clearImageCache() }
            verify(exactly = 0) { cache.removeResource(any()) }
            assertTrue(generated.exists())

            confirm("streaming", R.string.common_action_clear, 2)
            verify(exactly = 1) { cache.removeResource("first") }
            verify(exactly = 1) { cache.removeResource("second") }
            assertTrue(generated.exists())

            confirm("downloads", R.string.common_action_delete, 3)
            verify(exactly = 1) { DownloadService.sendRemoveAllDownloads(any(), MyDownloadService::class.java, false) }
            verify(exactly = 2) { cache.removeResource(any()) }
            verify(exactly = 0) { binder.downloadCache }
            assertTrue(generated.exists())

            confirm("waveforms", R.string.common_action_clear, 4)
            assertFalse(generated.exists())
            assertTrue(unrelated.exists())
            assertTrue(inactive.exists())
        } finally {
            unmockkStatic(DownloadService::class)
        }
    }

    @Test fun `unknown query shows empty state and clearing restores all cards`() {
        show()
        composeRule.onNode(isEditable()).performTextInput("zzzz-no-such-setting")
        composeRule.waitForIdle()
        repeat(4) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        }
        composeRule.onNodeWithTag("search_no_results_text").assertDoesNotExist()
        assertTrue(searchMessages.contains(context.getString(R.string.search_no_results_message)))
        composeRule.onNodeWithTag("data.card.cache", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(isEditable()).performTextReplacement("")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("data.card.cache", useUnmergedTree = true).assertExists()
    }

    @Test fun `history deep link makes history open control visible`() {
        composeRule.mainClock.autoAdvance = false
        show("history")
        val deadline = System.currentTimeMillis() + 30_000
        var visible = false
        while (System.currentTimeMillis() < deadline && !visible) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            visible = runCatching {
                composeRule.onNodeWithText(context.getString(R.string.settings_history_open_description)).assertIsDisplayed()
            }.isSuccess
        }
        assertTrue("History open control must be visible after measured scrolling", visible)
    }

    @Test fun `reset action is not repeated by recomposition`() {
        show()
        composeRule.onNodeWithText(context.getString(R.string.settings_restore_default_settings)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(false, context.preferences.getBoolean(pauseListenHistoryKey, true))
        composeRule.runOnIdle { context.preferences.edit().putBoolean(pauseListenHistoryKey, true).apply() }
        composeRule.waitForIdle()
        assertTrue(context.preferences.getBoolean(pauseListenHistoryKey, false))
    }
    @Test fun `matching inner history term keeps its action available`() {
        show()
        val query = context.getString(R.string.search_history_action_clear)
        composeRule.onNode(isEditable()).performTextInput(query)
        composeRule.waitForIdle()
        composeRule.onNode(hasText(query) and !isEditable()).performScrollTo().assertIsDisplayed()
    }

    @Test fun `backup singleton host stays mounted when search has no results`() {
        composeRule.mainClock.autoAdvance = false
        show()
        fun settle() {
            repeat(30) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
        }
        settle()
        composeRule.onNode(isEditable()).performTextInput("zzzz-no-such-setting")
        settle()
        composeRule.runOnIdle { ExportBackupDialog.showDialog() }
        settle()
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.runOnIdle { ExportBackupDialog.showDialog() }
        settle()
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.runOnIdle { ExportBackupDialog.hideDialog() }
        settle()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
    }

}
