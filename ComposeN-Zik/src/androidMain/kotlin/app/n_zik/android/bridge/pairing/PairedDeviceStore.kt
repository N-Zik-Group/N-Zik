package app.n_zik.android.bridge.pairing

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import timber.log.Timber

private const val TAG = "BridgeDeviceStore"
private const val DEVICE_ID_BYTES = 8
private const val DEVICE_TOKEN_BYTES = 32

/** A PC paired with this phone. Only the SHA-256 of its token is ever kept. */
@Serializable
data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val tokenSha256: String,
    val pairedAtMs: Long,
)

/** Credential returned once, at pairing time (contract §4.5); the token is never stored. */
class IssuedDevice(val deviceId: String, val deviceToken: String) {
    override fun toString(): String = "IssuedDevice(deviceId=$deviceId)"
}

/** Paired devices of contract §2 / §4.7. */
interface PairedDeviceStore {
    val devices: StateFlow<List<PairedDevice>>

    /** Registers a new device and returns its credential (new `deviceId` and `deviceToken`). */
    fun issue(deviceName: String): IssuedDevice

    /** `deviceId` of the device owning [deviceToken], `null` when unknown or revoked. */
    fun authenticate(deviceToken: String): String?

    /** Forgets the device; `true` when it existed. Other devices are untouched. */
    fun revoke(deviceId: String): Boolean
}

/** Where [JsonPairedDeviceStore] keeps its JSON document. */
interface PairedDeviceStorage {
    fun read(): String?
    fun write(json: String)
}

/** Test and fallback storage: nothing survives the process. */
class InMemoryPairedDeviceStorage : PairedDeviceStorage {
    @Volatile private var json: String? = null
    override fun read(): String? = json
    override fun write(json: String) {
        this.json = json
    }
}

/** Storage in the app's `encryptedPreferences` (AES-256, per N-Zik profile). */
class SharedPreferencesPairedDeviceStorage(private val preferences: SharedPreferences) : PairedDeviceStorage {
    override fun read(): String? = preferences.getString(KEY, null)
    override fun write(json: String) {
        check(preferences.edit().putString(KEY, json).commit()) { "Could not persist paired devices" }
    }

    private companion object {
        const val KEY = "bridgePairedDevices"
    }
}

/**
 * [PairedDeviceStore] serialized as a JSON list in a [PairedDeviceStorage]. Tokens are
 * 256 random bits and ids 64 random bits, both base64url without padding (contract §2).
 */
class JsonPairedDeviceStore(
    private val storage: PairedDeviceStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) : PairedDeviceStore {
    private val serializer = ListSerializer(PairedDevice.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val _devices = MutableStateFlow(load())
    override val devices: StateFlow<List<PairedDevice>> = _devices.asStateFlow()

    @Synchronized
    override fun issue(deviceName: String): IssuedDevice {
        val existingIds = _devices.value.map { it.deviceId }.toSet()
        var deviceId: String
        do {
            deviceId = randomBase64Url(DEVICE_ID_BYTES)
        } while (deviceId in existingIds)
        val token = randomBase64Url(DEVICE_TOKEN_BYTES)
        val device = PairedDevice(deviceId, deviceName, sha256Hex(token), clock())
        save(_devices.value + device)
        Timber.tag(TAG).i("Device $deviceId paired")
        return IssuedDevice(deviceId, token)
    }

    override fun authenticate(deviceToken: String): String? {
        val presented = sha256Hex(deviceToken).toByteArray()
        var match: String? = null
        // Constant-time comparison over every device: no early exit on the first match
        for (device in _devices.value) {
            if (MessageDigest.isEqual(presented, device.tokenSha256.toByteArray())) match = device.deviceId
        }
        return match
    }

    @Synchronized
    override fun revoke(deviceId: String): Boolean {
        val remaining = _devices.value.filterNot { it.deviceId == deviceId }
        if (remaining.size == _devices.value.size) return false
        save(remaining)
        Timber.tag(TAG).i("Device $deviceId revoked")
        return true
    }

    private fun load(): List<PairedDevice> {
        val stored = runCatching { storage.read() }
            .onFailure { Timber.tag(TAG).e(it, "Could not read paired devices") }
            .getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, stored) }
            .onFailure { Timber.tag(TAG).e(it, "Stored paired devices are unreadable, starting empty") }
            .getOrDefault(emptyList())
    }

    private fun save(devices: List<PairedDevice>) {
        storage.write(json.encodeToString(serializer, devices))
        _devices.value = devices
    }

    private fun randomBase64Url(bytes: Int): String =
        Base64Url.encode(ByteArray(bytes).also(random::nextBytes))

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
