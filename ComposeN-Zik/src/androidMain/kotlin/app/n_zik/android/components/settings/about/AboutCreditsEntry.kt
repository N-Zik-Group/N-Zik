package app.n_zik.android.components.settings.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import androidx.compose.ui.text.TextStyle
import app.n_zik.android.components.settings.common.ModernSettingsEntry
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.semiBold
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber

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
    val resources = LocalContext.current.resources
    val read by produceState<Result<String>?>(null, resources) {
        value = try {
            Result.success(withContext(NzikDispatchers.DATA) {
                resources.openRawResource(R.raw.credits).bufferedReader().use { it.readText() }
                    .removePrefix("# N-Zik credits").trimStart()
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("AboutCredits").w(error, "Could not read the credits")
            Result.failure(error)
        }
    }
    val result = read
    val credits = result?.getOrNull()
    AnimatedVisibility(
        visible = expanded && credits?.isNotEmpty() != false,
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.95f),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.95f),
    ) {
        val contentModifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp)
        when {
            result == null -> Column(contentModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemTextPlaceholder(null, typography().s.semiBold, width = 0.42f)
                ItemTextPlaceholder(null, typography().s, width = 0.84f)
                ItemTextPlaceholder(null, typography().s, width = 0.72f)
            }
            credits != null -> MarkdownText(
                markdown = credits,
                modifier = contentModifier,
                style = TextStyle(color = colorPalette().text),
            )
            else -> SettingsDescription(stringResource(R.string.common_load_failed), modifier = contentModifier)
        }
    }
}
