package app.n_zik.android.listentogether

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ListenTogetherInviteTest {
    @Test
    fun `generated invite format accepts and normalizes room code`() {
        assertEquals("AB12CD34", parse("https://metrolist.cc/listen?code=ab12CD34"))
        assertEquals("ABC12345", parse("https://METROLIST.CC/listen?code=ABC12345&utm_source=share"))
    }

    @Test
    fun `untrusted hosts paths schemes and malformed codes cannot join`() {
        listOf(
            "http://metrolist.cc/listen?code=ABC12345",
            "https://evil.example/listen?code=ABC12345",
            "https://metrolist.cc.evil.example/listen?code=ABC12345",
            "https://metrolist.cc@evil.example/listen?code=ABC12345",
            "https://user@metrolist.cc/listen?code=ABC12345",
            "https://metrolist.cc:444/listen?code=ABC12345",
            "https://metrolist.cc/listen/extra?code=ABC12345",
            "https://metrolist.cc/watch?code=ABC12345",
            "https://metrolist.cc/listen",
            "https://metrolist.cc/listen?code=",
            "https://metrolist.cc/listen?code=ABC12345&code=OTHER",
            "https://metrolist.cc/listen?code=123456789",
            "https://metrolist.cc/listen?code=SHORT",
            "https://metrolist.cc/listen?code=ABC%2F123",
            "https://metrolist.cc/listen?code=%20ABC12345",
            "mailto:metrolist.cc/listen?code=ABC12345",
        ).forEach { assertNull(it, parse(it)) }
    }

    @Test
    fun `view and shared text deliver the same invitation`() {
        val url = "https://metrolist.cc/listen?code=ABC12345"
        assertEquals(Uri.parse(url), listenTogetherInviteUri(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        assertEquals(Uri.parse(url), listenTogetherInviteUri(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "  $url\n")
        }))
        assertNull(listenTogetherInviteUri(Intent("unrelated", Uri.parse(url))))
        assertNull(listenTogetherInviteUri(Intent(Intent.ACTION_SEND)))
    }

    private fun parse(value: String): String? = parseListenTogetherInvite(Uri.parse(value))

    @Test
    fun `recreation restores deferred invitation but never replays consumed intent`() {
        val uri = Uri.parse("https://metrolist.cc/listen?code=ABCDEFGH")
        val launch = Intent(Intent.ACTION_VIEW, uri)
        assertEquals(uri, restoreListenTogetherInvite(launch, null))
        val pending = Bundle()
        saveListenTogetherInvite(pending, uri)
        assertEquals(uri, restoreListenTogetherInvite(launch, pending))
        val consumed = Bundle()
        saveListenTogetherInvite(consumed, null)
        assertNull(restoreListenTogetherInvite(launch, consumed))
        assertEquals(uri, listenTogetherInviteUri(launch))
    }
}
