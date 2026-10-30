package app.n_zik.android.components.ui.screens.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.it.fast4x.rimusic.ui.components.ButtonsRow
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassFilterChip
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape

/** Home filters retain individual compact chips and their horizontal scrolling. */
@Composable
internal fun <E> GlassHomeChips(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val settings = rememberGlassSettings()
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        ButtonsRow(chips, currentValue, onValueUpdate, modifier, contentPadding)
        return
    }
    Row(modifier.horizontalScroll(rememberScrollState()).padding(contentPadding)) {
        chips.forEach { (value, label) ->
            GlassHomeFilterChip(
                selected = currentValue == value,
                label = { Text(label) },
                onClick = { onValueUpdate(value) },
                settings = settings,
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Built-in playlists use the same labels and selection callbacks as their native row. */
@Composable
internal fun GlassHomeChips(
    chips: List<BuiltInPlaylist>,
    currentValue: BuiltInPlaylist,
    onValueUpdate: (BuiltInPlaylist) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val settings = rememberGlassSettings()
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        ButtonsRow(chips, currentValue, onValueUpdate, modifier, contentPadding)
        return
    }
    GlassHomeChips(chips.map { it to it.text }, currentValue, onValueUpdate, modifier, contentPadding)
}

/** The native chip owns clicks and selected semantics; Glass supplies only its surface. */
@Composable
internal fun GlassHomeFilterChip(
    selected: Boolean,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = uiRoundnessShape(),
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = colorPalette().background1,
        labelColor = colorPalette().text,
        selectedContainerColor = colorPalette().accent,
        selectedLabelColor = colorPalette().onAccent,
    ),
    settings: GlassSettings = rememberGlassSettings(),
) {
    GlassFilterChip(selected, onClick, label, modifier, shape = shape, colors = colors, settings = settings)
}
