package app.n_zik.android.components.menu.player

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

import app.n_zik.android.R
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal

@Immutable
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
    /** Connected PC of the PC bridge: a virtual output (contract §8.5). */
    PC,
}

/** Enumerates outputs, including Bluetooth details. Call on a data dispatcher. */
internal fun loadDevices(
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
                                    if (pairedDevices != null) PermissionUsageJournal.record(context, PermissionFeature.AUDIO_DEVICES, JournalPermission.BLUETOOTH, PermissionEventKind.USED)
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
                            name = customName ?: context.getString(R.string.audio_output_bluetooth_device_label),
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
                            name = context.getString(R.string.audio_output_wired_headphones_label),
                            type = AudioDeviceType.WIRED_HEADPHONES,
                            isConnected = true,
                            isActive = false,
                            deviceId = deviceInfo.id,
                            hardwareType = deviceInfo.type
                        )
                    }
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> {
                        AudioDevice(
                            name = deviceInfo.productName?.toString() ?: context.getString(R.string.audio_output_usb_audio_label),
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
                            name = context.getString(R.string.audio_output_phone_speaker_label),
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
                    isCar -> context.getString(R.string.automotive_title)
                    device.type == AudioDeviceType.PHONE_SPEAKER -> context.getString(R.string.audio_output_this_phone_label)
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
                .thenBy { it.isCar } // Show phone outputs before Android Auto.
                .thenByDescending { it.isActive }
                .thenBy { it.name }
            )

            onSuccess(sortedDevices.distinctBy { it.name })
        } else {
            loadDevicesLegacy(context, onSuccess)
        }
    } catch (e: Exception) {
        Timber.tag("AudioDeviceMenu").w(e, "Failed to load audio devices")
        onError(context.getString(R.string.audio_output_devices_unavailable))
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
private fun loadDevicesLegacy(context: Context, onSuccess: (List<AudioDevice>) -> Unit) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = mutableListOf<AudioDevice>()

    if (audioManager.isBluetoothA2dpOn) {
        devices.add(AudioDevice(context.getString(R.string.audio_output_bluetooth_device_label), AudioDeviceType.BLUETOOTH, true, true))
    }
    if (audioManager.isWiredHeadsetOn) {
        devices.add(AudioDevice(context.getString(R.string.audio_output_wired_headphones_label), AudioDeviceType.WIRED_HEADPHONES, true, true))
    }
    if (audioManager.isSpeakerphoneOn) {
        devices.add(AudioDevice(context.getString(R.string.audio_output_external_speaker_label), AudioDeviceType.EXTERNAL_SPEAKER, true, true))
    }
    if (devices.isEmpty() || !devices.any { it.isActive }) {
        devices.add(AudioDevice(context.getString(R.string.audio_output_phone_speaker_label), AudioDeviceType.PHONE_SPEAKER, true, true))
    }
    onSuccess(devices.filter { it.isActive }.take(1))
}

internal fun checkBluetoothPermission(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
} else true
