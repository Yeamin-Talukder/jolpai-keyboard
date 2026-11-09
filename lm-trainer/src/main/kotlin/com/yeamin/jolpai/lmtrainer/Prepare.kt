package com.yeamin.jolpai.lmtrainer

import java.io.BufferedWriter
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Bangla text → sentences of normalised words, then a vocabulary and a token file for training.
 *
 * Words are written the way the keyboard writes them: ড় ঢ় য় as single characters, ো ৌ composed,
 * no zero-width non-joiner. A word is Bangla letters and signs (and the zero-width joiner of র‍্য);
 * anything else that looks like a word (Latin letters, digits) becomes [UNK].
 */
object BanglaText {
    const val UNK = "<unk>"

    private val TOKEN = Regex("[\\u0980-\\u09E5\\u09F0-\\u09FF\\u200D]+|[A-Za-z0-9\\u09E6-\\u09EF]+")
    private val SENTENCE_END = Regex("[\\u0964\\u0965?!.\\n]+")

    fun normalize(s: String): String = s
        .replace("\u09AF\u09BC", "\u09DF").replace("\u09A1\u09BC", "\u09DC").replace("\u09A2\u09BC", "\u09DD")
        .replace("\u09C7\u09BE", "\u09CB").replace("\u09C7\u09D7", "\u09CC")
        .replace("\u200C", "")

    private fun isBanglaLetter(c: Char) = c in '\u0985'..'\u09B9' || c in '\u09DC'..'\u09DF' || c == '\u09CE'

    /** The sentences of [text], each as its words ([UNK] for non-Bangla words). */
    fun sentences(text: String): Sequence<List<String>> =
        SENTENCE_END.split(normalize(text)).asSequence().map { s ->
            TOKEN.findAll(s).map { m ->
                val w = m.value.trim('\u200D')
                if (w.isNotEmpty() && isBanglaLetter(w[0])) w else UNK
            }.toList()
        }

    /**
     * Sentences worth training on: 2–40 words, at least two Bangla, at most a quarter unknown,
     * and well-formed. Much of the OpenSubtitles Bangla text lost its vowel signs and spaces in
     * an old font conversion ("শষহয়ছঅনকবছরহল"); real Bangla has a vowel sign for roughly every
     * two or three letters and words of a few letters, so sentences far from that are dropped.
     */
    fun usable(words: List<String>): Boolean {
        if (words.size !in 2..40) return false
        val unk = words.count { it == UNK }
        if (words.size - unk < 2 || unk * 4 > words.size) return false
        var letters = 0
        var signs = 0
        var bangla = 0
        for (w in words) {
            if (w == UNK) continue
            bangla++
            for (c in w) {
                if (isBanglaLetter(c)) letters++ else if (c in '\u09BE'..'\u09CC') signs++
            }
        }
        return letters >= 4 && signs * 10 >= letters * 3 && letters <= bangla * 6
    }
}

