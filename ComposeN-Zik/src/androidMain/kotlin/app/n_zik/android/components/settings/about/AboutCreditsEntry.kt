package app.n_zik.android.components.settings.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import androidx.compose.ui.text.TextStyle
import app.n_zik.android.components.settings.common.ModernSettingsEntry
import app.n_zik.android.utils.coroutines.NzikDispatchers
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.withContext

@Composable
internal fun AboutCreditsEntry() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ModernSettingsEntry(
        title = stringResource(R.string.about_credits_title),
        text = stringResource(R.string.about_credits_open),
        icon = R.drawable.star_brilliant,
        expanded = expanded,
        onClick = { expanded = !expanded }
    )
    if (expanded) {
        val resources = LocalContext.current.resources
        val credits by produceState("", resources) {
            value = withContext(NzikDispatchers.DATA) {
                resources.openRawResource(R.raw.credits).bufferedReader().use { it.readText() }
            }
        }
        if (credits.isNotEmpty()) {
            MarkdownText(
                markdown = credits.removePrefix("# N-Zik credits").trimStart(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
                style = TextStyle(color = colorPalette().text)
            )
        }
    }
}
