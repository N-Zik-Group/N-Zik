@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.n_zik.android.components.menu.player

import app.it.fast4x.rimusic.utils.conditional
import androidx.compose.foundation.basicMarquee

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import app.n_zik.android.colorPalette
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.ui.sliders.SliderControl
import app.n_zik.android.gridMenuShape
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.getBottomSheetDeviceIcon

import androidx.compose.foundation.lazy.grid.items
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.semiBold

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.key
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.enums.MenuStyle
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.fadeOut
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.ui.window.Dialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import android.media.AudioDeviceCallback
import androidx.car.app.connection.CarConnection
import app.n_zik.android.utils.isCar
import app.n_zik.android.utils.getAudioDeviceIcon
import app.n_zik.android.utils.getBottomSheetDeviceIcon
import androidx.compose.runtime.livedata.observeAsState

data class AudioDevice(
    val name: String,
    val type: AudioDeviceType,
    val isConnected: Boolean,
    val isActive: Boolean = false,
    val batteryLevel: Int? = null,
    val deviceId: Int? = null,
    val isCar: Boolean = false,
    val hardwareType: Int = -1
)

enum class AudioDeviceType {
    BLUETOOTH,
    WIRED_HEADPHONES,
    PHONE_SPEAKER,
    EXTERNAL_SPEAKER,
    USB_HEADSET,
    HDMI,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioDeviceMenu(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
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

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadDevices(context, service?.preferredDeviceId, isCarProjectionActive, onSuccess = { devices ->
                audioDevices = devices
                isLoading = false
            }, onError = { error ->
                errorMessage = error
                isLoading = false
            })
        } else {
            errorMessage = context.getString(R.string.bluetooth_permission_required)
            isLoading = false
        }
    }

    fun refreshDevices() {
        loadDevices(context, service?.preferredDeviceId, currentIsCarProjectionActive, onSuccess = { devices ->
            audioDevices = devices
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            isLoading = false
        }, onError = {
            isLoading = false
        })
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
                bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        val handler = Handler(Looper.getMainLooper())

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
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        }

        val bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshDevices()
                handler.postDelayed({ refreshDevices() }, 1000)
                handler.postDelayed({ refreshDevices() }, 2500)
            }
        }

        context.registerReceiver(
            bluetoothReceiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )

        val batteryPollingRunnable = object : Runnable {
            override fun run() {
                refreshDevices()
                handler.postDelayed(this, 30000)
            }
        }
        handler.postDelayed(batteryPollingRunnable, 30000)

        onDispose {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
                    audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
                }
                context.unregisterReceiver(volumeChangeReceiver)
                context.unregisterReceiver(audioDeviceReceiver)
                context.unregisterReceiver(bluetoothReceiver)
                handler.removeCallbacksAndMessages(null)
            } catch (e: IllegalArgumentException) {
            }
        }
    }

    val menuStyle by rememberPreference(menuStyleKey, MenuStyle.List)

    val menuContent: @Composable (MenuStyle) -> Unit = { style ->
        Column(modifier = Modifier.animateContentSize()) {
            Crossfade(targetState = isLoading to errorMessage, label = "MenuStateTransition") { (loading, error) ->
                if (loading) {
                    val configuration = LocalConfiguration.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (configuration.screenHeightDp * 0.5f).dp)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (error != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                errorMessage = null
                                isLoading = true
                                refreshDevices()
                            }
                        ) {
                            Text(text = stringResource(R.string.retry))
                        }
                    }
                } else {
                    var audioQualityFormat by rememberPreference(audioQualityFormatKey, AudioQualityFormat.Auto)
                    val qualityOptions = listOf(
                        AudioQualityFormat.Auto to stringResource(R.string.audio_quality_automatic),
                        AudioQualityFormat.High to stringResource(R.string.audio_quality_format_high),
                        AudioQualityFormat.Medium to stringResource(R.string.audio_quality_format_medium),
                        AudioQualityFormat.Low to stringResource(R.string.audio_quality_format_low)
                    )
                    
                    if (style == MenuStyle.List) {
                        ListMenu.Menu(title = stringResource(R.string.audio_devices), showDragHandle = true) {
                            SectionTitle(stringResource(R.string.audio_output_title))
                            audioDevices.forEach { dev ->
                                    ListMenu.Entry(
                                        text = dev.name,
                                        enabled = !isCarProjectionActive || dev.isActive,
                                        icon = {
                                            val iconColor = if (dev.isActive) colorPalette().accent else colorPalette().text
                                            val isActuallyCar = dev.isCar || (dev.isActive && isCarProjectionActive)
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
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
                                        modifier = if (dev.isActive) Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()) else Modifier,
                                        subtitle = dev.batteryLevel?.let { "Battery: $it%" },
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
                                        onClick = {
                                        val wasPlaying = binder?.player?.isPlaying == true
                                        binder?.player?.pause()
                                        binder?.setPreferredAudioDevice(dev.deviceId)
                                        refreshDevices()
                                        coroutineScope.launch {
                                            // Poll every 250ms for 3 seconds to catch the volume after hardware route switch
                                            for (i in 1..12) {
                                                kotlinx.coroutines.delay(250)
                                                if (!isUserDragging) {
                                                    val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                                    val newMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                    if (newVol != currentVolume || newMax != maxVolume) {
                                                        currentVolume = newVol
                                                        maxVolume = newMax
                                                    }
                                                }
                                                if (i == 4 && wasPlaying) {
                                                    binder?.player?.play()
                                                }
                                            }
                                        }
                                        }
                                    )
                            }
                            
                            SectionTitle(stringResource(R.string.volume))
                            VolumeRow(
                                currentVolume = currentVolume,
                                maxVolume = maxVolume.toFloat(),
                                enabled = !isCarProjectionActive,
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
                                                .background(
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
                                    modifier = if (isSelected) Modifier.background(colorPalette().accent.copy(alpha = 0.1f), uiRoundnessShape()) else Modifier,
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
                            items(
                                count = audioDevices.size,
                                key = { index -> "${audioDevices[index].deviceId}_${audioDevices[index].isCar}_${index}" }
                            ) { index ->
                                val dev = audioDevices[index]
                                val isActuallyCar = dev.isCar || (dev.isActive && isCarProjectionActive)
                                
                                GridMenu.Entry(
                                    text = dev.name,
                                    enabled = !isCarProjectionActive || dev.isActive,
                                    icon = {
                                        val iconColor = if (dev.isActive) colorPalette().accent else colorPalette().text
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
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
                                    subtitle = dev.batteryLevel?.let { "Battery: $it%" },
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
                                    onClick = {
                                        val wasPlaying = binder?.player?.isPlaying == true
                                        binder?.player?.pause()
                                        binder?.setPreferredAudioDevice(dev.deviceId)
                                        refreshDevices()
                                        coroutineScope.launch {
                                            // Poll every 250ms for 3 seconds to catch the volume after hardware route switch
                                            for (i in 1..12) {
                                                kotlinx.coroutines.delay(250)
                                                if (!isUserDragging) {
                                                    val newVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                                                    val newMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                    if (newVol != currentVolume || newMax != maxVolume) {
                                                        currentVolume = newVol
                                                        maxVolume = newMax
                                                    }
                                                }
                                                if (i == 4 && wasPlaying) {
                                                    binder?.player?.play()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            
                            item(span = { GridItemSpan(maxLineSpan) }) { 
                                SectionTitle(stringResource(R.string.volume)) 
                            }
                            item {
                                VolumeGridEntry(
                                    currentVolume = currentVolume,
                                    maxVolume = maxVolume.toFloat(),
                                    enabled = !isCarProjectionActive,
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
                                    icon = {
                                        val iconColor = if (isSelected) colorPalette().accent else colorPalette().text
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(
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
    }
    menuContent(menuStyle)
}

@Composable
private fun VolumeRow(
    currentVolume: Float,
    maxVolume: Float,
    enabled: Boolean = true,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeComplete: () -> Unit = {}
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .graphicsLayer(alpha = alpha)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = colorPalette().accent,
                modifier = Modifier.size(18.dp)
            )
        }

        BasicText(
            text = stringResource(R.string.volume),
            style = typography().s.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().text
            )
        )

        BasicText(
            text = "${if (maxVolume > 0) kotlin.math.round(((currentVolume / maxVolume) * 100)).toInt() else 0}%",
            style = typography().xxs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().accent
            )
        )

        SliderControl(
            state = currentVolume,
            range = 0f..maxVolume,
            stepSize = 0f,
            drawValuePoints = false,
            showValue = false,
            onSlide = if (enabled) onVolumeChange else ({}),
            onSlideComplete = if (enabled) onVolumeChangeComplete else ({}),
            modifier = Modifier.weight(1f)
        )
    }
}



private fun loadDevices(
    context: Context,
    preferredDeviceId: Int?,
    isCarProjectionActive: Boolean,
    onSuccess: (List<AudioDevice>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = mutableListOf<AudioDevice>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            audioDevices.forEach { deviceInfo ->
                val device = when (deviceInfo.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                        val btDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val bluetoothManager = context.getSystemService(
                                        Context.BLUETOOTH_SERVICE
                                    ) as BluetoothManager
                                    val bluetoothAdapter = bluetoothManager.adapter
                                    val pairedDevices = bluetoothAdapter?.bondedDevices
                                    pairedDevices?.find {
                                        it.name == deviceInfo.productName.toString()
                                    }
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        } else null

                        val customName = btDevice?.let { dev ->
                            @SuppressLint("MissingPermission")
                            val alias = dev.alias
                            val btName = dev.name
                            when {
                                !alias.isNullOrBlank() -> alias
                                !btName.isNullOrBlank() -> btName
                                else -> null
                            }
                        } ?: deviceInfo.productName?.toString()

                        val batteryLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    btDevice?.let { device ->
                                        try {
                                            val method = BluetoothDevice::class.java.getMethod(
                                                "getBatteryLevel"
                                            )
                                            val level = method.invoke(device) as? Int
                                            level
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        } else null

                        AudioDevice(
                            name = customName ?: "Bluetooth Device",
                            type = AudioDeviceType.BLUETOOTH,
                            isConnected = true,
                            isActive = false,
                            batteryLevel = if (batteryLevel != null && batteryLevel >= 0 && batteryLevel <= 100) batteryLevel else null,
                            deviceId = deviceInfo.id,
                            hardwareType = deviceInfo.type
                        )
                    }

                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                        AudioDevice(
                            name = context.getString(R.string.wired_headphones),
                            type = AudioDeviceType.WIRED_HEADPHONES,
                            isConnected = true,
                            isActive = false,
                            deviceId = deviceInfo.id,
                            hardwareType = deviceInfo.type
                        )
                    }
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> {
                        AudioDevice(
                            name = deviceInfo.productName?.toString() ?: "USB Audio",
                            type = AudioDeviceType.USB_HEADSET,
                            isConnected = true,
                            isActive = false,
                            deviceId = deviceInfo.id,
                            hardwareType = deviceInfo.type
                        )
                    }
                    AudioDeviceInfo.TYPE_HDMI -> {
                        AudioDevice(
                            name = "HDMI",
                            type = AudioDeviceType.HDMI,
                            isConnected = true,
                            isActive = false,
                            deviceId = deviceInfo.id,
                            hardwareType = deviceInfo.type
                        )
                    }
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
                        AudioDevice(
                            name = context.getString(R.string.phone_speaker),
                            type = AudioDeviceType.PHONE_SPEAKER,
                            isConnected = true,
                            isActive = false,
                            deviceId = deviceInfo.id,
                            hardwareType = deviceInfo.type
                        )
                    }
                    else -> null
                }
                device?.let { devices.add(it) }
            }

            val activeDevice = determineActiveDevice(audioManager, audioDevices, preferredDeviceId)
            val updatedDevices = devices.map { device ->
                val isActive = device.deviceId == activeDevice?.id
                val isCar = isActive && isCarProjectionActive
                val displayName = when {
                    isCar -> context.getString(R.string.android_auto_1)
                    device.type == AudioDeviceType.PHONE_SPEAKER -> context.getString(R.string.this_phone)
                    else -> device.name
                }
                
                device.copy(
                    name = displayName,
                    isActive = isActive,
                    isCar = isCar,
                    batteryLevel = if (isCar) null else device.batteryLevel
                )
            }

            val sortedDevices = updatedDevices.sortedWith(
                compareBy<AudioDevice> {
                    when (it.type) {
                        AudioDeviceType.PHONE_SPEAKER -> 0
                        AudioDeviceType.WIRED_HEADPHONES -> 1
                        AudioDeviceType.USB_HEADSET -> 2
                        AudioDeviceType.BLUETOOTH -> 3
                        else -> 4
                    }
                }
                .thenBy { it.isCar } // false (Ce téléphone) avant true (Android Auto)
                .thenByDescending { it.isActive }
                .thenBy { it.name }
            )

            onSuccess(sortedDevices.distinctBy { it.name })
        } else {
            loadDevicesLegacy(context, onSuccess, onError)
        }
    } catch (e: Exception) {
        onError("Failed to load devices: ${e.message}")
    }
}

private fun determineActiveDevice(
    audioManager: AudioManager,
    audioDevices: Array<AudioDeviceInfo>,
    preferredDeviceId: Int?
): AudioDeviceInfo? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null

    if (preferredDeviceId != null) {
        val preferred = audioDevices.find { it.id == preferredDeviceId }
        if (preferred != null) return preferred
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val activeConfig = audioManager.activePlaybackConfigurations.firstOrNull()
        val activeDeviceInfo = activeConfig?.audioDeviceInfo
        if (activeDeviceInfo != null) {
            val matched = audioDevices.find { it.id == activeDeviceInfo.id }
            if (matched != null) return matched
        }
    }

    return when {
        audioDevices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ->
            audioDevices.find { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        audioDevices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        } ->
            audioDevices.find {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
        else -> audioDevices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
    }
}

@Suppress("DEPRECATION")
private fun loadDevicesLegacy(context: Context, onSuccess: (List<AudioDevice>) -> Unit, onError: (String) -> Unit) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = mutableListOf<AudioDevice>()

    if (audioManager.isBluetoothA2dpOn) {
        devices.add(AudioDevice("Bluetooth Device", AudioDeviceType.BLUETOOTH, true, true))
    }
    if (audioManager.isWiredHeadsetOn) {
        devices.add(AudioDevice("Wired Headphones", AudioDeviceType.WIRED_HEADPHONES, true, true))
    }
    if (audioManager.isSpeakerphoneOn) {
        devices.add(AudioDevice("External Speaker", AudioDeviceType.EXTERNAL_SPEAKER, true, true))
    }
    if (devices.isEmpty() || !devices.any { it.isActive }) {
        devices.add(AudioDevice("Phone Speaker", AudioDeviceType.PHONE_SPEAKER, true, true))
    }
    onSuccess(devices.filter { it.isActive }.take(1))
}

private fun checkBluetoothPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
} else true



@Composable
private fun VolumeGridEntry(
    currentVolume: Float,
    maxVolume: Float,
    enabled: Boolean = true,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeComplete: () -> Unit = {}
) {
    var isShowingDialog by remember { mutableStateOf(false) }

    if (isShowingDialog && enabled) {
        VolumeDialog(
            currentVolume = currentVolume,
            maxVolume = maxVolume,
            onVolumeChange = onVolumeChange,
            onVolumeChangeComplete = onVolumeChangeComplete,
            onDismiss = { isShowingDialog = false }
        )
    }

    GridMenu.Entry(
        text = stringResource(R.string.volume),
        enabled = enabled,
        icon = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = colorPalette().accent.copy(alpha = 0.1f),
                        shape = uiRoundnessShape()
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = colorPalette().accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        subtitle = "${if (maxVolume > 0) kotlin.math.round(((currentVolume / maxVolume) * 100)).toInt() else 0}%",
        onClick = { isShowingDialog = true },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.chevron_forward),
                tint = colorPalette().textSecondary,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
private fun VolumeDialog(
    currentVolume: Float,
    maxVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeComplete: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var dialogVolume by remember { mutableFloatStateOf(currentVolume) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colorPalette().background1)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = colorPalette().accent.copy(alpha = 0.1f),
                            shape = uiRoundnessShape()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = colorPalette().accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)

                BasicText(
                    text = stringResource(R.string.volume),
                    maxLines = 1,
                    style = typography().s.semiBold.copy(
                        color = colorPalette().text
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .conditional(!isScrollingTextDisabled) {
                            basicMarquee(iterations = Int.MAX_VALUE)
                        }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            BasicText(
                text = "${if (maxVolume > 0) kotlin.math.round(((dialogVolume / maxVolume) * 100)).toInt() else 0}%",
                style = typography().l.semiBold.copy(
                    color = colorPalette().accent
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            SliderControl(
                state = dialogVolume,
                onSlide = { dialogVolume = it },
                onSlideComplete = {
                    onVolumeChange(dialogVolume)
                    onVolumeChangeComplete()
                },
                range = 0f..maxVolume,
                stepSize = 0f,
                drawValuePoints = false,
                showValue = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onDismiss,
                    icon = R.drawable.close,
                    color = colorPalette().textSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
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
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}
