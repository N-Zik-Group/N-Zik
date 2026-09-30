package app.n_zik.android.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the face resolution (spec-profiles-page-face): [resolveFaceName] — an account
 * source wins only while that account is logged in with a captured name, otherwise the
 * profile's own display name, then the app default (never blank); [resolveFaceAvatar]
 * — the account photo, else the profile photo, else the deterministic initials (always
 * renders something); [faceInitials] / [faceInitialsColorIndex] determinism; and the
 * [relativeTime] bucket boundaries.
 */
class ProfileFaceTest {

    private val defaultName = "N-Zik Fan"

    private fun resolveName(
        source: String,
        profileName: String = "Danie",
        ytLoggedIn: Boolean = false,
        ytName: String = "",
        discordLoggedIn: Boolean = false,
        discordName: String = "",
        lastfmLoggedIn: Boolean = false,
        lastfmName: String = "",
    ) = resolveFaceName(
        source = source,
        profileName = profileName,
        ytLoggedIn = ytLoggedIn,
        ytName = ytName,
        discordLoggedIn = discordLoggedIn,
        discordName = discordName,
        lastfmLoggedIn = lastfmLoggedIn,
        lastfmName = lastfmName,
        defaultName = defaultName
    )

    // --- resolveFaceName

    @Test
    fun profileSourceResolvesTheProfileName() {
        assertEquals(
            "Danie",
            resolveName(
                source = FACE_SOURCE_PROFILE,
                ytLoggedIn = true, ytName = "Danie YT",
                discordLoggedIn = true, discordName = "DanieDisc",
                lastfmLoggedIn = true, lastfmName = "danie_fm"
            )
        )
    }

    @Test
    fun youtubeSourceResolvesTheAccountNameWhenLoggedIn() {
        assertEquals("Danie YT", resolveName(FACE_SOURCE_YOUTUBE, ytLoggedIn = true, ytName = "Danie YT"))
    }

    @Test
    fun youtubeSourceFallsBackWhenNotLoggedIn() {
        assertEquals("Danie", resolveName(FACE_SOURCE_YOUTUBE, ytLoggedIn = false, ytName = "Danie YT"))
    }

    @Test
    fun youtubeSourceFallsBackWhenTheAccountNameIsBlank() {
        assertEquals("Danie", resolveName(FACE_SOURCE_YOUTUBE, ytLoggedIn = true, ytName = "   "))
    }

    @Test
    fun discordSourceResolvesTheUsernameWhenLoggedIn() {
        assertEquals("DanieDisc", resolveName(FACE_SOURCE_DISCORD, discordLoggedIn = true, discordName = "DanieDisc"))
    }

    @Test
    fun discordSourceFallsBackWhenNotLoggedIn() {
        assertEquals("Danie", resolveName(FACE_SOURCE_DISCORD, discordLoggedIn = false, discordName = "DanieDisc"))
    }

    @Test
    fun lastfmSourceResolvesTheUsernameWhenLoggedIn() {
        assertEquals("danie_fm", resolveName(FACE_SOURCE_LASTFM, lastfmLoggedIn = true, lastfmName = "danie_fm"))
    }

    @Test
    fun lastfmSourceFallsBackWhenNotLoggedIn() {
        assertEquals("Danie", resolveName(FACE_SOURCE_LASTFM, lastfmLoggedIn = false, lastfmName = "danie_fm"))
    }

    @Test
    fun anAccountNameIsTrimmedBeforeItIsShown() {
        assertEquals("Danie", resolveName(FACE_SOURCE_YOUTUBE, ytLoggedIn = true, ytName = "  Danie  "))
    }

    @Test
    fun aBlankProfileNameFallsBackToTheDefaultName() {
        assertEquals(defaultName, resolveName(FACE_SOURCE_PROFILE, profileName = "   "))
    }

    @Test
    fun anUnknownSourceFallsBackToTheProfileName() {
        assertEquals("Danie", resolveName("something-else"))
    }

    @Test
    fun theResolvedNameIsNeverBlank() {
        val sources = listOf(FACE_SOURCE_PROFILE, FACE_SOURCE_YOUTUBE, FACE_SOURCE_DISCORD, FACE_SOURCE_LASTFM, "unknown")
        sources.forEach { source ->
            assertTrue(
                resolveName(source, profileName = "", ytName = "", discordName = "", lastfmName = "").isNotBlank(),
                "the face name must never be blank (source $source)"
            )
        }
    }