/** Plain text from a MediaWiki XML dump (articles only, markup removed). */
object WikiText {
    private val TEMPLATE_OPEN = "{{"
    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val REF = Regex("<ref[^>/]*/>|<ref[^>]*>.*?</ref>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val TAG = Regex("<[^>]+>")
    private val LINK_PIPED = Regex("\\[\\[[^\\[\\]|]*\\|([^\\[\\]]*)]]")
    private val LINK = Regex("\\[\\[([^\\[\\]|:]*)]]")
    private val LINK_NAMESPACE = Regex("\\[\\[[^\\[\\]]*:[^\\[\\]]*]]")
    private val EXTERNAL = Regex("\\[(?:https?:)?//\\S+\\s*([^\\]]*)]")
    private val QUOTES = Regex("'{2,}")

    /** The wikitext of each article (namespace 0, not a redirect). */
    fun articles(lines: Sequence<String>): Sequence<String> = sequence {
        var ns = -1
        val text = StringBuilder()
        var inText = false
        for (line in lines) {
            if (!inText) {
                val t = line.trim()
                if (t.startsWith("<ns>")) ns = t.removePrefix("<ns>").removeSuffix("</ns>").toIntOrNull() ?: -1
                val at = line.indexOf("<text")
                if (at >= 0) {
                    val start = line.indexOf('>', at)
                    if (start < 0 || line.substring(at, start).endsWith("/")) continue
                    text.setLength(0)
                    val rest = line.substring(start + 1)
                    val end = rest.indexOf("</text>")
                    if (end >= 0) {
                        text.append(rest, 0, end)
                        if (ns == 0) yield(text.toString())
                    } else {
                        text.append(rest).append('\n')
                        inText = true
                    }
                }
            } else {
                val end = line.indexOf("</text>")
                if (end >= 0) {
                    text.append(line, 0, end)
                    inText = false
                    if (ns == 0) yield(text.toString())
                } else {
                    text.append(line).append('\n')
                }
            }
        }
    }

    fun clean(raw: String): String {
        var s = unescape(raw)
        if (s.trimStart().startsWith("#")) return "" // redirect
        s = COMMENT.replace(s, " ")
        s = REF.replace(s, " ")
        s = removeNested(s, "{{", "}}")
        s = removeNested(s, "{|", "|}")
        // Images and categories may hold links in their captions: remove those first, nested.
        repeat(3) { s = LINK_NAMESPACE.replace(s, " ") }
        s = LINK_PIPED.replace(s) { it.groupValues[1] }
        s = LINK.replace(s) { it.groupValues[1] }
        s = EXTERNAL.replace(s) { it.groupValues[1] }
        s = TAG.replace(s, " ")
        s = QUOTES.replace(s, "")
        return s.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it[0] !in "=*#:;|!{}[]" }
            .joinToString("\n")
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&nbsp;", " ").replace("&amp;", "&")

    private fun removeNested(s: String, open: String, close: String): String {
        if (!s.contains(open)) return s
        val out = StringBuilder(s.length)
        var depth = 0
        var i = 0
        while (i < s.length) {
            if (s.startsWith(open, i)) {
                depth++; i += open.length; continue
            }
            if (depth > 0 && s.startsWith(close, i)) {
                depth--; i += close.length; continue
            }
            if (depth == 0) out.append(s[i])
            i++
        }
        return out.toString()
    }
}

/** A set of 64-bit hashes (open addressing), for dropping repeated sentences. */
class LongSet(bits: Int) {
    private val table = LongArray(1 shl bits)
    private val mask = (1 shl bits) - 1
    var size = 0
        private set

