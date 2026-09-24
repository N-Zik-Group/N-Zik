package app.n_zik.android.components.ui.screens.rescue

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.ui.styling.colorPaletteOf
import app.it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.customColorKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setDefaultPalette
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.rescue.RescueProcess
import com.kieronquinn.monetcompat.core.MonetCompat
import timber.log.Timber

/**
 * Lightweight Activity that runs in the `:rescue` process.
 *
 * Because [app.n_zik.android.MainApplication.onCreate] returns early when
 * `!isMainProcess()`, none of the heavy app initialization runs here:
 * no Room, no Koin/Hilt, no `Dependencies`, no `appContext()`, no player.
 * This Activity uses only [android.content.Context] and raw files.
 *
 * Timber is NOT planted in the `:rescue` process (the tree is set up in MainApplication, which
 * skips init for non-main processes), so debug builds plant a DebugTree here. Release builds
 * stay silent on purpose: this process handles credentials.
 */
class RescueActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.tag(TAG).i("Rescue Center started in process %d", Process.myPid())

        // The Rescue Center exists precisely so its write actions can run without the main
        // process: ask it to end itself the moment the screen opens (broadcast + safety-net
        // flag), whatever the entry point (launcher shortcut or in-app menu).
        //
        // But only when the main process is (likely) alive. When it is already dead (alive
        // marker stale or absent, or the trustworthy probe says so below 31) no kill request
        // is recorded at all: a stale flag would make the next healthy launch end itself at
        // startup — the unexpected self-kill this guard removes. The status line shows
        // "stopped" immediately in that case and the kill button stays disabled.
        if (RescueProcess.isMainProcessLikelyAlive(this)) {
            RescueProcess.requestKillMain(this)
        } else {
            Timber.tag(TAG).i(
                "Main process not alive (marker stale/absent): no kill request recorded on open"
            )
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(scrim = AndroidColor.TRANSPARENT)
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val systemDark = isSystemInDarkTheme()
            // Resolved once per system theme, never on recomposition: it reads preferences and may
            // set up MonetCompat.
            val colorScheme = remember(systemDark) { resolveColorScheme(systemDark) }

            MaterialTheme(colorScheme = colorScheme) {
                RescueScreen()
            }
        }
    }

    /**
     * The Rescue Center colors: the app palette when it can be resolved, a plain Material3 scheme
     * otherwise. Any failure (corrupt preferences, MonetCompat not ready, ...) must not take the
     * recovery screen down with it: it exists precisely for when the app is in a bad state.
     */
    private fun resolveColorScheme(systemDark: Boolean): ColorScheme =
        runCatching { appColorScheme(systemDark) }
            .onFailure { Timber.tag(TAG).e("App palette unavailable, using fallback: %s", it.javaClass.simpleName) }
            .getOrElse { if (systemDark) darkColorScheme() else lightColorScheme() }

    private fun appColorScheme(systemDark: Boolean): ColorScheme {
        val prefs = preferences
        val paletteName = prefs.getEnum(colorPaletteNameKey, ColorPaletteName.Dynamic)
        val paletteMode = prefs.getEnum(colorPaletteModeKey, ColorPaletteMode.Dark)
        val customColor = prefs.getInt(customColorKey, Color.Green.hashCode())

        val lightTheme = paletteMode == ColorPaletteMode.Light ||
            (paletteMode == ColorPaletteMode.System && !systemDark)

        var palette = colorPaletteOf(paletteName, paletteMode, !lightTheme)

        when (paletteName) {
            // Best effort in the :rescue process
            ColorPaletteName.MaterialYou -> runCatching {
                MonetCompat.enablePaletteCompat()
                MonetCompat.setup(this)
                val monet = MonetCompat.getInstance()
                monet.setDefaultPalette()
                dynamicColorPaletteOf(Color(monet.getAccentColor(this)), !lightTheme)
            }.onSuccess {
                palette = it
            }.onFailure {
                Timber.tag(TAG).e("MonetCompat not ready: %s", it.javaClass.simpleName)
            }

            ColorPaletteName.CustomColor -> palette = dynamicColorPaletteOf(Color(customColor), !lightTheme)

            else -> Unit
        }

        return if (lightTheme) {
            lightColorScheme(
                background = palette.background0,
                surface = palette.background1,
                surfaceVariant = palette.background2,
                onSurface = palette.text,
                onSurfaceVariant = palette.textSecondary,
                primaryContainer = palette.background2,
                onPrimaryContainer = Color.Black,
                primary = palette.accent
            )
        } else {
            darkColorScheme(
                background = palette.background0,
                surface = palette.background1,
                surfaceVariant = palette.background2,
                onSurface = palette.text,
                onSurfaceVariant = palette.textSecondary,
                primaryContainer = palette.background2,
                onPrimaryContainer = Color.White,
                primary = palette.accent
            )
        }
    }

    private companion object {
        const val TAG = "RescueActivity"
    }
}
