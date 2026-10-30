package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding

/** Settings scroll owners clear the header, physical rails and the bottom player. */
@Composable
internal fun rememberSettingsContentPadding(
    base: PaddingValues = PaddingValues(0.dp),
    bottomInsetHandledByFooter: Boolean = false,
): PaddingValues {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val safeSides = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val landscapeBase = remember(base, safeSides, isLandscape) {
        if (!isLandscape) base else object : PaddingValues by base {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
                base.calculateLeftPadding(layoutDirection) + safeSides.calculateLeftPadding(layoutDirection)

            override fun calculateRightPadding(layoutDirection: LayoutDirection) =
                base.calculateRightPadding(layoutDirection) + safeSides.calculateRightPadding(layoutDirection)
        }
    }
    val padding = rememberPageChromeContentPadding(landscapeBase)
    return remember(padding, bottomInsetHandledByFooter) {
        object : PaddingValues by padding {
            override fun calculateBottomPadding() =
                if (bottomInsetHandledByFooter) 0.dp else padding.calculateBottomPadding()
        }
    }
}

/** Apply after verticalScroll (and its container coordinate observer), never to the viewport. */
@Composable
internal fun Modifier.settingsTopContentPadding(): Modifier =
    padding(rememberSettingsContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)))
