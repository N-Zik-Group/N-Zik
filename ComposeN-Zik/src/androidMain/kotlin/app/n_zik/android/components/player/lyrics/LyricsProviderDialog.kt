package app.n_zik.android.components.player.lyrics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.n_zik.android.utils.semiBold
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DefaultDialog
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.dialogInputEnabled
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.typography

/** Edits each lyrics mode's provider selection and applies the selections together. */
@Composable
internal fun LyricsProviderDialog(
    initialSelection: Map<LyricsType, Set<LyricsProvider>>,
    onDismiss: () -> Unit,
    onApply: (Map<LyricsType, Set<LyricsProvider>>) -> Unit,
) {
    var draft by remember(initialSelection) { mutableStateOf(initialSelection) }
    val palette = colorPalette()

    DefaultDialog(
        onDismiss = onDismiss,
        horizontalAlignment = Alignment.Start,
        title = stringResource(R.string.lyrics_provider_settings_title),
        footer = {
            ButtonRow(
                buttons = listOf(
                    DialogButton(stringResource(R.string.common_action_cancel), onClick = onDismiss),
                    DialogButton(stringResource(R.string.common_action_apply), onClick = { onApply(draft) }, primary = true),
                )
            )
        }
    ) {
        BasicText(
            text = stringResource(R.string.lyrics_provider_minimum_one),
            style = typography().xs.copy(color = palette.textSecondary),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val inputEnabled = dialogInputEnabled
        LyricsType.entries.forEach { mode ->
            val title = when (mode) {
                LyricsType.Auto -> R.string.lyrics_mode_automatic_label
                LyricsType.Karaoke -> R.string.lyrics_mode_karaoke_label
                LyricsType.Synced -> R.string.lyrics_mode_synchronized_label
                LyricsType.Unsynced -> R.string.lyrics_mode_unsynchronized_label
            }
            BasicText(
                text = stringResource(title),
                style = typography().s.semiBold.copy(color = palette.text),
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .semantics { heading() }
            )
            val selected = draft.getValue(mode)
            LyricsProvider.availableFor(mode).forEach { provider ->
                val checked = provider in selected
                val enabled = inputEnabled && (!checked || selected.size > 1)
                ProviderCheckbox(
                    provider = provider,
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { checkedNow ->
                        val current = draft.getValue(mode)
                        val updated = if (checkedNow) current + provider else current - provider
                        if (updated.isNotEmpty()) draft = draft + (mode to updated)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProviderCheckbox(
    provider: LyricsProvider,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = colorPalette()
    val title = when (provider) {
        LyricsProvider.BetterLyrics -> R.string.lyrics_brand_betterlyrics
        LyricsProvider.LrcLib -> R.string.lyrics_brand_lrclib
        LyricsProvider.KuGou -> R.string.lyrics_brand_kugou
        LyricsProvider.YouTube -> R.string.onboarding_accounts_youtube
    }
    val wordTiming = stringResource(R.string.lyrics_provider_format_word_timed)
    val lineTiming = stringResource(R.string.lyrics_provider_format_line_timed)
    val plainText = stringResource(R.string.lyrics_provider_format_plain_text)
    val format = when (provider) {
        LyricsProvider.BetterLyrics -> "$wordTiming · $lineTiming"
        LyricsProvider.LrcLib -> "$lineTiming · $plainText"
        LyricsProvider.KuGou -> lineTiming
        LyricsProvider.YouTube -> plainText
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = palette.accent,
                uncheckedColor = palette.textSecondary,
                disabledCheckedColor = palette.textSecondary
            ),
            modifier = Modifier.padding(end = 12.dp)
        )
        Column {
            BasicText(text = stringResource(title), style = typography().xs.copy(color = palette.text))
            BasicText(text = format, style = typography().xxs.copy(color = palette.textSecondary))
        }
    }
}
