package app.n_zik.android.components.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.settings.common.LocalSettingsCardSlots
import app.n_zik.android.components.settings.common.SettingsCardSlot
import app.n_zik.android.components.settings.common.SettingsCardSlots
import app.n_zik.android.components.settings.common.SettingsPinnedFooter
import app.n_zik.android.components.settings.common.SettingsSourceFooter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsCardSlotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `lazy cards retain one source and shared host with current state captures`() {
        val slots = SettingsCardSlots()
        val value = mutableStateOf("before")
        val mounted = mutableSetOf<Int>()
        var sourceInstances = 0
        composeRule.setContent {
            Column {
                CompositionLocalProvider(LocalSettingsCardSlots provides slots) {
                    DisposableEffect(Unit) {
                        sourceInstances++
                        onDispose { }
                    }
                    Text("shared dialog host")
                    val captured = value.value
                    repeat(20) { card ->
                        SettingsCardSlot(card, modifier = { Modifier.height(400.dp) }) {
                            DisposableEffect(card) {
                                mounted.add(card)
                                onDispose { mounted.remove(card) }
                            }
                            Text("card $card: $captured")
                        }
                    }
                }
                LazyColumn(Modifier.height(300.dp).testTag("cards")) {
                    items((0 until 20).toList(), key = { it }) { card ->
                        val content = slots.content[card]
                        if (content != null) content.value()
                        else Spacer(Modifier.height(400.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("shared dialog host").assertCountEquals(1)
        composeRule.onNodeWithText("card 0: before").assertExists()
        composeRule.runOnIdle {
            assertEquals(1, sourceInstances)
            assertFalse("Far cards must not be composed eagerly", 19 in mounted)
            value.value = "after"
        }
        composeRule.onNodeWithText("card 0: after").assertExists()
        composeRule.onNodeWithTag("cards").performScrollToIndex(19)
        composeRule.onNodeWithText("card 19: after").assertExists()
        composeRule.onAllNodesWithText("shared dialog host").assertCountEquals(1)
        composeRule.runOnIdle { assertEquals(1, sourceInstances) }
    }

    @Test fun `legacy source hosts render cards directly without a collector`() {
        composeRule.setContent {
            SettingsCardSlot(1) { Text("direct card") }
        }
        composeRule.onNodeWithText("direct card").assertExists()
    }

    @Test fun `conditional sync slots retain their source position when enabled later`() {
        val slots = SettingsCardSlots()
        val enabled = mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalSettingsCardSlots provides slots) {
                SettingsCardSlot(1, order = 0) { Text("login") }
                if (enabled.value) SettingsCardSlot(2, order = 1) { Text("sync") }
                SettingsCardSlot(3, order = 2) { Text("following card") }
            }
        }
        composeRule.runOnIdle {
            assertEquals(listOf(1, 3), slots.titles.toList())
            enabled.value = true
        }
        composeRule.runOnIdle {
            assertEquals(listOf(1, 2, 3), slots.titles.toList())
            enabled.value = false
        }
        composeRule.runOnIdle { assertEquals(listOf(1, 3), slots.titles.toList()) }
    }

    @Test fun `restart prompt stays accessible after scrolling and releases its inset when dismissed`() {
        val slots = SettingsCardSlots()
        val warning = mutableStateOf(false)
        composeRule.setContent {
            Column(Modifier.height(300.dp)) {
                CompositionLocalProvider(LocalSettingsCardSlots provides slots) {
                    val visible = warning.value
                    SettingsCardSlot(SettingsSourceFooter) {
                        if (visible) Text("restart service")
                    }
                }
                LazyColumn(Modifier.weight(1f).testTag("cards")) {
                    items(20) { Spacer(Modifier.height(400.dp)) }
                }
                SettingsPinnedFooter(40.dp) {
                    slots.content[SettingsSourceFooter]?.value?.invoke()
                }
            }
        }
        composeRule.onNodeWithTag("cards").performScrollToIndex(19)
        composeRule.onNodeWithText("restart service").assertDoesNotExist()
        val initialHeight = composeRule.onNodeWithTag("cards").fetchSemanticsNode().boundsInRoot.height
        composeRule.runOnIdle { warning.value = true }
        composeRule.onNodeWithText("restart service").assertIsDisplayed()
        composeRule.runOnIdle { warning.value = false }
        composeRule.onNodeWithText("restart service").assertDoesNotExist()
        composeRule.waitForIdle()
        assertEquals(initialHeight, composeRule.onNodeWithTag("cards").fetchSemanticsNode().boundsInRoot.height, 0f)
    }
}
