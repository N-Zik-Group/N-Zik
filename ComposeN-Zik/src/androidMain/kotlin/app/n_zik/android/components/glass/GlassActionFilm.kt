package app.n_zik.android.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor

/** Retains the native elevated theme film, as navigation and header glass do. */
@Composable
internal fun glassActionFilmColor(): Color = surfaceColor(colorPalette().background1)
