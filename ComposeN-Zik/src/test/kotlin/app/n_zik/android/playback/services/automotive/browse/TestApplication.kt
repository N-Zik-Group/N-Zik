package app.n_zik.android.playback.services.automotive.browse

import android.app.Application

/**
 * Test-only application: MainApplication.onCreate() migrates credentials via
 * AndroidKeyStore (MasterKey), which is not available on the Robolectric JVM.
 * MainApplication is final, so Dependencies.application is a mock wired to
 * this app's context (same pattern as `MediaItemUtilsTest`). Shared by every
 * test class in the browse package via
 * `@Config(application = TestApplication::class)`.
 */
class TestApplication : Application()
