@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.n_zik.android.components.menu.player

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.car.app.connection.CarConnection
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.menu.menuSectionHorizontalAlignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.bridge.ActiveDevice
import app.n_zik.android.bridge.AudioOutput
import app.n_zik.android.bridge.BridgeServerController
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.glassAwareContentSize
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.theme.paletteDarkness
import app.n_zik.android.components.ui.UiButton as Button
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.AudioQualityFormat
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.audioQualityFormatKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getBottomSheetDeviceIcon
import app.n_zik.android.utils.isCar
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.shimmerEffect

/**
 * Adds the PC holding the bridge session ([pc], `null` when none) to [devices]. While the
 * output is the PC it is the only active entry; [fallbackName] names a PC without one.
 *
 * Counterpart of `AudioOutputManager.withBridgePc` (the mini-picker `AudioDevice` model,
 * sorted first while active): the same contract §8.5 rule on two different models — keep
 * both in sync when the rule changes.
 */
internal fun formatAudioDeviceBattery(context: Context, level: Int): String {
    val pattern = context.getString(R.string.audio_device_battery_format)
    val format = if (pattern.endsWith('%') && !pattern.endsWith("%%")) "$pattern%" else pattern
    return String.format(context.resources.configuration.locales[0], format, level)
}

internal fun Modifier.audioOptionSelection(isSelected: Boolean): Modifier = semantics {
    selected = isSelected
    role = Role.RadioButton
}

