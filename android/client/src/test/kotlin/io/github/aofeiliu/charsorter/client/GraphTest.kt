package io.github.aofeiliu.charsorter.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockWebServer

/** `/graph`, the only route that reports the uncertainty behind a ranking. */
class GraphTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.shutdown()

    @Test
    fun `GET graph decodes the parallel arrays`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                200,
                """
                {"graph_type": "bar_with_error",
                 "characters": ["Claude", "Dimitri", "Edelgard"],
                 "ratings": [3550.5, 1712.0, 1500.0],
                 "double_rds": [414.2, 96.5, 700.0]}
                """
            )
        )

        val graph = client.graph(4)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/lists/4/graph", request.path)
        assertEquals(listOf("Claude", "Dimitri", "Edelgard"), graph.characters)
        assertEquals(listOf(3550.5, 1712.0, 1500.0), graph.ratings)
        // Already doubled server-side: the caller must not double it again.
        assertEquals(listOf(414.2, 96.5, 700.0), graph.doubleRds)
    }

    @Test
    fun `an insertion sort list has no graph to decode`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(404, """{"error": "This list's controller has no graph."}""")
        )

        assertFailsWith<NotFoundException> { client.graph(4) }
    }
}
