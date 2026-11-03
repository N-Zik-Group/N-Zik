package app.n_zik.android.components.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class DynamicAppearanceOwnerTest {
    private val preferences: SharedPreferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("dynamic-owner", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    private val bright = dynamicColorPaletteOf(Color(0xffaaaaaa), false)
    private val dark = dynamicColorPaletteOf(Color(0xff555555), true)
    private var appearance = Appearance(bright, Typography.fallback, CircleShape, CircleShape, CircleShape)
    private val bars = mutableListOf<Boolean>()
    private val widgets = mutableListOf<ColorPalette>()

    private fun preference(key: String, value: String) {
        preferences.edit().putString(key, value).commit()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun toggle(enabled: Boolean) {
        preferences.edit().putBoolean(dynamicThemeRespectBrightnessKey, enabled).commit()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun `real preference listener toggles cached raw artwork and publishes all consumers without reloading`() = runTest {
        var loads = 0
        val owner = DynamicAppearanceOwner(preferences, this, { true }, { _, _ -> loads++; bright },
            { appearance }, { next, _ -> appearance = next }, { bars += it }, { widgets += it },
            StandardTestDispatcher(testScheduler))
        owner.start()
        try {
            owner.refresh("same-cover")
            testScheduler.runCurrent()
            assertFalse(appearance.colorPalette.isDark)
            toggle(true)
            assertTrue(appearance.colorPalette.isDark)
            assertEquals(appearance.colorPalette.text, appearance.typography.s.color)
            assertEquals(true, bars.last())
            assertEquals(appearance.colorPalette, widgets.last())
            toggle(false)
            assertEquals(bright, appearance.colorPalette)
            assertEquals(bright.text, appearance.typography.s.color)
            assertEquals(false, bars.last())
            assertEquals(bright, widgets.last())
            assertEquals(1, loads)
        } finally { owner.close() }
    }

    @Test fun `Light mode preference toggles monochrome dark and restores raw palette without reload`() = runTest {
        preference(colorPaletteModeKey, "Light")
        var loads = 0
        val owner = DynamicAppearanceOwner(preferences, this, { false }, { _, isDark ->
            assertFalse(isDark)
            loads++
            bright
        }, { appearance }, { next, _ -> appearance = next }, { bars += it }, { widgets += it },
            StandardTestDispatcher(testScheduler))
        owner.start()
        try {
            owner.refresh("same-light-cover")
            testScheduler.runCurrent()
            assertEquals(bright, appearance.colorPalette)
            toggle(true)
            assertEquals(dynamicColorPaletteOf(bright.accent, true), appearance.colorPalette)
            assertEquals(appearance.colorPalette.text, appearance.typography.s.color)
            assertTrue(bars.last())
            assertEquals(appearance.colorPalette, widgets.last())
            toggle(false)
            assertEquals(bright, appearance.colorPalette)
            assertEquals(bright.text, appearance.typography.s.color)
            assertFalse(bars.last())
            assertEquals(bright, widgets.last())
            assertEquals(1, loads)
        } finally { owner.close() }
    }

    @Test fun `toggle during new artwork extraction keeps that request and applies latest preference without reload`() = runTest {
        val pending = CompletableDeferred<ColorPalette>()
        var loads = 0
        val owner = DynamicAppearanceOwner(preferences, this, { true }, { url, _ ->
            loads++
            if (url == "new") pending.await() else bright
        }, { appearance }, { next, _ -> appearance = next }, { bars += it }, { widgets += it },
            StandardTestDispatcher(testScheduler))
        owner.start()
        try {
            owner.refresh("old")
            testScheduler.runCurrent()
            owner.refresh("new")
            testScheduler.runCurrent()
            toggle(true)
            assertEquals(bright, appearance.colorPalette)
            val newRaw = dynamicColorPaletteOf(Color(0xffcccccc), false)
            pending.complete(newRaw)
            testScheduler.runCurrent()
            assertTrue(appearance.colorPalette.isDark)
            assertEquals(newRaw.accent, appearance.colorPalette.accent)
            toggle(false)
            assertEquals(newRaw, appearance.colorPalette)
            assertEquals(2, loads)
        } finally { owner.close() }
    }

    @Test fun `superseded non cooperative extraction cannot overwrite or save cached toggle result`() = runTest {
        val pending = CompletableDeferred<ColorPalette>()
        var loads = 0
        val owner = DynamicAppearanceOwner(preferences, this, { true }, { _, _ ->
            loads++
            if (loads == 1) bright else withContext(NonCancellable) { pending.await() }
        }, { appearance }, { next, _ -> appearance = next }, { bars += it }, { widgets += it },
            StandardTestDispatcher(testScheduler))
        owner.start()
        try {
            owner.refresh("same")
            testScheduler.runCurrent()
            owner.refresh("same")
            testScheduler.runCurrent()
            toggle(true)
            val latest = appearance
            val saved = widgets.toList()
            pending.complete(dynamicColorPaletteOf(Color.Red, true))
            testScheduler.runCurrent()
            assertEquals(latest, appearance)
            assertEquals(saved, widgets)
            toggle(false)
            assertEquals(bright, appearance.colorPalette)
            assertEquals(2, loads)
        } finally { owner.close() }
    }

    @Test fun `mode and System changes extract compatible raw palette and keep pitch black readable`() = runTest {
        var systemDark = true
        val extractionModes = mutableListOf<Boolean>()
        val owner = DynamicAppearanceOwner(preferences, this, { systemDark }, { _, isDark ->
            extractionModes += isDark
            if (isDark) bright else dark
        }, { appearance }, { next, _ -> appearance = next }, { bars += it }, { widgets += it },
            StandardTestDispatcher(testScheduler))
        owner.start()
        try {
            toggle(true)
            preference(colorPaletteModeKey, "System")
            owner.refresh("same")
            testScheduler.runCurrent()
            assertTrue(appearance.colorPalette.isDark)
            systemDark = false
            owner.refresh("same")
            testScheduler.runCurrent()
            assertTrue(appearance.colorPalette.isDark)
            toggle(false)
            assertEquals(dark, appearance.colorPalette)
            preference(colorPaletteModeKey, "PitchBlack")
            owner.refresh("same")
            testScheduler.runCurrent()
            assertEquals(Color.Black, appearance.colorPalette.background0)
            assertEquals(Color.White, appearance.typography.s.color)
            assertTrue(bars.last())
            assertEquals(listOf(true, false, true), extractionModes)
        } finally { owner.close() }
    }

    @Test fun `leaving Dynamic and disposing prevents late publication and preference callbacks`() = runTest {
        val pending = CompletableDeferred<ColorPalette>()
        val owner = DynamicAppearanceOwner(preferences, this, { true }, { _, _ ->
            withContext(NonCancellable) { pending.await() }
        }, { appearance }, { next, _ -> appearance = next }, { bars += it }, { widgets += it },
            StandardTestDispatcher(testScheduler))
        owner.start()
        owner.refresh("pending")
        testScheduler.runCurrent()
        preference(colorPaletteNameKey, "Default")
        owner.cancel()
        pending.complete(bright)
        testScheduler.runCurrent()
        assertTrue(widgets.isEmpty())
        owner.close()
        preference(colorPaletteNameKey, "Dynamic")
        toggle(true)
        assertTrue(bars.isEmpty())
    }
    @Test fun `production raw snapshot derives all tones without repeating shared analysis and restores filtered fallback`() = runTest {
        val raw = app.n_zik.android.components.player.ArtworkColorSnapshot(
            lightPalette = null, darkPalette = null, lightColors = null, darkColors = null,
            fallbackDominant = Color.White, isMonochrome = true)
        val repository = app.n_zik.android.components.player.ArtworkColorRepository(backgroundScope)
        val source = app.n_zik.android.components.player.ArtworkColorKey("default", "white", "1", "HIGH", 0)
        var analyses = 0
        val owner = DynamicAppearanceOwner(preferences, this, { false }, { _, _ -> error("compatibility loader unused") },
            { appearance }, { next, _ -> appearance = next }, { bars += it }, { widgets += it },
            StandardTestDispatcher(testScheduler), loadArtwork = {
                repository.load(source) { analyses++; raw }
            })
        owner.start()
        try {
            preference(colorPaletteModeKey, "Light")
            owner.refresh("white")
            testScheduler.runCurrent()
            val original = appearance.colorPalette
            toggle(true)
            assertTrue(appearance.colorPalette.isDark)
            assertEquals(appearance.colorPalette.text, appearance.typography.s.color)
            preference(colorPaletteModeKey, "Dark")
            owner.refresh("white")
            testScheduler.runCurrent()
            assertTrue(appearance.colorPalette.isDark)
            toggle(false)
            preference(colorPaletteModeKey, "Light")
            owner.refresh("white")
            testScheduler.runCurrent()
            assertEquals(original, appearance.colorPalette)
            assertEquals(1, analyses)
        } finally { owner.close() }
    }
}
