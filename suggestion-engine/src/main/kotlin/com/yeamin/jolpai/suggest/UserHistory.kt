package com.yeamin.jolpai.suggest

import kotlin.math.pow

enum class Language(val storageKey: String) {
    ENGLISH("en"),
    BANGLA("bn");

    companion object {
        fun fromStorageKey(key: String): Language? = entries.firstOrNull { it.storageKey == key }
    }
}

/**
 * A learned word. [count] is how many times it was typed (it decides whether a word is the
 * user's own); [weight] is the same count fading over time (see [UserHistory.HALF_LIFE_MS]),
 * which ranks suggestions. A negative weight means "not stored yet": use [count].
 */
data class LearnedWord(val language: Language, val word: String, val count: Int, val lastUsed: Long, val weight: Float = -1f)

/** [word] typed after [previous]. */
data class LearnedBigram(
    val language: Language,
    val previous: String,
    val word: String,
    val count: Int,
    val lastUsed: Long = 0,
    val weight: Float = -1f,
)

/** [word] typed after [previous2] [previous] (a three-word sequence). */
data class LearnedTrigram(
    val language: Language,
    val previous2: String,
    val previous: String,
    val word: String,
    val count: Int,
    val lastUsed: Long,
    val weight: Float,
)

data class PersonalWord(val language: Language, val word: String)

/** Everything learned, as loaded from storage at start-up. */
data class HistorySnapshot(
    val words: List<LearnedWord> = emptyList(),
    val bigrams: List<LearnedBigram> = emptyList(),
    val trigrams: List<LearnedTrigram> = emptyList(),
    /** Avro: Roman word → the Bangla candidate the user picked. */
    val selections: Map<String, String> = emptyMap(),
    val personal: List<PersonalWord> = emptyList(),
)

/**
 * Where [UserHistory] saves changes. Implemented with Room in `core-data`; calls must
 * return immediately (the implementation writes in the background).
 */
interface HistoryStore {
    fun saveWord(word: LearnedWord)
    fun saveBigram(bigram: LearnedBigram)
    fun saveTrigram(trigram: LearnedTrigram) {}
    fun saveSelection(roman: String, bangla: String)
    fun savePersonal(word: PersonalWord, present: Boolean)
    fun deleteLearned(language: Language, word: String)

    /** Drops one learned pair (when the table is pruned). */
    fun deleteBigram(language: Language, previous: String, word: String) {}

    /** Drops one learned three-word sequence (when the table is pruned). */
    fun deleteTrigram(language: Language, previous2: String, previous: String, word: String) {}
    fun clearLearned()
}

/**
 * What the keyboard has learned from the user, kept in memory for instant lookups while
 * typing and persisted through [HistoryStore].
 *
 * - Learned words, word pairs (bigrams) and three-word sequences (trigrams), for completions
 *   and next-word prediction. After "আমি তোমাকে" the words the user typed after exactly those
 *   two words come first, then those typed after "তোমাকে" alone.
 * - Fading: every entry has a weight that halves every [HALF_LIFE_MS] it goes unused, so the
 *   suggestions follow how the user writes now, not years ago. Use counts don't fade (they
 *   decide what is the user's own word, which is never autocorrected).
 * - Avro picks: which Bangla candidate the user chose for a Roman word.
 * - The personal dictionary: words the user added explicitly. These are never autocorrected.
 *
 * Callers must not record anything typed in a sensitive field. Lookups are case-insensitive;
 * the most recent spelling is what gets suggested. Thread-safe.
 */
