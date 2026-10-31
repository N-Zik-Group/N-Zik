package app.n_zik.android.components.settings.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import app.n_zik.android.components.settings.common.settingsEntryEnter
import app.n_zik.android.components.settings.common.settingsEntryExit
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassJellyEnabledKey
import app.n_zik.android.components.glass.glassJellyIntensityKey
import app.n_zik.android.components.glass.glassRippleEnabledKey
import app.n_zik.android.components.glass.glassRippleIntensityKey
import app.n_zik.android.components.glass.glassRippleSizeKey
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.SliderSettingsEntry
import app.n_zik.android.components.settings.common.settingsSearchTarget
import kotlin.math.roundToInt

/** Shared by onboarding and settings; optical presets leave these interaction choices intact. */
@Composable
internal fun GlassInteractionSettingsCard(config: GlassSettings, store: GlassStore, target: String = "") {
    SettingsSectionCard(
        title = stringResource(R.string.glass_group_interactions),
        icon = R.drawable.sparkles,
        content = {
            OtherSwitchSettingEntry(
                title = stringResource(R.string.glass_ripple_enabled),
                text = stringResource(R.string.glass_ripple_description),
                icon = R.drawable.sparkles,
                isChecked = config.rippleEnabled,
                onCheckedChange = { store.setBoolean(glassRippleEnabledKey, it) },
                modifier = Modifier.testTag("settings.glass.ripple.enabled").settingsSearchTarget("glass.ripple.enabled"),
            )
            AnimatedVisibility(config.rippleEnabled || target in setOf("glass.ripple.intensity", "glass.ripple.size"), enter = settingsEntryEnter, exit = settingsEntryExit) {
                Column {
                    InteractionSlider(R.string.glass_ripple_intensity, R.string.glass_ripple_intensity_description,
                        config.rippleIntensity, 0f..1f, "ripple.intensity", R.drawable.eye) { store.setFloat(glassRippleIntensityKey, it) }
                    InteractionSlider(R.string.glass_ripple_size, R.string.glass_ripple_size_description,
                        config.rippleSize, .5f..2f, "ripple.size", R.drawable.resize) { store.setFloat(glassRippleSizeKey, it) }
                }
            }
            OtherSwitchSettingEntry(
                title = stringResource(R.string.glass_jelly_enabled),
                text = stringResource(R.string.glass_jelly_description),
                icon = R.drawable.shake_gesture,
                isChecked = config.jellyEnabled,
                onCheckedChange = { store.setBoolean(glassJellyEnabledKey, it) },
                modifier = Modifier.testTag("settings.glass.jelly.enabled").settingsSearchTarget("glass.jelly.enabled"),
            )
            AnimatedVisibility(config.jellyEnabled || target == "glass.jelly.intensity", enter = settingsEntryEnter, exit = settingsEntryExit) {
                InteractionSlider(R.string.glass_jelly_intensity, R.string.glass_jelly_intensity_description,
                    config.jellyIntensity, 0f..1f, "jelly.intensity", R.drawable.slow_motion) { store.setFloat(glassJellyIntensityKey, it) }
            }
        },
    )
}

/** Only the dependent controls animate; their saved values survive switching the effect off. */
@Composable
private fun InteractionSlider(@StringRes title: Int, @StringRes description: Int, value: Float,
    range: ClosedFloatingPointRange<Float>, tag: String, @DrawableRes icon: Int, onChange: (Float) -> Unit) {
    SliderSettingsEntry(
        title = stringResource(title), text = stringResource(description),
        state = value, range = range, icon = icon,
        stepSize = 0f, toDisplay = { "${(it * 100).roundToInt()}%" },
        inputValueText = Float::toString, onSlide = onChange,
        modifier = Modifier.testTag("settings.glass.$tag").settingsSearchTarget("glass.$tag"),
    )
}

internal fun glassInteractionKeywordResources(): List<Int> = listOf(
    R.string.glass_group_interactions,
    R.string.glass_ripple_enabled, R.string.glass_ripple_description,
    R.string.glass_ripple_intensity, R.string.glass_ripple_intensity_description,
    R.string.glass_ripple_size, R.string.glass_ripple_size_description,
    R.string.glass_jelly_enabled, R.string.glass_jelly_description,
    R.string.glass_jelly_intensity, R.string.glass_jelly_intensity_description,
)
