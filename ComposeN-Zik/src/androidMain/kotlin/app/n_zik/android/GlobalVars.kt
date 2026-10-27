package app.n_zik.android

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.autosyncKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.bassboostEnabledKey
import app.it.fast4x.rimusic.utils.handleAudioFocusEnabledKey
import app.it.fast4x.rimusic.utils.isConnectionMeteredEnabledKey
import app.it.fast4x.rimusic.utils.pauseOnHeadphoneDisconnectKey
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import app.it.fast4x.rimusic.utils.showSearchTabKey
import app.it.fast4x.rimusic.utils.showStatsInNavbarKey
import app.it.fast4x.rimusic.utils.ytAccountNameKey
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import androidx.compose.foundation.shape.CornerSize

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
fun topUiRoundnessShape(): Shape {
    val appearance = LocalAppearance.current
    val shape = appearance.uiRoundnessShape
    return if (shape is RoundedCornerShape) {
        RoundedCornerShape(
            topStart = shape.topStart,
            topEnd = shape.topEnd,
            bottomEnd = CornerSize(0),
            bottomStart = CornerSize(0)
        )
    } else {
        shape
    }
}

@Composable
fun showSearchIconInNav() = rememberPreference( showSearchTabKey, false ).value

@Composable
fun showStatsIconInNav() = rememberPreference( showStatsInNavbarKey, false ).value

@Composable
fun binder() = LocalPlayerServiceBinder.current?.service

fun appContext(): Context = Dependencies.application.applicationContext
fun context(): Context = Dependencies.application

// Explicit non-null return: the platform getString() is a nullable type, and the
// display-name consumers (Rewind deck, onboarding, Accounts card) expect a String
fun ytAccountName(): String = appContext().encryptedPreferences.getString(ytAccountNameKey, "").orEmpty()
fun ytAccountThumbnail() = appContext().encryptedPreferences.getString(ytAccountThumbnailKey, "")
fun isVideoEnabled() = appContext().preferences.getBoolean(showButtonPlayerVideoKey, false)

fun isConnectionMeteredEnabled() = appContext().preferences.getBoolean(isConnectionMeteredEnabledKey, true)
fun isHandleAudioFocusEnabled() = appContext().preferences.getBoolean(handleAudioFocusEnabledKey, true)
fun isPauseOnHeadphoneDisconnectEnabled() = appContext().preferences.getBoolean(pauseOnHeadphoneDisconnectKey, true)
fun isBassBoostEnabled() = appContext().preferences.getBoolean(bassboostEnabledKey, false)
fun isDebugModeEnabled() = appContext().preferences.getBoolean(logDebugEnabledKey, false)

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


