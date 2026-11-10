package app.n_zik.android.components.settings.data

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import app.n_zik.android.LocalPlayerAwareWindowInsets

import app.n_zik.android.components.settings.common.LocalSettingsSearchMessage
import app.n_zik.android.components.tab.Search
import java.time.Duration
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.text.format.Formatter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.profileStorageIdentity
import app.n_zik.android.core.profiles.ScopedTemporaryFiles
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import org.robolectric.annotation.GraphicsMode

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
    private val cardIds = listOf("storage", "backup", "auto-backup", "history", "reset")

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

    @Test fun `storage inspection follows bar and legend drags and clears on release or cancellation`() {
        val reading = mutableStateOf(requireNotNull(partitionStorage(
            100L, 80L, mapOf(StorageCategory.IMAGES to 20L),
        )))
        val completed = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalPlayerServiceBinder provides null) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StorageChart(reading.value)
                    DataStorageCleanup(emptyList(), null, onCleanupComplete = { completed.incrementAndGet() })
                }
            }
        }

        val bar = composeRule.onNodeWithTag("data.storage.chart.bar").fetchSemanticsNode().boundsInRoot
        val imagePoint = Offset(bar.left + bar.width * 0.1f, bar.center.y)
        val freePoint = Offset(bar.left + bar.width * 0.5f, bar.center.y)
        val zeroRow = composeRule.onNode(
            hasText(context.getString(StorageCategory.STREAMING.label)) and
                hasText(Formatter.formatShortFileSize(context, 0L)),
        )
        val zeroPoint = zeroRow.fetchSemanticsNode().boundsInRoot.center
        val root = composeRule.onRoot()

        fun assertInspected(category: StorageCategory) {
            composeRule.onNode(isSelected() and hasText(context.getString(category.label))).assertExists()
            composeRule.onAllNodes(isSelected()).assertCountEquals(1)
        }

        root.performTouchInput {
            down(imagePoint)
            advanceEventTime(1_000)
            moveTo(imagePoint)
        }
        assertInspected(StorageCategory.IMAGES)

        composeRule.runOnIdle {
            reading.value = requireNotNull(partitionStorage(100L, 60L, mapOf(StorageCategory.IMAGES to 40L)))
        }
        assertInspected(StorageCategory.IMAGES)

        root.performTouchInput { moveTo(freePoint) }
        assertInspected(StorageCategory.FREE)
        root.performTouchInput { moveTo(zeroPoint) }
        assertInspected(StorageCategory.STREAMING)
        root.performTouchInput { up() }
        composeRule.onAllNodes(isSelected()).assertCountEquals(0)

        root.performTouchInput {
            down(zeroPoint)
            advanceEventTime(1_000)
            moveTo(zeroPoint)
        }
        assertInspected(StorageCategory.STREAMING)
        root.performTouchInput { cancel() }
        composeRule.onAllNodes(isSelected()).assertCountEquals(0)
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(0, completed.get())
    }

    @Test fun `confirming populated active diagnostics removes only the selected logs`() {
        val owner = checkNotNull(profileStorageIdentity(context, "default"))
        val profile = ProfileStorageUsage(owner, "Main", emptyMap())
        val logs = context.filesDir.resolve("profiles/${owner.id}/diagnostics").apply { mkdirs() }
        val confirmedLog = logs.resolve("N-Zik_log.txt").apply { writeText("original diagnostics") }
        val protectedLog = context.filesDir.resolve("N-Zik_log.txt").apply { writeText("historical diagnostics") }
        val completed = AtomicInteger()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DataStorageCleanup(listOf(profile), profile, onCleanupComplete = { completed.incrementAndGet() })
                }
            }
        }

        try {
            composeRule.onNodeWithTag("data.storage.cleanup.diagnostic_logs").performScrollTo().performClick()
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodes(hasTestTag("data.storage.cleanup.confirmation")).fetchSemanticsNodes().isNotEmpty()
            }
            confirmedLog.appendText("\nlog written while confirming")
            composeRule.onNodeWithText(context.getString(R.string.common_action_clear)).assertIsEnabled().performClick()
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                completed.get() == 1
            }
            assertFalse(confirmedLog.exists())
            assertEquals("historical diagnostics", protectedLog.readText())
        } finally {
            confirmedLog.delete()
            protectedLog.delete()
        }
    }

    @Test fun `cleanup keeps empty actions usable and confirms a zero byte temporary by scope`() {
        val profile = ProfileStorageUsage(checkNotNull(profileStorageIdentity(context, "default")), "Main", emptyMap())
        val selected = mutableStateOf<ProfileStorageUsage?>(profile)
        val completed = AtomicInteger()
        val cover = coversDir(context, "default").apply { mkdirs() }.resolve("cover.jpg").apply { writeText("cover") }
        val unknown = context.cacheDir.resolve("export.tmp").apply { writeText("export") }
        val editor = ScopedTemporaryFiles.directory(context, ScopedTemporaryFiles.IMAGE_EDITOR, profile.identity)
            .resolve(java.util.UUID.randomUUID().toString()).apply { mkdirs() }
        val temporary = editor.resolve("input.jpg").apply { check(createNewFile()) }
        val fixture = checkNotNull(captureFiles(context, profile.identity, CleanupAction.TEMPORARY_FILES))
        assertTrue("The temporary fixture must be captured before exercising the dialog", fixture.any { root ->
            root.files.any { it.name == editor.name }
        })
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DataStorageCleanup(listOf(profile), selected.value, onCleanupComplete = { completed.incrementAndGet() })
                }
            }
        }

        try {
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodes(hasTestTag("data.storage.cleanup.temporary_files"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            for (action in listOf("images", "streaming", "downloads", "waveforms", "network", "diagnostic_logs")) {
                composeRule.onNodeWithTag("data.storage.cleanup.$action").assertIsEnabled()
            }
            composeRule.onNodeWithTag("data.storage.cleanup.network").performScrollTo().performClick()
            val noFiles = context.getString(R.string.storage_cleanup_no_files)
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodes(hasText(noFiles, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(context.getString(R.string.common_action_clear)).assertIsEnabled().performClick()
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                completed.get() == 1 && composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
            }
            composeRule.onNodeWithTag("data.storage.cleanup.temporary_files").assertIsEnabled()
            composeRule.onNodeWithTag("data.storage.cleanup.all").assertIsEnabled()
            assertEquals(0L, temporary.length())
            assertEquals("cover", cover.readText())
            assertEquals("export", unknown.readText())

            fun assertConfirmation(scope: String) {
                composeRule.onNodeWithTag("data.storage.cleanup.all").performScrollTo().performClick()
                val bodyTag = "data.storage.cleanup.confirmation"
                composeRule.waitUntil(10_000) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    composeRule.onAllNodes(hasTestTag(bodyTag)).fetchSemanticsNodes().isNotEmpty()
                }
                val body = composeRule.onNodeWithTag(bodyTag).fetchSemanticsNode().config[SemanticsProperties.Text]
                    .single().text
                val description = context.getString(R.string.storage_cleanup_dialog_all)
                assertEquals(context.getString(R.string.storage_cleanup_dialog_scope, scope, description), body)
                assertEquals(1, Regex(Regex.escape(scope)).findAll(body).count())
                composeRule.onNodeWithText(context.getString(R.string.common_action_clear)).assertExists()
            }

            assertConfirmation("Main")
            composeRule.onNodeWithText(context.getString(R.string.common_action_cancel)).performClick()
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty()
            }
            composeRule.runOnIdle { selected.value = null }
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodes(hasTestTag("data.storage.cleanup.all")).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("data.storage.cleanup.network").assertIsEnabled()
            assertConfirmation(context.getString(R.string.storage_cleanup_all_profiles))
        } finally {
            temporary.delete()
            editor.delete()
            cover.delete()
            unknown.delete()
        }
    }

    @Test fun `storage opens on Total and shows cache settings after selecting a profile`() {
        val downloads = mapOf(StorageCategory.DOWNLOADS to 5L)
        val profile = ProfileStorageUsage(ProfileStorageIdentity("default", "legacy"), "Principal", downloads)
        val sample = checkNotNull(partitionStorage(100L, 80L, downloads)).copy(profiles = listOf(profile))
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DataStorageCard(Search(initialVisible = false), mutableStateOf(false), measure = { sample })
                }
            }
        }

        composeRule.onNodeWithTag("settingsDialogTab0").assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.settings_cache_location_label)).assertDoesNotExist()
        composeRule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(hasText("Principal")).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(hasTestTag("data.storage.chart.bar")).fetchSemanticsNodes().isNotEmpty()
        }
        val tabs = composeRule.onNodeWithTag("data.storage.tabs").getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithTag("settingsDialogTab0").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithTag("settingsDialogTab1").getUnclippedBoundsInRoot()
        val indicator = composeRule.onNodeWithTag("settingsDialogTabIndicator").getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag("data.storage.chart.bar").getUnclippedBoundsInRoot()
        val percentage = composeRule.onNodeWithTag("data.storage.chart.percentage")
        val percent = java.text.NumberFormat.getPercentInstance(context.resources.configuration.locales[0])
        percentage.assert(hasText(percent.format(0.2)))
        val card = composeRule.onNodeWithTag("data.card.storage").getUnclippedBoundsInRoot()
        val band = composeRule.onNodeWithTag("data.storage.nzik-section").getUnclippedBoundsInRoot()
        assertEquals(bar.left.value, tabs.left.value, 1f)
        assertEquals(bar.right.value, tabs.right.value, 1f)
        assertEquals(48f, (bar.left - card.left).value, 1f)
        assertEquals(48f, (card.right - bar.right).value, 1f)
        val capacity = composeRule.onNodeWithText(context.getString(R.string.storage_breakdown_device_capacity))
            .getUnclippedBoundsInRoot()
        assertTrue(percentage.getUnclippedBoundsInRoot().top >= capacity.bottom)
        assertTrue(bar.top >= band.bottom)
        assertEquals(tabs.left.value, first.left.value, 1f)
        assertEquals(tabs.right.value, second.right.value, 1f)
        assertEquals(first.right.value, second.left.value, 1f)
        assertEquals((first.right - first.left).value, (second.right - second.left).value, 1f)
        assertEquals(first.left.value, indicator.left.value, 1f)
        assertEquals(first.right.value, indicator.right.value, 1f)
        composeRule.onNodeWithTag("settingsDialogTab1").performClick().assertIsSelected()
        composeRule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(hasText(context.getString(R.string.settings_cache_location_label)))
                .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodes(hasTestTag("data.storage.device")).fetchSemanticsNodes().isEmpty()
        }
        percentage.assertDoesNotExist()
        composeRule.onNodeWithTag("data.storage.device").assertDoesNotExist()
        composeRule.onNodeWithTag("data.storage.category.free").assertDoesNotExist()
        composeRule.onNodeWithTag("data.storage.category.other_used").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.storage_breakdown_device_capacity)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.storage_breakdown_usage)).assertDoesNotExist()
        val zero = Formatter.formatShortFileSize(context, 0)
        composeRule.onNodeWithTag("data.storage.category.network").assert(hasText(zero))
        composeRule.onNodeWithTag("data.storage.category.historical").assert(hasText(zero))
        composeRule.onNodeWithTag("data.storage.category.migration").assert(hasText(zero))
        composeRule.onNodeWithTag("data.storage.used").assert(hasText(Formatter.formatShortFileSize(context, 5)))
        composeRule.onNodeWithTag("data.storage.nzik-section").assert(hasText("Principal", substring = true))
        composeRule.onNodeWithTag("data.storage.info").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.storage_info_network)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.storage_info_installation)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.storage_info_free)).assertDoesNotExist()
    }

    @Test fun `grouped storage highlights the legend while dragging and clears on release`() {
        val sample = checkNotNull(partitionStorage(
            100L, 40L, mapOf(StorageCategory.DOWNLOADS to 10L, StorageCategory.IMAGES to 20L),
        ))
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance.copy(uiRoundnessShape = RoundedCornerShape(16.dp))) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DataStorageCard(Search(initialVisible = false), mutableStateOf(false), measure = { sample })
                }
            }
        }
        composeRule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(hasTestTag("data.storage.chart.bar")).fetchSemanticsNodes().isNotEmpty()
        }
        val bar = composeRule.onNodeWithTag("data.storage.chart.bar").fetchSemanticsNode().boundsInRoot
        val imagesTag = "data.storage.category.images"
        val images = composeRule.onNodeWithTag(imagesTag).fetchSemanticsNode().boundsInRoot.center
        val download = Offset(bar.left + bar.width * .35f, bar.center.y)
        val root = composeRule.onRoot()

        root.performTouchInput { down(download); advanceEventTime(1_000); moveTo(download) }
        composeRule.onNodeWithTag("data.storage.category.downloads").assertIsSelected()
        root.performTouchInput { moveTo(images) }
        composeRule.onNodeWithTag(imagesTag).assertIsSelected()
        root.performTouchInput { up() }
        composeRule.onAllNodes(hasTestTag(imagesTag) and isSelected()).assertCountEquals(0)
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `storage shows one device summary and puts separate volumes in Info`() {
        val primary = checkNotNull(partitionStorage(
            10_000_000_000L,
            7_600_000_000L,
            mapOf(
                StorageCategory.IMAGES to 18_000_000L,
                StorageCategory.STREAMING to 25_000_000L,
                StorageCategory.OTHER_NZIK to 12_000_000L,
                StorageCategory.INSTALLATION to 14_000_000L,
            ),
        ))
        val removable = checkNotNull(partitionStorage(
            8_000_000_000L, 2_000_000_000L, mapOf(StorageCategory.DOWNLOADS to 3_000_000L),
        )).copy(volumeLabel = "SD card", filePresence = StorageFilePresence(true, setOf("default")))
        val unrelated = checkNotNull(partitionStorage(
            8_000_000_000L, 3_000_000_000L, mapOf(StorageCategory.INSTALLATION to 6_144L),
        )).copy(volumeLabel = "Empty volume", filePresence = StorageFilePresence(false, emptySet()))
        val sample = primary.copy(
            profiles = listOf(
                ProfileStorageUsage(ProfileStorageIdentity("default", "legacy"), "Principal", emptyMap()),
            ),
            otherVolumes = listOf(removable, unrelated),
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance.copy(uiRoundnessShape = RoundedCornerShape(16.dp))) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DataStorageCard(Search(initialVisible = false), mutableStateOf(false), measure = { sample })
                }
            }
        }
        composeRule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(hasTestTag("data.storage.used") and hasText(Formatter.formatShortFileSize(context, 69_000_000L)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "data.storage.device")).assertCountEquals(1)
        val system = composeRule.onNodeWithTag("data.storage.category.other_used").getUnclippedBoundsInRoot()
        val free = composeRule.onNodeWithTag("data.storage.category.free").getUnclippedBoundsInRoot()
        val capacity = composeRule.onNodeWithText(context.getString(R.string.storage_breakdown_device_capacity))
            .getUnclippedBoundsInRoot()
        assertTrue(system.top < free.top && free.top < capacity.top)
        val zero = Formatter.formatShortFileSize(context, 0)
        composeRule.onNodeWithTag("data.storage.category.historical").assert(hasText(zero))
        composeRule.onNodeWithTag("data.storage.category.migration").assert(hasText(zero))
        composeRule.onNodeWithTag("data.storage.secondary.0").assertDoesNotExist()
        composeRule.onNodeWithTag("data.storage.category.downloads").assert(hasText(zero))
        if (System.getenv("NZIK_CAPTURE_STORAGE_SCREENSHOT") == "1") {
            val screenshot = File("build/reports/storage-redesign.png")
            screenshot.parentFile?.mkdirs()
            screenshot.outputStream().use {
                assertTrue(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        composeRule.onNodeWithTag("data.storage.additional-volumes").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(hasTestTag("data.storage.secondary.0"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("data.storage.secondary.1").assertDoesNotExist()
        composeRule.onNodeWithTag("data.storage.secondary.0.downloads")
            .assert(hasText(Formatter.formatShortFileSize(context, 3_000_000L)))
        composeRule.onNodeWithTag("data.storage.secondary.0.images").assertDoesNotExist()
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
        composeRule.onNodeWithTag("data.card.storage", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(isEditable()).performTextReplacement("")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("data.card.storage", useUnmergedTree = true).assertExists()
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
