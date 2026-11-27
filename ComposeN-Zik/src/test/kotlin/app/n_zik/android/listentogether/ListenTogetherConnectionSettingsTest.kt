package app.n_zik.android.listentogether

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.appTextResources
import app.n_zik.android.components.settings.common.resetListenTogetherSettingsDefaults
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ListenTogetherConnectionSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val factory = mockk<WebSocket.Factory>()
    private val socket = mockk<WebSocket>(relaxed = true)
    private val request = slot<Request>()
    private val listener = slot<WebSocketListener>()
    private val roomServer = "https://rooms.example.com/first".toHttpUrl()
    private val otherServer = "https://rooms.example.com/second".toHttpUrl()
    private lateinit var client: ListenTogetherClient

    @Before
    fun setUp() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appTextResources() } returns context.resources
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(context) } returns true
        every { NetworkQualityHelper.observeConnection(context) } returns emptyFlow()
        every { factory.newWebSocket(capture(request), capture(listener)) } returns socket
        every { socket.send(any<ByteString>()) } returns true
    }

    @After
    fun tearDown() {
        if (::client.isInitialized) client.disconnect()
        unmockkObject(NetworkQualityHelper)
        unmockkStatic("app.n_zik.android.GlobalVarsKt")
    }

    @Test
    fun `invalid saved address reaches error and a corrected address can connect`() {
        val invalid = " invalid wss://address "
        DataStoreUtils.saveString(context, ListenTogetherClient.PREF_SERVER_URL, invalid)
        startClient()

        client.connect()

        assertEquals(ConnectionState.ERROR, client.connectionState.value)
        assertEquals(invalid, DataStoreUtils.getString(context, ListenTogetherClient.PREF_SERVER_URL))
        verify(exactly = 0) { factory.newWebSocket(any(), any()) }

        DataStoreUtils.saveString(context, ListenTogetherClient.PREF_SERVER_URL, "wss://ROOMS.example.com:443/first")
        client.createRoom("Alice")

        assertEquals(ConnectionState.CONNECTING, client.connectionState.value)
        assertEquals(roomServer, request.captured.url)
    }

    @Test
    fun `room settings reset preserves the original recovery endpoint and credential`() {
        saveSession()
        DataStoreUtils.saveString(context, ListenTogetherClient.PREF_SERVER_URL, otherServer.toString())
        resetListenTogetherSettingsDefaults(context)
        startClient()

        client.connect()
        openHandshake(roomServer)

        assertEquals(roomServer, request.captured.url)
        assertEquals(otherServer.toString(), DataStoreUtils.getString(context, ListenTogetherClient.PREF_SERVER_URL))
        val codec = MessageCodec(true)
        verify(exactly = 1) {
            socket.send(match<ByteString> { frame ->
                val (type, payload) = codec.decode(frame.toByteArray())
                type == MessageTypes.RECONNECT && payload.contentEquals(ReconnectPayload("room-token").toByteArray())
            })
        }
    }

    @Test
    fun `recovery sends nothing when the handshake resolves to another endpoint`() {
        saveSession()
        startClient()

        client.connect()
        openHandshake(otherServer)

        verify(exactly = 0) { socket.send(any<ByteString>()) }
        verify { socket.cancel() }
        assertEquals(ConnectionState.ERROR, client.connectionState.value)
        assertTrue(client.hasPersistedSession)
    }

    @Test
    fun `older token without a server does not restore from the current setting`() {
        val prefs = DataStoreUtils.prefs(context)
        prefs.edit()
            .putString(ListenTogetherClient.PREF_SESSION_TOKEN, "legacy-token")
            .putString(ListenTogetherClient.PREF_ROOM_CODE, "ROOM")
            .putString(ListenTogetherClient.PREF_SESSION_TIMESTAMP, System.currentTimeMillis().toString())
            .putString(ListenTogetherClient.PREF_SERVER_URL, otherServer.toString())
            .apply()
        startClient()

        assertFalse(client.hasPersistedSession)
        client.connect()
        assertEquals(otherServer, request.captured.url)
    }

    private fun startClient() {
        client = ListenTogetherClient(context, factory)
        // The client loads preferences on its application scope outside the test coroutine scheduler.
        runBlocking { withTimeout(5_000) { client.persistedSessionLoaded.first { it } } }
    }

    private fun saveSession() {
        ListenTogetherSessionPreferences(context).save(
            token = "room-token",
            roomCode = "ROOM",
            userId = "user",
            isHost = true,
            timestamp = System.currentTimeMillis(),
            serverUrl = roomServer,
        )
    }

    private fun openHandshake(serverUrl: HttpUrl) {
        val response = Response.Builder()
            .request(Request.Builder().url(serverUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols")
            .build()
        listener.captured.onOpen(socket, response)
    }
}
