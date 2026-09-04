package io.github.aofeiliu.charsorter.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class VerdictTest {

    @Test
    fun `each stored value maps to its verdict`() {
        assertEquals(Verdict.CHAR1_WINS, Verdict.fromWireValue(1))
        assertEquals(Verdict.TIE, Verdict.fromWireValue(0))
        assertEquals(Verdict.CHAR2_WINS, Verdict.fromWireValue(-1))
    }

    @Test
    fun `a value outside the three is rejected as an argument error`() {
        val error = assertFailsWith<IllegalArgumentException> { Verdict.fromWireValue(2) }

        assertEquals("Not a comparison value: 2. Expected -1, 0 or 1.", error.message)
    }

    @Test
    fun `the nullable form tolerates a record written by something else`() {
        assertNull(Verdict.fromWireValueOrNull(2))
        assertEquals(Verdict.TIE, Verdict.fromWireValueOrNull(0))
    }
}
