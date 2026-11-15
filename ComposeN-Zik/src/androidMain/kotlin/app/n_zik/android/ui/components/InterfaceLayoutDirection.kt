package app.n_zik.android.ui.components

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.rightToLeftInterfaceKey

/** The interface may be mirrored in any language. Text keeps its own reading direction. */
@Composable
internal fun InterfaceLayoutDirection(content: @Composable () -> Unit) {
    val rightToLeft by rememberPreference(rightToLeftInterfaceKey, false)
    CompositionLocalProvider(
        LocalLayoutDirection provides if (rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}

/** Standalone native screens use the same profile setting as Compose. */
internal fun View.applyInterfaceLayoutDirection() {
    layoutDirection = if (context.preferences.getBoolean(rightToLeftInterfaceKey, false)) {
        View.LAYOUT_DIRECTION_RTL
    } else {
        View.LAYOUT_DIRECTION_LTR
    }
}
