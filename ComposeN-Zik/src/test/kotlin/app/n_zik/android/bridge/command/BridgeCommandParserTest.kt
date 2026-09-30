package app.n_zik.android.bridge.command

import app.n_zik.android.bridge.AddPosition
import app.n_zik.android.bridge.state.RepeatModeDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BridgeCommandParserTest {

    private fun valid(route: String, body: String): BridgeCommand {
        val parsed = BridgeCommandParser.parse(route, body)
        return (parsed as? ParsedCommand.Valid)?.command ?: error("Expected a valid command, got $parsed")
    }

    private fun assertInvalid(route: String, body: String) =
        assertEquals(ParsedCommand.Invalid, BridgeCommandParser.parse(route, body), "$route $body")

    @Test
    fun `every contract route parses with its fields and the commandId`() {
        assertEquals(BridgeCommand(PlayerAction.Play), valid("player/play", "{}"))
        assertEquals(BridgeCommand(PlayerAction.Pause, "c-1"), valid("player/pause", """{"commandId":"c-1"}"""))
        assertEquals(BridgeCommand(PlayerAction.Next), valid("player/next", """{"commandId":null}"""))
        assertEquals(BridgeCommand(PlayerAction.Previous), valid("player/previous", "{}"))
        assertEquals(BridgeCommand(PlayerAction.Seek(83_000L)), valid("player/seek", """{"positionMs":83000}"""))
        assertEquals(BridgeCommand(PlayerAction.Speed(1.5f)), valid("player/speed", """{"speed":1.5}"""))
        assertEquals(BridgeCommand(PlayerAction.Repeat(RepeatModeDto.ONE)), valid("player/repeat", """{"mode":"one"}"""))
        assertEquals(BridgeCommand(PlayerAction.Shuffle(true)), valid("player/shuffle", """{"enabled":true}"""))
        assertEquals(
            BridgeCommand(PlayerAction.QueuePlay(listOf("a", "b"), 1, 0L)),
            valid("queue/play", """{"trackIds":["a","b"],"startIndex":1}"""),
        )
        assertEquals(
            BridgeCommand(PlayerAction.QueueAdd(listOf("a"), AddPosition.END)),
            valid("queue/add", """{"trackIds":["a"],"position":"end"}"""),
        )
        assertEquals(BridgeCommand(PlayerAction.QueueRemove(2, "a")), valid("queue/remove", """{"index":2,"trackId":"a"}"""))
        assertEquals(
            BridgeCommand(PlayerAction.QueueMove(0, 3, "a")),
            valid("queue/move", """{"fromIndex":0,"toIndex":3,"trackId":"a"}"""),
        )
        assertEquals(BridgeCommand(PlayerAction.QueueJump(4, "a")), valid("queue/jump", """{"index":4,"trackId":"a"}"""))
        assertEquals(BridgeCommand(PlayerAction.QueueClear), valid("queue/clear", "{}"))
    }

    @Test
    fun `unknown fields are ignored and unknown routes are reported`() {
        assertEquals(BridgeCommand(PlayerAction.Play), valid("player/play", """{"future":42}"""))
        assertEquals(ParsedCommand.UnknownRoute, BridgeCommandParser.parse("player/stop", "{}"))
        assertEquals(ParsedCommand.UnknownRoute, BridgeCommandParser.parse("library/songs", "{}"))
    }

    @Test
    fun `malformed JSON and non-object bodies are invalid`() {
        assertInvalid("player/play", "")
        assertInvalid("player/play", "{not json")
        assertInvalid("player/play", "[]")
        assertInvalid("player/seek", "{}")
        assertInvalid("player/seek", """{"positionMs":"soon"}""")
    }

    @Test
    fun `bounds of contract section 9 are enforced`() {
        assertInvalid("player/seek", """{"positionMs":-1}""")
        assertInvalid("player/speed", """{"speed":0.2}""")
        assertInvalid("player/speed", """{"speed":4.5}""")
        valid("player/speed", """{"speed":0.25}""")
        valid("player/speed", """{"speed":4.0}""")
        assertInvalid("queue/play", """{"trackIds":[],"startIndex":0}""")
        assertInvalid("queue/play", """{"trackIds":["a"],"startIndex":1}""")
        assertInvalid("queue/play", """{"trackIds":["a"],"startIndex":-1}""")
        assertInvalid("queue/play", """{"trackIds":["a"],"startIndex":0,"positionMs":-5}""")
        val tooMany = List(501) { "\"t$it\"" }.joinToString(",", "[", "]")
        assertInvalid("queue/add", """{"trackIds":$tooMany,"position":"next"}""")
        val justEnough = List(500) { "\"t$it\"" }.joinToString(",", "[", "]")
        valid("queue/add", """{"trackIds":$justEnough,"position":"next"}""")
        assertInvalid("player/play", """{"commandId":"${"x".repeat(65)}"}""")
        valid("player/play", """{"commandId":"${"x".repeat(64)}"}""")
    }

    @Test
    fun `unknown enum values are invalid`() {
        assertInvalid("player/repeat", """{"mode":"forever"}""")
        assertInvalid("queue/add", """{"trackIds":["a"],"position":"first"}""")
    }
}
