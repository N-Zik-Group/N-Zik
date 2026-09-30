package app.n_zik.android.bridge.pairing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PairingCodeManagerTest {

    private var now = 1_000_000L
    private val manager = PairingCodeManager(clock = { now })

    private fun activeCode(): String = requireNotNull(manager.code.value) { "No active code" }.code

    @Test
    fun `no code is active until pairing mode opens`() {
        assertNull(manager.code.value)
        assertEquals(CodeValidation.Rejected, manager.validate("ABCDEF", null))

        manager.open()

        val code = activeCode()
        assertEquals(6, code.length)
        assertTrue(code.all { it in PairingCodeManager.ALPHABET })
        assertEquals(now + 120_000L, manager.code.value?.expiresAtMs)
    }

    @Test
    fun `display form is ABC-DEF`() {
        assertEquals("K7M-2QX", PairingCode("K7M2QX", 0).display)
    }

    @Test
    fun `manual validation accepts the normalized code once`() {
        manager.open()
        val code = activeCode()
        val typed = "${code.take(3).lowercase()} - ${code.drop(3).lowercase()}"

        assertEquals(CodeValidation.Accepted, manager.validate(typed, null))
        assertNotEquals(code, activeCode())
        assertEquals(CodeValidation.Rejected, manager.validate(code, null))
    }

    @Test
    fun `expired code is rejected and auto refresh replaces it`() {
        manager.open()
        val code = activeCode()
        now += 120_000L

        assertEquals(CodeValidation.Rejected, manager.validate(code, null))
        manager.refreshIfExpired()
        assertNotEquals(code, activeCode())
    }

    @Test
    fun `regenerating invalidates the previous code`() {
        manager.open()
        val old = activeCode()
        manager.regenerate()

        assertEquals(CodeValidation.Rejected, manager.validate(old, null))
        assertEquals(CodeValidation.Accepted, manager.validate(activeCode(), null))
    }

    @Test
    fun `QR path needs the bound requestId and cannot be replayed`() {
        manager.open()
        val code = manager.bindRequestId("q1w2e3r4t5y6u7i8o9p0aa")
        assertNotNull(code)
        assertEquals(activeCode(), code)
        code ?: return

        assertEquals(CodeValidation.Rejected, manager.validate(code, "other-request-id-00000"))
        assertEquals(CodeValidation.Accepted, manager.validate(code, "q1w2e3r4t5y6u7i8o9p0aa"))
        assertNull(manager.bindRequestId("q1w2e3r4t5y6u7i8o9p0aa"))
        assertEquals(CodeValidation.Rejected, manager.validate(activeCode(), "q1w2e3r4t5y6u7i8o9p0aa"))
    }

    @Test
    fun `binding a code about to expire replaces it first`() {
        manager.open()
        val old = activeCode()
        now += 100_000L

        val bound = manager.bindRequestId("q1w2e3r4t5y6u7i8o9p0aa")

        assertNotEquals(old, bound)
        assertEquals(activeCode(), bound)
        assertEquals(CodeValidation.Accepted, manager.validate(activeCode(), "q1w2e3r4t5y6u7i8o9p0aa"))
    }

    @Test
    fun `ten failures invalidate the code`() {
        manager.open()
        val code = activeCode()
        repeat(9) { assertEquals(CodeValidation.Rejected, manager.validate("222222".takeIf { it != code } ?: "333333", null)) }
        assertEquals(code, activeCode())

        manager.validate("not a code", null)

        assertNotEquals(code, activeCode())
    }

    @Test
    fun `closing pairing mode clears the code`() {
        manager.open()
        val code = activeCode()
        manager.close()

        assertNull(manager.code.value)
        assertEquals(CodeValidation.Rejected, manager.validate(code, null))
    }

    @Test
    fun `normalization rejects characters outside the alphabet`() {
        assertEquals("ABCDEF", PairingCodeManager.normalize("abc-def"))
        assertNull(PairingCodeManager.normalize("ABCDE0"))
        assertNull(PairingCodeManager.normalize("ABCDEFG"))
    }
}
