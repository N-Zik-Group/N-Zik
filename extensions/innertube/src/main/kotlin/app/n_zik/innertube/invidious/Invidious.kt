package app.n_zik.innertube.invidious

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import app.n_zik.innertube.utils.InnertubeLogger
import app.n_zik.innertube.common.HttpFetcher
import app.n_zik.innertube.common.PublicInstances


object Invidious: PublicInstances() {

    private const val VERIFIED_PUBLIC_INSTANCES =
        "https://raw.githubusercontent.com/iv-org/documentation/refs/heads/master/docs/instances.md"
    private const val UNOFFICIAL_PUBLIC_INSTANCES =
        "https://raw.githubusercontent.com/foreign-affairs/invidious-documentation/refs/heads/master/docs/instances.md"
    private const val SECTION_START =
        "## List of public Invidious Instances (sorted from oldest to newest):"
    private const val SECTION_END = "### Tor Onion Services:"

    /**
     * This pattern translates to:
     * "match any HTTP(S) url (path included) within opening and closing parentheses,
     * but only capture the domain name"
     *
     * - `\(` and `\)`: Match the opening and closing parentheses.
     * - `https?:\/\/`: Match either **_http://_** or **_https://_**.
     * - `([^)|\s]+\.[a-z]{2,})`: Capture only the domain name (excluding paths).
     * - `(?:\/[^)]*)?`: This is a non-capturing group that matches any path after the domain, but doesn’t capture it in the results.
     */
    internal val DOMAIN_NO_PATH_REGEX = Regex( "\\(https?://([^)|\\s]+\\.[a-z]{2,})(?:/[^)]*)?\\)" )

    /**
     * Injection seam (issue #606): this module can't depend on the app's centralized
     * NzikDispatchers (Gradle dependency direction is app -> this module, never the reverse).
     * Defaults to Dispatchers.IO so the module works standalone (e.g. in its own unit tests);
     * the app wires this to NzikDispatchers.DATA once at startup, same pattern as
     * VisualizerHelper.snapshotProvider (which is @Volatile for the same single-write-at-startup,
     * read-from-background-threads reason).
     */
    @Volatile
    var backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO

    internal val scopeExceptionHandler = CoroutineExceptionHandler { _, e -> InnertubeLogger.e("InvidiousScope", "Scope coroutine failed", e) }

    internal val scope = CoroutineScope( SupervisorJob() + scopeExceptionHandler )

    var useUnofficialInstances: Boolean = true      // TODO: implement a setting toggle
        set(value) {
            if( field == value )
                return
            else
                field = value

            // Re-fetch instances when boolean is flipped
            scope.launch( backgroundDispatcher ) {
                this@Invidious.fetchInstances()
            }
        }

    override suspend fun fetchInstances() {
        super.fetchInstances()

        val url = if( useUnofficialInstances ) UNOFFICIAL_PUBLIC_INSTANCES else VERIFIED_PUBLIC_INSTANCES

        try {
            val response = HttpFetcher.CLIENT
                                      .get( url )
                                      .bodyAsText()
                                      .substringAfter( SECTION_START )
                                      .substringBefore( SECTION_END )

            instances = getDistinctFirstGroup( response, DOMAIN_NO_PATH_REGEX )
        } catch ( e: HttpRequestTimeoutException ) {
            InnertubeLogger.e("Invidious", "Failed to fetch Invidious instances", e)
        }
    }
}