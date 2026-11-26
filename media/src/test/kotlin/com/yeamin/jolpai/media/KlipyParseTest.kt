package com.yeamin.jolpai.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KlipyParseTest {

    private fun item(id: Long, slug: String, mdGifSize: Long, type: String = "gif") = """
        {"id": $id, "slug": "$slug", "title": "Hello", "type": "$type",
         "file": {
           "hd": {"gif": {"url": "https://static.klipy.com/$slug-hd.gif", "width": 498, "height": 498, "size": 4001918}},
           "md": {"gif": {"url": "https://static.klipy.com/$slug-md.gif", "width": 498, "height": 498, "size": $mdGifSize},
                  "jpg": {"url": "https://static.klipy.com/$slug-md.jpg", "width": 498, "height": 498, "size": 20086}},
           "sm": {"gif": {"url": "https://static.klipy.com/$slug-sm.gif", "width": 220, "height": 220, "size": 314884},
                  "jpg": {"url": "https://static.klipy.com/$slug-sm.jpg", "width": 220, "height": 220, "size": 8560}},
           "xs": {"jpg": {"url": "https://static.klipy.com/$slug-xs.jpg", "width": 90, "height": 90, "size": 2949}}
         }}
    """

    @Test fun `parses search results with a small still preview and a sendable gif`() {
        val json = """{"result": true, "data": {"data": [${item(8041071659142944, "hello-hi-662", 900_000)}], "current_page": 1, "per_page": 24, "has_next": true}}"""
        val gifs = KlipyProvider.parse(json)
        assertEquals(1, gifs.size)
        val g = gifs[0]
        assertEquals("8041071659142944", g.id)
        assertEquals("hello-hi-662", g.slug)
        assertEquals("Hello", g.title)
        assertEquals("https://static.klipy.com/hello-hi-662-sm.jpg", g.previewUrl)
        assertEquals("https://static.klipy.com/hello-hi-662-md.gif", g.gifUrl)
    }

    @Test fun `big gifs are sent in the small size`() {
        val json = """{"result": true, "data": {"data": [${item(1, "big", 3_721_260)}]}}"""
        assertEquals("https://static.klipy.com/big-sm.gif", KlipyProvider.parse(json).single().gifUrl)
    }

    @Test fun `ads and broken items are skipped`() {
        val json = """{"result": true, "data": {"data": [${item(1, "ad-1", 900_000, type = "ad")}, {"id": 2, "slug": "no-files"}, ${item(3, "ok", 900_000)}]}}"""
        assertEquals(listOf("ok"), KlipyProvider.parse(json).map { it.slug })
    }

    @Test fun `empty or odd responses give no gifs`() {
        assertTrue(KlipyProvider.parse("""{"result": false}""").isEmpty())
        assertTrue(KlipyProvider.parse("""{"result": true, "data": {"data": []}}""").isEmpty())
    }
}
