package com.yeamin.jolpai.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiDataTest {
    companion object {
        private val data by lazy { EmojiData.load() }
    }

    @Test fun `loads every group`() {
        assertTrue(data.all.size > 1800)
        EmojiGroup.entries.forEach { assertTrue(it.name, data.group(it).isNotEmpty()) }
    }

    @Test fun `skin tones`() {
        val wave = data.find("👋")!!
        assertTrue(wave.hasTones)
        assertEquals("👋🏽", wave.withTone(3))
        assertEquals("👋", wave.withTone(0))
        assertEquals(wave, data.find("👋🏿"))
        assertTrue(!data.find("😀")!!.hasTones)
    }

    @Test fun `english keyword suggestions`() {
        // CLDR lists several laughing faces for "lol"; any of them is a good suggestion.
        assertTrue(data.suggestionsFor("lol").all { it.value in setOf("😄", "😆", "🤣", "😂", "😹", "🫠") })
        assertEquals(3, data.suggestionsFor("lol").size)
        assertTrue(data.suggestionsFor("Pizza").any { it.value == "🍕" })
    }

    @Test fun `bangla keyword suggestions`() {
        assertTrue(data.suggestionsFor("পতাকা").isNotEmpty())
        assertTrue(data.suggestionsFor("আনন্দ").any { it.value == "😂" })
    }

    @Test fun `search by prefix in both languages`() {
        assertTrue(data.search("cat").any { it.value == "🐱" })
        assertTrue(data.search("বাংলাদেশ").any { it.value == "🇧🇩" })
    }

    @Test fun `bangla names`() = assertNotNull(data.banglaName(data.find("😂")!!))

    @Test fun `too short words get no suggestions`() = assertTrue(data.suggestionsFor("a").isEmpty())
}
