package app.n_zik.android.listentogether

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

@OptIn(UnstableApi::class)
class QueueNavigationCommandsTest {
    private val navigation = setOf(
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    )
    private val compatibilityCommands = navigation + setOf(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_PREPARE,
        Player.COMMAND_STOP,
    )
    private val upstream = mockk<Player>(relaxed = true)
    private val guard = ListenTogetherGuestGuardPlayer(upstream, mockk<Context>(relaxed = true))

    @AfterEach
    fun resetGuestLock() {
        listenTogetherGuestLock.value = false
    }

    @Test
    fun `last item disables both next commands while preserving current track restart`() {
        every { upstream.availableCommands } returns commands(setOf(Player.COMMAND_SEEK_TO_PREVIOUS))

        assertFalse(guard.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(guard.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(guard.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertFalse(guard.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertTrue(guard.availableCommands.contains(Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun `repeat shuffle and radio queue changes retain the current upstream navigation policy`() {
        // Native commands already account for repeat wrapping, shuffle order and actual
        // radio queue append completion. The facade must follow each replacement immediately.
        var available = commands(emptySet())
        every { upstream.availableCommands } answers { available }
        for (nativeNavigation in listOf(emptySet(), navigation, setOf(Player.COMMAND_SEEK_TO_NEXT))) {
            available = commands(nativeNavigation)
            for (command in navigation) {
                assertEquals(command in nativeNavigation, guard.isCommandAvailable(command))
                assertEquals(command in nativeNavigation, guard.availableCommands.contains(command))
            }
        }
    }

    @Test
    fun `live edge next stays distinct from next media item`() {
        every { upstream.availableCommands } returns commands(setOf(Player.COMMAND_SEEK_TO_NEXT))

        assertTrue(guard.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertFalse(guard.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    }

    @Test
    fun `notification and automotive listeners receive filtered navigation and guest commands`() {
        val registered = slot<Player.Listener>()
        every { upstream.addListener(capture(registered)) } answers { }
        val published = slot<Player.Commands>()
        val listener = mockk<Player.Listener>(relaxed = true)
        every { listener.onAvailableCommandsChanged(capture(published)) } answers { }
        guard.addListener(listener)

        registered.captured.onAvailableCommandsChanged(commands(setOf(Player.COMMAND_SEEK_TO_PREVIOUS)))
        assertTrue(published.captured.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertFalse(published.captured.contains(Player.COMMAND_SEEK_TO_NEXT))

        listenTogetherGuestLock.value = true
        registered.captured.onAvailableCommandsChanged(commands(navigation))
        navigation.forEach { assertFalse(published.captured.contains(it)) }
        assertFalse(published.captured.contains(Player.COMMAND_STOP))
        assertTrue(published.captured.contains(Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun `listener adapters retain facade identity deduplication and removal`() {
        val registered = slot<Player.Listener>()
        every { upstream.addListener(capture(registered)) } answers { }
        val eventPlayer = slot<Player>()
        val listener = mockk<Player.Listener>(relaxed = true)
        every { listener.onEvents(capture(eventPlayer), any()) } answers { }
        guard.addListener(listener)
        guard.addListener(listener)

        registered.captured.onEvents(upstream, mockk<Player.Events>())
        assertSame(guard, eventPlayer.captured)
        verify(exactly = 1) { upstream.addListener(any()) }

        guard.removeListener(listener)
        verify(exactly = 1) { upstream.removeListener(registered.captured) }
    }

    @Test
    fun `every Media3 listener callback is explicitly forwarded including Java default methods`() {
        val received = mutableListOf<Pair<String, List<Any?>>>()
        val listener = Proxy.newProxyInstance(
            Player.Listener::class.java.classLoader,
            arrayOf(Player.Listener::class.java),
        ) { _, method, arguments ->
            received.add(method.toGenericString() to arguments.orEmpty().toList())
            null
        } as Player.Listener
        val adapter = CommandFilteringPlayerListener(listener) { it }
        val methods = Player.Listener::class.java.declaredMethods.filter { it.name.startsWith("on") }

        for (method in methods) {
            // getDeclaredMethod fails if a future Media3 callback silently inherits a default.
            val implementation = adapter.javaClass.getDeclaredMethod(method.name, *method.parameterTypes)
            val arguments = method.parameterTypes.map { type ->
                when (type) {
                    java.lang.Boolean.TYPE -> true
                    java.lang.Integer.TYPE -> 7
                    java.lang.Long.TYPE -> 11L
                    java.lang.Float.TYPE -> 0.5f
                    else -> mockkClass(type.kotlin, relaxed = true)
                }
            }
            implementation.invoke(adapter, *arguments.toTypedArray())
            assertEquals(method.toGenericString() to arguments, received.last(), method.name)
        }
        assertEquals(methods.size, received.size)
    }

    /** JVM-safe command snapshots; Android's real FlagSet requires SparseBooleanArray. */
    private fun commands(available: Set<Int>): Player.Commands {
        val result = mockk<Player.Commands>()
        every { result.contains(any()) } answers { firstArg<Int>() in available }
        every { result.buildUpon() } answers {
            val values = available.toMutableSet()
            val builder = mockk<Player.Commands.Builder>()
            every { builder.addAllCommands() } answers { values.addAll(compatibilityCommands); builder }
            every { builder.remove(any()) } answers { values.remove(firstArg<Int>()); builder }
            every { builder.build() } answers { commands(values.toSet()) }
            builder
        }
        return result
    }
}
