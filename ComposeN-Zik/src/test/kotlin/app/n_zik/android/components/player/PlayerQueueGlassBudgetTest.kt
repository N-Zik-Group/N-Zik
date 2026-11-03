package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.LocalGlassOpticsEnabled
import app.n_zik.android.components.glass.LocalGlassUnderlayCovered
import app.n_zik.android.components.glass.glassOpticsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.QueueType
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum
import app.it.fast4x.rimusic.utils.queueTypeKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PlayerQueueGlassBudgetTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var originalProfile: String

    @Before fun setUp() {
        originalProfile = getActiveProfile(context)
        setActiveProfile("queue-glass-budget-test", context)
        context.preferences.edit().clear().commit()
    }

    @After fun tearDown() {
        context.preferences.edit().clear().commit()
        setActiveProfile(originalProfile, context)
    }

    @Test fun `composable owner follows persisted queue type while visible and preserves modal coverage`() {
        context.preferences.edit().putEnum(queueTypeKey, QueueType.Modern).commit()
        val inherited = mutableStateOf(false)
        val visible = mutableStateOf(true)
        val full = mutableStateOf(false)
        var covered: Boolean? = null
        compose.setContent {
            val value = playerQueueUnderlayCovered(inherited.value, visible.value, full.value)
            SideEffect { covered = value }
        }
        fun assertCovered(expected: Boolean) {
            compose.runOnIdle {
                if (expected) assertTrue("player is covered", covered == true)
                else assertFalse("player stays live", covered != false)
            }
        }
        assertCovered(false)
        compose.runOnIdle {
            context.preferences.edit().putEnum(queueTypeKey, QueueType.Essential).commit()
        }
        assertCovered(false)
        compose.runOnIdle { full.value = true }
        assertCovered(true)
        compose.runOnIdle { full.value = false }
        assertCovered(false)
        compose.runOnIdle {
            context.preferences.edit().putEnum(queueTypeKey, QueueType.Modern).commit()
        }
        assertCovered(false)
        compose.runOnIdle { inherited.value = true }
        assertCovered(true)
        compose.runOnIdle {
            context.preferences.edit().putEnum(queueTypeKey, QueueType.Essential).commit()
            visible.value = false
        }
        assertCovered(true)
        compose.runOnIdle { inherited.value = false }
        assertCovered(false)
    }

    @Test fun `transparent queue keeps the player live from opening until closing finishes`() {
        assertFalse(playerQueueUnderlayCovered(false, false, QueueType.Modern))
        // The owner stays visible through opening, partial expansion, full expansion and closing.
        assertFalse(playerQueueUnderlayCovered(false, true, QueueType.Modern))
        assertFalse(playerQueueUnderlayCovered(false, false, QueueType.Modern))
    }

    @Test fun `opening either queue reduces player optics while visible decorations and foreground stay live`() {
        val visible = mutableStateOf(false)
        val inheritedOptics = mutableStateOf(true)
        val modalCovered = mutableStateOf(false)
        val settings = mutableStateOf(GlassSettings())
        var playerOptics = false
        var queueOptics = false
        var decorationsCovered = true
        compose.setContent {
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings.value) {
                val covered = playerQueueUnderlayCovered(modalCovered.value, visible.value, false)
                CompositionLocalProvider(
                    LocalGlassOpticsEnabled provides playerQueueOpticsEnabled(inheritedOptics.value, visible.value),
                    LocalGlassUnderlayCovered provides covered,
                ) {
                    val player = glassOpticsEnabled()
                    SideEffect { playerOptics = player; decorationsCovered = covered }
                    CompositionLocalProvider(
                        LocalGlassOpticsEnabled provides inheritedOptics.value,
                        LocalGlassUnderlayCovered provides modalCovered.value,
                    ) {
                        val queue = glassOpticsEnabled()
                        SideEffect { queueOptics = queue }
                    }
                }
            }
        }
        for (type in QueueType.entries) {
            compose.runOnIdle { context.preferences.edit().putEnum(queueTypeKey, type).commit() }
            compose.runOnIdle { assertTrue(playerOptics); visible.value = true }
            compose.runOnIdle {
                assertFalse("$type player optics must use the cheap covered-menu budget", playerOptics)
                assertFalse("$type visible player decorations must remain live", decorationsCovered)
                assertTrue("$type foreground queue retains its configured optics", queueOptics)
                settings.value = settings.value.copy(optimizationEnabled = false)
            }
            compose.runOnIdle {
                assertTrue("$type visible player retains optics when optimization is disabled", playerOptics)
                modalCovered.value = true
            }
            compose.runOnIdle {
                assertFalse(playerOptics)
                assertFalse(queueOptics)
                modalCovered.value = false
                settings.value = settings.value.copy(optimizationEnabled = true)
            }
            compose.runOnIdle { visible.value = false }
            compose.runOnIdle { assertTrue("Closing $type restores player optics", playerOptics) }
        }
        compose.runOnIdle { inheritedOptics.value = false; visible.value = true }
        compose.runOnIdle { assertFalse(queueOptics); visible.value = false }
        compose.runOnIdle { assertFalse("Closing queue cannot override inherited budget", playerOptics) }
    }

    @Test fun `opaque queue suspends decorations only at full coverage`() {
        assertFalse(playerQueueUnderlayCovered(false, false, QueueType.Essential))
        assertFalse(playerQueueUnderlayCovered(false, true, QueueType.Essential))
        assertTrue(playerQueueUnderlayCovered(false, true, QueueType.Essential, fullyCovering = true))
        assertFalse(playerQueueUnderlayCovered(false, false, QueueType.Essential))
    }

    @Test fun `partial or translucent coverage keeps the visible viewport live`() {
        assertFalse(queuePanelCoversPlayer(0, 1000, 24, 1f))
        assertFalse(queuePanelCoversPlayer(500, 1000, 24, 1f))
        assertFalse(queuePanelCoversPlayer(950, 1000, 24, 1f))
        assertTrue(queuePanelCoversPlayer(976, 1000, 24, 1f))
        assertTrue(queuePanelCoversPlayer(975, 1000, 24, 1f))
        assertFalse(queuePanelCoversPlayer(976, 1000, 24, .5f))
        assertFalse(queuePanelCoversPlayer(976, 1000, 24, 0f))
        assertFalse(queuePanelCoversPlayer(0, 0, 0, 1f))
    }

    @Test fun `global modal coverage survives queue opening and closure`() {
        for (queueType in QueueType.entries) for (queueVisible in listOf(false, true, false)) {
            assertTrue(playerQueueUnderlayCovered(true, queueVisible, queueType))
        }
    }
    @Test fun `mini queue owner pauses covered scene and restores it without lowering foreground budget`() {
        val visible = mutableStateOf(true)
        val full = mutableStateOf(false)
        val settings = mutableStateOf(GlassSettings())
        var sceneCovered = false
        var sceneOptics = true
        var foregroundOptics = false
        compose.setContent {
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings.value) {
            MiniPlayerQueueScene(visible.value, scene = {
                val covered = LocalGlassUnderlayCovered.current
                val optics = glassOpticsEnabled()
                SideEffect { sceneCovered = covered; sceneOptics = optics }
            }, overlay = {
                val report = LocalMiniQueueCoverage.current
                val optics = glassOpticsEnabled()
                val coverage = full.value
                SideEffect { report(coverage); foregroundOptics = optics }
            })
            }
        }
        compose.runOnIdle {
            assertFalse(sceneCovered)
            assertFalse(sceneOptics)
            assertTrue(foregroundOptics)
            settings.value = settings.value.copy(optimizationEnabled = false)
        }
        compose.runOnIdle { assertTrue(sceneOptics); assertFalse(sceneCovered); assertTrue(foregroundOptics)
            settings.value = settings.value.copy(optimizationEnabled = true) }
        compose.runOnIdle { assertFalse(sceneOptics); full.value = true }
        compose.runOnIdle { assertTrue(sceneCovered); settings.value = settings.value.copy(optimizationEnabled = false) }
        compose.runOnIdle { assertTrue(sceneCovered); assertFalse("Full coverage remains suppressed", sceneOptics)
            settings.value = settings.value.copy(optimizationEnabled = true) }
        compose.runOnIdle { assertTrue(sceneCovered); assertTrue(foregroundOptics); full.value = false }
        compose.runOnIdle { assertFalse(sceneCovered); full.value = true }
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { assertFalse(sceneCovered); assertTrue(sceneOptics) }
    }

    @Test fun `expanded endpoint survives configuration and viewport inset mismatch`() {
        // Configuration height can exclude navigation while the edge-to-edge owner includes it.
        val maxFraction = (2400f - 72f) / 2400f
        val viewport = 2560
        val endpoint = (viewport * maxFraction).toInt()
        assertFalse(queuePanelCoversPlayer(endpoint, viewport, 72, 1f))
        assertTrue(queuePanelAtExpandedEndpoint(endpoint, viewport, maxFraction, 1f))
        assertFalse(queuePanelAtExpandedEndpoint((viewport * .65f).toInt(), viewport, maxFraction, 1f))
        assertFalse(queuePanelAtExpandedEndpoint(endpoint - 2, viewport, maxFraction, 1f))
        assertFalse(queuePanelAtExpandedEndpoint(endpoint, viewport, maxFraction, .5f))
        assertFalse(queuePanelAtExpandedEndpoint(endpoint, viewport, maxFraction, 0f))
        assertFalse(queuePanelAtExpandedEndpoint(0, 0, maxFraction, 1f))
    }

}
