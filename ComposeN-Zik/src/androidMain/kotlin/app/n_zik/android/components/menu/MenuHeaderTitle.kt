package app.n_zik.android.components.menu

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassSectionAction
import app.n_zik.android.components.glass.GlassSectionActions
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuBack

@Composable
internal fun menuHeaderLeading(
    leading: (@Composable RowScope.() -> Unit)?,
    allowDefaultBack: Boolean,
): (@Composable RowScope.() -> Unit)? {
    if (leading != null || !allowDefaultBack) return leading
    return menuHeaderBackLeading(LocalMenuBack.current)
}

internal fun menuHeaderBackLeading(onBack: (() -> Unit)?): (@Composable RowScope.() -> Unit)? =
    onBack?.let { back ->
        {
            GlassSectionActions(
                actions = listOf(menuHeaderBackAction(back)),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }

@Composable
internal fun menuHeaderBackAction(onBack: () -> Unit): GlassSectionAction {
    val textColor = colorPalette().text
    return GlassSectionAction(
        icon = R.drawable.chevron_back,
        contentDescription = stringResource(R.string.common_action_back),
        nativeColor = textColor,
        glassColor = textColor,
        onClick = onBack,
    )
}

@Composable
internal fun MenuHeaderTitle(
    title: String?,
    leading: (@Composable RowScope.() -> Unit)?,
    trailing: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = Int.MAX_VALUE,
) {
    if (leading == null && trailing == null) {
        title?.let { MenuTitleText(it, titleMaxLines) }
        return
    }

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Row(verticalAlignment = Alignment.CenterVertically) { leading?.invoke(this) }
            Row(verticalAlignment = Alignment.CenterVertically) { trailing?.invoke(this) }
            title?.let { MenuTitleText(it, titleMaxLines) }
        },
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val before = measurables[0].measure(looseConstraints)
        val after = measurables[1].measure(looseConstraints)
        val titleWidth = (constraints.maxWidth - 2 * maxOf(before.width, after.width)).coerceAtLeast(0)
        val heading = measurables.getOrNull(2)?.measure(looseConstraints.copy(maxWidth = titleWidth))
        val width = constraints.maxWidth
        val height = maxOf(before.height, after.height, heading?.height ?: 0, constraints.minHeight)
        layout(width, height) {
            heading?.placeRelative((width - heading.width) / 2, (height - heading.height) / 2)
            before.placeRelative(0, (height - before.height) / 2)
            after.placeRelative(width - after.width, (height - after.height) / 2)
        }
    }
}

@Composable
private fun MenuTitleText(title: String, maxLines: Int) {
    Text(
        text = title,
        style = typography().m.copy(color = colorPalette().text),
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() }.padding(vertical = 10.dp),
    )
}
