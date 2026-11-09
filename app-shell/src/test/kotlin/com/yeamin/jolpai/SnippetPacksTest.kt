package com.yeamin.jolpai

import com.yeamin.jolpai.clipboard.SnippetPack
import com.yeamin.jolpai.data.SnippetCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetPacksTest {
    private val all = SnippetPack.entries.flatMap { it.items }

    @Test fun `shortcuts are unique, start with @ and have no spaces`() {
        val shortcuts = all.map { it.shortcut }
        assertEquals(shortcuts.size, shortcuts.toSet().size)
        assertTrue(shortcuts.all { it.startsWith("@") && it.length >= 3 && it.none(Char::isWhitespace) })
    }

    @Test fun `texts are unique and not blank`() {
        assertTrue(all.all { it.text.isNotBlank() && it.title.isNotBlank() })
        assertEquals(all.size, all.map { it.text }.toSet().size)
    }

    @Test fun `the Islamic pack has salam and its answer`() {
        val texts = SnippetPack.ISLAMIC.items.map { it.text }
        assertTrue("আসসালামু আলাইকুম" in texts)
        assertTrue("ওয়ালাইকুম আসসালাম" in texts)
        assertTrue(SnippetPack.ISLAMIC.items.all { it.category == SnippetCategory.ISLAMIC })
    }

    @Test fun `new snippets with an Islamic greeting are put under Islamic`() {
        assertEquals(SnippetCategory.ISLAMIC, SnippetCategory.suggest("আলহামদুলিল্লাহ, ভালো আছি"))
        assertEquals(SnippetCategory.ISLAMIC, SnippetCategory.suggest("Eid Mubarak to you all"))
        assertEquals(SnippetCategory.REPLY, SnippetCategory.suggest("On my way"))
    }
}
