package com.yeamin.lipi.emoji

/** The emoji picker's categories, in Unicode's order. */
enum class EmojiGroup(val unicodeName: String, val icon: String, val title: String) {
    SMILEYS("Smileys & Emotion", "😀", "Smileys"),
    PEOPLE("People & Body", "👋", "People"),
    ANIMALS("Animals & Nature", "🐻", "Animals & nature"),
    FOOD("Food & Drink", "🍔", "Food & drink"),
    TRAVEL("Travel & Places", "🚗", "Travel & places"),
    ACTIVITIES("Activities", "⚽", "Activities"),
    OBJECTS("Objects", "💡", "Objects"),
    SYMBOLS("Symbols", "❤️", "Symbols"),
    FLAGS("Flags", "🏳️", "Flags");

    companion object {
        fun fromUnicodeName(name: String): EmojiGroup? = entries.firstOrNull { it.unicodeName == name }
    }
}

/**
 * One emoji. [tones] holds the five skin-tone versions (light → dark) when the emoji has them.
 */
data class Emoji(
    val value: String,
    val group: EmojiGroup,
    val name: String,
    val tones: List<String>,
) {
    val hasTones: Boolean get() = tones.size == 5

    /** This emoji in skin tone [tone] (1–5), or the default yellow for 0 / no tones. */
    fun withTone(tone: Int): String = if (hasTones && tone in 1..5) tones[tone - 1] else value
}

/**
 * The full emoji set (Unicode Emoji 17.0) with English and Bangla names and keywords
 * (Unicode CLDR 49). Pure data; deciding which emoji the device can draw is up to the caller.
 */
class EmojiData private constructor(
    val all: List<Emoji>,
    private val byValue: Map<String, Emoji>,
    /** Lower-cased keyword or name → emoji, for both languages. */
    private val keywordIndex: Map<String, List<Emoji>>,
    private val banglaNames: Map<String, String>,
) {
    private val byGroup: Map<EmojiGroup, List<Emoji>> = all.groupBy { it.group }

    fun group(group: EmojiGroup): List<Emoji> = byGroup[group].orEmpty()

    /** The base emoji for [value], also when [value] is a skin-tone version. */
    fun find(value: String): Emoji? = byValue[value] ?: byValue[stripTones(value)]

    /** The Bangla name of an emoji, if CLDR has one. */
    fun banglaName(emoji: Emoji): String? = banglaNames[emoji.value]

    /**
     * Emoji whose keyword is exactly [word] (case-insensitive, English or Bangla), for the
     * suggestion strip: "lol" → 😂, "হাসি" → 😄 …
     */
    fun suggestionsFor(word: String, limit: Int = 3): List<Emoji> {
        if (word.length < 2) return emptyList()
        return keywordIndex[word.lowercase()].orEmpty().take(limit)
    }

    /**
     * Emoji matching [query] in either language: keywords or names starting with it, best
     * matches (whole-word, then prefix) first.
     */
    fun search(query: String, limit: Int = 120): List<Emoji> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val exact = keywordIndex[q].orEmpty()
        val prefix = keywordIndex.entries.asSequence()
            .filter { (k, _) -> k != q && (k.startsWith(q) || k.split(' ').any { it.startsWith(q) }) }
            .flatMap { it.value }
        return (exact.asSequence() + prefix).distinct().take(limit).toList()
    }

    companion object {
        private val TONE_RANGE = 0x1F3FB..0x1F3FF

        private fun stripTones(s: String): String = buildString {
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                if (cp !in TONE_RANGE) appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }

        /** Loads the bundled data (well under a second): call off the main thread. */
        fun load(): EmojiData = parse(
            readLines("/emoji/emoji.tsv"),
            readLines("/emoji/keywords-en.tsv"),
            readLines("/emoji/keywords-bn.tsv"),
        )

        fun parse(emojiLines: List<String>, englishLines: List<String>, banglaLines: List<String>): EmojiData {
            val all = emojiLines.mapNotNull { line ->
                val parts = line.split('\t')
                val group = EmojiGroup.fromUnicodeName(parts.getOrElse(1) { "" }) ?: return@mapNotNull null
                val tones = parts.getOrElse(3) { "" }.split('|').filter { it.isNotEmpty() }
                Emoji(parts[0], group, parts.getOrElse(2) { "" }, tones)
            }
            val byValue = HashMap<String, Emoji>(all.size * 2)
            all.forEach { e ->
                byValue[e.value] = e
                byValue[e.value.replace("️", "")] = e
            }
            val index = HashMap<String, MutableList<Emoji>>()
            fun addKeyword(k: String, e: Emoji) {
                val key = k.trim().lowercase()
                if (key.isEmpty()) return
                val list = index.getOrPut(key) { ArrayList(2) }
                if (e !in list) list += e
            }
            all.forEach { e -> addKeyword(e.name, e) }
            val banglaNames = HashMap<String, String>()
            for ((lines, isBangla) in listOf(englishLines to false, banglaLines to true)) {
                lines.forEach { line ->
                    val parts = line.split('\t')
                    val e = byValue[parts[0]] ?: return@forEach
                    val name = parts.getOrElse(1) { "" }
                    if (name.isNotEmpty()) {
                        addKeyword(name, e)
                        if (isBangla) banglaNames[e.value] = name
                    }
                    parts.getOrElse(2) { "" }.split('|').forEach { addKeyword(it, e) }
                }
            }
            return EmojiData(all, byValue, index, banglaNames)
        }

        private fun readLines(path: String): List<String> {
            val stream = EmojiData::class.java.getResourceAsStream(path) ?: error("Missing resource $path")
            return stream.bufferedReader(Charsets.UTF_8).use { r -> r.readLines().filter { it.isNotEmpty() } }
        }
    }
}

/** Text emoticons (kaomoji), offered next to emoji. */
object Kaomoji {
    val groups: List<Pair<String, List<String>>> = listOf(
        "Happy" to listOf("(^_^)", "(＾▽＾)", "(*^▽^*)", "(✿◠‿◠)", "ヽ(・∀・)ﾉ", "(≧▽≦)", "(o^▽^o)", "٩(◕‿◕)۶", "(◕‿◕)", "＼(^o^)／", "(•‿•)", "^_^"),
        "Love" to listOf("(♡˙︶˙♡)", "(´｡• ᵕ •｡`) ♡", "(｡♥‿♥｡)", "(づ￣ ³￣)づ", "( ˘ ³˘)♥", "♡(◡‿◡)", "(灬º‿º灬)♡", "<3"),
        "Sad" to listOf("(╥﹏╥)", "(T_T)", "(;_;)", "(｡•́︿•̀｡)", "(ಥ﹏ಥ)", "(._.)", "(ᗒᗣᗕ)՞", ":'("),
        "Angry" to listOf("(╬ಠ益ಠ)", "(ノಠ益ಠ)ノ彡┻━┻", "(¬_¬)", "(눈_눈)", "ヽ(`Д´)ﾉ", "(＃`Д´)", ">:("),
        "Surprised" to listOf("(°o°)", "(⊙_⊙)", "(O_O)", "Σ(°△°|||)", "(ﾟДﾟ;)", "w(°ｏ°)w", ":O"),
        "Other" to listOf("¯\\_(ツ)_/¯", "( ͡° ͜ʖ ͡°)", "(•_•)", "( •_•)>⌐■-■", "(⌐■_■)", "┬─┬ノ( º _ ºノ)", "(ง •̀_•́)ง", "(￣ー￣)ｂ", "(^_−)☆", ";)", ":P", "XD"),
    )
}
