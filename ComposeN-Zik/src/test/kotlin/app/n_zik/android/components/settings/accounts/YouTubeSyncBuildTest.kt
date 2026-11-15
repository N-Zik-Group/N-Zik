package app.n_zik.android.components.settings.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeSyncBuildTest {
    @Test
    fun syncIsAvailableOnlyInDebugAndDevBuilds() {
        val buildTypes = listOf("debug", "release64", "release32", "minified64", "minified32",
            "beta64", "beta32", "foss", "dev64", "dev32")

        assertEquals(listOf("debug", "dev64", "dev32"), buildTypes.filter { isYouTubeSyncBuild(it) })
    }
}
