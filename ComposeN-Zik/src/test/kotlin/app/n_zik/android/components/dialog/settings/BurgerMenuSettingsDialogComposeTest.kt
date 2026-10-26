package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavior of the burger menu customizer (spec-customizable-burger-menu): a confirmed toggle
 * and the full order persist to the global `app_settings` store (not the profile-scoped
 * "preferences" file), the "Add separator" footer button persists an extra separator
 * instance (G5 renegotiated), two stored separator instances render numbered per-instance
 * labels, the order parser keeps a stored prefix in place and appends every missing id at
 * the END in default order (not in its default slot), cancel leaves the store untouched,
 * and reset restores the default order with every toggle on.
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine on the JUnit 5
 * platform (`createComposeRule()` only works with JUnit 4), with the plain [Application] so the
 * app's heavy init (DI, Room, player) is skipped. Rows past the LazyColumn viewport are not
 * composed under Robolectric, so the assertions stick to the top rows and the buttons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class BurgerMenuSettingsDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val dataStore: SharedPreferences get() =
        app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before
    fun setUp() {
        // MainApplication is final, so Dependencies.application is a mock wired to this
        // Robolectric app: the confirm toast (Toaster) resolves its icon through the
        // service locator, which the plain test Application never initializes.
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    @After
    fun tearDown() {
        BurgerMenuSettingsDialog.hideDialog()
        unmockkAll()
        dataStore.edit()
            .remove(DataStoreUtils.KEY_BURGER_MENU_ORDER)
            .also { edit ->
                DataStoreUtils.DEFAULT_BURGER_MENU_ORDER.forEach { id ->
                    edit.remove(DataStoreUtils.burgerMenuToggleKey(id))
                }
            }
            .commit()
    }

    private fun content(block: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                block()
            }
        }
    }

    private fun storedOrder(): List<String> {
        val raw = dataStore.getString(DataStoreUtils.KEY_BURGER_MENU_ORDER, null)
        assertTrue("the order should be persisted on confirm", raw != null)
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getString(it) }
    }

    @Test
    fun resetAndConfirmReceivePhysicalTapsInsideTheCompleteDialog() {
        dataStore.edit().putBoolean(DataStoreUtils.burgerMenuToggleKey("rewind"), false).commit()
        BurgerMenuSettingsDialog.showDialog()
        content { BurgerMenuSettingsDialog.Render() }
        composeRule.onNodeWithText(app.getString(R.string.reset)).assertIsDisplayed().performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(app.getString(R.string.ok)).assertIsDisplayed().performTouchInput { click() }
        composeRule.waitForIdle()
        assertFalse(BurgerMenuSettingsDialog.isActive)
        assertTrue(dataStore.getBoolean(DataStoreUtils.burgerMenuToggleKey("rewind"), false))
        assertEquals(DataStoreUtils.DEFAULT_BURGER_MENU_ORDER, storedOrder())
    }

    @Test
    fun onlySeparatorRowsExposeTheRemoveButton() {
        dataStore.edit().putString(
            DataStoreUtils.KEY_BURGER_MENU_ORDER,
            BurgerMenuSettingsDialog.serializeOrder(
                listOf("separator", "separator") +
                    DataStoreUtils.DEFAULT_BURGER_MENU_ORDER.filter { it != "separator" }
            ),
        ).commit()
        content { BurgerMenuSettingsDialog.DialogBody() }
        val removeButtons = composeRule.onAllNodesWithContentDescription(app.getString(R.string.delete))
        removeButtons.assertCountEquals(2)
        removeButtons[0].performClick()
        repeat(30) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        removeButtons.assertCountEquals(1)
        composeRule.onNodeWithText(app.getString(R.string.ok)).performClick()
        composeRule.waitForIdle()
        assertEquals(1, storedOrder().count { it == "separator" })
        assertTrue(storedOrder().containsAll(DataStoreUtils.DEFAULT_BURGER_MENU_ORDER))
    }

    @Test
    fun confirmPersistsTheToggleOffAndTheFullOrderToTheGlobalStore() {
        content { BurgerMenuSettingsDialog.DialogBody() }

        composeRule.onNodeWithText(app.getString(R.string.rewind)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.rewind)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(app.getString(R.string.ok)).performClick()
        composeRule.waitForIdle()

        assertFalse(
            "the stored rewind toggle should be off",
            dataStore.getBoolean(DataStoreUtils.burgerMenuToggleKey("rewind"), true)
        )
        assertEquals(
            DataStoreUtils.DEFAULT_BURGER_MENU_ORDER,
            storedOrder()
        )
        assertTrue(
            "the untouched toggles keep the store default",
            dataStore.getBoolean(DataStoreUtils.burgerMenuToggleKey("history"), true)
        )
    }

    @Test
    fun cancelLeavesTheGlobalStoreUntouched() {
        content { BurgerMenuSettingsDialog.DialogBody() }

        composeRule.onNodeWithText(app.getString(R.string.rewind)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(app.getString(R.string.cancel)).performClick()
        composeRule.waitForIdle()

        assertFalse(dataStore.contains(DataStoreUtils.KEY_BURGER_MENU_ORDER))
        assertFalse(dataStore.contains(DataStoreUtils.burgerMenuToggleKey("rewind")))
    }

    @Test
    fun addSeparatorButtonPersistsAnExtraSeparatorInstance() {
        content { BurgerMenuSettingsDialog.DialogBody() }

        composeRule.onNodeWithText(app.getString(R.string.add_separator)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.add_separator)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(app.getString(R.string.ok)).performClick()
        composeRule.waitForIdle()

        // G5 renegotiated: the default instance plus the one added through the footer button
        assertEquals(2, storedOrder().count { it == "separator" })
    }

    @Test
    fun resetRestoresTheDefaultOrderAndEveryToggleOn() {
        dataStore.edit()
            .putString(
                DataStoreUtils.KEY_BURGER_MENU_ORDER,
                BurgerMenuSettingsDialog.serializeOrder(
                    listOf("rescue_center") + DataStoreUtils.DEFAULT_BURGER_MENU_ORDER.filter { it != "rescue_center" }
                )
            )
            .putBoolean(DataStoreUtils.burgerMenuToggleKey("extensions"), false)
            .commit()
        content { BurgerMenuSettingsDialog.DialogBody() }

        // The stored order is visible in the first row
        composeRule.onNodeWithText(app.getString(R.string.rescue_center)).assertIsDisplayed()

        composeRule.onNodeWithText(app.getString(R.string.reset)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(app.getString(R.string.ok)).performClick()
        composeRule.waitForIdle()

        assertEquals(DataStoreUtils.DEFAULT_BURGER_MENU_ORDER, storedOrder())
        assertTrue(
            "the reset flips the stored extensions toggle back on",
            dataStore.getBoolean(DataStoreUtils.burgerMenuToggleKey("extensions"), false)
        )
    }

    @Test
    fun twoStoredSeparatorInstancesRenderNumberedPerInstanceLabels() {
        // G5 renegotiated: more than one separator instance must be distinguishable in the
        // list, so each one gets its own "Separator (n)" label in stored order.
        dataStore.edit()
            .putString(
                DataStoreUtils.KEY_BURGER_MENU_ORDER,
                BurgerMenuSettingsDialog.serializeOrder(
                    listOf("separator", "separator") +
                        DataStoreUtils.DEFAULT_BURGER_MENU_ORDER.filter { it != "separator" }
                )
            )
            .commit()
        content { BurgerMenuSettingsDialog.DialogBody() }

        // The two separators sit at the top of the list, so their rows are composed
        // (rows past the LazyColumn viewport are not, see the class note).
        composeRule.onNodeWithText(app.getString(R.string.burger_menu_separator_instance, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.burger_menu_separator_instance, 2)).assertIsDisplayed()
    }

    @Test
    fun theOrderParserKeepsTheStoredPrefixAndAppendsMissingIdsAtTheEnd() {
        // G6: a stored prefix stays in place; every missing id is appended at the END of
        // the list in its default position relative to the other missing ids - "statistics"
        // lands after the prefix, not in its default slot (index 1).
        val stored = listOf("rescue_center", "history", "separator")
        val parsed = BurgerMenuSettingsDialog.parseOrder(BurgerMenuSettingsDialog.serializeOrder(stored))

        val missingInDefaultOrder = DataStoreUtils.DEFAULT_BURGER_MENU_ORDER.filter { it !in stored }
        assertEquals(
            "stored prefix + missing ids appended at the end in default order",
            stored + missingInDefaultOrder,
            parsed
        )
        assertEquals("statistics must land at index 3, not its default slot 1", "statistics", parsed[3])
    }
}
