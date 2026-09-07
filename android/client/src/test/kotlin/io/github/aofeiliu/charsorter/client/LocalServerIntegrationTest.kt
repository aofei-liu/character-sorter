package io.github.aofeiliu.charsorter.client

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Integration tests against a real Django instance, skipped by default.
 *
 * These exercise the two paths a MockWebServer can only imitate, and which
 * ROADMAP.md ("Two things P0 could not do") calls out as unproven:
 *
 *  - a genuine `201` from `POST /api/lists/<id>/comparisons`, decoded into a
 *    [Comparison] with a server-assigned id and timestamp, and undone again;
 *  - the 403 -> refresh -> retry path driven by a *genuinely* stale
 *    `csrftoken`, not an enqueued 403.
 *
 * Run against a local server only. The API's `DELETE` endpoints work, so this
 * must never point at `charsorter.lndyn.com`; that is what [LiveSmokeTest]
 * (read-only, anonymous) is for.
 *
 * ```
 * # from charactersorter/, with the Path B venv and dev_sqlite_settings:
 * #   python manage.py runserver 127.0.0.1:8000
 * #   python manage.py createsuperuser   # or reuse the seeded 'dev' account
 * CHARSORTER_LOCAL=1 ./gradlew :client:test --tests '*LocalServerIntegrationTest*'
 * ```
 *
 * Configuration, all optional:
 *
 * | Env var                 | Default                    |
 * | ----------------------- | -------------------------- |
 * | `CHARSORTER_LOCAL`      | unset -> every test skips  |
 * | `CHARSORTER_LOCAL_URL`  | `http://127.0.0.1:8000/`   |
 * | `CHARSORTER_LOCAL_USER` | `dev`                      |
 * | `CHARSORTER_LOCAL_PASS` | `devpass`                  |
 *
 * The account needs at least one `GlickoRatingController` list with two or
 * more characters. Glicko never reports `done`, so `nextComparison` always
 * hands back a real pair to answer.
 */
class LocalServerIntegrationTest {

    private val baseUrl: String =
        System.getenv("CHARSORTER_LOCAL_URL") ?: "http://127.0.0.1:8000/"
    private val username: String = System.getenv("CHARSORTER_LOCAL_USER") ?: "dev"
    private val password: String = System.getenv("CHARSORTER_LOCAL_PASS") ?: "devpass"

    private fun enabled() = assumeTrue(
        System.getenv("CHARSORTER_LOCAL") != null,
        "set CHARSORTER_LOCAL=1 to run the local integration suite"
    )

    private fun loggedInClient(): CharSorterClient {
        val client = CharSorterClient(baseUrl)
        client.login(username, password)
        assertTrue(client.isLoggedIn, "login handshake did not yield a session")
        return client
    }

    private fun CharSorterClient.glickoListId(): Int {
        val lists = lists()
        assumeTrue(lists.isNotEmpty(), "the $username account has no lists")
        val glicko = lists.firstOrNull { it.controllerType == "GL" }
        assumeTrue(glicko != null, "no GlickoRatingController list to answer")
        return glicko!!.id
    }

    @Test
    fun `the login handshake ends in a usable session`() {
        enabled()
        val client = loggedInClient()

        val lists = client.lists()

        // A second call reuses the sessionid cookie rather than re-running
        // the handshake; isLoggedIn is cookie state, no request.
        assertTrue(client.isLoggedIn)
        assertNotNull(lists)
    }

    @Test
    fun `answer one comparison, decode the 201, then undo it`() {
        enabled()
        val client = loggedInClient()
        val listId = client.glickoListId()

        val pair = client.nextComparison(listId)
        assertTrue(!pair.done && pair.char1 != null && pair.char2 != null)

        val record = client.submitComparison(
            listId, pair.char1!!.id, pair.char2!!.id, Verdict.CHAR1_WINS
        )

        // The parts a mock cannot vouch for: a real auto-increment id and a
        // server-set timestamp, from Django and not from a fixture.
        assertTrue(record.id > 0, "expected a server-assigned id, got ${record.id}")
        assertEquals(pair.char1!!.id, record.char1)
        assertEquals(pair.char2!!.id, record.char2)
        assertEquals(1, record.value)
        assertNotNull(OffsetDateTime.parse(record.timestamp))

        // Undo, so the run leaves the list as it found it.
        client.deleteComparison(listId, record.id)
    }

    @Test
    fun `a backdated timestamp round-trips`() {
        enabled()
        val client = loggedInClient()
        val listId = client.glickoListId()
        val pair = client.nextComparison(listId)
        assumeTrue(!pair.done && pair.char1 != null && pair.char2 != null)

        val yesterday = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
        val record = client.submitComparison(
            listId, pair.char1!!.id, pair.char2!!.id, Verdict.TIE, yesterday
        )

        assertEquals(0, record.value)
        // auto_now_add is overwritten after save(); the stored value is the
        // backdated one, to the second.
        assertEquals(
            yesterday.toEpochSecond(),
            OffsetDateTime.parse(record.timestamp).toEpochSecond()
        )

        client.deleteComparison(listId, record.id)
    }

    @Test
    fun `a genuinely stale csrftoken is refreshed and the write still lands`() {
        enabled()
        val client = loggedInClient()
        val listId = client.glickoListId()
        val pair = client.nextComparison(listId)
        assumeTrue(!pair.done && pair.char1 != null && pair.char2 != null)

        // Keep the real session, but replace the CSRF cookie with a value
        // Django will reject. The interceptor sends this same bad value as
        // X-CSRFToken, so CsrfViewMiddleware 403s before the view runs --
        // exactly the stale-token case, reached without an enqueued 403.
        val sessionId = client.cookieJar.value(SESSION_COOKIE)
        assertNotNull(sessionId, "no sessionid to preserve")
        client.cookieJar.restore(
            listOf(
                "sessionid=$sessionId; Path=/",
                "csrftoken=staley-mcstaleface; Path=/"
            )
        )

        // No CsrfException: call() catches the 403 on a non-GET, refreshes
        // the token from /login/, and retries once. The retry is safe
        // because the rejected request never reached the view.
        val record = client.submitComparison(
            listId, pair.char1!!.id, pair.char2!!.id, Verdict.CHAR2_WINS
        )

        assertTrue(record.id > 0)
        assertTrue(client.cookieJar.value(CSRF_COOKIE) != "staley-mcstaleface")

        client.deleteComparison(listId, record.id)
    }

    @Test
    fun `wrong credentials raise LoginFailedException`() {
        enabled()
        val client = CharSorterClient(baseUrl)

        val error = assertFailsWith<LoginFailedException> {
            client.login(username, "$password-definitely-wrong")
        }

        assertTrue(!client.isLoggedIn)
        // Django re-renders the form with a 200, not a transport failure.
        assertTrue(
            error.message!!.contains("username and password"),
            "was: ${error.message}"
        )
    }

    @Test
    fun `an unknown list id is a 404, not a leak`() {
        enabled()
        val client = loggedInClient()

        assertFailsWith<NotFoundException> { client.ranking(999_999) }
    }

    @Test
    fun `an anonymous read gets the JSON 401 envelope`() {
        enabled()
        val client = CharSorterClient(baseUrl)

        val error = assertFailsWith<NotAuthenticatedException> { client.lists() }

        assertEquals("Authentication required.", error.message)
    }
}
