package app.n_zik.android.utils.debug

import java.io.File

/**
 * Crash log auto-wipe policy (spec-maintenance-dialog): the crash log is wiped
 * automatically after [CRASH_AUTO_WIPE_REQUIRED_CLEAN_BOOTS] consecutive app boots
 * without a NEW crash block — a new crash since the previous boot restarts the
 * streak. The explicit Clear button of the crash log dialog stays the manual path.
 */

/** Boots without a new crash required before the crash log is wiped (2). */
const val CRASH_AUTO_WIPE_REQUIRED_CLEAN_BOOTS = 2

/** The app-settings key of the clean-boot streak counter (crash auto-wipe). */
const val CRASH_CLEAN_BOOT_STREAK_KEY = "crashCleanBootStreak"

/** The app-settings key of the last-seen crash block count (crash auto-wipe). */
const val CRASH_LAST_SEEN_COUNT_KEY = "crashLastSeenCount"

/** The crash log file inside [logsDir] (the file [CaptureCrash] appends blocks to). */
fun crashLogFile(logsDir: File): File = File(logsDir, "N-Zik_crash_log.txt")

/**
 * The state after one boot's auto-wipe evaluation.
 *
 * @property wipe whether the crash log file must be deleted at this boot
 * @property cleanBootStreak the streak to persist (0 after a wipe or a new crash)
 * @property lastSeenCrashCount the crash block count to persist (0 after a wipe)
 */
data class CrashLogWipeDecision(
    val wipe: Boolean,
    val cleanBootStreak: Int,
    val lastSeenCrashCount: Int,
)

/**
 * The auto-wipe decision for one app boot, as a pure function of the persisted state:
 * the boot sees [currentCrashCount] complete crash blocks in the log.
 *
 * - [currentCrashCount] > [lastSeenCrashCount] → a NEW crash was recorded since the
 *   previous boot: no wipe, the streak restarts at 0.
 * - otherwise the streak grows by one; at [requiredCleanBoots] the log is wiped (the
 *   streak and the last-seen count reset to 0 with it). A lower count than last seen
 *   (e.g. a manual Clear from the dialog) never counts as a crash.
 */
fun crashLogWipeDecision(
    currentCrashCount: Int,
    lastSeenCrashCount: Int,
    cleanBootStreak: Int,
    requiredCleanBoots: Int = CRASH_AUTO_WIPE_REQUIRED_CLEAN_BOOTS,
): CrashLogWipeDecision =
    if (currentCrashCount > lastSeenCrashCount) {
        CrashLogWipeDecision(wipe = false, cleanBootStreak = 0, lastSeenCrashCount = currentCrashCount)
    } else {
        val streak = cleanBootStreak + 1
        if (streak >= requiredCleanBoots) {
            CrashLogWipeDecision(wipe = true, cleanBootStreak = 0, lastSeenCrashCount = 0)
        } else {
            CrashLogWipeDecision(wipe = false, cleanBootStreak = streak, lastSeenCrashCount = currentCrashCount)
        }
    }
