package app.n_zik.android.components.dialog.backup

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.dialog.export.ExportBackupDialog
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Backup choices stay in the body while the single terminal action belongs to the fixed footer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class BackupDialogFooterTest {
    @get:Rule val composeRule = createComposeRule()
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
            hasText(context.getString(R.string.export)) and
                hasAnyAncestor(hasTestTag("dialogFrameFooter")),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onNodeWithText(context.getString(R.string.settings)).performClick()
        composeRule.onNode(
            hasText(context.getString(R.string.include_youtube_credentials)) and
                hasAnyAncestor(hasTestTag("dialogFrameBody")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag("dialogFrameFooter"), useUnmergedTree = true)
            .assertCountEquals(1)
    }
}
