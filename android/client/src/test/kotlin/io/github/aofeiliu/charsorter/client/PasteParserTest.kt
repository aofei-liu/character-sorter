package io.github.aofeiliu.charsorter.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasteParserTest {

    @Test
    fun `an inline fandom in parens is taken from the line itself`() {
        val parse = parsePaste("Utena Tenjou (Revolutionary Girl Utena)")

        assertEquals(1, parse.entries.size)
        assertEquals("Utena Tenjou", parse.entries[0].name)
        assertEquals("Revolutionary Girl Utena", parse.entries[0].fandom)
        assertTrue(parse.entries[0].inline)
        assertTrue(parse.skipped.isEmpty())
    }

    @Test
    fun `a header supplies the fandom for the bare names beneath it`() {
        val parse = parsePaste(
            """
            [Revolutionary Girl Utena]
            Utena Tenjou
            Anthy Himemiya
            """.trimIndent()
        )

        assertEquals(listOf("Utena Tenjou", "Anthy Himemiya"), parse.entries.map { it.name })
        assertEquals(
            listOf("Revolutionary Girl Utena", "Revolutionary Girl Utena"),
            parse.entries.map { it.fandom }
        )
        assertTrue(parse.entries.none { it.inline })
    }

    @Test
    fun `a second header takes over from the first`() {
        val parse = parsePaste(
            """
            [Revolutionary Girl Utena]
            Utena Tenjou

            [Neon Genesis Evangelion]
            Rei Ayanami
            """.trimIndent()
        )

        assertEquals("Revolutionary Girl Utena", parse.entries[0].fandom)
        assertEquals("Neon Genesis Evangelion", parse.entries[1].fandom)
    }

    @Test
    fun `an inline fandom overrides one line only and leaves the header in effect`() {
        val parse = parsePaste(
            """
            [Madoka Magica]
            Homura Akemi
            Sailor Neptune (Sailor Moon)
            Mami Tomoe
            """.trimIndent()
        )

        assertEquals(
            listOf("Madoka Magica", "Sailor Moon", "Madoka Magica"),
            parse.entries.map { it.fandom }
        )
        assertEquals(listOf(false, true, false), parse.entries.map { it.inline })
    }

    @Test
    fun `a bare name above any header is held back rather than sent to fail`() {
        val parse = parsePaste(
            """
            Kyubey
            [Madoka Magica]
            Homura Akemi
            """.trimIndent()
        )

        assertEquals(listOf("Homura Akemi"), parse.entries.map { it.name })
        assertEquals(1, parse.skipped.size)
        assertEquals(SkipReason.NO_FANDOM, parse.skipped[0].reason)
        assertEquals("Kyubey", parse.skipped[0].text)
    }

    @Test
    fun `a tab separates name from fandom for a spreadsheet paste`() {
        val parse = parsePaste("Rei Ayanami\tNeon Genesis Evangelion")

        assertEquals("Rei Ayanami", parse.entries[0].name)
        assertEquals("Neon Genesis Evangelion", parse.entries[0].fandom)
        assertTrue(parse.entries[0].inline)
    }

    @Test
    fun `a tab wins over trailing parens on the same line`() {
        val parse = parsePaste("Sailor Neptune\tSailor Moon (90s anime)")

        assertEquals("Sailor Neptune", parse.entries[0].name)
        assertEquals("Sailor Moon (90s anime)", parse.entries[0].fandom)
    }

    @Test
    fun `the last paren group is the fandom so a name may contain parens`() {
        val parse = parsePaste("Pyramid Head (red pyramid) (Silent Hill)")

        assertEquals("Pyramid Head (red pyramid)", parse.entries[0].name)
        assertEquals("Silent Hill", parse.entries[0].fandom)
    }

    @Test
    fun `blank and header lines still occupy a source line index`() {
        val parse = parsePaste("\nKyubey\n[Madoka Magica]\nHomura Akemi")

        assertEquals(1, parse.skipped[0].line)
        assertEquals("Kyubey", parse.skipped[0].text)
        assertEquals(3, parse.entries[0].line)
    }

    @Test
    fun `blank lines are ignored and whitespace is trimmed`() {
        val parse = parsePaste("\n   [Madoka Magica]   \n\n   Homura Akemi   \n\n")

        assertEquals(1, parse.entries.size)
        assertEquals("Homura Akemi", parse.entries[0].name)
        assertEquals("Madoka Magica", parse.entries[0].fandom)
    }

    @Test
    fun `an over-long name is flagged rather than truncated`() {
        val long = "x".repeat(FIELD_LIMIT + 1)
        val parse = parsePaste("$long (Madoka Magica)")

        assertTrue(parse.entries.isEmpty())
        assertEquals(SkipReason.NAME_TOO_LONG, parse.skipped[0].reason)
    }

    @Test
    fun `an over-long fandom is flagged rather than truncated`() {
        val parse = parsePaste("Homura Akemi\t${"x".repeat(FIELD_LIMIT + 1)}")

        assertTrue(parse.entries.isEmpty())
        assertEquals(SkipReason.FANDOM_TOO_LONG, parse.skipped[0].reason)
    }

    @Test
    fun `a name of exactly the limit is accepted`() {
        val parse = parsePaste("${"x".repeat(FIELD_LIMIT)} (Madoka Magica)")

        assertEquals(1, parse.entries.size)
        assertTrue(parse.skipped.isEmpty())
    }

    @Test
    fun `a line that is only a paren group has no name`() {
        val parse = parsePaste("(Sailor Moon)")

        assertTrue(parse.entries.isEmpty())
        assertEquals(SkipReason.NO_NAME, parse.skipped[0].reason)
    }

    @Test
    fun `an empty header clears the fandom instead of setting a blank one`() {
        val parse = parsePaste(
            """
            [Madoka Magica]
            Homura Akemi
            []
            Kyubey
            """.trimIndent()
        )

        assertEquals(listOf("Homura Akemi"), parse.entries.map { it.name })
        assertEquals(SkipReason.NO_FANDOM, parse.skipped[0].reason)
    }

    @Test
    fun `empty parens fall back to the header rather than sending a blank fandom`() {
        val parse = parsePaste(
            """
            [Madoka Magica]
            Homura Akemi ()
            """.trimIndent()
        )

        assertEquals("Madoka Magica", parse.entries[0].fandom)
        assertTrue(!parse.entries[0].inline)
    }

    @Test
    fun `a bracketed line that is not the whole line is a name`() {
        val parse = parsePaste("[Not a header] Homura (Madoka Magica)")

        assertEquals("[Not a header] Homura", parse.entries[0].name)
        assertEquals("Madoka Magica", parse.entries[0].fandom)
    }

    @Test
    fun `empty text parses to nothing`() {
        val parse = parsePaste("")

        assertTrue(parse.entries.isEmpty())
        assertTrue(parse.skipped.isEmpty())
    }
}

