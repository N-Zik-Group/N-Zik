package app.n_zik.android.ui.saveable

import app.it.fast4x.rimusic.ui.styling.Appearance
import app.n_zik.android.playback.services.diagnostics.PLAYBACK_DIAG_TAG
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import timber.log.Timber

/**
 * Issue #881 (gh-881): tolerant `Saver`s for the `rememberSaveable` slots of the Player screen
 * and the MainActivity root (spec M5) — fix for the `ClassCastException: ArrayList cannot be
 * cast to MutableState` crash of 2026-10-03T15:23:55 (field logs, gh-881).
 *
 * Mechanism (verified in the Compose runtime 1.12.0 sources): `rememberSaveable` keys its state
 * POSITIONALLY (`currentCompositeKeyHashCode.toString(36)`) and EVERY saveable of the Activity
 * — main composition and sub-compositions alike — shares one key space. When the composition
 * structure differs between the save and the restore (the Player screen is the only subtree
 * composed in a `BoxWithConstraints` sub-composition AND holds the most conditional saveables),
 * a saved payload lands in a FOREIGN slot — e.g. the `appearance` saveable's `ArrayList`
 * payload arrives at a `rememberSaveable { mutableStateOf(false) }` slot. The auto-saver's
 * `restore` then `checkcast`s the payload to `MutableState` → CCE (or the `stateSaver` overload's
 * `mutableStateSaver` wrapper fails its `require(it is SnapshotMutableState)` → IAE).
 *
 * These savers break that chain: they are passed through the PLAIN `saver =` overload only
 * (the `stateSaver =` overload is prohibited — its wrapper `require`s a `SnapshotMutableState`
 * payload). Their `restore(value: Any)` takes an untyped payload and safe-casts (`as?`) — a
 * foreign payload yields the default value instead of an exception, plus one `SAVEABLE_MISMATCH`
 * diagnostics line naming the SLOT and the payload class (slot + class names only, no sensitive
 * data). The net only exists in the converted in-scope slots: a shift landing on an unconverted
 * slot still crashes (out of scope, spec §3bis). The registry only ever calls `restore` with a
 * non-null payload (a null `consumeRestored` result takes the `init` lambda instead), so the
 * non-null parameter is a runtime guarantee.
 */

/**
 * Pure restore of a `Boolean` saveable payload: [default] when the payload is null or not a
 * `Boolean` (the foreign-payload case — see the file KDoc).
 */
fun restoreTolerantBool(payload: Any?, default: Boolean = false): Boolean =
    payload as? Boolean ?: default

/**
 * Pure restore of an `Int` saveable payload: [default] when the payload is null or not an `Int`
 * (the foreign-payload case — see the file KDoc). Checks the TYPE only — a type-correct but
 * out-of-domain value is guarded by [TolerantIntStateSaver]'s `isValid` domain check.
 */
fun restoreTolerantInt(payload: Any?, default: Int): Int = payload as? Int ?: default

/**
 * Pure restore of a `String?` saveable payload: `null` when the payload is null or not a `String`
 * (the foreign-payload case — see the file KDoc).
 */
fun restoreTolerantString(payload: Any?): String? = payload as? String

/**
 * The `SAVEABLE_MISMATCH` net (spec M5): one `PlaybackDiag` warn line naming [slot] (the
 * destination site), the payload class (therefore the source of the shift) and the expected type
 * [E]. No sensitive data — slot label and class names only. A null payload is a legitimate
 * "nothing was saved" restore and is NOT reported.
 *
 * Reified `is` check on purpose: `payload.javaClass == E::class.java` would NEVER match for
 * boxed primitives (`Boolean::class.java` is the primitive `boolean` class, not
 * `java.lang.Boolean`), so a legitimate payload would be falsely reported.
 */
inline fun <reified E : Any> reportSaveableMismatch(slot: String, payload: Any?): Boolean {
    if (payload == null || payload is E) return false
    Timber.tag(PLAYBACK_DIAG_TAG).w(
        "SAVEABLE_MISMATCH slot=%s payload=%s expected=%s -> default",
        slot,
        payload.javaClass.name,
        E::class.simpleName,
    )
    return true
}

/**
 * The truncated-list counterpart of [reportSaveableMismatch]: a saved `List` that is the RIGHT
 * TYPE (the reified type check would not fire) but SHORTER than the expected size — a corrupted
 * save. Same tag, same `SAVEABLE_MISMATCH slot=…` prefix, plus the observed size.
 */
