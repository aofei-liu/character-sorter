package io.github.aofeiliu.charsorter.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockWebServer

class LoginTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.shutdown()

    @Test
    fun `302 with a sessionid cookie is a successful login`() {
        server.enqueue(loginPage())
        server.enqueue(loginSuccess())
        val client = CharSorterClient(server.url("/").toString())

        client.login("owner", "hunter2")

        assertTrue(client.isLoggedIn)
        assertEquals("session-abc", client.cookieJar.value(SESSION_COOKIE))
        assertEquals("GET", server.takeRequest().method)

        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/login/", post.path)
        val body = post.body.readUtf8()
        assertTrue("username=owner" in body, body)
        assertTrue("password=hunter2" in body, body)
        // The token from the form, not the one from the cookie.
        assertTrue("csrfmiddlewaretoken=$LOGIN_PAGE_TOKEN" in body, body)
        // Django checks the referer strictly over HTTPS.
        assertEquals(server.url("/").toString().trimEnd('/'), post.getHeader("Referer"))
    }

    @Test
    fun `200 re-rendering the form is a failed login`() {
        server.enqueue(loginPage())
        server.enqueue(loginRejected())
        val client = CharSorterClient(server.url("/").toString())

        val error = assertFailsWith<LoginFailedException> { client.login("owner", "wrong") }

        assertTrue("username and password" in error.message!!, error.message!!)
        assertFalse(client.isLoggedIn)
        assertNull(client.cookieJar.value(SESSION_COOKIE))
    }

    @Test
    fun `the login redirect is not followed`() {
        server.enqueue(loginPage())
        server.enqueue(loginSuccess())
        val client = CharSorterClient(server.url("/").toString())

        client.login("owner", "hunter2")

        // Two requests, not three: following the 302 to "/" would make a
        // successful login indistinguishable from a rejected one.
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a login page with no token at all fails rather than posting`() {
        server.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody("<form></form>")
        )
        val client = CharSorterClient(server.url("/").toString())

        assertFailsWith<LoginFailedException> { client.login("owner", "hunter2") }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the cookie token is the fallback when the form carries none`() {
        server.enqueue(
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(200)
                .setHeader("Set-Cookie", "csrftoken=cookie-only; Path=/")
                .setBody("<form></form>")
        )
        server.enqueue(loginSuccess())
        val client = CharSorterClient(server.url("/").toString())

        client.login("owner", "hunter2")

        server.takeRequest()
        assertTrue("csrfmiddlewaretoken=cookie-only" in server.takeRequest().body.readUtf8())
    }

    @Test
    fun `a session can be saved and restored without the password`() {
        val client = server.loggedInClient()

        val saved = client.cookieJar.save()
        val restored = CharSorterClient(server.url("/").toString())
        restored.cookieJar.restore(saved)

        assertTrue(restored.isLoggedIn)
        assertEquals("session-abc", restored.cookieJar.value(SESSION_COOKIE))
        assertEquals("cookie-token-zzz", restored.cookieJar.value(CSRF_COOKIE))
    }

    @Test
    fun `logout forgets the session locally`() {
        val client = server.loggedInClient()

        client.logout()

        assertFalse(client.isLoggedIn)
    }
}
