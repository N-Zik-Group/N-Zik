package app.n_zik.android.components

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers spec-gh-606-threading-g5.md I/O & edge-case matrix row 5: every
 * RecognitionListener callback in [VoiceSearchUtils] reaches its UI lambda
 * directly and synchronously, on the caller (main) thread, with no Handler
 * round-trip. Effects are asserted immediately after each callback returns,
 * without idling the Robolectric main looper - a Handler.post implementation
 * would not have run the lambdas at that point.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's
 * junit-vintage-engine on the JUnit 5 platform — do not convert the annotations
 * to JUnit 5, the Robolectric runner only works with JUnit 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceSearchUtilsTest {

    private val results = mutableListOf<String>()
    private val partialResults = mutableListOf<String>()
    private val listeningStates = mutableListOf<Boolean>()
    private var errorCount = 0
    private var speechDetectedCount = 0
    private var onResultThread: Thread? = null

    @After
    fun tearDown() {
        unmockkAll()
        results.clear()
        partialResults.clear()
        listeningStates.clear()
        errorCount = 0
        speechDetectedCount = 0
        onResultThread = null
    }

    private class Harness(
        val utils: VoiceSearchUtils,
        val recognizer: SpeechRecognizer,
        val listener: CapturingSlot<RecognitionListener>,
    )

    /** Builds a [VoiceSearchUtils] with a fully mocked recognizer (permission granted, recognition available) and captures the listener wired to it. */
    private fun startListeningWith(): Harness {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        val recognizer = mockk<SpeechRecognizer>(relaxed = true)
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns recognizer

        val listener = slot<RecognitionListener>()
        val utils = VoiceSearchUtils(
            context = context,
            onResult = { result ->
                onResultThread = Thread.currentThread()
                results += result
            },
            onPartialResult = { partialResults += it },
            onError = { errorCount++ },
            onListeningStateChanged = { listeningStates += it },
            onSpeechDetected = { speechDetectedCount++ },
        )
        utils.startListening()

        verify { recognizer.setRecognitionListener(capture(listener)) }
        verify { recognizer.startListening(any()) }
        return Harness(utils, recognizer, listener)
    }

    private fun matchesBundle(vararg matches: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, ArrayList(matches.toList()))
    }

    @Test
    fun `onResults is delivered synchronously to onResult on the calling thread`() {
        val harness = startListeningWith()
        val invokingThread = Thread.currentThread()

        harness.listener.captured.onResults(matchesBundle("hello world"))

        assertEquals(listOf("hello world"), results)
        assertEquals(listOf(false), listeningStates)
        assertSame(invokingThread, onResultThread)
        verify { harness.recognizer.stopListening() }
    }

    @Test
    fun `onPartialResults is delivered synchronously to onPartialResult`() {
        val harness = startListeningWith()

        harness.listener.captured.onPartialResults(matchesBundle("partial phrase"))

        assertEquals(listOf("partial phrase"), partialResults)
        assertEquals(emptyList<Boolean>(), listeningStates)
    }

    @Test
    fun `onReadyForSpeech reports listening started`() {
        val harness = startListeningWith()

        harness.listener.captured.onReadyForSpeech(null)

        assertEquals(listOf(true), listeningStates)
    }

    @Test
    fun `onBeginningOfSpeech reports speech detected`() {
        val harness = startListeningWith()

        harness.listener.captured.onBeginningOfSpeech()

        assertEquals(1, speechDetectedCount)
    }

    @Test
    fun `onEndOfSpeech reports listening stopped and stops the recognizer`() {
        val harness = startListeningWith()
        harness.listener.captured.onReadyForSpeech(null)

        harness.listener.captured.onEndOfSpeech()

        assertEquals(listOf(true, false), listeningStates)
        verify { harness.recognizer.stopListening() }
    }

    @Test
    fun `onError before results reports the error and tears the recognizer down`() {
        val harness = startListeningWith()
        harness.listener.captured.onReadyForSpeech(null)

        harness.listener.captured.onError(SpeechRecognizer.ERROR_NETWORK)

        assertEquals(listOf(true, false), listeningStates)
        assertEquals(1, errorCount)
        verify { harness.recognizer.stopListening() }
        verify { harness.recognizer.destroy() }
    }

    @Test
    fun `onError after results is ignored`() {
        val harness = startListeningWith()
        harness.listener.captured.onResults(matchesBundle("final answer"))

        harness.listener.captured.onError(SpeechRecognizer.ERROR_NETWORK)

        assertEquals(0, errorCount)
        assertEquals(listOf(false), listeningStates)
        verify(exactly = 0) { harness.recognizer.destroy() }
    }

    @Test
    fun `stopListening cancels the recognizer and suppresses the end-of-speech state change`() {
        val harness = startListeningWith()

        harness.utils.stopListening()

        assertEquals(listOf(false), listeningStates)
        verify { harness.recognizer.cancel() }
        verify { harness.recognizer.destroy() }

        // The listener may still be invoked after cancel: isCancelled suppresses
        // the duplicate state change and the nulled recognizer makes it a no-op.
        harness.listener.captured.onEndOfSpeech()

        assertEquals(listOf(false), listeningStates)
    }

    @Test
    fun `permission denied reports error without creating a recognizer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        mockkStatic(SpeechRecognizer::class)

        val utils = VoiceSearchUtils(context = context, onResult = { results += it }, onError = { errorCount++ })
        utils.startListening()

        assertEquals(1, errorCount)
        verify(exactly = 0) { SpeechRecognizer.isRecognitionAvailable(any()) }
        verify(exactly = 0) { SpeechRecognizer.createSpeechRecognizer(any()) }
    }

    @Test
    fun `unavailable recognition reports error without creating a recognizer`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns false

        val utils = VoiceSearchUtils(context = context, onResult = { results += it }, onError = { errorCount++ })
        utils.startListening()

        assertEquals(1, errorCount)
        verify(exactly = 0) { SpeechRecognizer.createSpeechRecognizer(any()) }
    }
}
