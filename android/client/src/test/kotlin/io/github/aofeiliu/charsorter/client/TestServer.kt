package io.github.aofeiliu.charsorter.client

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** The login page Django serves, reduced to the parts the client reads. */
const val LOGIN_PAGE_TOKEN = "form-token-aaa"

fun loginPage(cookieToken: String = "cookie-token-zzz"): MockResponse = MockResponse()
    .setResponseCode(200)
    .setHeader("Set-Cookie", "csrftoken=$cookieToken; Path=/; Max-Age=31449600")
    .setBody(
        """
        <form method="post" action="/login/">
        <input type="hidden" name="csrfmiddlewaretoken" value="$LOGIN_PAGE_TOKEN">
        <input name="username"><input type="password" name="password">
        </form>
        """.trimIndent()
    )

/** Django's 302 to the next page, plus the session cookie. */
fun loginSuccess(): MockResponse = MockResponse()
    .setResponseCode(302)
    .setHeader("Location", "/")
    .setHeader("Set-Cookie", "sessionid=session-abc; Path=/; HttpOnly")

/** Django re-renders the form with a 200 when the credentials are wrong. */
fun loginRejected(): MockResponse = loginPage().setBody(
    "<form method=\"post\"><p>Please enter a correct username and password.</p></form>"
)

fun json(code: Int, body: String): MockResponse = MockResponse()
    .setResponseCode(code)
    .setHeader("Content-Type", "application/json")
    .setBody(body)

/**
 * What `CsrfViewMiddleware` actually returns: an HTML page, not the JSON
 * error envelope, because it rejects the request before the view runs.
 */
fun csrfForbidden(): MockResponse = MockResponse()
    .setResponseCode(403)
    .setHeader("Content-Type", "text/html")
    .setBody(
        "<!DOCTYPE html><html><head><title>403 Forbidden</title></head>" +
            "<body><h1>Forbidden <span>(403)</span></h1>" +
            "<p>CSRF verification failed. Request aborted.</p></body></html>"
    )

/** Logs the client in against [server], consuming two enqueued responses. */
fun MockWebServer.loggedInClient(): CharSorterClient {
    enqueue(loginPage())
    enqueue(loginSuccess())
    val client = CharSorterClient(url("/").toString())
    client.login("owner", "hunter2")
    takeRequest()
    takeRequest()
    return client
}

const val COMPARISON_201 = """
{"id": 42, "char1": 3, "char2": 7, "value": 1, "timestamp": "2026-09-02T09:41:12.523Z"}
"""

/** Requests received beyond the two the login handshake itself spends. */
val MockWebServer.postLoginRequestCount: Int
    get() = requestCount - 2
