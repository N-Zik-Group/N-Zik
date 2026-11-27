package app.n_zik.android.components.menu

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuBack

@Composable
internal fun menuHeaderTrailing(
    trailing: (@Composable RowScope.() -> Unit)?,
    allowDefaultBack: Boolean,
): (@Composable RowScope.() -> Unit)? {
    if (trailing != null || !allowDefaultBack) return trailing
    val onBack = LocalMenuBack.current ?: return null
    return {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.chevron_back),
                contentDescription = stringResource(R.string.common_action_back),
                tint = colorPalette().text,
            )
        }
    }
}

@Composable
internal fun MenuHeaderTitle(title: String?, trailing: (@Composable RowScope.() -> Unit)?) {
    if (trailing == null) {
        title?.let { MenuTitleText(it) }
        return
    }

    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            title?.let { MenuTitleText(it) }
        },
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val action = measurables.first().measure(looseConstraints)
        val titleWidth = (constraints.maxWidth - 2 * action.width).coerceAtLeast(0)
        val heading = measurables.getOrNull(1)?.measure(looseConstraints.copy(maxWidth = titleWidth))
        val width = constraints.maxWidth
        val height = maxOf(action.height, heading?.height ?: 0, constraints.minHeight)
        layout(width, height) {
            heading?.placeRelative((width - heading.width) / 2, (height - heading.height) / 2)
            action.placeRelative(width - action.width, (height - action.height) / 2)
        }
    }
}

@Composable
private fun MenuTitleText(title: String) {
    Text(
        text = title,
        style = typography().m.copy(color = colorPalette().text),
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }.padding(top = 5.dp, bottom = 10.dp),
    )
}
