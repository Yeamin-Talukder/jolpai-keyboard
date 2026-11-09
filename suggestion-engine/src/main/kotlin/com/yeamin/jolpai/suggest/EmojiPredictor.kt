package com.yeamin.jolpai.suggest

import kotlin.math.sqrt

/**
 * Emoji that suit what is being written, from the meaning of its words rather than an exact
 * keyword: "so exhausted after work" → 😴, "we won the final" → 🏆.
 *
 * The next-word model has learned a vector for each word, and words used alike get similar
 * vectors. Each emoji is represented by the vectors of its English keywords (Unicode CLDR);
 * for each recent content word of the sentence, an emoji gets the closeness of its nearest
 * keyword (when close enough, [WORD_MIN]); these add up over the words, the latest counting
 * most, so "happy birthday" favours 🎂 over an emoji matching only one word. Only clear
 * matches ([MIN_SCORE]) are offered.
 *
 * No training data with emoji is needed, and nothing leaves the phone. Built once in the
 * background (a few thousand keyword vectors, about 2 MB); [predict] takes about a millisecond.
 */
class EmojiPredictor(
    private val model: NextWordModel,
    /** Emoji (best-known first) with their English keywords. */
    emoji: List<Pair<String, List<String>>>,
) {
    private val dim = model.dim
    private val keywordTokens: IntArray
    private val keywordVectors: FloatArray
    /** For each keyword index, the emoji (indices into [emojiValues]) it belongs to. */
    private val keywordEmoji: Array<IntArray>
    private val emojiValues: List<String> = emoji.map { it.first }

    /** Per emoji: 1, or more for the emoji people use most (so "love" picks ❤️, not 💌). */
    private val boost: FloatArray = FloatArray(emojiValues.size) { i -> if (emojiValues[i].replace("FE0F", "") in POPULAR) POPULAR_BOOST else 1f }

    init {
        // Keywords shared by a great many emoji (face, hand, person …) say nothing about any one of them.
        val usage = HashMap<String, Int>()
        // CLDR keywords may be phrases ("in love"): the model reads single words.
        val words = emoji.map { (_, kws) -> kws.flatMap { it.split(' ', '-') }.filter { it.isNotEmpty() }.distinct() }
        words.forEach { kws -> kws.forEach { usage[it] = (usage[it] ?: 0) + 1 } }
        val byKeyword = LinkedHashMap<Int, MutableList<Int>>()
        words.forEachIndexed { e, kws ->
            kws.forEach { k ->
                if ((usage[k] ?: 0) > MAX_KEYWORD_USES) return@forEach
                val t = model.token(k)
                if (t < NextWordModel.FIRST_WORD || k in STOP_WORDS) return@forEach
                val list = byKeyword.getOrPut(t) { ArrayList(2) }
                if (e !in list) list += e
            }
        }
        keywordTokens = byKeyword.keys.toIntArray()
        keywordEmoji = byKeyword.values.map { it.toIntArray() }.toTypedArray()
        keywordVectors = FloatArray(keywordTokens.size * dim)
        val v = FloatArray(dim)
        keywordTokens.forEachIndexed { i, t ->
            model.vector(t, v)
            normalise(v)
            System.arraycopy(v, 0, keywordVectors, i * dim, dim)
        }
    }

    /**
     * Up to [limit] emoji for the sentence [words] (oldest first; the last may be the word just
     * typed), best first. Empty when nothing fits clearly.
     */
    fun predict(words: List<String>, limit: Int = 2): List<String> {
        val recent = words.takeLast(RECENT_WORDS).map { it.lowercase() }
        if (recent.isEmpty()) return emptyList()
        val total = FloatArray(emojiValues.size)
        val perWord = FloatArray(emojiValues.size)
        val v = FloatArray(dim)
        // Only words with a topic count; the newest of them counts fully, each older one less.
        val content = recent.filter { it !in STOP_WORDS && model.token(it) >= NextWordModel.FIRST_WORD }.distinct()
        content.forEachIndexed { i, w ->
            val t = model.token(w)
            val age = content.size - 1 - i
            val weight = Math.pow(RECENCY.toDouble(), age.toDouble()).toFloat()
            model.vector(t, v)
            normalise(v)
            perWord.fill(0f)
            for (k in keywordTokens.indices) {
                var dot = 0f
                val base = k * dim
                for (d in 0 until dim) dot += v[d] * keywordVectors[base + d]
                if (dot < WORD_MIN) continue
                for (e in keywordEmoji[k]) if (dot > perWord[e]) perWord[e] = dot
            }
            for (e in perWord.indices) total[e] += perWord[e] * weight
        }
        // The bar is on the match itself; popularity only orders the emoji that pass it.
        return total.indices.filter { total[it] >= MIN_SCORE }
            .sortedByDescending { total[it] * boost[it] }
            .take(limit)
            .map { emojiValues[it] }
    }

    private fun normalise(v: FloatArray) {
        var n = 0f
        for (x in v) n += x * x
        val inv = if (n > 0f) 1f / sqrt(n) else 0f
        for (d in v.indices) v[d] *= inv
    }

    companion object {
        /** How close (cosine of word vectors, 0–1) a word must be to a keyword to count at all. */
        private const val WORD_MIN = 0.6f

        /** The summed score an emoji needs to be offered (one close match, or two looser ones). */
        internal const val MIN_SCORE = 0.85f

        /** Words of the sentence looked at, and how much less each older one counts. */
        private const val RECENT_WORDS = 6
        private const val RECENCY = 0.9f

        /** Words that carry no topic (they are also left out of emoji keywords). */
        private val STOP_WORDS = (
            "a an the and or but if so as of to in on at by for from with about into over after before " +
                "i me my mine you your yours he him his she her hers it its we us our they them their " +
                "this that these those there here what which who whom whose when where why how " +
                "is am are was were be been being do does did done have has had having will would " +
                "shall should can could may might must not no yes just very really too also only " +
                "some any all each every much many more most such than then now still even ever " +
                "get got go going gone make made let lets one two up down out off again back well " +
                "time way thing things something anything someone see saw seen look say said tell told know knew " +
                "think thought want need come came take took give gave put keep went new old first last next"
            ).split(' ').toSet()

        /** A keyword used by more emoji than this is too general to match on. */
        private const val MAX_KEYWORD_USES = 80

        private const val POPULAR_BOOST = 1.3f

        /** The most used emoji (from public emoji-use rankings), preferred when several fit. */
        private val POPULAR = ("😂❤🤣👍😭🙏😘🥰😍😊🎉😁💕🥺😅🔥☺🤦♥🤷🙄😆🤗😉🎂🤔👏🙂😳🥳😎👌💜😔💪✨💖👀😋😏😢👉💗😩💯🌹💞🎈💙😃😡💐😜🙈🤞😄🤤🙌🤪❣😀💋💀👇💔😌💓🤩🙃😬😱😴🤭😐🌞😒😇🌸😈🎶✌🎊🥵😞💚☀🖤💰😚👑🎁💥🙋☹😑🥴👈💩✅☕🍕🎵🌙⚽🏆📞🚗🐶🐱🌧☔🍔🍰🎓💼🏖🎄🌍").let { s ->
            val out = HashSet<String>()
            var i = 0
            while (i < s.length) {
                val n = Character.charCount(s.codePointAt(i))
                out += s.substring(i, i + n)
                i += n
            }
            out
        }
    }
}
