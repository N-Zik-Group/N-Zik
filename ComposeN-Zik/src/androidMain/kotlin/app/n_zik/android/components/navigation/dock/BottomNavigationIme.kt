package app.n_zik.android.components.navigation.dock

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import app.it.fast4x.rimusic.enums.NavigationBarPosition

/** Existing bottom anchors already reserve the system bar inset. */
internal fun bottomNavigationImeLiftPx(
    position: NavigationBarPosition,
    imeBottomPx: Int,
    systemBottomPx: Int,
): Int = if (position.isBottomType) (imeBottomPx - systemBottomPx).coerceAtLeast(0) else 0

/** Read during placement or drawing to follow the platform IME animation. */
@Composable
internal fun rememberBottomNavigationImeLift(position: NavigationBarPosition): () -> Int {
    val ime = WindowInsets.ime
    val systemBars = WindowInsets.systemBars
    val density = LocalDensity.current
    return remember(position, ime, systemBars, density) {
        { bottomNavigationImeLiftPx(position, ime.getBottom(density), systemBars.getBottom(density)) }
    }
}
