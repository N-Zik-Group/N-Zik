package app.n_zik.android.bridge

import app.n_zik.android.bridge.HotspotAddressResolver.NicAddresses
import java.net.Inet4Address
import java.net.InetAddress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HotspotAddressResolverTest {

    private fun ipv4(value: String): Inet4Address = InetAddress.getByName(value) as Inet4Address

    @Test
    fun `picks the private address of the soft-AP interface`() {
        val interfaces = listOf(
            NicAddresses("rmnet_data0", listOf(ipv4("100.64.12.7"))),
            NicAddresses("wlan1", listOf(ipv4("10.187.45.100"))),
        )

        assertEquals(ipv4("10.187.45.100"), HotspotAddressResolver.pickHotspotAddress(interfaces))
    }

    @Test
    fun `recognizes the OEM soft-AP interface names`() {
        listOf("ap0", "swlan0", "softap0", "wlan2").forEach { name ->
            val interfaces = listOf(NicAddresses(name, listOf(ipv4("192.168.43.1"))))

            assertEquals(ipv4("192.168.43.1"), HotspotAddressResolver.pickHotspotAddress(interfaces), name)
        }
    }

    @Test
    fun `never picks cellular or VPN interfaces`() {
        val interfaces = listOf(
            NicAddresses("rmnet_data0", listOf(ipv4("10.20.30.40"))),
            NicAddresses("ccmni0", listOf(ipv4("10.20.30.41"))),
            NicAddresses("tun0", listOf(ipv4("10.8.0.2"))),
        )

        assertNull(HotspotAddressResolver.pickHotspotAddress(interfaces))
    }

    @Test
    fun `ignores public addresses on a soft-AP interface`() {
        val interfaces = listOf(NicAddresses("ap0", listOf(ipv4("8.8.8.8"))))

        assertNull(HotspotAddressResolver.pickHotspotAddress(interfaces))
    }
}