class ReviewPasteTest {

    private fun existing(vararg pairs: Pair<String, String>) =
        pairs.mapIndexed { index, (name, fandom) -> Character(index + 1, name, fandom) }

    @Test
    fun `a name already in the list is marked rather than posted again`() {
        val parse = parsePaste(
            """
            [Madoka Magica]
            Homura Akemi
            Sayaka Miki
            """.trimIndent()
        )

        val reviewed = reviewPaste(parse.entries, existing("Homura Akemi" to "Madoka Magica"))

        assertEquals(listOf(EntryStatus.IN_LIST, EntryStatus.NEW), reviewed.map { it.status })
    }

    @Test
    fun `matching is on name and fandom together`() {
        val parse = parsePaste("Homura Akemi (Madoka Magica Rebellion)")

        val reviewed = reviewPaste(parse.entries, existing("Homura Akemi" to "Madoka Magica"))

        assertEquals(listOf(EntryStatus.NEW), reviewed.map { it.status })
    }

    @Test
    fun `matching ignores case so a re-paste is recognized`() {
        val parse = parsePaste("homura akemi (madoka magica)")

        val reviewed = reviewPaste(parse.entries, existing("Homura Akemi" to "Madoka Magica"))

        assertEquals(listOf(EntryStatus.IN_LIST), reviewed.map { it.status })
    }

    @Test
    fun `the same pair twice in one paste marks the second as repeated`() {
        val parse = parsePaste(
            """
            [Madoka Magica]
            Homura Akemi
            Homura Akemi
            """.trimIndent()
        )

        val reviewed = reviewPaste(parse.entries, emptyList())

        assertEquals(listOf(EntryStatus.NEW, EntryStatus.REPEATED), reviewed.map { it.status })
    }

    @Test
    fun `an empty list leaves every entry new`() {
        val parse = parsePaste("Homura Akemi (Madoka Magica)")

        val reviewed = reviewPaste(parse.entries, emptyList())

        assertEquals(listOf(EntryStatus.NEW), reviewed.map { it.status })
    }
}
