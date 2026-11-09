package app.n_zik.android.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.n_zik.android.MainApplication
import app.n_zik.android.core.backup.LibraryStartup
import app.n_zik.android.core.backup.LibraryStartupException
import app.n_zik.android.core.backup.LibraryStartupState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaylistWidgetRefreshWorkerTest {
    private val state = MutableStateFlow<LibraryStartupState>(LibraryStartupState.Waiting)
    private val startup = mockk<LibraryStartup>(relaxed = true)
    private val app = mockk<MainApplication>()
    private val widgetManager = mockk<AppWidgetManager>()
    private val workManager = mockk<WorkManager>(relaxed = true)
    private lateinit var context: Context

    @Before
    fun setUp() {
        val base = RuntimeEnvironment.getApplication()
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = app
        }
        every { app.libraryStartup } returns startup
        every { startup.state } returns state
        mockkStatic(AppWidgetManager::class)
        mockkObject(WorkManager.Companion)
        every { AppWidgetManager.getInstance(context) } returns widgetManager
        every { WorkManager.getInstance(context) } returns workManager
        mockkObject(PlaylistWidgetManager)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `update and resize coalesce without trying to render while startup is waiting`() {
        val receiver = PlaylistWidgetReceiver()

        receiver.onUpdate(context, widgetManager, intArrayOf(12, 18))
        receiver.onAppWidgetOptionsChanged(context, widgetManager, 12, Bundle())

        verify(exactly = 2) {
            workManager.enqueueUniqueWork(
                PlaylistWidgetRefreshWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
        coVerify(exactly = 0) { PlaylistWidgetManager.refreshIdleWidgets(any(), any(), any()) }
        coVerify(exactly = 0) { PlaylistWidgetManager.updateIdleWidget(any(), any(), any()) }
    }

    @Test
    // The JUnit method joins the worker coroutine after explicitly releasing startup.
    fun `worker reloads current widget ids only after readiness`() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val currentIds = intArrayOf(18, 25)
        coEvery { app.awaitLibraryReady() } coAnswers { ready.await() }
        every { widgetManager.getAppWidgetIds(any()) } returns currentIds
        coEvery { PlaylistWidgetManager.refreshIdleWidgets(context, widgetManager, currentIds) } returns Unit
        val worker = PlaylistWidgetRefreshWorker(context, mockk<WorkerParameters>(relaxed = true))

        val result = async(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }
        verify(exactly = 0) { widgetManager.getAppWidgetIds(any()) }
        ready.complete(Unit)

        assertTrue(result.await() is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { PlaylistWidgetManager.refreshIdleWidgets(context, widgetManager, currentIds) }
    }

    @Test
    // The JUnit method calls the worker directly without starting WorkManager.
    fun `worker requests a retry without reading widgets when startup recovery fails`() = runBlocking {
        val cause = IOException("Journal cannot be read")
        state.value = LibraryStartupState.Failed(cause)
        coEvery { app.awaitLibraryReady() } throws LibraryStartupException(cause)
        val worker = PlaylistWidgetRefreshWorker(context, mockk<WorkerParameters>(relaxed = true))

        assertTrue(worker.doWork() is ListenableWorker.Result.Retry)
        verify(exactly = 1) { startup.start() }
        verify(exactly = 0) { widgetManager.getAppWidgetIds(any()) }
    }
}
