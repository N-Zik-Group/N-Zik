package app.n_zik.android.components.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference

@Composable
internal fun <T> SelectionMenu(
    title: String,
    choices: List<T>,
    selectedChoice: T,
    label: @Composable (T) -> String,
    icon: (T) -> Int,
    style: MenuStyle = rememberPreference(menuStyleKey, MenuStyle.List).value,
    onSelected: (T) -> Unit,
) {
    val palette = colorPalette()
    val entryIcon: @Composable (T, Boolean) -> Unit = { choice, active ->
        Box(
            modifier = Modifier.size(32.dp)
                .iconBadgeBackground(palette.accent.copy(alpha = if (active) 0.2f else 0.1f), uiRoundnessShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon(choice)), null, Modifier.size(18.dp), tint = palette.accent)
        }
    }
    val indicator: @Composable (Boolean) -> Unit = { active ->
        RadioButton(
            selected = active,
            onClick = null,
            modifier = Modifier.size(20.dp),
            colors = RadioButtonDefaults.colors(selectedColor = palette.accent, unselectedColor = palette.textSecondary),
        )
    }
    val selectionBackground = Modifier.background(palette.accent.copy(alpha = 0.1f), uiRoundnessShape())
    fun selectionModifier(active: Boolean): Modifier =
        menuSelectionSemantics(active).then(if (active) selectionBackground else Modifier)

    if (style == MenuStyle.List) {
        ListMenu.Menu(title = title) {
            choices.forEach { choice ->
                val active = choice == selectedChoice
                ListMenu.Entry(
                    text = label(choice),
                    icon = { entryIcon(choice, active) },
                    trailingContent = { indicator(active) },
                    modifier = selectionModifier(active),
                    onClick = { onSelected(choice) },
                )
            }
        }
    } else {
        GridMenu.Menu(title = title) {
            items(choices) { choice ->
                val active = choice == selectedChoice
                GridMenu.Entry(
                    text = label(choice),
                    icon = { entryIcon(choice, active) },
                    iconHasOwnBackground = true,
                    trailingContent = { indicator(active) },
                    modifier = selectionModifier(active),
                    onClick = { onSelected(choice) },
                )
            }
        }
    }
}

internal fun menuSelectionSemantics(active: Boolean): Modifier = Modifier.semantics {
    role = Role.RadioButton
    selected = active
}