fun reportTruncatedSaveable(slot: String, size: Int, expectedSize: Int) {
    Timber.tag(PLAYBACK_DIAG_TAG).w(
        "SAVEABLE_MISMATCH slot=%s payload=List(size=%d) expected size>=%d -> element defaults",
        slot,
        size,
        expectedSize,
    )
}

/**
 * Tolerant saver for `rememberSaveable { mutableStateOf(<boolean>) }` slots: the payload is the
 * plain `Boolean` value (Bundle-compatible), and a foreign payload (any other type landing in
 * this slot — the generics are erased at runtime, so no `checkcast` is ever inserted) restores
 * to the default without crashing. [slot] is the site label reported in `SAVEABLE_MISMATCH`
 * (which screen/slot received the foreign payload).
 */
class TolerantBoolStateSaver(private val slot: String) : Saver<MutableState<Boolean>, Any> {
    override fun SaverScope.save(value: MutableState<Boolean>): Any = value.value

    override fun restore(value: Any): MutableState<Boolean> {
        reportSaveableMismatch<Boolean>(slot, value)
        return mutableStateOf(restoreTolerantBool(value))
    }
}

/**
 * Tolerant saver for `rememberSaveable { mutableStateOf(<int>) }` slots — used for
 * `PlayerSheetState.previousAnchor`, where [default] MUST be a valid PlayerSheet anchor
 * (`dismissedAnchor`/`collapsedAnchor`/`expandedAnchor`).
 *
 * [isValid] additionally guards the TYPE-CORRECT case: a foreign `Int` payload (another
 * saveable's value shifted into this slot) passes the `as? Int` cast but would crash
 * `rememberPlayerSheetState` on `error("Unknown PlayerSheet anchor")` — an [isValid]-rejected
 * value restores to [default] and is reported as a `SAVEABLE_MISMATCH` domain violation.
 */
class TolerantIntStateSaver(
    private val default: Int,
    private val slot: String,
    private val isValid: (Int) -> Boolean = { true },
) : Saver<MutableState<Int>, Any> {

    override fun SaverScope.save(value: MutableState<Int>): Any = value.value

    override fun restore(value: Any): MutableState<Int> {
        reportSaveableMismatch<Int>(slot, value)
        val restored = restoreTolerantInt(value, default)
        if (!isValid(restored)) {
            // Type-correct but out-of-domain payload — a positional shift from another Int saveable.
            Timber.tag(PLAYBACK_DIAG_TAG).w(
                "SAVEABLE_MISMATCH slot=%s payload=%d is outside the allowed domain -> default",
                slot,
                restored,
            )
            return mutableStateOf(default)
        }
        return mutableStateOf(restored)
    }
}

/**
 * Tolerant saver for `rememberSaveable { mutableStateOf<String?>(…) }` slots (the LyricsScreen
 * edited-title/artist slots, spec M5 review fix): a `null` value saves nothing (the registry
 * takes the `init` lambda on restore), and a foreign payload restores to `null` without crashing.
 */
class TolerantStringStateSaver(private val slot: String) : Saver<MutableState<String?>, Any> {
    // `null` saveable = "nothing was saved" — the registry takes the `init` lambda on restore.
    override fun SaverScope.save(value: MutableState<String?>): Any? = value.value

    override fun restore(value: Any): MutableState<String?> {
        reportSaveableMismatch<String>(slot, value)
        return mutableStateOf(restoreTolerantString(value))
    }
}

/**
 * Tolerant saver for the root `appearance` saveable (`MainActivity`, previously
 * `stateSaver = Appearance.Companion` — a prohibited overload, see the file KDoc). [fallback]
 * is the same preference-derived default the saveable's `init` lambda computes
 * (`::computeAppearance` at the site), used when the payload is not a saved appearance `List`
 * at all (the structural-corruption case). A `List` payload is restored through
 * [Appearance.Companion.restore], which is itself tolerant element-wise.
 */
class TolerantAppearanceStateSaver(
    private val fallback: () -> Appearance,
    private val slot: String = "appearance",
) : Saver<MutableState<Appearance>, Any> {

    override fun SaverScope.save(value: MutableState<Appearance>): Any =
        with(Appearance.Companion) { save(value.value) }

    // The companion already reports a non-List payload, so no second log here.
    override fun restore(value: Any): MutableState<Appearance> =
        mutableStateOf(Appearance.Companion.restore(value, fallback))
}
