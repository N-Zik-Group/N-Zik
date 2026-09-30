package app.n_zik.android.bridge.pairing

import app.n_zik.android.bridge.pairing.PairingQrPayload.ParseResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PairingQrPayloadTest {

    private val valid =
        """{"v":1,"type":"nzik-pair","requestId":"q1w2e3r4t5y6u7i8o9p0aa","deviceName":" PC-SALON ","port":53817,"ips":["192.168.1.20","10.0.0.5"]}"""

    @Test
    fun `contract example parses`() {
        val result = PairingQrPayload.parse(valid)

        assertEquals(
            ParseResult.Valid(PairingQrPayload("q1w2e3r4t5y6u7i8o9p0aa", "PC-SALON", 53817, listOf("192.168.1.20", "10.0.0.5"))),
            result,
        )
    }

    @Test
    fun `other QR codes are not pairing codes`() {
        assertEquals(ParseResult.NotPairingQr, PairingQrPayload.parse("https://example.com"))
        assertEquals(ParseResult.NotPairingQr, PairingQrPayload.parse(valid.replace("nzik-pair", "other")))
    }

    @Test
    fun `unsupported version is reported`() {
        assertEquals(ParseResult.UnsupportedVersion, PairingQrPayload.parse(valid.replace("\"v\":1", "\"v\":2")))
    }

    @Test
    fun `out of range fields are invalid`() {
        val cases = listOf(
            valid.replace("q1w2e3r4t5y6u7i8o9p0aa", "short"),
            valid.replace(" PC-SALON ", "   "),
            valid.replace(" PC-SALON ", "x".repeat(65)),
            valid.replace("53817", "70000"),
            valid.replace("""["192.168.1.20","10.0.0.5"]""", "[]"),
            valid.replace("""["192.168.1.20","10.0.0.5"]""", """["10.0.0.1","10.0.0.2","10.0.0.3","10.0.0.4","10.0.0.5"]"""),
            valid.replace("192.168.1.20", "8.8.8.8"),
            valid.replace("192.168.1.20", "192.168.1.300"),
        )
        cases.forEach { assertEquals(ParseResult.Invalid, PairingQrPayload.parse(it), it) }
    }

    @Test
    fun `candidates on the phone subnet come first, keeping the QR order`() {
        val ordered = PairingQrPayload.orderCandidates(
            ips = listOf("10.0.0.5", "192.168.1.20", "172.16.0.3", "192.168.1.30"),
            phoneIp = "192.168.1.14",
            prefixLength = 24,
        )

        assertEquals(listOf("192.168.1.20", "192.168.1.30", "10.0.0.5", "172.16.0.3"), ordered)
    }

    @Test
    fun `private IPv4 ranges follow RFC 1918`() {
        assertTrue(PairingQrPayload.isPrivateIpv4("172.31.255.1"))
        assertFalse(PairingQrPayload.isPrivateIpv4("172.32.0.1"))
        assertFalse(PairingQrPayload.isPrivateIpv4("127.0.0.1"))
    }
}
