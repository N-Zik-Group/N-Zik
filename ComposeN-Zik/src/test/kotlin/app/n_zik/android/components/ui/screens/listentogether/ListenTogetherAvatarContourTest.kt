package app.n_zik.android.components.ui.screens.listentogether

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import app.n_zik.android.R
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.ui.screens.profiles.ProfileAvatarFrame
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.listentogether.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListenTogetherAvatarContourTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `all participant avatar contours fit the exact circular face beneath unchanged host and current user badges`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val master = mutableStateOf(true)
        val roles = listOf("host", "current", "guest")
        val page = Color.Black
        compose.setContent {
            // Deliberately square artist preference: ListenTogether's existing faces remain circles.
            val shape = RoundedCornerShape(0.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides GlassSettings(enabled = master.value, quality = GlassQuality.Performance,
                    rim = 1f, areaModes = mapOf(GlassArea.SettingsCards to mode.value))) {
                Row {
                    roles.forEach { role ->
                        Box(Modifier.size(72.dp, 120.dp).background(page).testTag("fixture.$role")) {
                            UserAvatar(UserInfo(role, "Test $role", role == "host"),
                                isCurrentUser = role == "current", isClickable = false, onClick = {})
                        }
                    }
                    // Independent render-order oracle: native face, then its exact rim,
                    // then the original translucent badge primitive. No inferred AA/color math.
                    Box(Modifier.size(72.dp, 120.dp).background(page).testTag("badge.reference")) {
                        Box(Modifier.align(Alignment.TopCenter).size(56.dp)) {
                            ProfileAvatarFrame(56.dp, GlassArea.SettingsCards, shape = CircleShape) {
                                Box(Modifier.size(56.dp).clip(CircleShape)
                                    .background(DefaultDarkColorPalette.accent.copy(alpha = .45f)),
                                    contentAlignment = Alignment.Center) {
                                    Text("T", style = typography().l.semiBold, color = DefaultDarkColorPalette.text)
                                }
                            }
                            Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(20.dp)
                                .clip(CircleShape).background(DefaultDarkColorPalette.accent.copy(alpha = .6f)),
                                contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.person), null, tint = DefaultDarkColorPalette.onAccent,
                                    modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
        val nativeFaces = roles.associateWith { role ->
            compose.onNodeWithTag("listen.avatar.face.$role", useUnmergedTree = true)
                .assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp).captureToImage().toPixelMap()
        }
        val nativeScenes = roles.associateWith { compose.onNodeWithTag("fixture.$it").captureToImage().toPixelMap() }
        val faceBounds = roles.associateWith {
            compose.onNodeWithTag("listen.avatar.face.$it", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        val badgeBounds = roles.take(2).associateWith {
            compose.onNodeWithTag("listen.avatar.badge.$it", useUnmergedTree = true)
                .assertWidthIsEqualTo(20.dp).assertHeightIsEqualTo(20.dp).fetchSemanticsNode().boundsInRoot
        }
        for ((enabled, areaMode) in listOf(true to GlassAreaMode.ContourOnly,
            true to GlassAreaMode.GlassAndContour, true to GlassAreaMode.GlassOnly, false to GlassAreaMode.GlassAndContour)) {
            compose.runOnIdle { master.value = enabled; mode.value = areaMode }
            val drawsContour = enabled && areaMode.hasContour
            val currentBadgeReference = compose.onNodeWithTag("badge.reference").captureToImage().toPixelMap()
            roles.forEach { role ->
                val face = compose.onNodeWithTag("listen.avatar.face.$role", useUnmergedTree = true)
                face.assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
                assertEquals(faceBounds[role], face.fetchSemanticsNode().boundsInRoot)
                val pixels = face.captureToImage().toPixelMap()
                val original = nativeFaces.getValue(role)
                val inset = with(compose.density) { 4.dp.toPx() }
                val radius = pixels.width / 2f - inset
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val dx = x + .5f - pixels.width / 2f
                    val dy = y + .5f - pixels.height / 2f
                    if (!drawsContour || dx*dx + dy*dy < radius*radius) {
                        assertEquals("$role face/initials must not be distorted at $x,$y", original[x,y], pixels[x,y])
                    }
                }
                if (drawsContour) assertNotEquals("$role rim follows the actual56dp face edge", original[0,pixels.height/2], pixels[0,pixels.height/2])
                val host = compose.onNodeWithTag("fixture.$role")
                val scene = host.captureToImage().toPixelMap()
                val originalScene = nativeScenes.getValue(role)
                val hostBounds = host.fetchSemanticsNode().boundsInRoot
                val bounds = faceBounds.getValue(role)
                val left = (bounds.left - hostBounds.left).toInt()
                val top = (bounds.top - hostBounds.top).toInt()
                // No new border surrounds the72dp slot, labels, or protruding rank badge.
                for (y in 0 until scene.height) for (x in 0 until scene.width) {
                    if (x < left || x >= left + pixels.width || y < top || y >= top + pixels.height) {
                        assertEquals("$role contour cannot escape its exact face rectangle", originalScene[x,y], scene[x,y])
                    }
                }
                if (role != "guest") {
                    val badge = compose.onNodeWithTag("listen.avatar.badge.$role", useUnmergedTree = true)
                    assertEquals(badgeBounds[role], badge.fetchSemanticsNode().boundsInRoot)
                    val badgeRect = badge.fetchSemanticsNode().boundsInRoot
                    val cx = badgeRect.center.x - hostBounds.left
                    val cy = badgeRect.center.y - hostBounds.top
                    val innerRadius = badgeRect.width / 2f - 2f
                    for (y in 0 until scene.height) for (x in 0 until scene.width) {
                        val dx = x + .5f - cx
                        val dy = y + .5f - cy
                        if (dx*dx + dy*dy < innerRadius*innerRadius) {
                            if (role == "host" || !drawsContour) {
                                assertEquals("Opaque host badge and native OFF must stay pixel-identical", originalScene[x,y], scene[x,y])
                            } else {
                                // Exact independent Compose source-over rendering preserves native
                                // alpha and glyph AA while proving the rim is beneath the badge.
                                assertEquals("Current-user badge must render above the shared face rim at $x,$y",
                                    currentBadgeReference[x,y], scene[x,y])
                                if (originalScene[x,y] == DefaultDarkColorPalette.onAccent) {
                                    assertEquals("Opaque rank glyph remains pixel-identical", originalScene[x,y], scene[x,y])
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