class UserHistory(
    private val store: HistoryStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(var form: String, var count: Int, var lastUsed: Long, var weight: Float)

    private val words = HashMap<Language, HashMap<String, Entry>>()
    private val bigrams = HashMap<Language, HashMap<String, HashMap<String, Entry>>>()

    /** language → "previous2 previous" (lower case) → next word → entry. */
    private val trigrams = HashMap<Language, HashMap<String, HashMap<String, Entry>>>()
    private val selections = HashMap<String, String>()
    private val personal = HashMap<Language, HashMap<String, String>>()

    @Synchronized
    fun load(snapshot: HistorySnapshot) {
        words.clear(); bigrams.clear(); trigrams.clear(); selections.clear(); personal.clear()
        val now = clock()
        snapshot.words.forEach { w ->
            words.getOrPut(w.language) { HashMap() }[key(w.word)] = Entry(w.word, w.count, w.lastUsed, w.weight.orCount(w.count))
        }
        snapshot.bigrams.forEach { b ->
            // Pairs saved before fading existed have no time: count them as used now.
            bigrams.getOrPut(b.language) { HashMap() }.getOrPut(key(b.previous)) { HashMap() }[key(b.word)] =
                Entry(b.word, b.count, if (b.lastUsed > 0) b.lastUsed else now, b.weight.orCount(b.count))
        }
        snapshot.trigrams.forEach { t ->
            trigrams.getOrPut(t.language) { HashMap() }.getOrPut(pairKey(t.previous2, t.previous)) { HashMap() }[key(t.word)] =
                Entry(t.word, t.count, if (t.lastUsed > 0) t.lastUsed else now, t.weight.orCount(t.count))
        }
        selections.putAll(snapshot.selections)
        snapshot.personal.forEach { p -> personal.getOrPut(p.language) { HashMap() }[key(p.word)] = p.word }
    }

    @Synchronized
    fun count(language: Language, word: String): Int = words[language]?.get(key(word))?.count ?: 0

    @Synchronized
    fun isPersonal(language: Language, word: String): Boolean = personal[language]?.containsKey(key(word)) == true

    /** True for personal-dictionary words and words the user has typed at least [minCount] times. */
    @Synchronized
    fun isKnown(language: Language, word: String, minCount: Int = 2): Boolean =
        isPersonal(language, word) || count(language, word) >= minCount

    /** Learned and personal words starting with [prefix] (case-insensitive), most used recently first. */
    @Synchronized
    fun wordsWithPrefix(language: Language, prefix: String, limit: Int): List<String> {
        val p = key(prefix)
        val now = clock()
        val top = TopN<String>(limit)
        personal[language]?.forEach { (k, form) -> if (k.startsWith(p) && k != p) top.offer(form, Float.MAX_VALUE / 2) }
        words[language]?.forEach { (k, e) -> if (k.startsWith(p) && k != p) top.offer(e.form, faded(e, now)) }
        return top.result().distinct()
    }

    /** How often the user has typed [word] right after [previous] (0 if never). */
    @Synchronized
    fun bigramCount(language: Language, previous: String, word: String): Int =
        bigrams[language]?.get(key(previous))?.get(key(word))?.count ?: 0

    /** How strongly [word] follows [previous] in the user's recent typing (faded; 0 if never). */
    @Synchronized
    fun pairWeight(language: Language, previous: String, word: String): Float =
        bigrams[language]?.get(key(previous))?.get(key(word))?.let { faded(it, clock()) } ?: 0f

    /** Words the user has typed after [previous], strongest (frequent and recent) first. */
    @Synchronized
    fun nextWords(language: Language, previous: String, limit: Int): List<String> =
        nextWordScores(language, null, previous, limit).map { it.first }

    /**
     * Next words from the user's own typing with a score: three-word sequences after
     * [previous2] [previous] count [TRIGRAM_BOOST] times as much as pairs after [previous].
     * Best first; empty when nothing was learned after [previous].
     */
    @Synchronized
    fun nextWordScores(language: Language, previous2: String?, previous: String?, limit: Int): List<Pair<String, Float>> {
        if (previous.isNullOrBlank()) return emptyList()
        val now = clock()
        val scores = HashMap<String, Float>()
        val forms = HashMap<String, String>()
        bigrams[language]?.get(key(previous))?.forEach { (k, e) ->
            scores[k] = faded(e, now)
            forms[k] = e.form
        }
        if (!previous2.isNullOrBlank()) {
            trigrams[language]?.get(pairKey(previous2, previous))?.forEach { (k, e) ->
                scores[k] = (scores[k] ?: 0f) + TRIGRAM_BOOST * faded(e, now)
                forms[k] = e.form
            }
        }
        val top = TopN<String>(limit)
        scores.forEach { (k, s) -> if (s >= MIN_WEIGHT) top.offer(k, s) }
        return top.result().map { k -> forms.getValue(k) to scores.getValue(k) }
    }

    @Synchronized
    fun selection(roman: String): String? = selections[roman]

    /** Roman spellings the user picked Avro words for that start with [prefix] (exact case). */
    @Synchronized
    fun selectionsWithPrefix(prefix: String, limit: Int): List<String> =
        selections.keys.asSequence().filter { it.length > prefix.length && it.startsWith(prefix) }.take(limit).toList()

    /**
     * Records that [word] was typed, after [previous] if given (null at a sentence start), and
     * after [previous2] [previous] when the word before that is known too.
     */
    @Synchronized
    fun recordWord(language: Language, word: String, previous: String?, previous2: String? = null) {
        if (word.isBlank()) return
        val now = clock()
        val map = words.getOrPut(language) { HashMap() }
        val e = map.getOrPut(key(word)) { Entry(word, 0, now, 0f) }
        bump(e, word, now)
        store?.saveWord(LearnedWord(language, word, e.count, now, e.weight))
        if (map.size > MAX_WORDS) prune(language, map)

        if (previous.isNullOrBlank()) return
        val followers = bigrams.getOrPut(language) { HashMap() }.getOrPut(key(previous)) { HashMap() }
        val b = followers.getOrPut(key(word)) { Entry(word, 0, now, 0f) }
        bump(b, word, now)
        store?.saveBigram(LearnedBigram(language, key(previous), word, b.count, now, b.weight))
        if (bigrams.getValue(language).sumSize() > MAX_BIGRAMS) prunePairs(language)

        if (previous2.isNullOrBlank()) return
        val context = pairKey(previous2, previous)
        val after = trigrams.getOrPut(language) { HashMap() }.getOrPut(context) { HashMap() }
        val t = after.getOrPut(key(word)) { Entry(word, 0, now, 0f) }
        bump(t, word, now)
        store?.saveTrigram(LearnedTrigram(language, key(previous2), key(previous), word, t.count, now, t.weight))
        if (trigrams.getValue(language).sumSize() > MAX_TRIGRAMS) pruneTrigrams(language)
    }

    @Synchronized
    fun recordSelection(roman: String, bangla: String) {
        if (roman.isEmpty() || selections[roman] == bangla) return
        selections[roman] = bangla
        store?.saveSelection(roman, bangla)
    }

    @Synchronized
    fun addPersonal(language: Language, word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        personal.getOrPut(language) { HashMap() }[key(w)] = w
        store?.savePersonal(PersonalWord(language, w), present = true)
    }

    @Synchronized
    fun removePersonal(language: Language, word: String) {
        personal[language]?.remove(key(word))
        store?.savePersonal(PersonalWord(language, word), present = false)
    }

    /** Forgets a learned word (e.g. the user removed it from suggestions). */
    @Synchronized
    fun forget(language: Language, word: String) {
        words[language]?.remove(key(word))
        bigrams[language]?.values?.forEach { it.remove(key(word)) }
        trigrams[language]?.values?.forEach { it.remove(key(word)) }
        store?.deleteLearned(language, word)
    }

    /** Forgets everything learned from typing. The personal dictionary is kept. */
    @Synchronized
    fun clearLearned() {
        words.clear(); bigrams.clear(); trigrams.clear(); selections.clear()
        store?.clearLearned()
    }

    // ---------------------------------------------------------------- fading

    /** One more use: fade the old weight to now, add one. */
    private fun bump(e: Entry, form: String, now: Long) {
        e.weight = faded(e, now) + 1f
        e.count++
        e.form = form
        e.lastUsed = now
    }

    /** [e]'s weight faded to [now]: halved for every [HALF_LIFE_MS] since it was last used. */
    private fun faded(e: Entry, now: Long): Float {
        val age = now - e.lastUsed
        if (age <= 0 || e.lastUsed <= 0) return e.weight
        return (e.weight * 0.5.pow(age.toDouble() / HALF_LIFE_MS)).toFloat()
    }

    private fun Float.orCount(count: Int): Float = if (this < 0f) count.toFloat() else this

    // ---------------------------------------------------------------- pruning

    /** Drops the least-used, oldest tenth of the words when the table grows too large. */
    private fun prune(language: Language, map: HashMap<String, Entry>) {
        val victims = map.entries
            .sortedWith(compareBy<Map.Entry<String, Entry>> { it.value.count }.thenBy { it.value.lastUsed })
            .take(map.size / 10)
            .map { it.key to it.value.form }
        for ((k, form) in victims) {
            map.remove(k)
            store?.deleteLearned(language, form)
        }
    }

    /** Drops the most faded tenth of the pairs. */
    private fun prunePairs(language: Language) {
        val table = bigrams[language] ?: return
        val now = clock()
        val all = table.flatMap { (prev, next) -> next.map { (k, e) -> Triple(prev, k, faded(e, now)) } }
        all.sortedBy { it.third }.take(all.size / 10).forEach { (prev, k, _) ->
            table[prev]?.remove(k)
            if (table[prev]?.isEmpty() == true) table.remove(prev)
            store?.deleteBigram(language, prev, k)
        }
    }

    /** Drops the most faded tenth of the three-word sequences. */
    private fun pruneTrigrams(language: Language) {
        val table = trigrams[language] ?: return
        val now = clock()
        val all = table.flatMap { (ctx, next) -> next.map { (k, e) -> Triple(ctx, k, faded(e, now)) } }
        all.sortedBy { it.third }.take(all.size / 10).forEach { (ctx, k, _) ->
            table[ctx]?.remove(k)
            if (table[ctx]?.isEmpty() == true) table.remove(ctx)
            val (p2, p1) = ctx.split(' ', limit = 2)
            store?.deleteTrigram(language, p2, p1, k)
        }
    }

    private fun HashMap<String, HashMap<String, Entry>>.sumSize(): Int = values.sumOf { it.size }

    private fun key(word: String) = word.lowercase()
    private fun pairKey(previous2: String, previous: String) = key(previous2) + " " + key(previous)

    companion object {
        const val MAX_WORDS = 20_000
        const val MAX_BIGRAMS = 60_000
        const val MAX_TRIGRAMS = 40_000

        /** Learned weights halve after this long unused (30 days). */
        const val HALF_LIFE_MS = 30L * 24 * 60 * 60 * 1000

        /** A three-word match is stronger evidence than a pair. */
        const val TRIGRAM_BOOST = 2f

        /** Below this a learned next word has faded out (about one use, five months ago). */
        const val MIN_WEIGHT = 0.03f
    }
}
