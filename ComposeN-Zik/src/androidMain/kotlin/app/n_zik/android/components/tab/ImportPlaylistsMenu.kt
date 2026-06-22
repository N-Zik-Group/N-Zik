package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.enums.ImportPlaylistType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.typography
import app.n_zik.android.colorPalette

class ImportPlaylistsMenu(
    private val onImportNzik: () -> Unit,
    private val onImportSpotify: () -> Unit
) : Descriptive, MenuIcon {
    override val messageId: Int = R.string.import_playlist
    override val iconId: Int = R.drawable.import_outline
    override val menuIconTitle: String
        @Composable get() = stringResource(messageId)

    var showDialog = mutableStateOf(false)

    override fun onShortClick() {
        showDialog.value = true
    }

    @Composable
    fun Render() {
        if (showDialog.value) {
            app.it.fast4x.rimusic.ui.components.themed.DefaultDialog(
                onDismiss = { showDialog.value = false }
            ) {
                Column(Modifier.padding(16.dp)) {
                    ImportPlaylistType.entries.forEach { type ->
                        BasicText(
                            text = type.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDialog.value = false
                                    when(type) {
                                        ImportPlaylistType.RiMusic -> onImportNzik()
                                        ImportPlaylistType.Exportify -> onImportSpotify()
                                        ImportPlaylistType.RiPlay -> onImportSpotify()
                                    }
                                }
                                .padding(16.dp),
                            style = typography().m.copy(color = colorPalette().text)
                        )
                    }
                }
            }
        }
    }
}
