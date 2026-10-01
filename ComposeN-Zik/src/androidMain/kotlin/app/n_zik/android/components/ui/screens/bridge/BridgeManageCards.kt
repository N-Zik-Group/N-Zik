package app.n_zik.android.components.ui.screens.bridge

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.utils.isIgnoringBatteryOptimizations
import app.it.fast4x.rimusic.utils.semiBold
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.bridge.ActiveDevice
import app.n_zik.android.bridge.AutoStopSettings
import app.n_zik.android.bridge.BridgeServerController
import app.n_zik.android.colorPalette
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAG = "BridgeManageUi"

/**
 * "Connected PC" card of "Manage server": the PC holding the session and Disconnect, which
 * closes its session with `4001 KICKED` without revoking it (contract §6.4) — no confirmation.
 */
@Composable
internal fun ConnectedPcCard(device: ActiveDevice) {
    val palette = colorPalette()
    val scope = rememberCoroutineScope()
    var kicking by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = stringResource(R.string.bridge_connected_pc),
        icon = R.drawable.devices,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = device.deviceName,
                    style = typography().s.semiBold,
                    color = palette.text,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                ManageButton(
                    text = stringResource(R.string.bridge_disconnect),
                    containerColor = palette.red,
                    contentColor = palette.textSecondary,
                    enabled = !kicking,
                    fillWidth = false,
                    onClick = {
                        kicking = true
                        // NonCancellable: the card leaves the composition as soon as the kick ends the
                        // session, the success toast must still show
                        scope.launch(NonCancellable) {
                            // `false`: the PC already left; the card follows activeDevice on its own
                            val kicked = try {
                                BridgeServerController.kick()
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Could not disconnect the active PC")
                                false
                            }
                            if (kicked) Toaster.s(R.string.bridge_disconnected, device.deviceName)
                            kicking = false
                        }
                    },
                )
            }
        },
    )
}

/**
 * "Auto-stop" card: the two settings of contract §11.2, applied live to the running server.
 * Same entries as the N-Zik settings pages (icon entry showing the value, choice dialog).
 */
@Composable
internal fun AutoStopCard(settings: AutoStopSettings) {
    val context = LocalContext.current

    SettingsSectionCard(
        title = stringResource(R.string.bridge_auto_stop),
        icon = R.drawable.time,
        description = stringResource(R.string.bridge_auto_stop_description) + "\n" +
            stringResource(R.string.bridge_auto_stop_inactivity_hint),
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AutoStopEntry(
                    title = stringResource(R.string.bridge_auto_stop_no_client),
                    icon = R.drawable.devices,
                    minutes = settings.noClientMinutes,
                    onSelected = { BridgeServerController.setAutoStopNoClient(context, it) },
                )
                AutoStopEntry(
                    title = stringResource(R.string.bridge_auto_stop_inactivity),
                    icon = R.drawable.sleep,
                    minutes = settings.inactivityMinutes,
                    onSelected = { BridgeServerController.setAutoStopInactivity(context, it) },
                )
            }
        },
    )
}

/** One auto-stop delay: entry showing the current value, dialog offering Never/5/15/30/60 min. */
@Composable
private fun AutoStopEntry(
    title: String,
    icon: Int,
    minutes: Int,
    onSelected: (Int) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val never = stringResource(R.string.bridge_auto_stop_never)
    val valueText: @Composable (Int) -> String = { value ->
        if (value == AutoStopSettings.NEVER) never else stringResource(R.string.bridge_auto_stop_minutes, value)
    }

    OtherSettingsEntry(
        title = title,
        text = valueText(minutes),
        icon = icon,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        ValueSelectorDialog(
            title = title,
            values = AutoStopSettings.OPTIONS,
            selectedValue = minutes,
            onValueSelected = {
                onSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            valueText = valueText,
        )
    }
}

/**
 * Battery optimization warning: shown only while the app is optimized, re-checked on every
 * `ON_RESUME` (the system settings page is a detour). The link asks for the exemption, or opens
 * the optimization list when the request screen does not exist.
 */
@Composable
internal fun BatteryOptimizationWarning() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = colorPalette()
    var optimized by remember { mutableStateOf(!context.isIgnoringBatteryOptimizations) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) optimized = !context.isIgnoringBatteryOptimizations
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        optimized = !context.isIgnoringBatteryOptimizations
    }
    if (!optimized) return

    SettingsSectionCard(
        title = stringResource(R.string.bridge_battery_title),
        icon = R.drawable.battery_opti,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.bridge_battery_warning),
                    style = typography().xs,
                    color = palette.red,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ManageButton(
                    text = stringResource(R.string.bridge_battery_action),
                    containerColor = palette.accent,
                    contentColor = palette.textSecondary,
                    onClick = {
                        runCatching {
                            settingsLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData("package:${context.packageName}".toUri())
                            )
                        }.recoverCatching {
                            Timber.tag(TAG).w("No battery-optimization request screen, opening the settings list")
                            settingsLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }.onFailure {
                            Timber.tag(TAG).e(it, "No battery optimization settings screen")
                        }
                    },
                )
            }
        },
    )
}

@Composable
private fun ManageButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = uiRoundnessShape(),
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier).heightIn(min = 48.dp),
    ) {
        Text(text, style = typography().s.semiBold)
    }
}
