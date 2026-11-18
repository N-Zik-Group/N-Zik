package app.n_zik.android

import android.content.Context
import android.content.res.Resources
import androidx.core.content.ContextCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.BoundedCornerSize
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.ModernBlackColorPalette
import app.n_zik.android.ui.styling.PureBlackColorPalette
import app.n_zik.android.utils.autosyncKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.bassboostEnabledKey
import app.n_zik.android.utils.handleAudioFocusEnabledKey
import app.n_zik.android.utils.isConnectionMeteredEnabledKey
import app.n_zik.android.utils.pauseOnHeadphoneDisconnectKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showSearchTabKey
import app.n_zik.android.utils.showStatsInNavbarKey
import app.n_zik.android.utils.ytAccountNameKey
import app.n_zik.android.utils.ytAccountThumbnailKey
import app.n_zik.android.components.menu.straightBottomMenuShape
import app.n_zik.android.components.theme.LocalNativeSurfaceMix
import app.n_zik.android.components.theme.NativeSurfaceMix

@Composable
fun typography() = LocalAppearance.current.typography

@Composable
@ReadOnlyComposable
fun colorPalette() = LocalAppearance.current.colorPalette

@Composable
fun thumbnailShape() = LocalAppearance.current.thumbnailShape

@Composable
fun artistThumbnailShape() = LocalAppearance.current.artistThumbnailShape

@Composable
fun uiRoundnessShape() = LocalAppearance.current.uiRoundnessShape

@Composable
fun exactUiRoundnessShape(): Shape {
    val appearance = LocalAppearance.current
    val shape = appearance.uiRoundnessShape
    return if (shape is RoundedCornerShape) {
        val size = shape.topStart
        if (size is BoundedCornerSize) {
            RoundedCornerShape(size.dp)
        } else {
            shape
        }
    } else {
        shape
    }
}

@Composable
fun gridMenuShape(): Shape {
    val appearance = LocalAppearance.current
    val shape = appearance.uiRoundnessShape
    return if (shape is RoundedCornerShape) {
        val size = shape.topStart
        if (size is BoundedCornerSize) {
            RoundedCornerShape(BoundedCornerSize(size.dp, 0.15f))
        } else {
            shape
        }
    } else {
        shape
    }
}

@Composable
fun searchBubbleShape(): Shape {
    val appearance = LocalAppearance.current
    val shape = appearance.uiRoundnessShape
    return if (shape is RoundedCornerShape) {
        val size = shape.topStart
        // 0.5f caps corners at 50% of the smallest dimension, so large global radii render as full circles
        if (size is BoundedCornerSize) {
            RoundedCornerShape(BoundedCornerSize(size.dp, 0.5f))
        } else {
            shape
        }
    } else {
        shape
    }
}

@Composable
fun topUiRoundnessShape(): Shape = straightBottomMenuShape(LocalAppearance.current.uiRoundnessShape)

@Composable
fun showSearchIconInNav() = rememberPreference( showSearchTabKey, false ).value

@Composable
fun showStatsIconInNav() = rememberPreference( showStatsInNavbarKey, false ).value

@Composable
fun binder() = LocalPlayerServiceBinder.current?.service

fun appContext(): Context = Dependencies.application.applicationContext

/** Resolves text resources using the app language even outside an Activity. */
fun appTextResources(): Resources = ContextCompat.getContextForLanguage(appContext()).resources
fun context(): Context = Dependencies.application

// Explicit non-null return: the platform getString() is a nullable type, and the
// display-name consumers (Rewind deck, onboarding, Accounts card) expect a String
fun ytAccountName(): String = appContext().encryptedPreferences.getString(ytAccountNameKey, "").orEmpty()
fun ytAccountThumbnail() = appContext().encryptedPreferences.getString(ytAccountThumbnailKey, "")

fun isConnectionMeteredEnabled() = appContext().preferences.getBoolean(isConnectionMeteredEnabledKey, false)
fun isHandleAudioFocusEnabled() = appContext().preferences.getBoolean(handleAudioFocusEnabledKey, true)
fun isPauseOnHeadphoneDisconnectEnabled() = appContext().preferences.getBoolean(pauseOnHeadphoneDisconnectKey, true)
fun isBassBoostEnabled() = appContext().preferences.getBoolean(bassboostEnabledKey, false)

/** Resolve native fills before Glass applies its own material and opacity. */
internal fun nativeSurfaceColor(
    base: Color,
    mode: ColorPaletteMode,
    blackPalette: Boolean,
    recessed: Boolean = false,
    navigation: Boolean = false,
): Color = when {
    mode == ColorPaletteMode.PureBlack -> if (navigation) Color.Black.copy(alpha = .90f) else Color.Black
    mode == ColorPaletteMode.PitchBlack || blackPalette -> when {
        navigation -> Color(0xFF1A1A1A).copy(alpha = .90f)
        recessed -> Color(0xFF101010)
        else -> Color(0xFF1A1A1A)
    }
    else -> base
}

/** Preference identity survives local palette copies used for foreground contrast. */
internal fun hasBlackPaletteSurfaces(name: ColorPaletteName, palette: ColorPalette): Boolean =
    name == ColorPaletteName.PureBlack || name == ColorPaletteName.ModernBlack ||
        palette === PureBlackColorPalette || palette === ModernBlackColorPalette

internal fun nativeSurfaceMix(mode: ColorPaletteMode, name: ColorPaletteName, palette: ColorPalette): NativeSurfaceMix {
    val namedBlack = name == ColorPaletteName.PureBlack || name == ColorPaletteName.ModernBlack
    val pure = mode == ColorPaletteMode.PureBlack
    val pitch = !pure && (mode == ColorPaletteMode.PitchBlack || hasBlackPaletteSurfaces(name, palette))
    return NativeSurfaceMix(
        pitchBlack = if (pitch) 1f else 0f,
        pureBlack = if (pure) 1f else 0f,
        miniPlayerBlack = if (namedBlack) 1f else 0f,
    )
}

@Composable
private fun nativeSurface(base: Color, recessed: Boolean = false, navigation: Boolean = false): Color {
    LocalNativeSurfaceMix.current?.let { mix ->
        return mix.color(base,
            nativeSurfaceColor(base, ColorPaletteMode.PitchBlack, false, recessed, navigation),
            nativeSurfaceColor(base, ColorPaletteMode.PureBlack, false, recessed, navigation))
    }
    val mode = rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark).value
    val palette = colorPalette()
    val name = rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic).value
    return nativeSurfaceColor(
        base, mode, hasBlackPaletteSurfaces(name, palette),
        recessed, navigation,
    )
}

/** Native raised surface, preserving each mode's coverage policy. */
@Composable
fun surfaceColor(base: Color): Color = nativeSurface(base)

/** Native navigation coverage; Glass retains ownership of its own opacity. */
@Composable
fun navigationSurfaceColor(floating: Boolean = false): Color {
    val base = colorPalette().background1
    val fill = if (floating) base.copy(alpha = minOf(base.alpha, .95f)) else base
    return nativeSurface(fill, navigation = true)
}

/** Native content surface within raised menus and dialogs. */
@Composable
fun recessedSurfaceColor(base: Color): Color = nativeSurface(base, recessed = true)

/** Black modes and named black palettes share accent foreground and floating coverage rules. */
@Composable
fun isPitchBlackSurface(): Boolean {
    val mode = rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark).value
    val name = rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic).value
    return hasBlackPaletteSurfaces(name, colorPalette()) ||
        mode == ColorPaletteMode.PitchBlack || mode == ColorPaletteMode.PureBlack
}
