package app.n_zik.android.extensions.youtubelogin

import app.n_zik.innertube.YouTubeSession
import app.n_zik.innertube.fillYouTubeChannelEmails
import app.n_zik.innertube.parseYouTubeAccountDetails
import app.n_zik.innertube.parseYouTubeChannels
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLoginSelectionTest {
    @Test
    fun resumesDevicePromptOnlyForANewGoogleSession() {
        assertTrue(isGoogleDevicePrompt("https://accounts.google.com/v3/signin/challenge/dp?fixture=1"))
        assertFalse(isGoogleDevicePrompt("https://accounts.google.com/v3/signin/challenge/pwd"))
        assertFalse(isGoogleDevicePrompt("https://accounts.google.com.example.org/v3/signin/challenge/dp"))
        val before = googleSessionCookies("SID=previous; AEC=old")
        assertFalse(hasNewGoogleSession(before, googleSessionCookies("SID=previous; AEC=new")))
        assertFalse(hasNewGoogleSession(before, googleSessionCookies("AEC=new")))
        assertTrue(hasNewGoogleSession(before, googleSessionCookies("SID=verified; AEC=new")))
    }

    @Test
    fun keepsDiscoveredEmailWithinItsGoogleAccountGroup() {
        fun accountGroup(email: String, channel: String) = """
            {"accountSectionListRenderer":{
                "header":{"googleAccountHeaderRenderer":{"email":{"runs":[{"text":"$email"}]}}},
                "contents":[${channel("||$channel", "@$channel")}]
            }}
        """.trimIndent()
        val grouped = parseYouTubeChannels(accounts(
            accountGroup("first@example.org", "first"),
            accountGroup("second@example.org", "second"),
            channel("||other", "@other"),
        ))
        assertEquals(listOf("first@example.org", "second@example.org", null), grouped.map { it.email })
        val switcher = ")]}'\n" + accounts(
            accountGroup("first@example.org", "first"),
            accountGroup("second@example.org", "second"),
        )
        val choices = parseYouTubeChannels(accounts(
            channel("||second", "@second"),
            channel("||first", "@first"),
            channel("||unknown", "@unknown"),
        ))
        val enriched = fillYouTubeChannelEmails(choices, parseYouTubeChannels(switcher))
        assertEquals(listOf("second@example.org", "first@example.org", null), enriched.map { it.email })
        assertEquals(choices.map { it.dataSyncId }, enriched.map { it.dataSyncId })
    }
    private val initial = YouTubeSession("SAPISID=first", "visitor1", "one", "0")
    private val activeRenderer = channel("||one", "@first", selected = true)

    @Test
    fun emptyDiscoveryRequiresAVerifiedIdentity() = runTest {
        assertTrue(selectableYouTubeChannels(parseYouTubeChannels(accounts()), initial).isEmpty())
        val result = verifyYouTubeChoice(
            initial, channel = null, freshlyCaptured = true, isCurrent = { true },
            fetchAccount = {
                parseYouTubeAccountDetails(
                    """{"responseContext":{"mainAppWebResponseContext":{"loggedOut":true}}}"""
                )
            },
            fetchChoices = { error("Guest result cannot discover or publish") },
        )
        assertNull(result)
    }

    @Test
    fun singleChannelValidatesTheCapturedSession() = runTest {
        val choice = selectableYouTubeChannels(parseYouTubeChannels(accounts(activeRenderer)), initial).single()
        assertEquals(initial, choice.currentOrDirectSession(initial))
        val verified = verifyYouTubeChoice(
            initial, choice, freshlyCaptured = true, isCurrent = { true },
            fetchAccount = { parseYouTubeAccountDetails(accountMenu("@first")) },
            fetchChoices = { error("Matching handle already verifies the channel") },
        )
        assertEquals("@first", verified?.info?.channelHandle)
    }

    @Test
    fun duplicateNamesKeepIndependentRoutesForChannelsUnderTheSameGoogleAccount() = runTest {
        val entries = parseYouTubeChannels(accounts(activeRenderer, channel("brand||one", "@brand")))
        val channels = selectableYouTubeChannels(entries, initial)
        assertEquals(listOf("Same Name", "Same Name"), channels.map { it.name })
        val target = channels[1]
        assertEquals("https://images.example/avatar.jpg", target.thumbnailUrl)
        val candidate = requireNotNull(target.currentOrDirectSession(initial))
        assertEquals(initial.copy(dataSyncId = "brand"), candidate)
        val verified = verifyYouTubeChoice(
            candidate, target, freshlyCaptured = false, isCurrent = { true },
            fetchAccount = { session ->
                assertEquals("brand", session.dataSyncId)
                parseYouTubeAccountDetails(accountMenu("@brand"))
            },
            fetchChoices = { error("Matching handle already verifies the channel") },
        )
        assertEquals("@brand", verified?.info?.channelHandle)
    }

    @Test
    fun anotherGoogleAccountUsesTheFreshCookieVisitorDataAndSessionIndex() = runTest {
        val target = selectableYouTubeChannels(
            parseYouTubeChannels(accounts(activeRenderer, channel("||two", "@second", "/signin?authuser=2"))),
            initial,
        )[1]
        assertEquals("https://music.youtube.com/signin?authuser=2", target.navigationUrl())
        assertNull(target.currentOrDirectSession(initial))
        val finishedUrl = "https://music.youtube.com/?authuser=2"
        val fresh = requireNotNull(
            parseYouTubePageSession(config(finishedUrl, "2"), finishedUrl, "SAPISID=second")
        )
        val verified = verifyYouTubeChoice(
            fresh, target, freshlyCaptured = true, isCurrent = { true },
            fetchAccount = { session ->
                assertEquals(YouTubeSession("SAPISID=second", "visitor2", "two", "2"), session)
                parseYouTubeAccountDetails(accountMenu("@second"))
            },
            fetchChoices = { error("Matching captured route already verifies this account") },
        )
        assertEquals("@second", verified?.info?.channelHandle)
        assertNull(verifyYouTubeChoice(
            fresh.copy(authUser = "0"), target, freshlyCaptured = true, isCurrent = { true },
            fetchAccount = { error("Wrong session index must fail before the request") },
            fetchChoices = { emptyList() },
        ))
    }

    @Test
    fun matchingDisplayNamesCannotVerifyTheWrongAccount() = runTest {
        val choice = parseYouTubeChannels(accounts(channel("brand||one", "@brand"))).single()
        val result = verifyYouTubeChoice(
            initial.copy(dataSyncId = "brand"), choice, freshlyCaptured = false, isCurrent = { true },
            fetchAccount = { parseYouTubeAccountDetails(accountMenu("@first")) },
            fetchChoices = { listOf(choice) },
        )
        assertNull(result)
    }

    @Test
    fun failedVerificationLeavesTheExistingSessionSelected() = runTest {
        var published = initial
        val choice = parseYouTubeChannels(accounts(channel("brand||one", "@brand"))).single()
        val candidate = requireNotNull(choice.currentOrDirectSession(initial))
        val result = verifyYouTubeChoice(
            candidate, choice, freshlyCaptured = false, isCurrent = { true },
            fetchAccount = { null },
            fetchChoices = { error("Validation returned no identity") },
        )
        if (result != null) published = candidate
        assertEquals(initial, published)
    }

    @Test
    fun navigationOrCancellationInvalidatesAnInFlightResult() = runTest {
        val gate = YouTubeLoginPageGate()
        val revision = gate.revision
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        val verified = async {
            verifyYouTubeChoice(
                initial, channel = null, freshlyCaptured = true, isCurrent = { gate.isCurrent(revision) },
                fetchAccount = {
                    started.complete(Unit)
                    complete.await()
                    parseYouTubeAccountDetails(accountMenu("@first"))
                },
                fetchChoices = { emptyList() },
            )
        }
        started.await()
        gate.invalidate()
        complete.complete(Unit)
        assertNull(verified.await())
    }

    @Test
    fun partialConfigAndStaleJavaScriptAreRejected() {
        val url = "https://music.youtube.com/?authuser=2"
        assertNull(parseYouTubePageSession(config(url, "2"), url, null))
        assertNull(parseYouTubePageSession(config(url, "2"), MUSIC_HOME, initial.cookie))
        assertNull(parseYouTubePageSession(config(url, ""), url, initial.cookie))
        assertNull(parseYouTubePageSession(config(url, "2").replace("visitor2", ""), url, initial.cookie))
        assertFalse(isYouTubeMusicPage("https://music.youtube.com.attacker.example/"))
        assertFalse(isYouTubeMusicPage("http://music.youtube.com/"))
    }

    @Test
    fun validatedFallbackRetainsOptionalRoutingAbsenceWithoutMatchingAnotherIdentity() = runTest {
        val raw = """{"url":"$MUSIC_HOME","loggedIn":true,"visitorData":"legacy-visitor"}"""
        val session = requireNotNull(parseYouTubePageSession(raw, MUSIC_HOME, initial.cookie, requireRoutingFields = false))
        assertEquals(YouTubeSession(initial.cookie, "legacy-visitor", null, null), session)
        val target = parseYouTubeChannels(accounts(channel("||two", "@second", "/signin?authuser=2"))).single()
        val verified = verifyYouTubeChoice(
            session, target, freshlyCaptured = true, isCurrent = { true },
            fetchAccount = { error("Missing auth index must not verify a nondefault identity") },
            fetchChoices = { emptyList() },
        )
        assertNull(verified)
    }

    @Test
    fun youtubeAccountVerificationAllowsOnlyTheExpectedSecureOrigin() {
        assertTrue(isYouTubeLoginPage("https://accounts.youtube.com/accounts/CheckConnection"))
        assertFalse(isYouTubeLoginPage("https://accounts.youtube.com.example.org/accounts/CheckConnection"))
        assertFalse(isYouTubeLoginPage("http://accounts.youtube.com/accounts/CheckConnection"))
        assertFalse(isYouTubeMusicPage("https://accounts.youtube.com/accounts/CheckConnection"))
    }

    @Test
    fun switchNavigationRejectsOtherOriginsAndUntrustedSchemes() {
        for (url in listOf("//google.example/login", "javascript:window.close()", "https://user@music.youtube.com/")) {
            val channel = parseYouTubeChannels(accounts(channel("||two", "@second", url))).single()
            assertNull(channel.navigationUrl())
        }
    }

    private fun accounts(vararg items: String) = """
        {"actions":[{"updateChannelSwitcherPageAction":{"page":{"channelSwitcherPageRenderer":{"contents":[
            ${items.joinToString(",")}
        ]}}}}]}
    """.trimIndent()

    private fun channel(syncId: String, handle: String, signIn: String? = null, selected: Boolean = false) = """
        {"accountItemRenderer":{
            "accountName":{"simpleText":"Same Name"},
            "channelHandle":{"simpleText":"$handle"},
            "accountPhoto":{"thumbnails":[{"url":"https://images.example/avatar.jpg"}]},
            "isSelected":$selected,
            "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[
                {"datasyncIdToken":{"datasyncIdToken":"$syncId"}}
                ${signIn?.let { """,{"accountSigninToken":{"signinUrl":"$it"}}""" }.orEmpty()}
            ]}}}
        }
    """.trimIndent()

    private fun accountMenu(handle: String) = """
        {"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"activeAccountHeaderRenderer":{
            "accountName":{"runs":[{"text":"Same Name"}]},
            "channelHandle":{"simpleText":"$handle"}
        }}}}}}]}
    """.trimIndent()

    private fun config(url: String, index: String) =
        """{"url":"$url","loggedIn":true,"visitorData":"visitor2","dataSyncId":"||two","authUser":"$index"}"""
}
