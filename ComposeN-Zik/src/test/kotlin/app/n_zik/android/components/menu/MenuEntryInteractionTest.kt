package app.n_zik.android.components.menu

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.Sort
import app.n_zik.android.components.menu.playlist.CollectionImportStatus
import app.n_zik.android.components.menu.playlist.rememberCollectionAddAction
import app.n_zik.android.components.tab.SmartShuffle
import app.n_zik.android.components.tab.StatefulMenuIcon
import app.n_zik.android.components.ui.app.MenuStackContent
import app.n_zik.android.components.ui.screens.player.Repeat
import app.n_zik.android.enums.QueueLoopType
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.PlaylistSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.enums.TransitionEffect
import app.n_zik.android.ui.components.LocalMenuBack
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.queueLoopTypeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class MenuEntryInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `no transition replaces a submenu page without retaining the outgoing page`() {
        val menu = MenuState().apply { display { Text("Parent menu") } }
        composeRule.setContent { MenuStackContent(menu, TransitionEffect.None) }
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle {
            menu.display { Text("Child menu") }
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(64)

        composeRule.onNodeWithText("Child menu", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Parent menu", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `sort toolbar names the action that opens criteria`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val menu = MenuState()
        val sort = Sort(menu, mutableStateOf(PlaylistSortBy.Name), mutableStateOf(SortOrder.Ascending), mutableStateOf(MenuStyle.List))
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) { sort.ToolBarButton() }
        }

        val criterion = context.getString(R.string.library_sort_action_choose_criterion)
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_sort_order_title))
            .assert(SemanticsMatcher("sort criteria long press") { node ->
                node.config.getOrNull(SemanticsActions.OnLongClick)?.label == criterion
            })
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.runOnIdle { assertTrue(menu.isDisplayed) }
    }

    @Test
    fun `outgoing menu action is unavailable while the new page enters and restored on back`() {
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        val menuState = MenuState()
        var opened = 0
        menuState.display {
            ListMenu.Entry("Information", icon = { Spacer(Modifier.size(32.dp)) }, onClick = {
                opened++
                menuState.display {
                    ListMenu.Entry("Back", icon = { Spacer(Modifier.size(32.dp)) }, onClick = LocalMenuBack.current)
                }
            })
        }

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.testTag("menu-stack")) {
                    MenuStackContent(menuState, TransitionEffect.Fade)
                }
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Information").performClick()
        composeRule.runOnIdle { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeBy(64)
        val inaccessible = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
        composeRule.onNode(
            hasText("Information") and hasAnyAncestor(inaccessible),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag("menu-stack").performTouchInput {
            click(Offset(width * 0.1f, centerY))
        }
        composeRule.runOnIdle { assertEquals(1, opened) }

        composeRule.onNodeWithText("Back").performClick()
        composeRule.runOnIdle { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("Information").performClick()
        composeRule.runOnIdle { assertEquals(2, opened) }
    }

    @Test
    fun `list and grid expose their menu title and section headings`() {
        val useGrid = mutableStateOf(false)
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                if (useGrid.value) {
                    GridMenu.Menu(title = "Track options", showDragHandle = false) {
                        item { Text("Playback", modifier = Modifier.menuSectionHeading()) }
                    }
                } else {
                    ListMenu.Menu(title = "Track options", showDragHandle = false) {
                        Text("Playback", modifier = Modifier.menuSectionHeading())
                    }
                }
            }
        }

        val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        for (grid in listOf(false, true)) {
            composeRule.runOnIdle { useGrid.value = grid }
            composeRule.onNodeWithText("Track options").assert(isHeading)
            composeRule.onNodeWithText("Playback").assert(isHeading)
        }
    }

    @Test
    fun `conditional list content loses interaction before its exit finishes`() {
        val visible = mutableStateOf(true)
        var clicks = 0
        composeRule.setContent {
            Box(Modifier.testTag("menu")) {
                AnimatedMenuContent(visible = visible.value) {
                    Text("Opacity", modifier = Modifier.size(80.dp, 48.dp).clickable { clicks++ })
                }
            }
        }

        val root = composeRule.onNodeWithTag("menu")
        val itemCenter = composeRule.onNodeWithText("Opacity").fetchSemanticsNode().boundsInRoot.center -
            root.fetchSemanticsNode().boundsInRoot.topLeft
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            visible.value = false
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeBy(48)

        val inaccessible = SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility)
        composeRule.onNode(hasText("Opacity") and hasAnyAncestor(inaccessible), useUnmergedTree = true).assertExists()
        root.performTouchInput { click(itemCenter) }
        composeRule.runOnIdle { assertEquals(0, clicks) }

        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("Opacity", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `conditional grid cell closes its gap while neighboring items retain their identity`() {
        val visible = mutableStateOf(true)
        composeRule.setContent {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.size(200.dp, 120.dp)) {
                if (visible.value) {
                    animatedMenuItem(1) { Spacer(Modifier.size(48.dp).testTag("optional")) }
                }
                animatedMenuItem(2) { Spacer(Modifier.size(48.dp).testTag("first")) }
                animatedMenuItem(3) { Spacer(Modifier.size(48.dp).testTag("second")) }
            }
        }

        val first = composeRule.onNodeWithTag("first")
        val second = composeRule.onNodeWithTag("second")
        val firstId = first.fetchSemanticsNode().id
        val secondId = second.fetchSemanticsNode().id
        assertTrue(first.fetchSemanticsNode().boundsInRoot.top < second.fetchSemanticsNode().boundsInRoot.top)

        composeRule.runOnIdle { visible.value = false }

        composeRule.onNodeWithTag("optional").assertDoesNotExist()
        assertEquals(firstId, first.fetchSemanticsNode().id)
        assertEquals(secondId, second.fetchSemanticsNode().id)
        assertEquals(first.fetchSemanticsNode().boundsInRoot.top, second.fetchSemanticsNode().boundsInRoot.top, .5f)
    }

    @Test
    fun `list and grid expose toggle state and keep informational rows passive`() {
        val useGrid = mutableStateOf(false)
        val checked = mutableStateOf(false)
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val toggle = Modifier.menuToggleSemantics(checked.value)
                val toggleClick = { checked.value = !checked.value }
                if (useGrid.value) {
                    GridMenu.Menu(showDragHandle = false) {
                        item { GridMenu.Entry("Listener", icon = {}) }
                        item {
                            GridMenu.Entry("Sync volume", icon = {}, modifier = toggle, onClick = toggleClick)
                        }
                    }
                } else {
                    ListMenu.Menu(showDragHandle = false) {
                        ListMenu.Entry("Listener", icon = {})
                        ListMenu.Entry("Sync volume", icon = {}, modifier = toggle, onClick = toggleClick)
                    }
                }
            }
        }

        val toggle = composeRule.onNodeWithText("Sync volume")
        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        val noClick = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)
        for (grid in listOf(false, true)) {
            composeRule.runOnIdle {
                useGrid.value = grid
                checked.value = false
            }
            composeRule.onNodeWithText("Listener").assert(noClick)
            toggle.assert(switchRole).assertIsOff().performClick().assertIsOn()
        }
    }

    @Test
    fun `shared menu icons preserve opted in toggle states and long press labels`() {
        val useGrid = mutableStateOf(false)
        val checked = mutableStateOf(false)
        var longPressCount = 0
        val toggle = StatefulMenuIcon(
            menuState = MenuState(),
            messageId = R.string.sync_action_sync,
            readActive = { checked.value },
            readIcon = { R.drawable.sync },
            readTitle = { R.string.sync_action_sync },
            performAction = { checked.value = !checked.value },
        ).withMenuClick { checked.value = !checked.value }
        val action = object : MenuIcon {
            override val iconId: Int = R.drawable.sync
            @get:Composable
            override val menuIconTitle: String get() = "Sync likes"
            override val menuLongClick: () -> Unit get() = { longPressCount++ }
            @get:Composable
            override val menuLongClickLabel: String get() = "Remove from favorites"
            override fun onShortClick() = Unit
        }
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                if (useGrid.value) {
                    GridMenu.Menu(showDragHandle = false) {
                        item { toggle.GridMenuItem() }
                        item { action.GridMenuItem() }
                    }
                } else {
                    ListMenu.Menu(showDragHandle = false) {
                        toggle.ListMenuItem()
                        action.ListMenuItem()
                    }
                }
            }
        }

        val switch = composeRule.onNodeWithText("Sync")
        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        val noToggleState = SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState)
        val removesFavorites = SemanticsMatcher("Remove from favorites long press label") {
            it.config.getOrNull(SemanticsActions.OnLongClick)?.label == "Remove from favorites"
        }
        for (grid in listOf(false, true)) {
            composeRule.runOnIdle {
                useGrid.value = grid
                checked.value = false
            }
            composeRule.onNodeWithText("Sync likes")
                .assert(noToggleState)
                .assert(removesFavorites)
                .performSemanticsAction(SemanticsActions.OnLongClick)
            switch.assert(switchRole).assertIsOff().performClick().assertIsOn()
        }
        assertEquals(2, longPressCount)
    }

    @Test
    fun `smart shuffle shows checked state and cannot toggle while loading in either menu`() {
        val useGrid = mutableStateOf(false)
        val active = mutableStateOf(false)
        val loading = mutableStateOf(false)
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        lateinit var toggle: SmartShuffle
        lateinit var label: String

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val action = SmartShuffle(
                    isRecommendationEnabled = { active.value },
                    isRecommendationsLoading = { loading.value },
                    onToggleRecommendation = { active.value = !active.value },
                )
                val title = action.menuIconTitle
                SideEffect {
                    toggle = action
                    label = title
                }
                if (useGrid.value) {
                    GridMenu.Menu(showDragHandle = false) { item { action.GridMenuItem() } }
                } else {
                    ListMenu.Menu(showDragHandle = false) { action.ListMenuItem() }
                }
            }
        }

        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        for (grid in listOf(false, true)) {
            composeRule.runOnIdle {
                useGrid.value = grid
                active.value = false
                loading.value = false
            }
            val row = composeRule.onNodeWithText(label)
            row.assert(switchRole).assertIsOff().performClick().assertIsOn()
            composeRule.runOnIdle { loading.value = true }
            row.assertIsNotEnabled().assertIsOn()
            composeRule.runOnIdle {
                toggle.onShortClick()
                assertTrue(active.value)
            }
        }
    }

    @Test
    fun `repeat announces each of its three modes in either menu`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.preferences.edit().putString(queueLoopTypeKey, QueueLoopType.Default.name).apply()
        val useGrid = mutableStateOf(false)
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val repeat = Repeat.init()
                if (useGrid.value) {
                    GridMenu.Menu(showDragHandle = false) { item { repeat.GridMenuItem() } }
                } else {
                    ListMenu.Menu(showDragHandle = false) { repeat.ListMenuItem() }
                }
            }
        }

        fun state(resId: Int) = SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            context.getString(resId),
        )
        val row = composeRule.onNodeWithText(context.getString(R.string.player_action_repeat))
        for (grid in listOf(false, true)) {
            composeRule.runOnIdle { useGrid.value = grid }
            row.assert(state(R.string.player_repeat_mode_off)).performClick()
            row.assert(state(R.string.player_repeat_mode_one)).performClick()
            row.assert(state(R.string.player_repeat_mode_all)).performClick()
            row.assert(state(R.string.player_repeat_mode_off))
        }
    }

    @Test
    fun `retained playlist action enables only when a choice is available`() {
        val addAvailable = mutableStateOf(false)
        val importStatus = mutableStateOf(CollectionImportStatus.Unavailable)
        val exportVisible = mutableStateOf(false)
        val exportSupported = mutableStateOf(false)
        val menuState = MenuState()
        var initialAction: MenuIcon? = null

        composeRule.setContent {
            val action = rememberCollectionAddAction(
                sourceId = "collection",
                menuState = menuState,
                canImportCollection = importStatus.value.canOpenImport,
                hasTracks = addAvailable.value,
                openImport = {},
                openPicker = {},
                canExport = exportVisible.value,
                openExport = if (exportSupported.value) ({}) else null,
            )
            SideEffect { if (initialAction == null) initialAction = action }
        }

        fun checkAvailability(expected: Boolean) {
            composeRule.runOnIdle { assertEquals(expected, checkNotNull(initialAction).isEnabled) }
        }
        checkAvailability(false)
        composeRule.runOnIdle { importStatus.value = CollectionImportStatus.Loading }
        checkAvailability(true)
        composeRule.runOnIdle {
            importStatus.value = CollectionImportStatus.Empty
            exportVisible.value = true
        }
        checkAvailability(false)
        composeRule.runOnIdle { exportSupported.value = true }
        checkAvailability(true)
        composeRule.runOnIdle {
            exportVisible.value = false
            addAvailable.value = true
        }
        checkAvailability(true)
        composeRule.runOnIdle { addAvailable.value = false }
        checkAvailability(false)
    }
}
