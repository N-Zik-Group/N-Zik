package app.n_zik.android.components.tab

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.ui.components.navigation.nav.AbstractNavigationBar
import app.it.fast4x.rimusic.ui.components.navigation.nav.HorizontalNavigationBar
import app.it.fast4x.rimusic.ui.components.navigation.nav.VerticalNavigationBar
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.BuildConfig
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.updater.models.CheckUpdateState
import app.n_zik.android.updater.models.MajorUpdateConfig
import app.it.fast4x.rimusic.utils.checkUpdateStateKey
import app.it.fast4x.rimusic.utils.lastBuildTypeKey
import app.it.fast4x.rimusic.utils.lastVersionCodeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.seenChangelogsVersionKey
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NavigationButtonRecompositionTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private var previousProfile: String? = null

    @Before
    fun isolatePreferences() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("navigation_recomposition_test", context)
        context.preferences.edit().clear().commit()
    }

    @After
    fun restorePreferences() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `horizontal content recomposition keeps one slot per tab`() = verifyRegistration(horizontal = true)

    @Test
    fun `vertical content recomposition keeps one slot per tab`() = verifyRegistration(horizontal = false)

    @Test
    fun `Skeleton renders navigation immediately and keeps latest selection callback`() {
        context.preferences.edit()
            .putString(navigationBarPositionKey, NavigationBarPosition.Bottom.name)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name)
            .putString(checkUpdateStateKey, CheckUpdateState.Disabled.name)
            .putInt(lastVersionCodeKey, BuildConfig.VERSION_CODE)
            .putString(lastBuildTypeKey, MajorUpdateConfig.getCurrentBuildType())
            .putString(seenChangelogsVersionKey, BuildConfig.VERSION_NAME)
            .commit()
        val selected = mutableIntStateOf(0)
        val callbackRevision = mutableIntStateOf(0)
        val tabOrder = mutableStateOf(listOf("songs", "albums", "artists"))
        var deliveredRevision = -1
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = Typography.fallback,
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.setContent {
            val navController = remember { NavController(context) }
            val revision = callbackRevision.intValue
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
            ) {
                Skeleton(
                    navController = navController,
                    tabIndex = selected.intValue,
                    onTabChanged = {
                        deliveredRevision = revision
                        selected.intValue = it
                    },
                    navBarContent = { emit ->
                        // Home emits from distinct branches, not one shared loop callsite.
                        tabOrder.value.forEachIndexed { index, id ->
                            when (id) {
                                "songs" -> emit(index, "Nav songs", R.drawable.musical_notes)
                                "albums" -> emit(index, "Nav albums", R.drawable.musical_notes)
                                "artists" -> emit(index, "Nav artists", R.drawable.musical_notes)
                            }
                        }
                    },
                ) { BasicText("Selected $it") }
            }
        }
        composeRule.onNodeWithText("Nav songs").assertIsDisplayed()
        composeRule.onNodeWithText("Nav albums").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Selected 1").assertExists()
        composeRule.runOnIdle { callbackRevision.intValue = 1 }
        composeRule.onNodeWithText("Nav songs").performClick()
        composeRule.onNodeWithText("Selected 0").assertExists()
        composeRule.runOnIdle { assertEquals(1, deliveredRevision) }
        composeRule.runOnIdle { tabOrder.value = listOf("artists", "albums", "songs") }
        assertVisualTabOrder("Nav artists", "Nav albums", "Nav songs")
        composeRule.runOnIdle { tabOrder.value = listOf("songs", "albums", "artists") }
        assertVisualTabOrder("Nav songs", "Nav albums", "Nav artists")
    }

    private fun assertVisualTabOrder(vararg labels: String) {
        val positions = labels.map { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left
        }
        assertTrue("Expected visual order ${labels.toList()}, got x positions $positions",
            positions.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun `slot replacement updates already rendered content`() {
        val slots = NavigationButtonSlots<String>()
        slots[1] = "Songs"
        composeRule.setContent { DrawSlotLabels(slots) }
        composeRule.onNodeWithText("Songs").assertExists()
        composeRule.runOnIdle { slots[1] = "Updated songs" }
        composeRule.onNodeWithText("Updated songs").assertExists()
        composeRule.runOnIdle { assertEquals(1, slots.size) }
    }

    @Test
    fun `composition slots remove hidden tabs and follow navigation index order`() {
        val slots = NavigationButtonSlots<String>()
        val tabs = mutableStateOf(listOf(0, 1, 2, 3, 4))
        composeRule.setContent {
            tabs.value.forEach { slots.register(it, "Tab $it") }
            DrawSlotLabels(slots)
        }
        composeRule.runOnIdle {
            assertEquals(listOf("Tab 0", "Tab 1", "Tab 2", "Tab 3", "Tab 4"), slots.toList())
            tabs.value = listOf(4, 1)
        }
        composeRule.onNodeWithText("Tab 0").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf("Tab 1", "Tab 4"), slots.toList())
            tabs.value = listOf(1, 0, 4)
        }
        composeRule.onNodeWithText("Tab 0").assertExists()
        composeRule.runOnIdle {
            assertEquals(listOf("Tab 0", "Tab 1", "Tab 4"), slots.toList())
        }
    }

    private fun verifyRegistration(horizontal: Boolean) {
        val labelRevision = mutableIntStateOf(0)
        val visibleTabs = mutableStateOf(listOf(0, 1, 2, 3, 4))
        lateinit var bar: AbstractNavigationBar
        var contentPasses = 0
        var outerPasses = 0
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = Typography.fallback,
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.setContent {
            outerPasses++
            val context = LocalContext.current
            bar = remember {
                if (horizontal) HorizontalNavigationBar(1, {}, NavController(context))
                else VerticalNavigationBar(1, {}, NavController(context))
            }
            val tabs = visibleTabs.value
            bar.add { emit ->
                // This read invalidates only the content lambda, not the outer add/clear.
                val revision = labelRevision.intValue
                contentPasses++
                tabs.forEach { index -> emit(index, "Tab $index revision $revision", R.drawable.musical_notes) }
            }
            if (outerPasses == 1) {
                assertEquals("Slots must exist before the first frame and before effects", 5,
                    Snapshot.withoutReadObservation { bar.buttonList.size })
            }
            CompositionLocalProvider(LocalAppearance provides appearance) {
                DrawNavigationSlots(bar.buttonList)
            }
            BasicText("Visible tabs: ${tabs.joinToString()}")
        }
        repeat(3) {
            composeRule.runOnIdle {
                assertEquals(5, bar.buttonList.size)
                labelRevision.intValue++
            }
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle {
            assertEquals(4, contentPasses)
            assertEquals("Child recomposition must leave the outer registration scope untouched", 1, outerPasses)
            assertEquals(5, bar.buttonList.size)
            visibleTabs.value = listOf(4, 1)
        }
        composeRule.onNodeWithText("Visible tabs: 4, 1").assertExists()
        composeRule.runOnIdle {
            assertEquals("Changing visible tabs must recompose the outer registration scope", 2, outerPasses)
            assertEquals(2, bar.buttonList.size)
        }
    }
}

@Composable
private fun DrawSlotLabels(slots: NavigationButtonSlots<String>) {
    slots.forEach { BasicText(it) }
}

@Composable
private fun DrawNavigationSlots(slots: NavigationButtonSlots<@Composable () -> Unit>) {
    slots.forEach { it() }
}