    // --- resolveFaceAvatar

    @Test
    fun youtubeSourceResolvesTheAccountAvatarWhenLoggedIn() {
        val avatar = resolveFaceAvatar(
            source = FACE_SOURCE_YOUTUBE,
            profileName = "Danie",
            profilePhoto = "/storage/avatar.jpg",
            ytLoggedIn = true,
            ytAvatar = "https://yt.example.com/pic.jpg",
            discordLoggedIn = false, discordAvatar = "",
            lastfmLoggedIn = false, lastfmAvatar = ""
        )
        assertEquals(FaceAvatar.Photo("https://yt.example.com/pic.jpg"), avatar)
    }

    @Test
    fun youtubeSourceFallsBackToTheProfilePhotoWhenNotLoggedIn() {
        val avatar = resolveFaceAvatar(
            source = FACE_SOURCE_YOUTUBE,
            profileName = "Danie",
            profilePhoto = "/storage/avatar.jpg",
            ytLoggedIn = false,
            ytAvatar = "https://yt.example.com/pic.jpg",
            discordLoggedIn = false, discordAvatar = "",
            lastfmLoggedIn = false, lastfmAvatar = ""
        )
        assertEquals(FaceAvatar.Photo("/storage/avatar.jpg"), avatar)
    }

    @Test
    fun discordSourceResolvesTheAccountAvatarWhenLoggedIn() {
        val avatar = resolveFaceAvatar(
            source = FACE_SOURCE_DISCORD,
            profileName = "Danie",
            profilePhoto = null,
            ytLoggedIn = false, ytAvatar = "",
            discordLoggedIn = true, discordAvatar = "https://discord.example.com/pic.png",
            lastfmLoggedIn = false, lastfmAvatar = ""
        )
        assertEquals(FaceAvatar.Photo("https://discord.example.com/pic.png"), avatar)
    }

    @Test
    fun lastfmSourceResolvesTheAccountAvatarWhenLoggedIn() {
        val avatar = resolveFaceAvatar(
            source = FACE_SOURCE_LASTFM,
            profileName = "Danie",
            profilePhoto = null,
            ytLoggedIn = false, ytAvatar = "",
            discordLoggedIn = false, discordAvatar = "",
            lastfmLoggedIn = true, lastfmAvatar = "https://lastfm.example.com/pic.jpg"
        )
        assertEquals(FaceAvatar.Photo("https://lastfm.example.com/pic.jpg"), avatar)
    }

    @Test
    fun theProfilePhotoWinsForTheProfileSource() {
        val avatar = resolveFaceAvatar(
            source = FACE_SOURCE_PROFILE,
            profileName = "Danie",
            profilePhoto = "/storage/avatar.jpg",
            ytLoggedIn = false, ytAvatar = "",
            discordLoggedIn = false, discordAvatar = "",
            lastfmLoggedIn = false, lastfmAvatar = ""
        )
        assertEquals(FaceAvatar.Photo("/storage/avatar.jpg"), avatar)
    }

    @Test
    fun noPhotoFallsBackToTheInitialsOfTheProfileName() {
        val avatar = resolveFaceAvatar(
            source = FACE_SOURCE_PROFILE,
            profileName = "Danie",
            profilePhoto = null,
            ytLoggedIn = false, ytAvatar = "",
            discordLoggedIn = false, discordAvatar = "",
            lastfmLoggedIn = false, lastfmAvatar = ""
        )
        assertEquals(FaceAvatar.Initials("Danie"), avatar)
    }

    @Test
    fun aBlankProfilePhotoFallsBackToTheInitials() {
        val avatar = resolveFaceAvatar(
            source = FACE_SOURCE_PROFILE,
            profileName = "Danie",
            profilePhoto = "   ",
            ytLoggedIn = false, ytAvatar = "",
            discordLoggedIn = false, discordAvatar = "",
            lastfmLoggedIn = false, lastfmAvatar = ""
        )
        assertEquals(FaceAvatar.Initials("Danie"), avatar)
    }

    // --- faceInitials

