package app.n_zik.android.components.settings.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.utils.secondary

@Composable
internal fun SettingsPageTitle(title: String, @DrawableRes icon: Int, showBetaBadge: Boolean = false) {
    HeaderWithIcon(
        title = title,
        iconId = icon,
        enabled = false,
        modifier = Modifier,
        showBetaBadge = showBetaBadge,
        maxTitleLines = 2,
        onClick = {},
    )
}

@Composable
internal fun SettingsPageDescription(text: String) {
    BasicText(
        text = text,
        style = typography().xxs.secondary.copy(textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    )
}
