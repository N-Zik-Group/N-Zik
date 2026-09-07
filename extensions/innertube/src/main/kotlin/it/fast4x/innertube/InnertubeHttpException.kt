package it.fast4x.innertube

import io.ktor.http.*

/**
 * Exception thrown when an InnerTube API call returns a non-success HTTP status.
 *
 * @param operation The API operation that failed (e.g. "browse", "search")
 * @param status The HTTP status code and description
 */
class InnertubeHttpException(
    val operation: String,
    val status: HttpStatusCode,
) : Exception("$operation failed with HTTP ${status.value} (${status.description})")