    @Test
    fun faceInitialsTakesTheFirstLetterOfTheFirstTwoWords() {
        assertEquals("MC", faceInitials("marie curie"))
    }

    @Test
    fun faceInitialsUppercasesEachLetter() {
        // First letter of each of the first two words: "n-zik" -> N, "fan" -> F.
        assertEquals("NF", faceInitials("n-zik fan"))
    }

    @Test
    fun faceInitialsIgnoresExtraWhitespace() {
        assertEquals("MC", faceInitials("  marie   curie  "))
    }

    @Test
    fun faceInitialsOfANameWithMoreThanTwoWordsIgnoresTheRest() {
        // Only the first two words count: "marie" -> M, "sophie" -> S ("curie" is ignored).
        assertEquals("MS", faceInitials("marie sophie curie"))
    }

    @Test
    fun faceInitialsOfANameWithoutSpacesTakesTheFirstLetter() {
        assertEquals("N", faceInitials("n-zik"))
    }

    @Test
    fun faceInitialsOfABlankNameIsTheFallbackLetter() {
        assertEquals("?", faceInitials("   "))
    }

    // --- faceInitialsColorIndex

    @Test
    fun theSameNameAlwaysGetsTheSameColorIndex() {
        val names = listOf("marie", "Danie", "n-zik fan", "a b c", "")
        names.forEach { name ->
            assertEquals(faceInitialsColorIndex(name), faceInitialsColorIndex(name), "color index must be deterministic for '$name'")
        }
    }

    @Test
    fun theColorIndexStaysInRangeForEveryName() {
        listOf("marie", "Danie", "n-zik fan", "a", "x".repeat(64), "").forEach { name ->
            val index = faceInitialsColorIndex(name)
            assertTrue(index in 0 until FACE_INITIALS_COLOR_COUNT, "color index $index out of range for '$name'")
        }
    }

    @Test
    fun theColorIndexIgnoresTheTrailingWhitespace() {
        assertEquals(faceInitialsColorIndex("Danie"), faceInitialsColorIndex("Danie   "))
    }

    @Test
    fun aBlankNameGetsIndexZero() {
        assertEquals(0, faceInitialsColorIndex(""))
    }

    // --- relativeTime

    private val now = 1_000_000_000_000L

    @Test
    fun lessThanAMinuteAgoIsJustNow() {
        assertEquals(RelativeUnit.JustNow, relativeTime(now - 0, now).unit)
        assertEquals(RelativeUnit.JustNow, relativeTime(now - 59_999L, now).unit)
    }

    @Test
    fun aMinuteAgoIsOneMinute() {
        assertEquals(RelativeTime(RelativeUnit.Minutes, 1), relativeTime(now - 60_000L, now))
    }

    @Test
    fun theMinuteBucketStopsJustBeforeAnHour() {
        assertEquals(RelativeTime(RelativeUnit.Minutes, 59), relativeTime(now - 3_599_999L, now))
    }

    @Test
    fun theHourBucketStopsJustBeforeADay() {
        assertEquals(RelativeTime(RelativeUnit.Hours, 23), relativeTime(now - 86_399_999L, now))
    }

    @Test
    fun theDayBucketCountsWholeDays() {
        assertEquals(RelativeTime(RelativeUnit.Days, 6), relativeTime(now - 604_799_999L, now))
    }

    @Test
    fun theWeekBucketCountsWholeWeeks() {
        assertEquals(RelativeTime(RelativeUnit.Weeks, 4), relativeTime(now - 2_591_999_999L, now))
    }

    @Test
    fun theMonthBucketCountsThirtyDayMonths() {
        assertEquals(RelativeTime(RelativeUnit.Months, 12), relativeTime(now - 31_535_999_999L, now))
    }

    @Test
    fun olderThanAYearShowsThePlainDate() {
        val millis = now - 400L * 86_400_000L
        assertEquals(RelativeTime(RelativeUnit.Date, millis), relativeTime(millis, now))
    }

    @Test
    fun aNegativeDeltaIsClampedToJustNow() {
        // Clock skew: the "last used" timestamp is in the future
        assertEquals(RelativeTime(RelativeUnit.JustNow, 0), relativeTime(now + 5_000L, now))
    }
}
