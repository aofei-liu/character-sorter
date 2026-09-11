package io.github.aofeiliu.charsorter.client

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** The list and character CRUD the editing UI is built on. */
class CrudTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() = server.shutdown()

    private fun bodyOf(request: okhttp3.mockwebserver.RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body.readUtf8()) as JsonObject

    @Test
    fun `GET characters decodes the envelope`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                200,
                """
                {"characters": [
                  {"id": 3, "name": "Dimitri", "fandom": "FE3H"},
                  {"id": 7, "name": "Claude", "fandom": "FE3H"}
                ]}
                """
            )
        )

        val characters = client.characters(1)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/lists/1/characters", request.path)
        assertEquals(2, characters.size)
        assertEquals(Character(3, "Dimitri", "FE3H"), characters[0])
        // This endpoint never serializes images, whatever show_images says.
        assertNull(characters[1].image)
    }

    @Test
    fun `POST a character sends only the declared fields`() {
        val client = server.loggedInClient()
        server.enqueue(json(201, """{"id": 9, "name": "Byleth", "fandom": "FE3H"}"""))

        val created = client.addCharacter(1, "Byleth", "FE3H")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/lists/1/characters", request.path)
        val body = bodyOf(request)
        assertEquals("Byleth", body["name"]?.jsonPrimitive?.content)
        assertEquals("FE3H", body["fandom"]?.jsonPrimitive?.content)
        // characterlist is set by the view from the URL and must not be
        // client-supplied; PR #10 removed it from the form for that reason.
        assertNull(body["characterlist"])
        assertEquals(Character(9, "Byleth", "FE3H"), created)
    }

    @Test
    fun `PATCH a character omits the field that was not given`() {
        val client = server.loggedInClient()
        server.enqueue(json(200, """{"id": 3, "name": "Dimitri Alexandre", "fandom": "FE3H"}"""))

        val updated = client.updateCharacter(1, 3, name = "Dimitri Alexandre")

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/lists/1/characters/3", request.path)
        val body = bodyOf(request)
        assertEquals("Dimitri Alexandre", body["name"]?.jsonPrimitive?.content)
        // Absent, not null: the server seeds a partial bind from the stored
        // row, so a null here would be a validation error rather than a no-op.
        assertNull(body["fandom"])
        assertEquals("Dimitri Alexandre", updated.name)
    }

    @Test
    fun `PATCH with nothing to change never reaches the network`() {
        val client = server.loggedInClient()

        assertFailsWith<IllegalArgumentException> { client.updateCharacter(1, 3) }
        assertFailsWith<IllegalArgumentException> { client.updateList(1) }

        assertEquals(0, server.postLoginRequestCount)
    }

    @Test
    fun `DELETE a character sends no body and decodes no response`() {
        val client = server.loggedInClient()
        server.enqueue(MockResponse().setResponseCode(204))

        client.deleteCharacter(1, 3)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/lists/1/characters/3", request.path)
        assertEquals(0, request.bodySize)
    }

    @Test
    fun `POST a list sends the controller type on the wire`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                201,
                """{"id": 5, "title": "villains", "controller_type": "GL",
                    "show_images": false}"""
            )
        )

        val created = client.createList("villains", "GL")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/lists", request.path)
        val body = bodyOf(request)
        assertEquals("GL", body["controller_type"]?.jsonPrimitive?.content)
        // Not sent unless asked for: the server drops it anyway when no
        // image-search key is configured.
        assertNull(body["show_images"])
        assertEquals(CharacterList(5, "villains", "GL", false), created)
    }

    @Test
    fun `POST a list sends show_images when it is given`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                201,
                """{"id": 5, "title": "villains", "controller_type": "IS",
                    "show_images": true}"""
            )
        )

        val created = client.createList("villains", "IS", showImages = true)

        assertTrue(bodyOf(server.takeRequest())["show_images"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(created.showImages)
    }

    @Test
    fun `PATCH a list edits the title alone`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                200,
                """{"id": 5, "title": "heroes", "controller_type": "GL",
                    "show_images": false}"""
            )
        )

        val updated = client.updateList(5, title = "heroes")

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/lists/5", request.path)
        val body = bodyOf(request)
        assertEquals("heroes", body["title"]?.jsonPrimitive?.content)
        assertNull(body["controller_type"])
        assertEquals("heroes", updated.title)
    }

    @Test
    fun `DELETE a list targets the list itself`() {
        val client = server.loggedInClient()
        server.enqueue(MockResponse().setResponseCode(204))

        client.deleteList(5)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/lists/5", request.path)
    }

    @Test
    fun `a rejected field surfaces as InvalidRequestException with its fields`() {
        val client = server.loggedInClient()
        server.enqueue(
            json(
                400,
                """{"error": "Invalid fields.",
                    "fields": {"controller_type": ["Select a valid choice."]}}"""
            )
        )

        val err = assertFailsWith<InvalidRequestException> {
            client.createList("villains", "NOPE")
        }

        assertEquals(listOf("Select a valid choice."), err.fields["controller_type"])
    }

    @Test
    fun `a write retries once with a refreshed CSRF token`() {
        val client = server.loggedInClient()
        server.enqueue(csrfForbidden())
        server.enqueue(loginPage(cookieToken = "cookie-token-refreshed"))
        server.enqueue(json(201, """{"id": 9, "name": "Byleth", "fandom": "FE3H"}"""))

        val created = client.addCharacter(1, "Byleth", "FE3H")

        assertEquals("cookie-token-zzz", server.takeRequest().getHeader("X-CSRFToken"))
        server.takeRequest()
        assertEquals("cookie-token-refreshed", server.takeRequest().getHeader("X-CSRFToken"))
        assertEquals(9, created.id)
    }
}
