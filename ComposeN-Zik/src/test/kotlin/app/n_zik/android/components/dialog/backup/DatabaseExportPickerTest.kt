package app.n_zik.android.components.dialog.backup

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.n_zik.android.components.dialog.export.rememberDatabaseExport
import app.n_zik.android.core.backup.DatabaseBackup
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DatabaseExportPickerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `ZIP export requests a ZIP document and writes the prepared bytes`() {
        val activity = composeRule.activity
        val stage = File(activity.cacheDir, "database-backup-picker-test.stage")
        check(stage.mkdir())
        val payload = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 1, 2, 3)
        val stagedFile = stage.resolve("backup.zip").apply { writeBytes(payload) }
        val output = File.createTempFile("backup-picker-", ".zip", activity.cacheDir)
        try {
            composeRule.setContent {
                val export = rememberDatabaseExport(
                    prepare = { DatabaseBackup.Export(stagedFile, DatabaseBackup.Format.ZIP) },
                    context = activity,
                    onResult = { it.getOrThrow() },
                )
                Button(onClick = export.launch) { Text("Export database") }
            }
            composeRule.onNodeWithText("Export database").performClick()

            val shadow = Shadows.shadowOf(activity)
            composeRule.waitUntil(10_000) { shadow.peekNextStartedActivityForResult() != null }
            val picker = checkNotNull(shadow.peekNextStartedActivityForResult()?.intent)
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
            assertEquals("application/zip", picker.type)
            assertTrue(picker.getStringExtra(Intent.EXTRA_TITLE).orEmpty().endsWith(".zip"))

            composeRule.runOnIdle {
                shadow.receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(output)))
            }
            composeRule.waitUntil(10_000) { !stage.exists() }
            assertArrayEquals(payload, output.readBytes())
        } finally {
            stage.deleteRecursively()
            output.delete()
        }
    }
}
