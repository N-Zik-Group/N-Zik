package app.n_zik.android.bridge

import app.n_zik.android.bridge.pairing.PairedDeviceStore

/** Outcome of checking a device token presented as `Authorization: Bearer <token>`. */
sealed interface AuthResult {
    data class Accepted(val deviceId: String) : AuthResult
    data object Revoked : AuthResult
}

/** Validates device tokens. */
fun interface DeviceAuthenticator {
    fun authenticate(deviceToken: String): AuthResult
}

/** Tokens issued by pairing: unknown or revoked ones are `DEVICE_REVOKED` (contract §2). */
internal class StoreDeviceAuthenticator(private val store: PairedDeviceStore) : DeviceAuthenticator {
    override fun authenticate(deviceToken: String): AuthResult =
        store.authenticate(deviceToken)?.let { AuthResult.Accepted(it) } ?: AuthResult.Revoked
}