internal fun withBridgePcEntry(
    devices: List<AudioDevice>,
    pc: ActiveDevice?,
    output: AudioOutput,
    fallbackName: String,
): List<AudioDevice> {
    if (pc == null) return devices
    val onPc = output == AudioOutput.PC
    val pcEntry = AudioDevice(
        name = pc.deviceName.ifBlank { fallbackName },
        type = AudioDeviceType.PC,
        isConnected = true,
        isActive = onPc,
    )
    return (if (onPc) devices.map { it.copy(isActive = false, isCar = false) } else devices) + pcEntry
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioDeviceMenu(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var audioDevices by remember { mutableStateOf<List<AudioDevice>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var currentVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    var isUserDragging by remember { mutableStateOf(false) }
    var maxVolume by remember { mutableStateOf(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }
    val coroutineScope = rememberCoroutineScope()

    val binder = LocalPlayerServiceBinder.current
    val service = binder?.service
    var showDevicePopup by remember { mutableStateOf(false) }

    val carConnection = remember { CarConnection(context) }
    val carConnectionType by carConnection.type.observeAsState(CarConnection.CONNECTION_TYPE_NOT_CONNECTED)
    val isCarProjectionActive = carConnectionType == CarConnection.CONNECTION_TYPE_PROJECTION || carConnectionType == CarConnection.CONNECTION_TYPE_NATIVE

    val currentIsCarProjectionActive by rememberUpdatedState(isCarProjectionActive)

    // PC bridge (contract §8.5): the connected PC is one more output
    val bridgePc by BridgeServerController.activeDevice.collectAsStateWithLifecycle()
    val bridgeOutput by BridgeServerController.audioOutput.collectAsStateWithLifecycle()
    val bridgePcFallbackName = stringResource(R.string.bridge_connected_pc)
    val shownDevices = remember(audioDevices, bridgePc, bridgeOutput, bridgePcFallbackName) {
        withBridgePcEntry(audioDevices, bridgePc, bridgeOutput, bridgePcFallbackName)
    }

    val bridgePcSubtitle = stringResource(R.string.bridge_audio_output_pc_subtitle)

    fun selectBridgeOutput(output: AudioOutput) {
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            if (!BridgeServerController.selectAudioOutput(output)) Toaster.w(R.string.bridge_audio_output_pc_unavailable)
        }
    }

    fun refreshDevices() {
        coroutineScope.launch(NzikDispatchers.DATA) {
            loadDevices(context, service?.preferredDeviceId, currentIsCarProjectionActive, onSuccess = { devices ->
                audioDevices = devices
                errorMessage = null
                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                isLoading = false
            }, onError = { error ->
                if (isLoading) errorMessage = error
                isLoading = false
            })
        }
    }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        PermissionUsageJournal.result(context, PermissionFeature.AUDIO_DEVICES, Manifest.permission.BLUETOOTH_CONNECT, isGranted)
        if (!isGranted) Toaster.w(R.string.audio_output_bluetooth_permission_description)
        refreshDevices()
    }

    fun selectDevice(device: AudioDevice) {
        if (device.type == AudioDeviceType.PC) {
            selectBridgeOutput(AudioOutput.PC)
            return
        }
        if (binder?.setPreferredAudioDevice(device.deviceId) != true) return
        if (BridgeServerController.audioOutput.value == AudioOutput.PC) selectBridgeOutput(AudioOutput.PHONE)
        refreshDevices()
        coroutineScope.launch {
            repeat(12) {
                delay(250)
                if (!isUserDragging) {
                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                }
            }
        }
    }

    LaunchedEffect(service?.preferredDeviceId, carConnectionType) {
        refreshDevices()
    }

    DisposableEffect(Unit) {
        val volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC && !isUserDragging) {
                        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    }
                }
            }
        }

        val audioDeviceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshDevices()
            }
        }

        context.registerReceiver(
            volumeChangeReceiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.registerReceiver(
                audioDeviceReceiver,
                IntentFilter().apply {
                    addAction(AudioManager.ACTION_HEADSET_PLUG)
                    addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                    }
                }
            )
        }

        if (!checkBluetoothPermission(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionUsageJournal.record(context, PermissionFeature.AUDIO_DEVICES, JournalPermission.BLUETOOTH, PermissionEventKind.REQUESTED)
                bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        // Timers below; cancelled in onDispose.
        val timerScope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)

        val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    refreshDevices()
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    refreshDevices()
                }
            }
        } else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null) // null = main looper
        }

        val bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshDevices()
                timerScope.launch { delay(1000); refreshDevices() }
                timerScope.launch { delay(2500); refreshDevices() }
            }
        }

        context.registerReceiver(
            bluetoothReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )

        timerScope.launch {
            while (true) {
                delay(30000)
                refreshDevices()
            }
        }

        onDispose {
            timerScope.cancel()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
                    audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
                }
                context.unregisterReceiver(volumeChangeReceiver)
                context.unregisterReceiver(audioDeviceReceiver)
                context.unregisterReceiver(bluetoothReceiver)
            } catch (error: IllegalArgumentException) {
                Timber.tag("AudioDeviceMenu").d("Unable to unregister audio device receiver (%s)", error.javaClass.simpleName)
            }
        }
    }

    val menuStyle by rememberPreference(menuStyleKey, MenuStyle.List)

    Column(modifier = Modifier.glassAwareContentSize()) {
        val error = errorMessage
        if (!isLoading && error != null) {
            AudioDeviceStatusMenu(error = error, menuStyle = menuStyle) {
                errorMessage = null
                isLoading = true
                refreshDevices()
            }
        } else {
            var audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.Auto)
            val selectedRowBackground = Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape())
            val qualityOptions = listOf(
                AudioQualityFormat.Auto to stringResource(R.string.audio_quality_automatic),
                AudioQualityFormat.High to stringResource(R.string.audio_quality_format_high),
                AudioQualityFormat.Low to stringResource(R.string.audio_quality_format_low)
            )
            if (menuStyle == MenuStyle.List) {
                ListMenu.Menu(title = stringResource(R.string.audio_devices), showDragHandle = true) {
                    SectionTitle(stringResource(R.string.audio_output_title))
                    if (isLoading) {
                        AudioDeviceListLoadingRows()
                    } else shownDevices.forEach { dev ->
                            ListMenu.Entry(
                                text = dev.name,
                                enabled = !isCarProjectionActive || dev.isActive,
                                icon = {
                                    val iconColor = if (dev.isActive) colorPalette().accent else colorPalette().text
                                    val isActuallyCar = dev.isCar || (dev.type != AudioDeviceType.PC && dev.isActive && isCarProjectionActive)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .iconBadgeBackground(
                                                color = if (dev.isActive) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                                shape = uiRoundnessShape()
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val iconRes = getBottomSheetDeviceIcon(dev.type, dev.name, isActuallyCar)
                                        if (iconRes is ImageVector) {
                                            Icon(
                                                imageVector = iconRes,
                                                contentDescription = null,
                                                tint = iconColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else if (iconRes is Int) {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = null,
                                                tint = iconColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.audioOptionSelection(dev.isActive)
                                    .then(if (dev.isActive) selectedRowBackground else Modifier),
                                subtitle = if (dev.type == AudioDeviceType.PC) bridgePcSubtitle else dev.batteryLevel?.let { formatAudioDeviceBattery(context, it) },
                                trailingContent = {
                                    AnimatedVisibility(
                                        visible = dev.isActive,
                                        enter = fadeIn() + scaleIn(),
                                        exit = fadeOut() + scaleOut()
                                    ) {
                                        RadioButton(
                                            selected = true,
                                            onClick = null,
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = colorPalette().accent,
                                                unselectedColor = colorPalette().textSecondary
                                            )
                                        )
                                    }
                                },
                                onClick = { selectDevice(dev) }
                            )
                    }
                    SectionTitle(stringResource(R.string.player_statistics_volume_label))
                    VolumeRow(
                        currentVolume = currentVolume,
                        maxVolume = maxVolume.toFloat(),
                        enabled = !isLoading && !isCarProjectionActive,
                        onVolumeChange = { newVolume ->
                            isUserDragging = true
                            currentVolume = newVolume
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                newVolume.toInt(),
                                0
                            )
                        },
                        onVolumeChangeComplete = {
                            isUserDragging = false
                        }
                    )
                    SectionTitle(stringResource(R.string.audio_quality_format))
                    qualityOptions.forEach { (format, label) ->
                        val isSelected = audioQualityFormat == format
                        ListMenu.Entry(
                            text = label,
                            icon = {
                                val iconColor = if (isSelected) colorPalette().accent else colorPalette().text
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .iconBadgeBackground(
                                            color = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                            shape = uiRoundnessShape()
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.audio_quality),
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.audioOptionSelection(isSelected)
                                .then(if (isSelected) selectedRowBackground else Modifier),
                            trailingContent = {
                                if (isSelected) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = colorPalette().accent,
                                            unselectedColor = colorPalette().textSecondary
                                        )
                                    )
                                }
                            },
                            onClick = { audioQualityFormat = format }
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                GridMenu.Menu(title = stringResource(R.string.audio_devices), showDragHandle = true) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(stringResource(R.string.audio_output_title))
                    }
                    if (isLoading) {
                        items(count = AudioDeviceLoadingCount, key = { "audio-device-loading-$it" }) { index ->
                            AudioDeviceGridLoadingTile(
                                modifier = if (index == 0) Modifier.progressSemantics() else Modifier,
                            )
                        }
                    } else items(
                        count = shownDevices.size,
                        key = { index -> "${shownDevices[index].deviceId}_${shownDevices[index].isCar}_${index}" }
                    ) { index ->
                        val dev = shownDevices[index]
                        val isActuallyCar = dev.isCar || (dev.type != AudioDeviceType.PC && dev.isActive && isCarProjectionActive)
                        GridMenu.Entry(
                            text = dev.name,
                            enabled = !isCarProjectionActive || dev.isActive,
                            modifier = Modifier.audioOptionSelection(dev.isActive),
                            icon = {
                                val iconColor = if (dev.isActive) colorPalette().accent else colorPalette().text
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .iconBadgeBackground(
                                            color = if (dev.isActive) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                            shape = uiRoundnessShape()
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val iconRes = getBottomSheetDeviceIcon(dev.type, dev.name, isActuallyCar)
                                    if (iconRes is ImageVector) {
                                        Icon(
                                            imageVector = iconRes,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else if (iconRes is Int) {
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            subtitle = if (dev.type == AudioDeviceType.PC) bridgePcSubtitle else dev.batteryLevel?.let { formatAudioDeviceBattery(context, it) },
                            trailingContent = {
                                AnimatedVisibility(
                                    visible = dev.isActive,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    RadioButton(
                                        selected = true,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = colorPalette().accent,
                                            unselectedColor = colorPalette().textSecondary
                                        )
                                    )
                                }
                            },
                            onClick = { selectDevice(dev) }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(stringResource(R.string.player_statistics_volume_label))
                    }
                    item {
                        VolumeGridEntry(
                            currentVolume = currentVolume,
                            maxVolume = maxVolume.toFloat(),
                            enabled = !isLoading && !isCarProjectionActive,
                            onVolumeChange = { newVolume ->
                                isUserDragging = true
                                currentVolume = newVolume
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    newVolume.toInt(),
                                    0
                                )
                            },
                            onVolumeChangeComplete = {
                                isUserDragging = false
                            }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(stringResource(R.string.audio_quality_format))
                    }
                    items(
                        count = qualityOptions.size,
                        key = { index -> qualityOptions[index].first.name }
                    ) { index ->
                        val (format, label) = qualityOptions[index]
                        val isSelected = audioQualityFormat == format
                        GridMenu.Entry(
                            text = label,
                            modifier = Modifier.audioOptionSelection(isSelected),
                            icon = {
                                val iconColor = if (isSelected) colorPalette().accent else colorPalette().text
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .iconBadgeBackground(
                                            color = if (isSelected) colorPalette().accent.copy(alpha = 0.2f) else colorPalette().accent.copy(alpha = 0.1f),
                                            shape = uiRoundnessShape()
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.audio_quality),
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            trailingContent = {
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    RadioButton(
                                        selected = true,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = colorPalette().accent,
                                            unselectedColor = colorPalette().textSecondary
                                        )
                                    )
                                }
                            },
                            onClick = { audioQualityFormat = format }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

/** Uses the same menu surface for the progress and retry content. */
@Composable
internal fun AudioDeviceStatusMenu(
    error: String?,
    menuStyle: MenuStyle = MenuStyle.List,
    onRetry: () -> Unit,
) {
    val title = stringResource(R.string.audio_devices)
    if (menuStyle == MenuStyle.Grid) {
        GridMenu.Menu(title = title) {
            if (error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { AudioDeviceRetry(error, onRetry) }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(stringResource(R.string.audio_output_title))
                }
                items(count = AudioDeviceLoadingCount, key = { "audio-device-loading-$it" }) { index ->
                    AudioDeviceGridLoadingTile(
                        modifier = if (index == 0) Modifier.progressSemantics() else Modifier,
                    )
                }
            }
        }
    } else {
        ListMenu.Menu(title = title) {
            if (error != null) AudioDeviceRetry(error, onRetry)
            else {
                SectionTitle(stringResource(R.string.audio_output_title))
                AudioDeviceListLoadingRows()
            }
        }
    }
}

@Composable
private fun AudioDeviceRetry(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color.hsl(0f, .8f, .35f + .30f * paletteDarkness()),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = colorPalette().text,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.common_action_retry))
        }
    }
}

private const val AudioDeviceLoadingCount = 3

@Composable
private fun AudioDeviceListLoadingRows() {
    repeat(AudioDeviceLoadingCount) { index ->
        ListMenu.Entry(
            text = "",
            icon = { AudioDeviceIconPlaceholder() },
            modifier = if (index == 0) Modifier.progressSemantics() else Modifier,
            loading = true,
        )
    }
}

@Composable
private fun AudioDeviceGridLoadingTile(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        GridMenu.Entry(
            text = "",
            icon = { AudioDeviceIconPlaceholder() },
            enabled = false,
        )
        ItemTextPlaceholder(
            text = null,
            style = typography().xs.semiBold,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun AudioDeviceIconPlaceholder() {
    Spacer(Modifier.size(32.dp).clip(uiRoundnessShape()).shimmerEffect())
}

@Composable
private fun SectionTitle(title: String) {
    BasicText(
        text = title,
        style = typography().xxs.semiBold.copy(
            color = colorPalette().accent,
            textAlign = TextAlign.Start
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .menuSectionHorizontalAlignment()
    )
}
