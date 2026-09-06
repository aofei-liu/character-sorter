package io.github.aofeiliu.charsorter.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Read-only probes against the deployed site, skipped by default.
 *
 * Run with `CHARSORTER_LIVE=1 ./gradlew :client:test`. No credentials are
 * needed and nothing here writes: `api_view` checks authentication before
 * anything else, so an anonymous call exercises the real 401 envelope. The
 * host also has to be reachable, which needs `*.lndyn.com` on the
 * environment's Custom network allowlist.
 */
class LiveSmokeTest {

    private fun live() = assumeTrue(System.getenv("CHARSORTER_LIVE") == "1")

    @Test
    fun `an anonymous read gets the JSON 401 envelope`() {
        live()
        val client = CharSorterClient()

        assertFalse(client.isLoggedIn)
        val error = runCatching { client.lists() }.exceptionOrNull()

        assertTrue(error is NotAuthenticatedException, "was $error")
        assertEquals("Authentication required.", error.message)
    }

    @Test
    fun `the login page sets a csrftoken cookie and carries a form token`() {
        live()
        val client = CharSorterClient()

        // Wrong credentials on purpose: this asserts the handshake's shape,
        // not that any account exists.
        val error = runCatching { client.login("", "") }.exceptionOrNull()

        assertTrue(error is LoginFailedException, "was $error")
        assertFalse(client.isLoggedIn)
        assertTrue(client.cookieJar.value(CSRF_COOKIE) != null, "no csrftoken cookie")
    }
}
