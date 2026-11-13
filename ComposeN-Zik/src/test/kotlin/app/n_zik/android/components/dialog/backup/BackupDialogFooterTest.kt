package app.n_zik.android.components.dialog.backup

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.dialog.export.ExportBackupDialog
import app.n_zik.android.core.backup.DatabaseBackup
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** Backup choices stay in the body while the single terminal action belongs to the fixed footer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class BackupDialogFooterTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        ExportBackupDialog.showDialog()
    }

    @After fun tearDown() {
        ExportBackupDialog.hideDialog()
    }

    @Test fun `export action has one footer and credentials remain in the body`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ExportBackupDialog.Render()
            }
        }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onAllNodes(
            hasText(context.getString(R.string.common_action_export)) and
                hasAnyAncestor(hasTestTag("dialogFrameFooter")),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithText(context.getString(R.string.settings_title)).performClick()
        composeRule.onNode(
            hasText(context.getString(R.string.backup_include_youtube_credentials)) and
                hasAnyAncestor(hasTestTag("dialogFrameBody")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag("dialogFrameFooter"), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test fun `export all reaches profile state when the settings picker is cancelled`() {
        val stage = File(context.cacheDir, "database-backup-all-test.stage")
        check(stage.mkdir())
        val stagedFile = stage.resolve("backup.zip").apply { writeBytes(byteArrayOf(1)) }
        mockkObject(DatabaseBackup)
        try {
            every { DatabaseBackup.prepareExport(any(), any()) } returns
                DatabaseBackup.Export(stagedFile, DatabaseBackup.Format.ZIP)
            composeRule.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    ExportBackupDialog.Render()
                }
            }
            composeRule.onNodeWithText(context.getString(R.string.backup_export_all))
                .performScrollTo().performClick()
            composeRule.onNode(
                hasText(context.getString(R.string.common_action_export)) and
                    hasAnyAncestor(hasTestTag("dialogFrameFooter")),
                useUnmergedTree = true,
            ).performClick()

            val shadow = Shadows.shadowOf(composeRule.activity)
            for (mime in listOf("application/zip", "text/csv", "text/plain")) {
                composeRule.waitUntil(10_000) { shadow.peekNextStartedActivityForResult() != null }
                composeRule.runOnIdle {
                    val intent = checkNotNull(shadow.nextStartedActivityForResult?.intent)
                    assertEquals(mime, intent.type)
                    shadow.receiveResult(intent, Activity.RESULT_CANCELED, null)
                }
            }
            composeRule.waitUntil(10_000) { !stage.exists() }
        } finally {
            unmockkObject(DatabaseBackup)
            stage.deleteRecursively()
        }
    }

    @Test fun `recreated settings activity completes the outstanding ZIP picker with the staged bytes`() {
        val stage = File(context.cacheDir, "database-backup-recreation-test.stage")
        check(stage.mkdir())
        val payload = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 1, 2, 3)
        val stagedFile = stage.resolve("backup.zip").apply { writeBytes(payload) }
        val output = File.createTempFile("backup-recreation-", ".zip", context.cacheDir)
        mockkObject(DatabaseBackup)
        try {
            every { DatabaseBackup.prepareExport(any(), any()) } returns
                DatabaseBackup.Export(stagedFile, DatabaseBackup.Format.ZIP)
            composeRule.activityRule.scenario.onActivity(::installRecreationHost)
            composeRule.onNodeWithText(context.getString(R.string.common_action_export)).performClick()
            val shadow = Shadows.shadowOf(composeRule.activity)
            composeRule.waitUntil(10_000) { shadow.peekNextStartedActivityForResult() != null }
            val launch = checkNotNull(shadow.nextStartedActivityForResult)
            assertEquals("application/zip", launch.intent.type)

            composeRule.activityRule.scenario.recreate()
            composeRule.activityRule.scenario.onActivity(::installRecreationHost)
            composeRule.waitForIdle()
            composeRule.onAllNodes(isDialog()).assertCountEquals(1)
            composeRule.activityRule.scenario.onActivity { recreated ->
                assertTrue(recreated.activityResultRegistry.dispatchResult(
                    launch.requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(output)),
                ))
            }
            composeRule.waitUntil(10_000) { !stage.exists() }
            assertArrayEquals(payload, output.readBytes())
        } finally {
            unmockkObject(DatabaseBackup)
            stage.deleteRecursively()
            output.delete()
        }
    }

    private fun installRecreationHost(activity: ComponentActivity) {
        val host = ComposeView(activity).apply {
            id = android.R.id.custom
            setContent {
                CompositionLocalProvider(LocalAppearance provides appearance) { ExportBackupDialog.Render() }
            }
        }
        activity.setContentView(host)
    }
}
