package app.n_zik.android.ui.screens.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.styling.Dimensions

@Composable
internal fun rememberSearchTabContentPadding(topInsetProvider: () -> Dp): PaddingValues =
    rememberPageChromeContentPadding(
        base = PaddingValues(bottom = Dimensions.bottomSpacer),
        topInsetProvider = topInsetProvider,
    )

@Composable
internal fun SearchTabHeader(@StringRes title: Int, @DrawableRes icon: Int) {
    HeaderWithIcon(
        title = stringResource(title),
        iconId = icon,
        enabled = false,
        modifier = Modifier,
        onClick = {},
    )
}
