package io.github.aofeiliu.charsorter.client

import kotlin.test.Test
import kotlin.test.assertEquals

class ListOrderTest {

    private fun list(id: Int) = CharacterList(
        id = id, title = "List $id", controllerType = "GL", showImages = false
    )

    private fun idsOf(lists: List<CharacterList>) = lists.map { it.id }

    @Test
    fun `the saved order wins over the server's`() {
        val lists = listOf(list(1), list(2), list(3))

        assertEquals(listOf(3, 1, 2), idsOf(ListOrder.apply(lists, listOf(3, 1, 2))))
    }

    @Test
    fun `an id the server no longer returns is dropped`() {
        val lists = listOf(list(1), list(3))

        assertEquals(listOf(3, 1), idsOf(ListOrder.apply(lists, listOf(3, 2, 1))))
    }

    @Test
    fun `a list the saved order has not seen is appended in server order`() {
        val lists = listOf(list(1), list(2), list(4), list(5))

        assertEquals(listOf(2, 1, 4, 5), idsOf(ListOrder.apply(lists, listOf(2, 1))))
    }

    @Test
    fun `no saved order leaves the server's order alone`() {
        val lists = listOf(list(1), list(2), list(3))

        assertEquals(listOf(1, 2, 3), idsOf(ListOrder.apply(lists, emptyList())))
    }

    @Test
    fun `a repeated id cannot duplicate a list`() {
        val lists = listOf(list(1), list(2))

        assertEquals(listOf(2, 1), idsOf(ListOrder.apply(lists, listOf(2, 2, 1))))
    }

    @Test
    fun `moving shifts one place in each direction`() {
        assertEquals(listOf(1, 3, 2), ListOrder.moved(listOf(1, 2, 3), 3, -1))
        assertEquals(listOf(2, 1, 3), ListOrder.moved(listOf(1, 2, 3), 1, 1))
    }

    @Test
    fun `moving past either end changes nothing`() {
        assertEquals(listOf(1, 2, 3), ListOrder.moved(listOf(1, 2, 3), 1, -1))
        assertEquals(listOf(1, 2, 3), ListOrder.moved(listOf(1, 2, 3), 3, 1))
    }

    @Test
    fun `moving an id that is not there changes nothing`() {
        assertEquals(listOf(1, 2), ListOrder.moved(listOf(1, 2), 9, -1))
    }
}
