package app.n_zik.android.utils

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kieronquinn.monetcompat.core.MonetCompat
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import kotlinx.coroutines.launch

val LocalMonetCompat = staticCompositionLocalOf { MonetCompat.getInstance() }

context(lifecycleOwner: LifecycleOwner)
inline fun MonetCompat.invokeOnReady(
    state: Lifecycle.State = Lifecycle.State.CREATED,
    crossinline block: () -> Unit
) = lifecycleOwner.lifecycleScope.launch {
    lifecycleOwner.repeatOnLifecycle(state) {
        awaitMonetReady()
        block()
    }
}

fun MonetCompat.setDefaultPalette(palette: ColorPalette = DefaultLightColorPalette) {
    defaultAccentColor = palette.accent.toArgb()
    defaultBackgroundColor = palette.background0.toArgb()
    defaultPrimaryColor = palette.background1.toArgb()
    defaultSecondaryColor = palette.background2.toArgb()
}



