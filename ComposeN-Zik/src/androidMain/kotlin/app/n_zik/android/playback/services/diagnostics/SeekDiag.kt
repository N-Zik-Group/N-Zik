package app.n_zik.android.playback.services.diagnostics

import androidx.media3.common.Player

/**
 * Issue #881 (gh-881): pure helpers for the seek-loop instrumentation (spec S1/S2/S3) —
 * JVM-testable, no Android or player state involved.
 */

/**
 * Human-readable name of a [Player] position-discontinuity reason
 * ([Player.onPositionDiscontinuity]). media3 1.10.1 values (verified on the AAR via javap):
 * AUTO_TRANSITION=0, SEEK=1, SEEK_ADJUSTMENT=2, SKIP=3, REMOVE=4, INTERNAL=5, SILENCE_SKIP=6.
 */
fun discontinuityReasonName(reason: Int): String = when (reason) {
    Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
    Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
    Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
    Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
    Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
    Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
    Player.DISCONTINUITY_REASON_SILENCE_SKIP -> "SILENCE_SKIP"
    else -> "UNKNOWN($reason)"
}

/**
 * All the DISTINCT [Player] seek player-command ids of media3 1.10.1 (verified on the AAR via
 * `javap -constants`: values 4–12) — single source of truth, shared with
 * `AutoSessionCallback`'s seek filter.
 *
 * Note: media3 declares four DEPRECATED value aliases of the same ids
 * (`COMMAND_SEEK_IN_CURRENT_WINDOW = 5`, `COMMAND_SEEK_TO_PREVIOUS_WINDOW = 6`,
 * `COMMAND_SEEK_TO_NEXT_WINDOW = 8`, `COMMAND_SEEK_TO_WINDOW = 10`) — being the same ints,
 * set membership covers them automatically; the deprecated constants are not referenced
 * (that would add deprecation warnings for zero coverage gain).
 */
val SEEK_PLAYER_COMMANDS: Set<Int> = setOf(
    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
    Player.COMMAND_SEEK_BACK,
    Player.COMMAND_SEEK_FORWARD,
)

/**
 * Human-readable name of a [Player] seek player-command requested through a MediaSession
 * controller. media3 1.10.1: the legacy `onSeekTo`/`onSeekForward`/`onSeekBack` overrides no
 * longer exist, so the AOSP-interop controller path routes player commands through
 * `MediaSession.Callback.onPlayerCommandRequest`.
 *
 * SCOPE NOTE (verified in the media3 1.10.1 sources): media3's OWN controllers
 * (`MediaControllerImplBase` — the in-app media notification and the media-button path) do NOT
 * go through `onPlayerCommandRequest`; they apply their seeks straight to the player as
 * dedicated transactions. Those seeks are captured by the S1 `SEEK_CALL` stack instead (their
 * frames show `MediaControllerImplBase`/`MediaSessionStub`), so the ABSENCE of a `SEEK_SESSION`
 * line does NOT rule out the notification/media-button path — only the AOSP-interop path
 * (lockscreen / Android Auto legacy) emits `SEEK_SESSION`.
 */
fun seekCommandName(playerCommand: Int): String = when (playerCommand) {
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> "SEEK_TO"
    Player.COMMAND_SEEK_BACK -> "SEEK_BACK"
    Player.COMMAND_SEEK_FORWARD -> "SEEK_FORWARD"
    Player.COMMAND_SEEK_TO_PREVIOUS -> "SEEK_PREVIOUS"
    Player.COMMAND_SEEK_TO_NEXT -> "SEEK_NEXT"
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> "SEEK_PREVIOUS_MEDIA_ITEM"
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> "SEEK_NEXT_MEDIA_ITEM"
    Player.COMMAND_SEEK_TO_DEFAULT_POSITION -> "SEEK_TO_DEFAULT_POSITION"
    Player.COMMAND_SEEK_TO_MEDIA_ITEM -> "SEEK_TO_MEDIA_ITEM"
    else -> "UNKNOWN($playerCommand)"
}

/**
 * Framework/JVM prefixes excluded from [seekCallerStack]: everything below the first app
 * frame is either the player internals (media3), the platform, Compose plumbing or the JVM —
 * none of it identifies the caller of the seek. (The `DiagExoPlayer` instrumentation frames
 * themselves are dropped by the caller — see `DiagExoPlayer.logSeek` — before [seekCallerStack]
 * runs.)
 */
private val SEEK_STACK_FRAME_PREFIXES = listOf(
    "androidx.media3.",
    "androidx.",
    "android.",
    "com.android.",
    "kotlin.",
    "kotlinx.",
    "java.",
    "jdk.",
    "dalvik.",
    "libcore.",
)

/**
 * Pure stack-trace extraction for the `SEEK_CALL` diagnostics line (spec S1): drops framework
 * frames, keeps the first [maxFrames] meaningful frames as `className.methodName` (frame NAMES
 * only — never method arguments, so no URL/token/cookie can leak), joins them with ` <- `
 * (nearest caller first) and caps the result at [maxChars]. An empty result is fine — it means
 * every frame was framework-internal.
 */
fun seekCallerStack(trace: Array<out StackTraceElement>, maxFrames: Int = 8, maxChars: Int = 400): String {
    val frames = trace
        .map { it.className + "." + it.methodName }
        .filter { frame -> SEEK_STACK_FRAME_PREFIXES.none { prefix -> frame.startsWith(prefix) } }
        .take(maxFrames)
        .joinToString(" <- ")
    return if (frames.length > maxChars) frames.substring(0, maxChars) + "…" else frames
}
