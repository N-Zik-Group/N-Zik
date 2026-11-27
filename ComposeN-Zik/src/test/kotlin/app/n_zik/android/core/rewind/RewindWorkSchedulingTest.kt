package app.n_zik.android.core.rewind

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import app.n_zik.android.components.settings.common.resetAdditionalSettingsDefaults
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RewindWorkSchedulingTest {
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test
    fun `master toggle cancels and rearms all four jobs for the active profile`() {
        updateRewindWorkPreference(context, DataStoreUtils.KEY_REWIND_ENABLED, false)
        workNames.forEach { name -> verify(exactly = 1) { workManager.cancelUniqueWork(profileWorkName(name)) } }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }

        updateRewindWorkPreference(context, DataStoreUtils.KEY_REWIND_ENABLED, true)
        workNames.forEach(::verifyEnqueued)
    }

    @Test
    fun `changing a reminder toggle only reschedules its corresponding job`() {
        val key = DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED
        updateRewindWorkPreference(context, key, false)
        verify(exactly = 1) { workManager.cancelUniqueWork(profileWorkName(workNames.first())) }

        updateRewindWorkPreference(context, key, true)
        verifyEnqueued(workNames.first())
        verify(exactly = 1) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `reset restores scheduling for previously disabled rewind jobs`() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, false)

        resetAdditionalSettingsDefaults(context)

        workNames.forEach(::verifyEnqueued)
    }

    private fun verifyEnqueued(name: String) {
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(profileWorkName(name), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        }
    }

    private fun profileWorkName(name: String): String =
        ProfileWorkOwner.name(name, checkNotNull(ProfileWorkOwner.capture(context)))

    private val workNames = listOf(
        "RewindReminderWorker", "RewindYearlyReminderWorker",
        "RewindMonthlyPlaylistWorker", "RewindYearlyPlaylistWorker",
    )
}
