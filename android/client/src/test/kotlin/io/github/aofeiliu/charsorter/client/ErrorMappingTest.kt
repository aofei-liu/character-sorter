package io.github.aofeiliu.charsorter.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** The API's error shape is not uniform; this is the table from ROADMAP.md. */
class ErrorMappingTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.shutdown()

    @Test
    fun `401 means the session expired`() {
        val client = server.loggedInClient()
        server.enqueue(json(401, """{"error": "Authentication required."}"""))

        val error = assertFailsWith<NotAuthenticatedException> { client.lists() }

        assertEquals("Authentication required.", error.message)
    }

    @Test
    fun `400 surfaces the per-field errors`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                400,
                """
                {"error": "Invalid fields.",
                 "fields": {"title": ["This field is required."],
                            "controller_type": ["Select a valid choice.",
                                                "Not one of the choices."]}}
                """
            )
        )

        val error = assertFailsWith<InvalidRequestException> { client.submitComparison(1, 3, 7, Verdict.TIE) }

        assertEquals("Invalid fields.", error.message)
        assertEquals(listOf("This field is required."), error.fields["title"])
        assertEquals(2, error.fields.getValue("controller_type").size)
    }

    @Test
    fun `400 without a fields key still carries the sentence`() {
        val client = server.loggedInClient()
        server.enqueue(json(400, """{"error": "Field value must be one of -1, 0 or 1."}"""))

        val error = assertFailsWith<InvalidRequestException> { client.submitComparison(1, 3, 7, Verdict.TIE) }

        assertEquals("Field value must be one of -1, 0 or 1.", error.message)
        assertTrue(error.fields.isEmpty())
    }

    @Test
    fun `404 is not-found whether the body is JSON or HTML`() {
        val client = server.loggedInClient()
        server.enqueue(json(404, """{"error": "No such object."}"""))
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setHeader("Content-Type", "text/html")
                .setBody("<h1>Not Found</h1><p>The requested URL was not found.</p>")
        )

        assertEquals("No such object.", assertFailsWith<NotFoundException> { client.ranking(9) }.message)
        // Django's own HTML 404 (an unrouted path) must not become a parse error.
        assertEquals("No such object.", assertFailsWith<NotFoundException> { client.ranking(9) }.message)
    }

    @Test
    fun `405 has an empty body and names the allowed methods`() {
        val client = server.loggedInClient()
        server.enqueue(
            MockResponse().setResponseCode(405).setHeader("Allow", "GET, POST").setBody("")
        )

        val error = assertFailsWith<MethodNotAllowedException> { client.lists() }

        assertEquals("GET, POST", error.allow)
    }

    @Test
    fun `a 500 keeps the body for a bug report`() {
        val client = server.loggedInClient()
        server.enqueue(MockResponse().setResponseCode(500).setBody("<h1>Server Error (500)</h1>"))

        val error = assertFailsWith<UnexpectedResponseException> { client.lists() }

        assertEquals(500, error.code)
        assertTrue("Server Error" in error.body)
    }

    @Test
    fun `a 200 that is not JSON is reported as unexpected, not as a crash`() {
        val client = server.loggedInClient()
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>login page</html>"))

        val error = assertFailsWith<UnexpectedResponseException> { client.lists() }

        assertEquals(200, error.code)
        // The decode failure is the only account of *why* the body was
        // unusable, so it has to survive as the cause.
        assertNotNull(error.cause)
    }

    @Test
    fun `every API failure is one ApiException a caller can catch`() {
        val client = server.loggedInClient()
        server.enqueue(json(401, """{"error": "Authentication required."}"""))

        assertFailsWith<ApiException> { client.lists() }
    }
}
