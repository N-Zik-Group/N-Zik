package app.n_zik.android.utils

import android.media.AudioDeviceInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import app.n_zik.android.components.menu.player.AudioDeviceType
import app.n_zik.android.playback.services.AudioOutputManager

/**
 * Returns true if the device name suggests it is a pair of earbuds (buds).
 */
fun isBuds(name: String?): Boolean {
    if (name == null) return false
    val lowerName = name.lowercase()
    return lowerName.contains("buds") ||
           lowerName.contains("airpods") ||
           lowerName.contains("earpods") ||
           lowerName.contains("earphone") ||
           lowerName.contains("freebuds") ||
           lowerName.contains("pods") ||
           lowerName.contains("wf") 
}

/**
 * Returns true if the device name suggests it is a speaker.
 */
fun isSpeaker(name: String?): Boolean {
    if (name == null) return false
    val lowerName = name.lowercase()
    return lowerName.contains("speaker") ||
           lowerName.contains("soundbar") ||
           lowerName.contains("homepod") ||
           lowerName.contains("echo") ||
           lowerName.contains("boombox") ||
           lowerName.contains("audio system") ||
           lowerName.contains("sound") ||
           lowerName.contains("audio") ||
           lowerName.contains("stereo") ||
           lowerName.contains("music") ||
           lowerName.contains("box") ||
           lowerName.contains("party") ||
           lowerName.contains("waves")
}

/**
 * Returns true if the device name suggests it is a car infotainment system.
 */
fun isCar(name: String?): Boolean {
    if (name == null) return false
    val lowerName = name.lowercase()
    val carKeywords = listOf(
        "car", "bt_auto", "toyota", "vw", "audi", "ford", "honda", "hyundai", "nissan", "kia",
        "mazda", "renault", "peugeot", "citroen", "fiat", "bmw", "mercedes", "volvo", "subaru",
        "jeep", "dodge", "chevrolet", "buick", "cadillac", "gmc", "tesla", "porsche", "ferrari",
        "lamborghini", "maserati", "bentley", "rolls-royce", "lexus", "acura", "infiniti", "genesis",
        "bt-auto", "auto", "vehicle", "infotainment", "uconnect", "sync", "mycar", "blue&me", "hand-free"
    )
    return carKeywords.any { lowerName.contains(it) }
}

/** Returns the appropriate icon (either a drawable resource ID or a Compose ImageVector) based on the audio device type and name. */
fun getAudioDeviceIcon(type: Int, name: String?, isCarForced: Boolean = false): Any {
    if (isCarForced) return app.n_zik.android.R.drawable.car
    
    return when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        26, 27, 30 -> { // TYPE_BLE_HEADSET, TYPE_BLE_SPEAKER, TYPE_BLE_BROADCAST
            val deviceName = name ?: ""
            when {
                isBuds(deviceName) -> app.n_zik.android.R.drawable.buds
                isSpeaker(deviceName) -> Icons.Filled.SpeakerGroup
                isCar(deviceName) -> app.n_zik.android.R.drawable.car
                else -> Icons.Filled.Headphones
            }
        }
        
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE -> {
            val deviceName = name ?: ""
            if (isCar(deviceName)) app.n_zik.android.R.drawable.car
            else app.n_zik.android.R.drawable.headphones
        }
        
        AudioDeviceInfo.TYPE_BUS -> app.n_zik.android.R.drawable.car

        // Virtual output of the PC bridge (contract §8.5)
        AudioOutputManager.TYPE_BRIDGE_PC -> Icons.Filled.Computer
        
        else -> app.n_zik.android.R.drawable.devices
    }
}

/**
 * Returns the appropriate Compose ImageVector based on the bottom sheet's AudioDeviceType and name.
 */
fun getBottomSheetDeviceIcon(
    type: AudioDeviceType,
    name: String?,
    isCarForced: Boolean = false
): Any {
    if (isCarForced) return app.n_zik.android.R.drawable.car
    
    return when (type) {
        AudioDeviceType.BLUETOOTH -> {
            val deviceName = name ?: ""
            when {
                isBuds(deviceName) -> app.n_zik.android.R.drawable.buds
                isSpeaker(deviceName) -> Icons.Filled.SpeakerGroup
                isCar(deviceName) -> app.n_zik.android.R.drawable.car
                else -> Icons.Filled.Bluetooth
            }
        }
        AudioDeviceType.WIRED_HEADPHONES -> Icons.Filled.Headphones
        AudioDeviceType.USB_HEADSET -> Icons.Filled.Usb
        AudioDeviceType.HDMI -> Icons.Filled.Tv
        AudioDeviceType.EXTERNAL_SPEAKER -> Icons.Filled.Speaker
        AudioDeviceType.PHONE_SPEAKER -> Icons.Filled.PhoneAndroid
        AudioDeviceType.PC -> Icons.Filled.Computer
    }
}
