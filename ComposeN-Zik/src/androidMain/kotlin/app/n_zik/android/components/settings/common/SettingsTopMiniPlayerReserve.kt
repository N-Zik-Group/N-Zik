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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding

/** Settings scroll owners clear the header, physical rails and the bottom player. */
@Composable
internal fun rememberSettingsContentPadding(
    base: PaddingValues = PaddingValues(0.dp),
    footerBottomInset: PaddingValues? = null,
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
    val playerInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    return remember(padding, playerInsets, footerBottomInset) {
        object : PaddingValues by padding {
            override fun calculateBottomPadding(): Dp {
                if (footerBottomInset == null) return padding.calculateBottomPadding()
                val playerClearance = playerInsets.calculateBottomPadding() - footerBottomInset.calculateBottomPadding()
                return if (playerClearance > 0.dp) playerClearance + SettingsFooterMiniPlayerGap else 0.dp
            }
        }
    }
}

/** Apply after verticalScroll (and its container coordinate observer), never to the viewport. */
@Composable
internal fun Modifier.settingsTopContentPadding(): Modifier =
    padding(rememberSettingsContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)))
