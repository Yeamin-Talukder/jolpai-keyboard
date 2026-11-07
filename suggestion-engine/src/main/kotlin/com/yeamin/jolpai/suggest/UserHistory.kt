package com.yeamin.jolpai.suggest

enum class Language(val storageKey: String) {
    ENGLISH("en"),
    BANGLA("bn");

    companion object {
        fun fromStorageKey(key: String): Language? = entries.firstOrNull { it.storageKey == key }
    }
}

data class LearnedWord(val language: Language, val word: String, val count: Int, val lastUsed: Long)
data class LearnedBigram(val language: Language, val previous: String, val word: String, val count: Int)
data class PersonalWord(val language: Language, val word: String)

/** Everything learned, as loaded from storage at start-up. */
data class HistorySnapshot(
    val words: List<LearnedWord> = emptyList(),
    val bigrams: List<LearnedBigram> = emptyList(),
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
    fun saveSelection(roman: String, bangla: String)
    fun savePersonal(word: PersonalWord, present: Boolean)
    fun deleteLearned(language: Language, word: String)
    fun clearLearned()
}

/**
 * What the keyboard has learned from the user, kept in memory for instant lookups while
 * typing and persisted through [HistoryStore].
 *
 * - Learned words and word pairs (for next-word prediction), with use counts.
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
    private class Entry(var form: String, var count: Int, var lastUsed: Long)

    private val words = HashMap<Language, HashMap<String, Entry>>()
    private val bigrams = HashMap<Language, HashMap<String, HashMap<String, Entry>>>()
    private val selections = HashMap<String, String>()
    private val personal = HashMap<Language, HashMap<String, String>>()

    @Synchronized
    fun load(snapshot: HistorySnapshot) {
        words.clear(); bigrams.clear(); selections.clear(); personal.clear()
        snapshot.words.forEach { w ->
            words.getOrPut(w.language) { HashMap() }[key(w.word)] = Entry(w.word, w.count, w.lastUsed)
        }
        snapshot.bigrams.forEach { b ->
            bigrams.getOrPut(b.language) { HashMap() }.getOrPut(key(b.previous)) { HashMap() }[key(b.word)] =
                Entry(b.word, b.count, 0)
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

    /** Learned and personal words starting with [prefix] (case-insensitive), most used first. */
    @Synchronized
    fun wordsWithPrefix(language: Language, prefix: String, limit: Int): List<String> {
        val p = key(prefix)
        val top = TopN<String>(limit)
        personal[language]?.forEach { (k, form) -> if (k.startsWith(p) && k != p) top.offer(form, Float.MAX_VALUE / 2) }
        words[language]?.forEach { (k, e) -> if (k.startsWith(p) && k != p) top.offer(e.form, e.count.toFloat()) }
        return top.result().distinct()
    }

    /** Words the user has typed after [previous], most frequent first. */
    @Synchronized
    fun nextWords(language: Language, previous: String, limit: Int): List<String> {
        val followers = bigrams[language]?.get(key(previous)) ?: return emptyList()
        val top = TopN<String>(limit)
        followers.values.forEach { top.offer(it.form, it.count.toFloat()) }
        return top.result()
    }

    @Synchronized
    fun selection(roman: String): String? = selections[roman]

    /** Roman spellings the user picked Avro words for that start with [prefix] (exact case). */
    @Synchronized
    fun selectionsWithPrefix(prefix: String, limit: Int): List<String> =
        selections.keys.asSequence().filter { it.length > prefix.length && it.startsWith(prefix) }.take(limit).toList()

    /** Records that [word] was typed, after [previous] if given (null at a sentence start). */
    @Synchronized
    fun recordWord(language: Language, word: String, previous: String?) {
        if (word.isBlank()) return
        val now = clock()
        val map = words.getOrPut(language) { HashMap() }
        val e = map.getOrPut(key(word)) { Entry(word, 0, now) }
        e.form = word
        e.count++
        e.lastUsed = now
        store?.saveWord(LearnedWord(language, word, e.count, now))
        if (map.size > MAX_WORDS) prune(language, map)

        if (previous != null && previous.isNotBlank()) {
            val followers = bigrams.getOrPut(language) { HashMap() }.getOrPut(key(previous)) { HashMap() }
            val b = followers.getOrPut(key(word)) { Entry(word, 0, 0) }
            b.form = word
            b.count++
            store?.saveBigram(LearnedBigram(language, key(previous), word, b.count))
        }
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
        store?.deleteLearned(language, word)
    }

    /** Forgets everything learned from typing. The personal dictionary is kept. */
    @Synchronized
    fun clearLearned() {
        words.clear(); bigrams.clear(); selections.clear()
        store?.clearLearned()
    }

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

    private fun key(word: String) = word.lowercase()

    private companion object {
        const val MAX_WORDS = 20_000
    }
}
