package app.n_zik.android.playback.services

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.n_zik.android.appContext
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.playback.exceptions.StreamResolutionOfflineException
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.nextPage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * S5 (offline gate, 2026-10-22): call-site pin for the offline fast-fail in
 * [DataSpec.process] — while offline, a network-only stream id (11 chars) throws
 * [StreamResolutionOfflineException] BEFORE the first Innertube call. The pure predicate
 * is pinned in its own tests; this pins the wiring at the call site (appContext +
 * NetworkQualityHelper, no device context — appContext is stubbed).
 */
class StreamResolutionFastFailCallSiteTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @OptIn(UnstableApi::class)
    fun offlineNetworkOnlyStreamFailsFastWithTheMarkerBeforeAnyInnertubeCall() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns mockk<Context>(relaxed = true)
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        mockkStatic("it.fast4x.innertube.requests.NextPageKt")

        // A bare mock receiver: the offline fast-fail path throws before it dereferences
        // the DataSpec, so no Uri/URL construction is needed (Uri.parse is unmocked on the JVM).
        val dataSpec = mockk<DataSpec>()

        assertThrows(StreamResolutionOfflineException::class.java) {
            dataSpec.process("abcdefghijk", AudioQualityFormat.High, connectionMetered = false)
        }

        coVerify(exactly = 0) { Innertube.nextPage(videoId = "abcdefghijk") }
    }
}
