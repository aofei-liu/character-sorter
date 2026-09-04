package io.github.aofeiliu.charsorter.client

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class EndpointsTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.shutdown()

    @Test
    fun `GET lists decodes the envelope`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                200,
                """
                {"lists": [
                  {"id": 1, "title": "fe3h husbandos", "controller_type": "GL",
                   "show_images": false},
                  {"id": 2, "title": "fe3h waifus", "controller_type": "IS",
                   "show_images": true}
                ]}
                """
            )
        )

        val lists = client.lists()

        assertEquals("/api/lists", server.takeRequest().path)
        assertEquals(2, lists.size)
        assertEquals(CharacterList(1, "fe3h husbandos", "GL", false), lists[0])
        assertTrue(lists[1].showImages)
    }

    @Test
    fun `GET a list decodes the ranking, including an integer annotation`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                200,
                """
                {"id": 1, "title": "fe3h husbandos", "controller_type": "GL",
                 "show_images": false, "progress": "Average confidence: 0.834",
                 "characters": [
                   {"id": 3, "name": "Dimitri", "fandom": "FE3H", "rank": 1,
                    "annotation": 1732},
                   {"id": 7, "name": "Claude", "fandom": "FE3H", "rank": 2,
                    "annotation": null}
                 ]}
                """
            )
        )

        val ranking = client.ranking(1)

        assertEquals("/api/lists/1", server.takeRequest().path)
        assertEquals("Average confidence: 0.834", ranking.progress)
        // Glicko annotates with an int and insertion sort with a string; both
        // arrive as text rather than blowing up the decoder.
        assertEquals("1732", ranking.characters[0].annotation)
        assertNull(ranking.characters[1].annotation)
        assertEquals(2, ranking.characters[1].rank)
    }

    @Test
    fun `a string annotation decodes too`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                200,
                """
                {"id": 2, "title": "fe3h waifus", "controller_type": "IS",
                 "show_images": false, "progress": "3/10 sorted",
                 "characters": [
                   {"id": 5, "name": "Edelgard", "fandom": "FE3H", "rank": 1,
                    "annotation": "Now Sorting"}
                 ]}
                """
            )
        )

        assertEquals("Now Sorting", client.ranking(2).characters[0].annotation)
    }

    @Test
    fun `GET next decodes a pair with images`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                200,
                """
                {"done": false, "progress": "3/10 sorted",
                 "char1": {"id": 3, "name": "Dimitri", "fandom": "FE3H",
                           "image": {"thumbnailLink": "https://img/1",
                                     "contextLink": "https://ctx/1"}},
                 "char2": {"id": 7, "name": "Claude", "fandom": "FE3H"}}
                """
            )
        )

        val next = client.nextComparison(1)

        assertEquals("/api/lists/1/next", server.takeRequest().path)
        assertEquals(false, next.done)
        assertEquals(3, next.char1!!.id)
        assertEquals("https://img/1", next.char1!!.image!!.thumbnailLink)
        assertNull(next.char2!!.image)
    }

    @Test
    fun `GET next decodes a finished list`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(200, """{"done": true, "char1": null, "char2": null, "progress": null}""")
        )

        val next = client.nextComparison(1)

        assertTrue(next.done)
        assertNull(next.char1)
        assertNull(next.progress)
    }

    @Test
    fun `the comparison POST body carries char1, char2 and value`() {
        val client = server.loggedInClient()
        server.enqueue(json(201, COMPARISON_201))

        val record = client.submitComparison(1, 3, 7, Verdict.CHAR1_WINS)

        val post = server.takeRequest()
        assertEquals("/api/lists/1/comparisons", post.path)
        assertEquals("application/json; charset=utf-8", post.getHeader("Content-Type"))
        assertEquals("""{"char1":3,"char2":7,"value":1}""", post.body.readUtf8())
        assertEquals(42, record.id)
        assertEquals(1, record.value)
        assertEquals("2026-09-02T09:41:12.523Z", record.timestamp)
    }

    @Test
    fun `each verdict sends the value the server accepts`() {
        val client = server.loggedInClient()
        for (verdict in Verdict.entries) {
            server.enqueue(json(201, COMPARISON_201))
            client.submitComparison(1, 3, 7, verdict)
            assertTrue(
                """"value":${verdict.wireValue}""" in server.takeRequest().body.readUtf8(),
                verdict.name
            )
        }
        assertEquals(listOf(1, 0, -1), Verdict.entries.map { it.wireValue })
    }

    @Test
    fun `an offline comparison sends a backdated timestamp with an offset`() {
        val client = server.loggedInClient()
        server.enqueue(json(201, COMPARISON_201))

        client.submitComparison(
            1, 3, 7, Verdict.TIE,
            OffsetDateTime.of(2026, 9, 1, 12, 30, 0, 0, ZoneOffset.UTC)
        )

        assertEquals(
            """{"char1":3,"char2":7,"value":0,"timestamp":"2026-09-01T12:30:00Z"}""",
            server.takeRequest().body.readUtf8()
        )
    }

    @Test
    fun `a future timestamp is refused before it reaches the server`() {
        val client = server.loggedInClient()

        // Forward-dating breaks every read path for the list until wall-clock
        // time catches up, so a fast clock must not be able to send one.
        assertFailsWith<IllegalArgumentException> {
            client.submitComparison(
                1, 3, 7, Verdict.TIE, OffsetDateTime.now(ZoneOffset.UTC).plusHours(1)
            )
        }
        assertEquals(0, server.postLoginRequestCount)
    }

    @Test
    fun `comparing a character with itself is refused locally`() {
        val client = server.loggedInClient()

        assertFailsWith<IllegalArgumentException> {
            client.submitComparison(1, 3, 3, Verdict.TIE)
        }
        assertEquals(0, server.postLoginRequestCount)
    }

    @Test
    fun `undo accepts the 204 with no body`() {
        val client = server.loggedInClient()
        server.enqueue(MockResponse().setResponseCode(204))

        client.deleteComparison(1, 42)

        assertEquals("/api/lists/1/comparisons/42", server.takeRequest().path)
    }
}