    /** Adds [h]; false if it was already there (or the set is nearly full). */
    fun add(h0: Long): Boolean {
        val h = if (h0 == 0L) 1L else h0
        if (size * 4 > table.size * 3) return false
        var i = (h xor (h ushr 29)).toInt() and mask
        while (true) {
            val v = table[i]
            if (v == 0L) { table[i] = h; size++; return true }
            if (v == h) return false
            i = (i + 1) and mask
        }
    }
}

/**
 * prepare: reads the sources, writes `sentences-<source>.txt` (one normalised sentence per line,
 * repeats dropped, every 200th line to `valid-<source>.txt`), then `vocab.tsv` (the most common
 * [vocabSize] Bangla words with counts) and `train.bin` / `valid-*.bin` (token ids, 0 = unknown,
 * one sentence per record: length, then ids).
 */
class Prepare(
    private val out: File,
    private val vocabSize: Int,
    /** Everyday sources (all but Wikipedia) count this many times. */
    private val repeatEveryday: Int = 1,
    /** Wikipedia is cut off after this many words. */
    private val wikiWords: Long = Long.MAX_VALUE,
) {

    private fun hash(words: List<String>): Long {
        var h = -0x340d631b7bdddcdbL // FNV-1a 64
        for (w in words) {
            for (c in w) { h = (h xor c.code.toLong()) * 0x100000001b3L }
            h = (h xor 32L) * 0x100000001b3L
        }
        return h
    }

    fun subtitles(file: File) = write("subtitles", GZIPInputStream(file.inputStream(), 1 shl 16).bufferedReader().lineSequence())

    /** One sentence per line ([column]: the tab-separated column to take, e.g. Tatoeba\x27s third). */
    fun lines(file: File, source: String, column: Int = -1) =
        write(source, filter = false, texts = file.bufferedReader().lineSequence().map { if (column < 0) it else it.split('\t').getOrElse(column) { "" } })

    /** Words that only abusive comments use: comments containing any are left out. */
    private var blocked: Set<String> = emptySet()

    /**
     * Learns [blocked] from a labelled comment set: words seen at least [minCount] times in
     * comments labelled abusive ([abusive] labels) and never in the others.
     */
    fun learnBlocklist(file: File, textColumn: String, labelColumn: String, abusive: Set<String>, minCount: Int = 4, clean: List<File> = emptyList()) {
        val bad = HashMap<String, Int>()
        val good = HashSet<String>()
        Xlsx.rows(file).forEach { row ->
            val label = row[labelColumn]?.trim()?.lowercase() ?: return@forEach
            val words = BanglaText.sentences(row[textColumn] ?: "").flatten().filter { it != BanglaText.UNK }
            if (label in abusive) words.forEach { bad.merge(it, 1, Int::plus) } else good += words
        }
        // Words that clean, edited text also uses (read-aloud sentences, Tatoeba, the encyclopedia) are not slurs.
        clean.forEach { f -> f.forEachLine { line -> BanglaText.sentences(line).forEach { good += it } } }
        blocked = bad.filter { (w, n) -> n >= minCount && w !in good }.keys
        File(out, "blocklist.txt").writeText(blocked.sorted().joinToString("\n"))
        println("blocklist: ${blocked.size} words (from comments labelled ${abusive.joinToString()})")
    }

    /** Text columns of a spreadsheet; with [labelColumn], only rows whose label is in [keepLabels]. */
    fun xlsx(file: File, source: String, columns: List<String>, labelColumn: String? = null, keepLabels: Set<String> = emptySet()) {
        val texts = Xlsx.rows(file).drop(1).flatMap { row ->
            if (labelColumn != null && row[labelColumn]?.trim()?.lowercase() !in keepLabels) emptySequence()
            else columns.asSequence().mapNotNull { row[it] }
        }
        write(source, texts)
    }

    fun wikipedia(file: File) {
        val lines = file.inputStream().buffered(1 shl 16).bufferedReader().lineSequence()
        write("wikipedia", WikiText.articles(lines).map { WikiText.clean(it) })
    }

    private fun write(source: String, texts: Sequence<String>, filter: Boolean = true) {
        val seen = LongSet(26)
        var kept = 0L
        var words = 0L
        var n = 0L
        var dropped = 0L
        File(out, "sentences-$source.txt").bufferedWriter().use { train ->
            File(out, "valid-$source.txt").bufferedWriter().use { valid ->
                for (text in texts) {
                    val parts = BanglaText.sentences(text).toList()
                    // A comment with an abusive word is left out whole, not just that sentence.
                    if (filter && blocked.isNotEmpty() && parts.any { s -> s.any { it in blocked } }) { dropped++; continue }
                    for (s in parts) {
                        if (!BanglaText.usable(s) || !seen.add(hash(s))) continue
                        val w: BufferedWriter = if (++n % 200 == 0L) valid else train
                        w.write(s.joinToString(" "))
                        w.newLine()
                        kept++
                        words += s.size
                        if (kept % 1_000_000 == 0L) println("  $source: $kept sentences, $words words")
                    }
                }
            }
        }
        println("$source: $kept sentences, $words words" + if (dropped > 0) " ($dropped texts with blocked words left out)" else "")
    }

    /** Counts words over the training sentences and writes the vocabulary and the token files. */
    fun encode() {
        val counts = HashMap<String, Int>(1 shl 22)
        val trainFiles = out.listFiles { f -> f.name.startsWith("sentences-") }!!.sortedBy { it.name }
        // Everyday text is what people type: it counts [repeatEveryday] times, and the encyclopedia
        // is cut to [wikiWords] words (enough for spellings, not enough to set the style).
        fun isWiki(f: File) = f.name.contains("wikipedia")
        fun repeats(f: File) = if (isWiki(f)) 1 else repeatEveryday
        fun linesOf(f: File, each: (String) -> Unit) {
            var words = 0L
            f.bufferedReader().useLines { seq ->
                for (line in seq) {
                    if (isWiki(f) && words >= wikiWords) break
                    words += line.count { it == ' ' } + 1
                    each(line)
                }
            }
        }
        trainFiles.forEach { f -> val n = repeats(f); linesOf(f) { line -> line.split(' ').forEach { if (it != BanglaText.UNK) counts.merge(it, n, Int::plus) } } }
        val vocab = counts.entries.filter { it.value >= 3 }.sortedByDescending { it.value }.take(vocabSize)
        val total = counts.values.sumOf { it.toLong() }
        val covered = vocab.sumOf { it.value.toLong() }
        println("vocabulary: ${vocab.size} of ${counts.size} words, covering %.2f%% of word uses".format(100.0 * covered / total))
        File(out, "vocab.tsv").bufferedWriter().use { w -> vocab.forEach { w.write("${it.key}\t${it.value}\n") } }
        // Ids as in the model file: 0 unknown, 1 start, 2 end, words from 3.
        val id = HashMap<String, Int>(vocab.size * 2)
        vocab.forEachIndexed { i, e -> id[e.key] = i + 3 }
        fun encodeFiles(files: List<File>, target: File, repeatTrain: Boolean) {
            var sentences = 0
            var tokens = 0L
            DataOutputStream(target.outputStream().buffered(1 shl 16)).use { o ->
                files.forEach { f -> repeat(if (repeatTrain) repeats(f) else 1) {
                    linesOf(f) { line ->
                        val ws = line.split(' ')
                        o.writeShort(ws.size)
                        ws.forEach { o.writeShort((id[it] ?: 0).toShort().toInt()) }
                        sentences++
                        tokens += ws.size
                    }
                } }
            }
            println("${target.name}: $sentences sentences, $tokens words")
        }
        require(vocab.size + 3 <= 65535) { "ids are written as 16 bits" }
        encodeFiles(trainFiles, File(out, "train.bin"), repeatTrain = true)
        out.listFiles { f -> f.name.startsWith("valid-") && f.name.endsWith(".txt") }!!.forEach { f ->
            encodeFiles(listOf(f), File(out, f.name.removeSuffix(".txt") + ".bin"), repeatTrain = false)
        }
    }
}

/** Token files written by [Prepare.encode]: sentences of word ids. */
class Corpus(val tokens: ShortArray, val starts: IntArray) {
    val sentences: Int get() = starts.size - 1
    fun length(s: Int) = starts[s + 1] - starts[s]
    fun token(s: Int, i: Int): Int = tokens[starts[s] + i].toInt() and 0xFFFF

    companion object {
        fun read(file: File, limit: Int = Int.MAX_VALUE): Corpus {
            val bytes = file.readBytes()
            val buf = java.nio.ByteBuffer.wrap(bytes)
            val starts = ArrayList<Int>()
            var count = 0
            // First pass: sentence boundaries.
            var p = 0
            var tokens = 0
            while (p < bytes.size && count < limit) {
                val len = buf.getShort(p).toInt() and 0xFFFF
                starts += tokens
                tokens += len
                p += 2 + 2 * len
                count++
            }
            starts += tokens
            val out = ShortArray(tokens)
            p = 0
            var t = 0
            for (s in 0 until count) {
                val len = buf.getShort(p).toInt() and 0xFFFF
                p += 2
                for (i in 0 until len) { out[t++] = buf.getShort(p); p += 2 }
            }
            return Corpus(out, starts.toIntArray())
        }

        fun stream(file: File): InputStream = file.inputStream()
    }
}
