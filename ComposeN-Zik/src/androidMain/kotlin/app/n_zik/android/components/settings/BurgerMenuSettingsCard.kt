package app.n_zik.android.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.BurgerMenuSettingsDialog

/**
 * Burger menu customizer card (UI settings tab, spec-customizable-burger-menu, G1 = A):
 * the same shape as the Maintenance / Extensions cards - a compact card with an "Open"
 * entry that opens the customizer dialog (drag reorder + per-item show/hide, persisted
 * in the global `app_settings` store). The burger item in the app header never opens
 * this dialog: its short tap goes to the Settings screen and its long press deep-links
 * here (settings?tab=1&focus=burger, which scrolls to this card). This card is the only
 * entry point to the dialog.
 */
@Composable
fun BurgerMenuSettingsCard(modifier: Modifier = Modifier) {
    // The dialog is a process-wide singleton; hide it when this card leaves composition
    // so a stale isActive cannot re-open it on the next visit.
    DisposableEffect(Unit) {
        onDispose { BurgerMenuSettingsDialog.hideDialog() }
    }
    SettingsSectionCard(
        title = stringResource(R.string.burger_menu),
        icon = R.drawable.burger,
        modifier = modifier,
        content = {
            OtherSettingsEntry(
                title = stringResource(R.string.burger_menu),
                text = stringResource(R.string.burger_menu_customize),
                icon = R.drawable.burger,
                onClick = { BurgerMenuSettingsDialog.showDialog() },
            )
            // The dialog host (composed here so the card is self-contained).
            BurgerMenuSettingsDialog.Render()
        },
    )
}
