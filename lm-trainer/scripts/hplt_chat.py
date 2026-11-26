"""Chat-style Bangla sentences from HPLT 2.0 web text (CC0), for the next-word model.

There is no public corpus of real Bangla chat messages, so this pulls sentences from web
pages that read like people talking: short, first or second person, spoken (চলিত) verb
forms, questions and everyday words. Formal (সাধু) sentences, URLs, numbers-heavy lines and
sentences with blocked (abusive) words are dropped.

    python -E -P hplt_chat.py OUT.txt BLOCKLIST.txt SHARD.parquet [SHARD.parquet ...]

hplt_chat.sh runs it over all 174 ben_Beng files (download, filter, delete, 3 at a time).
Writes one sentence per line (appends), for `lm-trainer prepare DIR --lines chat=OUT.txt`,
which then normalises, removes repeats and holds out every 200th line.
Needs pyarrow (pip install --user pyarrow).
"""
import re
import sys

import pyarrow.parquet as pq

SPLIT = re.compile(r"[।॥?!\n]+|(?<=[ঀ-৿])\.\s")
WORD = re.compile(r"[ঀ-৿‌‍]+")
LATIN_OR_DIGIT = re.compile(r"[A-Za-z0-9০-৯]")
URLISH = re.compile(r"https?:|www\.|\.com|@")

# Informal second person and close-relationship pronouns: the strongest chat signal.
SECOND = {"তুমি", "তুই", "তোমার", "তোর", "তোমাকে", "তোকে", "তোমরা", "তোরা", "তোমাদের", "তোদের", "তোমায়"}
FIRST = {"আমি", "আমার", "আমাকে", "আমরা", "আমাদের", "আমায়"}
# Everyday conversation words.
CHATTY = {
    "আচ্ছা", "ঠিক", "হ্যাঁ", "হ্যা", "না", "কি", "কী", "কেন", "কেমন", "কোথায়", "কখন", "ভাই", "দোস্ত",
    "আপু", "ভাইয়া", "আম্মু", "আব্বু", "প্লিজ", "ওকে", "হুম", "আরে", "ধুর", "যাক", "তো", "রে", "নাকি",
    "এখন", "আজকে", "কালকে", "একটু", "অনেক", "সত্যি", "থ্যাংকস", "ধন্যবাদ", "সরি", "বাসায়", "খাইছো",
}
# Spoken first-person verb endings (করছি, যাচ্ছি, গেলাম, যাবো, করতাম).
SPOKEN_VERB = re.compile(r"(ছি|চ্ছি|লাম|ছিলাম|তাম|বো)$")
# সাধু (formal literary) forms: leave the sentence out.
SADHU = re.compile(r"(িয়াছ|িতেছ|িয়াছিল|িবার|িয়া$|িলেন$|িল$|িতে$|হইয়া|করিয়া|কহিল|তাহার|যাহা|তাহা|ইহা|উহা)")
# News and notice style words, including the reporting verbs around quoted speech in news.
FORMAL = {
    "উল্লেখ্য", "প্রতিবেদক", "সংবাদদাতা", "জানিয়েছেন", "বলেন", "বলেছেন", "জানান", "করেন", "অনুষ্ঠিত",
    "প্রকাশিত", "কর্তৃপক্ষ", "দাবি", "দাবী", "মন্তব্য", "সরকার", "সরকারের", "মন্ত্রী", "প্রশাসনের", "পুলিশ",
}
# Quotation marks: a sentence quoted inside a report.
QUOTE = re.compile(r"[‘’“”\"']")


# Verb forms used only with তুমি / তুই (করছিস, খাচ্ছো): talking to a friend. Shorter endings
# (বি, লি, িস) also end nouns (চবি, অফিস), so those verbs are listed one by one instead.
INFORMAL_VERB = re.compile(r"(ছিস|চ্ছিস|ছো|চ্ছো|ছিলি|ছিলে|তিস)$")
INFORMAL_WORDS = set("""
যাবি করবি আসবি খাবি বলবি পারবি দিবি নিবি থাকবি দেখবি শুনবি বুঝবি জানবি ঘুমাবি
বললি করলি গেলি এলি আসলি খেলি দিলি নিলি পারলি দেখলি শুনলি বুঝলি জানলি
পারিস করিস বলিস যাস খাস আসিস থাকিস জানিস বুঝিস দেখিস শুনিস দিস নিস
শোন শোনো বলো করো দেখো এসো যাও খাও দাও নাও চলো থাকো বসো আসো
""".split())


def score(words):
    """(score, addressed): addressed = talks to তুমি / তুই; score counts all chat signals."""
    s = 0
    addressed = False
    personal = False
    for w in words:
        if w in SECOND:
            s += 2
            addressed = True
        elif w in FIRST:
            s += 1
            personal = True
        elif w in CHATTY:
            s += 1
        if len(w) >= 3 and w not in FIRST and w not in SECOND:
            if w in INFORMAL_WORDS or INFORMAL_VERB.search(w):
                s += 2
                addressed = True
            elif SPOKEN_VERB.search(w):
                s += 1
                personal = True
    return s, addressed, personal


def chat_sentences(text, blocked):
    for raw in SPLIT.split(text):
        raw = raw.strip()
        if not raw or URLISH.search(raw) or QUOTE.search(raw):
            continue
        words = WORD.findall(raw)
        if not 3 <= len(words) <= 20:
            continue
        if len(LATIN_OR_DIGIT.findall(raw)) > 3:
            continue
        if any(w in blocked for w in words):
            continue
        if any(SADHU.search(w) for w in words) or any(w in FORMAL for w in words):
            continue
        s, addressed, personal = score(words)
        # Talking to someone: any length. About oneself: only short ones (long ones are speeches).
        if (addressed and s >= 3) or (personal and s >= 3 and len(words) <= 10):
            yield raw


def main():
    out, blocklist, shards = sys.argv[1], sys.argv[2], sys.argv[3:]
    blocked = set(open(blocklist, encoding="utf-8").read().split())
    total_docs = kept = kept_words = 0
    with open(out, "a", encoding="utf-8") as o:
        for shard in shards:
            f = pq.ParquetFile(shard)
            for g in range(f.metadata.num_row_groups):
                for text in f.read_row_group(g, columns=["text"]).column("text").to_pylist():
                    total_docs += 1
                    for s in chat_sentences(text, blocked):
                        o.write(s.replace("\n", " ") + "\n")
                        kept += 1
                        kept_words += len(WORD.findall(s))
            print(f"{shard}: {total_docs} pages so far, {kept} sentences, {kept_words} words", flush=True)


if __name__ == "__main__":
    main()
