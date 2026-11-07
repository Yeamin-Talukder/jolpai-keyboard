package com.yeamin.jolpai.voice

/**
 * Spoken punctuation: "comma", "full stop", "new line" … (English) and "কমা", "দাঁড়ি",
 * "প্রশ্নবোধক চিহ্ন", "নতুন লাইন" … (Bangla) become the punctuation itself.
 *
 * Applied to every result, because recognisers don't always do this themselves (and for
 * Bangla usually don't).
 */
object VoiceCommands {

    private val english = listOf(
        "new paragraph" to "\n\n",
        "new line" to "\n",
        "full stop" to ".",
        "period" to ".",
        "comma" to ",",
        "question mark" to "?",
        "exclamation mark" to "!",
        "exclamation point" to "!",
        "colon" to ":",
        "semicolon" to ";",
    )

    private val bangla = listOf(
        "নতুন অনুচ্ছেদ" to "\n\n",
        "নতুন লাইন" to "\n",
        "পূর্ণচ্ছেদ" to "।",
        "দাঁড়ি" to "।",
        "দাড়ি" to "।",
        "কমা" to ",",
        "প্রশ্নবোধক চিহ্ন" to "?",
        "প্রশ্নচিহ্ন" to "?",
        "বিস্ময়সূচক চিহ্ন" to "!",
        "বিস্ময় চিহ্ন" to "!",
        "কোলন" to ":",
    )

    private val commands: List<Pair<Regex, String>> = (english + bangla).map { (spoken, mark) ->
        Regex("(^|\\s)" + Regex.escape(precompose(spoken)) + "(?=$|\\s|[.,!?।])", RegexOption.IGNORE_CASE) to mark
    }

    fun apply(text: String): String {
        var out = precompose(text)
        for ((pattern, mark) in commands) {
            out = pattern.replace(out) { m -> m.groupValues[1] + mark }
        }
        // Tidy spaces: none before punctuation, none around line breaks.
        out = out.replace(Regex("[ \\t]+([.,!?;:।])"), "$1")
        out = out.replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
        return out.trim(' ')
    }

    /** ড় ঢ় য় can be written as one character or as letter + nukta; compare them as one form. */
    private fun precompose(s: String): String = s
        .replace("ড়", "ড়")
        .replace("ঢ়", "ঢ়")
        .replace("য়", "য়")
}
