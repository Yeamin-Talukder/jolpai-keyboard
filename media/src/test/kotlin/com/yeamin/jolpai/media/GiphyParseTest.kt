package com.yeamin.jolpai.media

import org.junit.Assert.assertEquals
import org.junit.Test

class GiphyParseTest {
    @Test fun `parses the fields Lipi uses and skips incomplete items`() {
        val json = """
            {"data":[
              {"id":"abc","title":"Happy dance","images":{
                 "fixed_width_small_still":{"url":"https://media.giphy.com/abc/still.gif"},
                 "fixed_width":{"url":"https://media.giphy.com/abc/200w.gif","width":"200"}}},
              {"id":"broken","images":{}}
            ],"meta":{"status":200}}
        """.trimIndent()
        val gifs = GiphyProvider.parse(json)
        assertEquals(1, gifs.size)
        assertEquals(Gif("abc", "Happy dance", "https://media.giphy.com/abc/still.gif", "https://media.giphy.com/abc/200w.gif"), gifs[0])
    }

    @Test fun `empty response`() = assertEquals(emptyList<Gif>(), GiphyProvider.parse("""{"meta":{"status":401}}"""))
}
