package app.n_zik.android.bridge

/** Outcome of checking a device token presented as `Authorization: Bearer <token>`. */
sealed interface AuthResult {
    data class Accepted(val deviceId: String) : AuthResult
    data object Revoked : AuthResult
}

/** Validates device tokens. Pairing (story 4) provides the real implementation. */
fun interface DeviceAuthenticator {
    fun authenticate(deviceToken: String): AuthResult
}

/** No device can be paired yet: every token is unknown, hence `DEVICE_REVOKED` (contract §2). */
internal val RejectAllAuthenticator = DeviceAuthenticator { AuthResult.Revoked }
