package com.yeamin.jolpai.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandsTest {
    @Test fun english() {
        assertEquals("hello, how are you?", VoiceCommands.apply("hello comma how are you question mark"))
        assertEquals("done.\nnext", VoiceCommands.apply("done full stop new line next"))
        assertEquals("wow!", VoiceCommands.apply("wow exclamation mark"))
    }

    @Test fun bangla() {
        assertEquals("আমি ভালো আছি।", VoiceCommands.apply("আমি ভালো আছি দাঁড়ি"))
        assertEquals("তুমি কেমন আছো?", VoiceCommands.apply("তুমি কেমন আছো প্রশ্নবোধক চিহ্ন"))
        assertEquals("হ্যাঁ, ঠিক আছে", VoiceCommands.apply("হ্যাঁ কমা ঠিক আছে"))
    }

    @Test fun `words that only contain a command are kept`() =
        assertEquals("commander periodic", VoiceCommands.apply("commander periodic"))

    @Test fun `plain text is unchanged`() = assertEquals("just text", VoiceCommands.apply("just text"))
}
