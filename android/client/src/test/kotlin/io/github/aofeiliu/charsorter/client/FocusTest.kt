package io.github.aofeiliu.charsorter.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockWebServer

class FocusTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.shutdown()

    private fun pair(weight: String) = json(
        200,
        """
        {"done": false, "progress": "Average confidence: 0.500",
         "match_weight": $weight,
         "char1": {"id": 7, "name": "Alice", "fandom": "Fandom"},
         "char2": {"id": 9, "name": "Bob", "fandom": "Fandom"}}
        """
    )

    @Test
    fun `focus becomes a query parameter`() {
        val client = server.loggedInClient()
        server.enqueue(pair("0.0042"))

        val next = client.nextComparison(3, focus = 7)

        assertEquals("/api/lists/3/next?focus=7", server.takeRequest().path)
        assertEquals(7, next.char1?.id)
        assertEquals(0.0042, next.matchWeight)
    }

    @Test
    fun `no focus sends no query parameter`() {
        val client = server.loggedInClient()
        server.enqueue(pair("0.0042"))

        client.nextComparison(3)

        assertEquals("/api/lists/3/next", server.takeRequest().path)
    }

    @Test
    fun `a null match weight decodes, as insertion sort sends`() {
        val client = server.loggedInClient()
        server.enqueue(pair("null"))

        assertNull(client.nextComparison(3).matchWeight)
    }

    @Test
    fun `an absent match weight decodes, as an older server sends`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                200,
                """
                {"done": false, "progress": null,
                 "char1": {"id": 7, "name": "Alice", "fandom": "Fandom"},
                 "char2": {"id": 9, "name": "Bob", "fandom": "Fandom"}}
                """
            )
        )

        assertNull(client.nextComparison(3).matchWeight)
    }

    @Test
    fun `a foreign focus id surfaces the server's 404`() {
        val client = server.loggedInClient()
        server.enqueue(json(404, """{"error": "No such object."}"""))

        val failure = runCatching { client.nextComparison(3, focus = 99) }
        assertTrue(failure.isFailure)
    }
}
