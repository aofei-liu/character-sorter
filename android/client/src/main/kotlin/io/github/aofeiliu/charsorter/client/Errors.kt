package io.github.aofeiliu.charsorter.client

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base class for every failure the API reports.
 *
 * The API's error shape is *not* uniform: 401, 400 and most 404s carry
 * `{"error": ...}`, but a CSRF rejection is Django's own HTML page and a 405
 * has an empty body. Nothing here parses a body it was not promised, so
 * [message] falls back to a fixed sentence whenever the body is not JSON.
 */
sealed class ApiException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** 401. The session is missing or expired — re-run the login handshake. */
class NotAuthenticatedException(message: String) : ApiException(message)

/**
 * 403. Django's `CsrfViewMiddleware` rejected the write *before* the view
 * ran, so the body is an HTML page and no record was stored. The client
 * refreshes the token and retries once before raising this.
 */
class CsrfException(message: String) : ApiException(message)

/** 400. [fields] is present when a `ModelForm` rejected the body. */
class InvalidRequestException(
    message: String,
    val fields: Map<String, List<String>> = emptyMap()
) : ApiException(message)

/**
 * 404. The API returns this for "no such object" *and* for "not yours",
 * deliberately — distinguishing them would confirm another user's list
 * exists. Do not present it as anything but not-found.
 */
class NotFoundException(message: String) : ApiException(message)

/** 405. Empty body; the server names the permitted methods in [allow]. */
class MethodNotAllowedException(val allow: String?) :
    ApiException("Method not allowed." + (allow?.let { " Allowed: $it" } ?: ""))

/**
 * Any other status, or a body that did not decode.
 *
 * [cause] carries the decode failure when there was one; [body] is retained
 * either way, since an unexpected status often has no parseable body at all.
 */
class UnexpectedResponseException(
    val code: Int,
    val body: String,
    cause: Throwable? = null
) : ApiException("Unexpected HTTP $code response.", cause)

/**
 * The login handshake did not end in a session.
 *
 * Django re-renders the login form with a 200 on bad credentials, so this is
 * the ordinary wrong-password path and not only a transport failure.
 */
class LoginFailedException(message: String) : ApiException(message)

@Serializable
internal data class ErrorBody(
    @SerialName("error") val error: String? = null,
    @SerialName("fields") val fields: Map<String, List<String>> = emptyMap()
)
