package io.github.aofeiliu.charsorter.client

/**
 * A caller-chosen display order for lists, applied over `GET /api/lists`.
 *
 * The server has no ordering field and returns lists by id, so this order is
 * the client's own and lives wherever the client persists it. Pure functions
 * here; storage is the caller's problem.
 */
object ListOrder {

    /**
     * [lists] in [savedIds] order, ids that no longer exist dropped and lists
     * the saved order has not seen appended in the server's own order.
     *
     * Appending rather than dropping matters: a list created on the website or
     * another device is not in [savedIds] and must still be reachable.
     */
    fun apply(lists: List<CharacterList>, savedIds: List<Int>): List<CharacterList> {
        val byId = lists.associateBy { it.id }
        val known = savedIds.distinct()
        val ordered = known.mapNotNull { byId[it] }
        val seen = known.toSet()
        return ordered + lists.filter { it.id !in seen }
    }

    /**
     * [ids] with [id] shifted by [offset] places, or unchanged if that would
     * leave the list or [id] is absent.
     */
    fun moved(ids: List<Int>, id: Int, offset: Int): List<Int> {
        val from = ids.indexOf(id)
        val to = from + offset
        if (from < 0 || to < 0 || to > ids.lastIndex) {
            return ids
        }
        val moved = ids.toMutableList()
        moved.removeAt(from)
        moved.add(to, id)
        return moved
    }
}
