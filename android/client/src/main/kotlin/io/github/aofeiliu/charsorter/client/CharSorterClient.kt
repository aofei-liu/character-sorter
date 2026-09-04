package io.github.aofeiliu.charsorter.client

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal const val CSRF_COOKIE = "csrftoken"
internal const val SESSION_COOKIE = "sessionid"

const val DEFAULT_BASE_URL = "https://charsorter.lndyn.com/"

/**
 * Client for the character sorter's JSON API.
 *
 * Pure JVM by design: no Android or AndroidX type appears in this module, so
 * every risky part of the protocol is testable without the SDK.
 *
 * The instance owns a cookie jar and is safe to share across threads; calls
 * are blocking, so keep them off a UI thread.
 */
class CharSorterClient(
    baseUrl: String = DEFAULT_BASE_URL,
    httpClient: OkHttpClient = OkHttpClient()
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val origin: String = "${base.scheme}://${base.host}" +
        if (base.port == HttpUrl.defaultPort(base.scheme)) "" else ":${base.port}"

    /** Exposed so a caller can persist the session; see [SessionCookieJar]. */
    val cookieJar = SessionCookieJar(base)

    private val client: OkHttpClient = httpClient.newBuilder()
        .cookieJar(cookieJar)
        // A successful login is a 302 and a failed one is a 200 re-rendering
        // the form. Following redirects makes those two hard to tell apart.
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(CsrfInterceptor(origin, cookieJar))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** True once a `sessionid` cookie is held — no request is made. */
    val isLoggedIn: Boolean
        get() = cookieJar.value(SESSION_COOKIE) != null

    /**
     * Runs the session login handshake against the HTML site.
     *
     * `GET /login/` sets the `csrftoken` cookie and returns a form carrying
     * `csrfmiddlewaretoken`; the form POST then needs both that token and a
     * `Referer` of the site's origin. Success is a 302 plus a `sessionid`
     * cookie. Django's default session lifetime is two weeks, so treat a
     * later 401 and a re-login as a normal path rather than an error.
     *
     * @throws LoginFailedException on bad credentials or a missing token.
     */
    fun login(username: String, password: String) {
        val page = request(Request.Builder().url(url("login/")).build())
        val html = page.use { it.body?.string().orEmpty() }
        val token = FORM_TOKEN.find(html)?.groupValues?.get(1)
            ?: cookieJar.value(CSRF_COOKIE)
            ?: throw LoginFailedException("The login page carried no CSRF token.")

        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("csrfmiddlewaretoken", token)
            .build()
        request(Request.Builder().url(url("login/")).post(form).build()).use { response ->
            if (response.isRedirect && isLoggedIn) {
                return
            }
            throw LoginFailedException(
                if (response.code == 200) "Login rejected: check the username and password."
                else "Login failed with HTTP ${response.code}."
            )
        }
    }

    /** Forgets the session locally. The server-side session is left alone. */
    fun logout() = cookieJar.clear()

    /** `GET /api/lists` — the caller's own lists, oldest first. */
    fun lists(): List<CharacterList> =
        get<ListsResponse>(url("api/lists")).lists

    /**
     * `GET /api/lists/<id>` — the ranked order, with annotations and progress.
     *
     * Every call replays the list's entire comparison history server-side, so
     * fetch it when something changed, never on a timer.
     */
    fun ranking(listId: Int): Ranking = get(url("api/lists/$listId"))

    /**
     * `GET /api/lists/<id>/next` — the pair to ask next.
     *
     * **Hold the returned pair; do not re-fetch it.** Glicko samples from a
     * softmax, so a second call returns a different, equally valid question.
     * [submitComparison] names its characters explicitly, so answer the pair
     * you were handed.
     */
    fun nextComparison(listId: Int): NextComparison = get(url("api/lists/$listId/next"))

    /**
     * `POST /api/lists/<id>/comparisons` — answers one comparison.
     *
     * [timestamp] is for offline queueing and may be backdated, which is safe
     * because ratings are replayed in timestamp order. It must carry a UTC
     * offset and must not be in the future: the Glicko maths measures elapsed
     * days from each record, and a negative interval breaks every read path
     * for that list until wall-clock time catches up. The server refuses one,
     * and so does this method rather than spending a round trip on it.
     *
     * The returned [Comparison.id] is the only handle for [deleteComparison]
     * — the API has no `GET` on `/comparisons`.
     */
    fun submitComparison(
        listId: Int,
        char1: Int,
        char2: Int,
        verdict: Verdict,
        timestamp: OffsetDateTime? = null
    ): Comparison {
        require(char1 != char2) { "A character cannot be compared with itself." }
        if (timestamp != null) {
            require(!timestamp.isAfter(OffsetDateTime.now(timestamp.offset))) {
                "timestamp must not be in the future."
            }
        }
        val body = buildJsonObject {
            put("char1", char1)
            put("char2", char2)
            put("value", verdict.wireValue)
            if (timestamp != null) {
                put("timestamp", timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }
        }
        return post(url("api/lists/$listId/comparisons"), body)
    }

    /**
     * `DELETE /api/lists/<id>/comparisons/<rec_id>` — undo.
     *
     * [recordId] comes from this process's own [submitComparison], so undo
     * does not survive a restart. That is an API gap, not a client one.
     */
    fun deleteComparison(listId: Int, recordId: Int) {
        val target = url("api/lists/$listId/comparisons/$recordId")
        call(Request.Builder().url(target).delete().build()) { }
    }

    private inline fun <reified T> get(target: HttpUrl): T =
        call(Request.Builder().url(target).build()) { json.decodeFromString<T>(it) }

    private inline fun <reified T> post(target: HttpUrl, body: JsonObject): T =
        call(Request.Builder().url(target).post(body.toRequestBody()).build()) {
            json.decodeFromString<T>(it)
        }

    private fun <T> call(request: Request, decode: (String) -> T): T {
        var response = request(request)
        if (response.code == 403 && request.method != "GET") {
            // CsrfViewMiddleware rejects before the view runs, so nothing was
            // written and this retry cannot duplicate a record.
            response.close()
            refreshCsrfToken()
            response = request(request)
        }
        val body = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) {
            throw errorFor(response, body)
        }
        return try {
            decode(body)
        } catch (err: Exception) {
            throw UnexpectedResponseException(response.code, body, err)
        }
    }

    private fun request(request: Request): Response = client.newCall(request).execute()

    /** Re-reads `csrftoken` from a page that sets it. */
    private fun refreshCsrfToken() {
        request(Request.Builder().url(url("login/")).build()).close()
    }

    private fun errorFor(response: Response, body: String): ApiException = when (response.code) {
        400 -> {
            val parsed = errorBody(body)
            InvalidRequestException(
                parsed?.error ?: "The request was rejected.", parsed?.fields ?: emptyMap()
            )
        }
        401 -> NotAuthenticatedException(errorBody(body)?.error ?: "Authentication required.")
        // Django's own HTML page, not the JSON envelope: never parsed.
        403 -> CsrfException("The CSRF token was rejected.")
        404 -> NotFoundException(errorBody(body)?.error ?: "No such object.")
        405 -> MethodNotAllowedException(response.header("Allow"))
        else -> UnexpectedResponseException(response.code, body)
    }

    /** Decodes an error envelope, or null when the body is HTML or empty. */
    private fun errorBody(body: String): ErrorBody? = try {
        json.decodeFromString<ErrorBody>(body)
    } catch (err: Exception) {
        null
    }

    private fun url(path: String): HttpUrl = base.newBuilder(path)!!.build()

    private fun JsonObject.toRequestBody(): RequestBody =
        json.encodeToString(JsonObject.serializer(), this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val FORM_TOKEN =
            Regex("""name=["']csrfmiddlewaretoken["'][^>]*value=["']([^"']+)["']""")
    }
}
