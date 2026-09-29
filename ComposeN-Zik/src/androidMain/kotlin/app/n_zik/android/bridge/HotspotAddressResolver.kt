package app.n_zik.android.bridge

import java.net.Inet4Address
import java.net.NetworkInterface
import timber.log.Timber

private const val TAG = "BridgeHotspot"

/**
 * Finds the IPv4 address of the phone's own Wi-Fi hotspot (tethering access point), used
 * when the phone is not a Wi-Fi client: PCs connected to the hotspot reach the bridge on
 * this address. Android exposes no public API for it, so the network interfaces are scanned.
 */
internal object HotspotAddressResolver {

    /** Soft-AP interface names used by AOSP and OEM ROMs (ap0, wlan1, swlan0, softap0…). */
    private val HOTSPOT_INTERFACE = Regex("^(ap|wlan|swlan|softap)\\d+$")

    fun currentAddress(): Inet4Address? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .map { nic -> NicAddresses(nic.name, nic.inetAddresses.toList().filterIsInstance<Inet4Address>()) }
            .let(::pickHotspotAddress)
    }.onFailure { Timber.tag(TAG).w(it, "Could not scan network interfaces") }.getOrNull()

    /**
     * Picks the first private IPv4 of a soft-AP interface. Cellular (`rmnet*`, `ccmni*`),
     * VPN (`tun*`, `ppp*`) and any other interface never match, so the bridge can never
     * listen on mobile data (contract §11.1).
     */
    fun pickHotspotAddress(interfaces: List<NicAddresses>): Inet4Address? =
        interfaces
            .filter { HOTSPOT_INTERFACE.matches(it.name) }
            .flatMap { it.addresses }
            .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }

    data class NicAddresses(val name: String, val addresses: List<Inet4Address>)
}
