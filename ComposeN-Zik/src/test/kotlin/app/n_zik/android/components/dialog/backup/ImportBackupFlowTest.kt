package app.n_zik.android.components.dialog.backup

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.components.import.ImportChainRunner
import app.n_zik.android.components.onboarding.OnboardingImportScreen
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.profileDisplayName
import app.n_zik.android.utils.saveProfileDisplayName
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.writeProfileEntries
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import timber.log.Timber

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h800dp")
class ImportBackupFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )
    private val targetTitle get() = context.getString(R.string.backup_import_target_profile_title)
    private var onboardingCompletions = 0
    private val importFailure = AtomicReference<Throwable>()
    private val importMessages = CopyOnWriteArrayList<String>()
    private val importLog = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (tag in setOf("ImportProfileState", "FireAndForget", "ImportFromFile", "RescueFiles")) {
                importMessages += "$tag: $message"
                if (t != null) importFailure.compareAndSet(null, t)
            }
        }
    }

    @Before fun setUp() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        setActiveProfile(DEFAULT_PROFILE_ID, context)
        assertTrue(context.writeProfileEntries(listOf("listener" to "Listener")))
        context.saveProfileDisplayName("listener", "Listener")
        RestartAppDialog.isActive = false
        Timber.plant(importLog)
        mockkObject(ImportChainRunner)
        every { ImportChainRunner.start(any(), any(), any(), any(), any(), any(), any()) } returns Unit
    }

    @After fun tearDown() {
        ImportBackupDialog.hideDialog()
        ImportTargetProfileDialog.clear()
        RestartAppDialog.isActive = false
        Timber.uproot(importLog)
        unmockkObject(ImportChainRunner)
    }

    @Test fun `settings defaults to database and closes after profiles-only import`() {
        importProfilesOnly(fromOnboarding = false)
    }

    @Test fun `onboarding defaults to database and settings and advances after profiles-only import`() {
        importProfilesOnly(fromOnboarding = true)
    }

    @Test fun `settings restores the chosen profile and file after recreation`() {
        restoreTargetChoice(fromOnboarding = false)
    }

    @Test fun `onboarding restores the chosen profile and files after recreation`() {
        restoreTargetChoice(fromOnboarding = true)
    }

    @Test fun `settings aborts a combined import when the second picker is cancelled`() {
        cancelSecondPicker(fromOnboarding = false)
    }

    @Test fun `onboarding aborts a combined import when the second picker is cancelled`() {
        cancelSecondPicker(fromOnboarding = true)
    }

    private fun importProfilesOnly(fromOnboarding: Boolean) {
        val ui = NzikDispatchers.UI
        mockkObject(NzikDispatchers)
        every { NzikDispatchers.DATA } returns ui
        try {
            verifyProfilesOnlyImport(fromOnboarding)
        } finally {
            unmockkObject(NzikDispatchers)
        }
    }

    private fun verifyProfilesOnlyImport(fromOnboarding: Boolean) {
        openEntry(fromOnboarding)
        option(BackupContent.Database).assertIsOn().performClick()
        if (fromOnboarding) {
            option(BackupContent.Settings).assertIsOn().performClick()
        } else {
            option(BackupContent.Settings).assertIsOff()
        }
        option(BackupContent.Themes).assertIsOff()
        option(BackupContent.Profiles).assertIsOff().performScrollTo().performClick()
        submit(fromOnboarding)
        val archive = Uri.parse("content://backup/profiles")
        shadowOf(context.contentResolver).registerInputStream(
            archive,
            ByteArrayInputStream("restored\tRestored Profile".encodeToByteArray()),
        )
        returnPickerResult("text/plain", archive)

        try {
            composeRule.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                importFailure.get()?.let { throw AssertionError("Profiles-only import failed", it) }
                if (fromOnboarding) onboardingCompletions == 1 else !ImportBackupDialog.isActive
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Profiles-only import did not finish: $importMessages", importFailure.get() ?: timeout)
        }
        assertEquals("Restored Profile", context.profileDisplayName("restored"))
        assertEquals(if (fromOnboarding) 1 else 0, onboardingCompletions)
        assertFalse(RestartAppDialog.isActive)
        composeRule.onNodeWithText(targetTitle).assertDoesNotExist()
        verify(exactly = 0) { ImportChainRunner.start(any(), any(), any(), any(), any(), any(), any()) }
        assertNull(shadowOf(composeRule.activity).peekNextStartedActivityForResult())
    }

    private fun restoreTargetChoice(fromOnboarding: Boolean) {
        val database = Uri.parse("content://backup/database")
        val settings = Uri.parse("content://backup/settings")
        openEntry(fromOnboarding)
        submit(fromOnboarding)
        returnPickerResult("application/zip", database)
        if (fromOnboarding) returnPickerResult("text/csv", settings)

        waitForTarget()
        targetSubmit().assertIsNotEnabled()
        composeRule.onNodeWithText("Listener").performClick()
        assertListenerSelected()
        val oldActivity = composeRule.activity
        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { installHost(it, fromOnboarding) }
        assertTrue(oldActivity !== composeRule.activity)
        waitForTarget()
        assertListenerSelected()
        targetSubmit().performClick()
        composeRule.runOnIdle {
            verify(exactly = 1) {
                ImportChainRunner.start(
                    any(), "listener", database, if (fromOnboarding) settings else null, null, any(), null,
                )
            }
        }
    }

    private fun cancelSecondPicker(fromOnboarding: Boolean) {
        val settings = Uri.parse("content://backup/settings-retry")
        openEntry(fromOnboarding)
        if (!fromOnboarding) option(BackupContent.Settings).performClick()
        submit(fromOnboarding)
        returnPickerResult("application/zip", Uri.parse("content://backup/cancelled-database"))
        returnPickerResult("text/csv", null)

        composeRule.onNodeWithText(targetTitle).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(0, onboardingCompletions)
            if (!fromOnboarding) assertTrue(ImportBackupDialog.isActive)
            verify(exactly = 0) { ImportChainRunner.start(any(), any(), any(), any(), any(), any(), any()) }
        }
        option(BackupContent.Database).assertIsOn().performScrollTo().performClick()
        option(BackupContent.Settings).assertIsOn()
        submit(fromOnboarding)
        returnPickerResult("text/csv", settings)
        waitForTarget()
        composeRule.onNodeWithText("Listener").performClick()
        targetSubmit().performClick()
        composeRule.runOnIdle {
            verify(exactly = 1) { ImportChainRunner.start(any(), "listener", null, settings, null, any(), null) }
        }
    }

    private fun openEntry(fromOnboarding: Boolean) {
        composeRule.activityRule.scenario.onActivity {
            if (!fromOnboarding) ImportBackupDialog.showDialog()
            installHost(it, fromOnboarding)
        }
    }

    private fun installHost(activity: ComponentActivity, fromOnboarding: Boolean) {
        val host = ComposeView(activity).apply {
            id = android.R.id.custom
            setContent {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    if (fromOnboarding) {
                        OnboardingImportScreen(onComplete = { onboardingCompletions++ })
                    } else {
                        ImportBackupDialog.Render()
                    }
                }
            }
        }
        activity.setContentView(host)
    }

    private fun option(content: BackupContent) =
        composeRule.onNodeWithText(context.getString(content.labelRes))

    private fun submit(fromOnboarding: Boolean) {
        val action = composeRule.onNodeWithText(context.getString(R.string.common_action_import))
        if (fromOnboarding) action.performScrollTo()
        action.performClick()
    }

    private fun returnPickerResult(expectedMime: String, uri: Uri?) {
        val shadow = shadowOf(composeRule.activity)
        composeRule.waitUntil(10_000) { shadow.peekNextStartedActivityForResult() != null }
        composeRule.activityRule.scenario.onActivity { activity ->
            val request = checkNotNull(shadow.nextStartedActivityForResult)
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
            assertTrue(expectedMime in request.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES).orEmpty())
            val result = if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK
            assertTrue(activity.activityResultRegistry.dispatchResult(request.requestCode, result, uri?.let {
                Intent().setData(it)
            }))
        }
    }

    private fun waitForTarget() {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(targetTitle).assertIsDisplayed()
    }

    private fun targetSubmit() = composeRule.onNode(
        hasText(context.getString(R.string.common_action_import)) and hasAnyAncestor(
            hasTestTag("dialogFrameFooter") and hasAnySibling(
                hasTestTag("dialogFrameHeader") and hasAnyDescendant(hasText(targetTitle)),
            ),
        ),
    )

    private fun assertListenerSelected() {
        composeRule.onNode(
            isSelected() and hasAnySibling(hasText("Listener")),
            useUnmergedTree = true,
        ).assertExists()
    }
}
