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
import app.n_zik.android.ui.styling.BoundedCornerSize
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.ModernBlackColorPalette
import app.n_zik.android.ui.styling.PureBlackColorPalette
import app.n_zik.android.utils.autosyncKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.bassboostEnabledKey
import app.n_zik.android.utils.handleAudioFocusEnabledKey
import app.n_zik.android.utils.isConnectionMeteredEnabledKey
import app.n_zik.android.utils.pauseOnHeadphoneDisconnectKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showButtonPlayerVideoKey
import app.n_zik.android.utils.showSearchTabKey
import app.n_zik.android.utils.showStatsInNavbarKey
import app.n_zik.android.utils.ytAccountNameKey
import app.n_zik.android.utils.ytAccountThumbnailKey
import app.n_zik.android.components.menu.straightBottomMenuShape

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
fun isVideoEnabled() = appContext().preferences.getBoolean(showButtonPlayerVideoKey, true)

fun isConnectionMeteredEnabled() = appContext().preferences.getBoolean(isConnectionMeteredEnabledKey, false)
fun isHandleAudioFocusEnabled() = appContext().preferences.getBoolean(handleAudioFocusEnabledKey, true)
fun isPauseOnHeadphoneDisconnectEnabled() = appContext().preferences.getBoolean(pauseOnHeadphoneDisconnectKey, true)
fun isBassBoostEnabled() = appContext().preferences.getBoolean(bassboostEnabledKey, false)

/** Surface color for elevated components (cards, sheets, burger menu, navigation bar): [base] normally, dark gray in pitch black (see [isPitchBlackSurface]). */
@Composable
fun surfaceColor(base: Color): Color =
    if (isPitchBlackSurface()) Color(0xFF1A1A1A) else base

/** Darker menu and dialog content within elevated PitchBlack bands. */
@Composable
fun recessedSurfaceColor(base: Color): Color =
    if (isPitchBlackSurface()) Color(0xFF101010) else base

/** True when the active palette renders surfaces on pure black. */
@Composable
fun isPitchBlackSurface(): Boolean {
    val colorPaletteMode = rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark).value
    return colorPalette() === PureBlackColorPalette ||
        colorPalette() === ModernBlackColorPalette ||
        colorPaletteMode == ColorPaletteMode.PitchBlack
}


