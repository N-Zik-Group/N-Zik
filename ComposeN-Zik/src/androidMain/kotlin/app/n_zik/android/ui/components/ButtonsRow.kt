package app.n_zik.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassFilterChip
import app.n_zik.android.enums.BuiltInPlaylist
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.uiRoundnessShape

@Composable
fun <E> ButtonsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        chips.forEach { (value, label) ->
            GlassFilterChip(
                label = { Text(label) },
                selected = currentValue == value,
                shape = uiRoundnessShape(),
                colors = FilterChipDefaults
                    .filterChipColors(
                        containerColor = colorPalette().background1,
                        labelColor = colorPalette().text,
                        selectedContainerColor = colorPalette().accent,
                        selectedLabelColor = colorPalette().onAccent,
                    ),
                onClick = { onValueUpdate(value) }
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
fun ButtonsRow(
    chips: List<BuiltInPlaylist>,
    currentValue: BuiltInPlaylist,
    onValueUpdate: (BuiltInPlaylist) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
    ) {
        chips.forEach { playlistType ->
            GlassFilterChip(
                label = { Text( playlistType.text ) },
                selected = currentValue == playlistType,
                shape = uiRoundnessShape(),
                colors = FilterChipDefaults
                    .filterChipColors(
                        containerColor = colorPalette().background1,
                        labelColor = colorPalette().text,
                        selectedContainerColor = colorPalette().accent,
                        selectedLabelColor = colorPalette().onAccent,
                    ),
                onClick = { onValueUpdate(playlistType) }
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}


