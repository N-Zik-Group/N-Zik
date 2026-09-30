package app.n_zik.android.components

import androidx.compose.runtime.Composable
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.playback.services.PlayerServiceModern

/**
 * Issue #866 (gh-866): shuffle button icon, with the transient app-wide confirmation flash —
 * `R.drawable.shuffle_ok` while the flash is active (any shuffle press: Android Auto, media
 * notification, in-app UI), otherwise the plain `R.drawable.shuffle`. Pure so the
 * flash → drawable mapping is unit-testable.
 */
fun shuffleButtonIconRes(flashActive: Boolean): Int =
    if (flashActive) R.drawable.shuffle_ok else R.drawable.shuffle

/** Issue #866 (gh-866): [shuffleButtonIconRes] resolved from the live binder's flash state. */
fun shuffleButtonIconRes(binder: PlayerServiceModern.Binder?): Int =
    shuffleButtonIconRes(binder?.shuffleOkFlashActive == true)

/**
 * Issue #866 (gh-866): [shuffleButtonIconRes] from the composition's binder — for call sites
 * inside composables (`icon = shuffleButtonIcon()`).
 */
@Composable
fun shuffleButtonIcon(): Int = shuffleButtonIconRes(LocalPlayerServiceBinder.current)

/**
 * Issue #866 (gh-866): radio button icon, state-dependent — `R.drawable.radio_stop` while the
 * radio is active, `R.drawable.radio` while inactive. The state change IS the press feedback
 * (no ok badge: at button size its check read as an "X" = disabled, user feedback 2026-09-30).
 * Pure so the state → drawable mapping is unit-testable.
 */
fun radioButtonIconRes(isRadioActive: Boolean): Int =
    if (isRadioActive) R.drawable.radio_stop else R.drawable.radio

/** Issue #866 (gh-866): [radioButtonIconRes] resolved from the live binder's radio state. */
fun radioButtonIconRes(binder: PlayerServiceModern.Binder?): Int =
    radioButtonIconRes(binder?.isRadioActive == true)

/**
 * Issue #866 (gh-866): [radioButtonIconRes] from the composition's binder — for call sites
 * inside composables (`icon = radioButtonIcon()`).
 */
@Composable
fun radioButtonIcon(): Int = radioButtonIconRes(LocalPlayerServiceBinder.current)

/**
 * Issue #866 (gh-866): discover button icon, state-dependent — `R.drawable.discover_stop`
 * (discover + "stop" badge) while discover mode is ON, `R.drawable.discover` while OFF —
 * same treatment as the radio (the state change is the press feedback). Each discover
 * button already holds its reactive `rememberPreference(discoverKey)` state (the same one
 * driving its accent color — `OnSharedPreferenceChangeListener` picks up the central
 * `NZikRadio.toggleDiscover()` write), so the icon is mapped from that local state.
 * Pure so the state → drawable mapping is unit-testable.
 */
fun discoverButtonIconRes(isDiscoverEnabled: Boolean): Int =
    if (isDiscoverEnabled) R.drawable.discover_stop else R.drawable.discover
