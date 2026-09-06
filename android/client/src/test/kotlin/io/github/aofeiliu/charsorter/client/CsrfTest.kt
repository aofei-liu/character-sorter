package io.github.aofeiliu.charsorter.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockWebServer

class CsrfTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.shutdown()

    @Test
    fun `writes carry the CSRF token and the referer`() {
        val client = server.loggedInClient()
        server.enqueue(json(201, COMPARISON_201))

        client.submitComparison(1, 3, 7, Verdict.CHAR1_WINS)

        val post = server.takeRequest()
        assertEquals("cookie-token-zzz", post.getHeader("X-CSRFToken"))
        assertEquals(server.url("/").toString().trimEnd('/'), post.getHeader("Referer"))
        assertTrue(post.getHeader("Cookie")!!.contains("sessionid=session-abc"))
    }

    @Test
    fun `DELETE carries the CSRF token too`() {
        val client = server.loggedInClient()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(204))

        client.deleteComparison(1, 42)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/lists/1/comparisons/42", request.path)
        assertEquals("cookie-token-zzz", request.getHeader("X-CSRFToken"))
    }

    @Test
    fun `reads send no CSRF header`() {
        val client = server.loggedInClient()
        server.enqueue(json(200, """{"lists": []}"""))

        client.lists()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertNull(request.getHeader("X-CSRFToken"))
    }

    @Test
    fun `a stale token is refreshed and the write retried once`() {
        val client = server.loggedInClient()
        server.enqueue(csrfForbidden())
        server.enqueue(loginPage(cookieToken = "fresh-token"))
        server.enqueue(json(201, COMPARISON_201))

        val record = client.submitComparison(1, 3, 7, Verdict.TIE)

        assertEquals(42, record.id)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("/login/", server.takeRequest().path)
        val retry = server.takeRequest()
        assertEquals("POST", retry.method)
        assertEquals("fresh-token", retry.getHeader("X-CSRFToken"))
        // The body is replayed unchanged; CSRF is rejected before the view
        // runs, so the first attempt stored nothing to duplicate.
        assertEquals("""{"char1":3,"char2":7,"value":0}""", retry.body.readUtf8())
    }

    @Test
    fun `a second 403 raises CsrfException without parsing the HTML body`() {
        val client = server.loggedInClient()
        server.enqueue(csrfForbidden())
        server.enqueue(loginPage(cookieToken = "fresh-token"))
        server.enqueue(csrfForbidden())

        val error = assertFailsWith<CsrfException> {
            client.submitComparison(1, 3, 7, Verdict.CHAR2_WINS)
        }

        // The 403 body is Django's HTML page. A client that decoded every
        // error body as JSON would surface a parse failure here instead.
        assertEquals("The CSRF token was rejected.", error.message)
        assertEquals(3, server.postLoginRequestCount)
    }

    @Test
    fun `a 403 on a read is not retried`() {
        val client = server.loggedInClient()
        server.enqueue(csrfForbidden())

        assertFailsWith<CsrfException> { client.ranking(1) }

        assertEquals(1, server.postLoginRequestCount)
    }
}
