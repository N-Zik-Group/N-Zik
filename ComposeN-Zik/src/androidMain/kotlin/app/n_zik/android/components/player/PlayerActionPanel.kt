package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keeps the landscape toolbar at its default baseline as upcoming songs expand above it. */
@Composable
fun PlayerActionPanel(
    layout: PlayerActionToolbarLayout,
    landscape: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val breathingRoom = if (landscape)
        ((layout.hostHeight(50.dp) - PlayerActionToolbarHeight) / 2).coerceAtLeast(0.dp)
    else 0.dp
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = breathingRoom),
        verticalArrangement = if (landscape) Arrangement.spacedBy(breathingRoom, Alignment.Bottom)
            else Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
