package app.n_zik.android.components.dialog.updater

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.updater.models.GithubRelease
import app.n_zik.android.updater.services.Updater
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for the landscape width cap on [NewUpdateAvailableDialog].
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 *
 * The dialog's root column is capped at 420.dp: on wide screens (landscape phones,
 * portrait tablets) the column must not span the full window, while narrow portrait
 * phones (< 420.dp) keep filling the width.
 * This ui-test version has no `assertWidthIsAtMost`, so the width is asserted through
 * [getBoundsInRoot] on the tagged column.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NewUpdateAvailableDialogLandscapeWidthTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    // Object state saved so @After can restore it: it persists across test classes
    // in the same Gradle test worker
    private var hadBuild = false
    private var savedBuild: GithubRelease.Build? = null
    private var savedCancelled = false

    @Before
    fun setUp() {
        // Render() reads Updater.build.readableSize -> appContext(): stub the service
        // locator with the repo convention (MediaItemUtilsTest) — a mockk'd MainApplication.
        // The running app must stay the plain Application (MainApplication.onCreate touches
        // the AndroidKeyStore, absent on the JVM), so the stub only needs applicationContext.
        val appContext = ApplicationProvider.getApplicationContext<Application>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns appContext
        Dependencies.init(mainApplication)
        // `Updater.build` is lateinit: probe it instead of the lateinit intrinsic
        // (`Updater::build.isInitialized` does not resolve from the test compilation)
        hadBuild = try {
            savedBuild = Updater.build
            true
        } catch (_: UninitializedPropertyAccessException) {
            false
        }
        savedCancelled = NewUpdateAvailableDialog.isCancelled
        Updater.build = GithubRelease.Build(
            id = 1u,
            url = "https://example.com/N-Zik.apk",
            name = "N-Zik.apk",
            size = 1048576u,
            createdAt = "2026-01-01T00:00:00Z",
            downloadUrl = "https://example.com/download/N-Zik.apk",
        )
        NewUpdateAvailableDialog.isCancelled = false
        NewUpdateAvailableDialog.isActive = true
    }

    @After
    fun tearDown() {
        NewUpdateAvailableDialog.isActive = false
        NewUpdateAvailableDialog.isCancelled = savedCancelled
        // Updater.build is lateinit and cannot be unset; restore the previous value when
        // one existed (a fresh test worker always starts uninitialized)
        if (hadBuild) savedBuild?.let { Updater.build = it }
    }

    @Config(qualifiers = "w900dp-h500dp")
    @Test
    fun `dialog column is capped at 420dp in landscape`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                NewUpdateAvailableDialog.Render()
            }
        }
        composeRule.waitForIdle()

        // DpRect in this ui-unit version exposes left/right only, no width property
        val bounds = composeRule.onNodeWithTag("updateDialogColumn").getBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue(
            "dialog column must be capped at 420.dp in landscape, was $width",
            width <= 420.dp,
        )
    }

    @Config(qualifiers = "w393dp-h851dp")
    @Test
    fun `dialog column still fills the width in portrait`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                NewUpdateAvailableDialog.Render()
            }
        }
        composeRule.waitForIdle()

        // DpRect in this ui-unit version exposes left/right only, no width property
        val bounds = composeRule.onNodeWithTag("updateDialogColumn").getBoundsInRoot()
        val width = bounds.right - bounds.left
        // 393.dp screen: with fillMaxWidth() the column fills the window, so it stays
        // within a few dp of the screen width; the 3.dp allowance absorbs the dialog
        // window's inset. A content-width column (fillMaxWidth removed) would be far smaller.
        assertTrue(
            "dialog column should still fill the portrait width, was $width",
            width >= 390.dp,
        )
    }
}
