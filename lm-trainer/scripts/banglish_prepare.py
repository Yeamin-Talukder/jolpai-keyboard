"""Builds the Banglish next-word model's training files from real Banglish text only.

  python -E -P banglish_prepare.py OUT_DIR --tlit DIR --youtube FILE.xlsx --banten FILE.csv
      --chaos FILE.xlsx --mixtox FILE.csv --sentiment FILE.csv --list banglish-words.tsv [--vocab 15000]

Writes OUT_DIR/vocab.tsv, train.bin, valid-tlit.bin, valid-youtube.bin (+ the .txt of each held-out
set) in the format train_torch.py reads (big-endian u16 length, then u16 word ids; 0 unknown,
1 start, 2 end, words from 3).

Sources (all allow commercial use; credits in suggestion-engine/src/main/resources/lm/NOTICE.txt):
- BanglaTLit-PT (MIT): 246k romanized comments and posts. Its val and test sentences are held out
  (valid-tlit), as in the app's other Banglish tests.
- YouTube news comments (Mendeley 3c3j3bkxvn v4, CC BY 4.0): comments written in Banglish (at least
  3 Latin words, no Bangla script, half or more of the words in the app's Banglish list). Every
  100th comment is held out (valid-youtube).
- BANTEN (Mendeley j3y2y9nfck v2, CC BY 4.0): the Banglish side of 14k human translations.
- Code-mixed Chaos (Mendeley 23dp3t88vk v1, CC BY 4.0): Banglish comments labelled Non-Toxic only.
- MixTox-SA (Mendeley bsdcd7zkyz v1, CC BY 4.0): Banglish rows labelled NonToxic or Neutral.
- Transliterated Bengali sentiment corpus (Mendeley s6mtp2zzpc v1, CC BY 4.0).

Words are lower-case a–z runs. Spellings that sound alike (the keyboard's BanglishFold: valo,
bhalo, bhaalo) become one word, written in its most common spelling, so the little data there is
isn't split across spellings. Sentences with swear words are left out.
"""
import argparse, csv, html, os, random, re, struct, sys, zipfile
from collections import Counter, defaultdict

csv.field_size_limit(10 ** 9)
sys.stdout.reconfigure(encoding="utf-8")

VOWELS = set("aeiouwy")
MAP = {"v": "b", "z": "j", "c": "s", "q": "k", "f": "p", "w": "o", "y": "i"}


def fold(word):
    """Same as BanglishFold.fold in the app."""
    out = []
    prev = " "
    for c in word.lower():
        if not ("a" <= c <= "z"):
            continue
        after, prev = prev, c
        if c == "h" and "a" <= after <= "z" and after not in VOWELS:
            continue
        m = MAP.get(c, c)
        if out and out[-1] == m:
            continue
        out.append(m)
    return "".join(out)


BANGLA = re.compile(r"[ঀ-৿]")
SPLIT = re.compile(r"[.!?\n।|]+|\.\.+")
WORD = re.compile(r"[a-z]+")
URL = re.compile(r"https?://\S+|www\.\S+|\S+@\S+")
SWEAR_PARTS = ("chod", "chud", "khanki", "haramjad", "haramzad", "haramkhor", "beshya", "bessa", "leora", "gandu",
               "bainchod", "madarchod", "magir", "maggir", "baler", "balchal", "vuda", "voda", "bhoda", "chutmar")
SWEAR_WORDS = {"magi", "maggi", "bal", "baal", "pod", "harami", "chut", "fokinni", "fokinnir", "fuck", "fucking", "shit",
               "bitch", "sex", "sexy", "porn", "xxx", "dick", "pussy"}


def swear(w):
    r = w.replace("kichud", "").replace("kisud", "").replace("choddo", "")
    return w in SWEAR_WORDS or any(p in r for p in SWEAR_PARTS)


def sentences(text):
    """Lower-case word lists of the Banglish sentences in a text (none if it has Bangla script)."""
    if not text or BANGLA.search(text):
        return []
    text = URL.sub(" ", html.unescape(text).lower())
    out = []
    for part in SPLIT.split(text):
        ws = WORD.findall(part)
        if len(ws) >= 2 and not any(swear(w) for w in ws):
            out.append(ws[:40])
    return out


def xlsx_rows(path):
    """Rows of the first sheet as {column letter: text}, shared strings resolved."""
    z = zipfile.ZipFile(path)
    names = z.namelist()
    strs = []
    if "xl/sharedStrings.xml" in names:
        ss = z.read("xl/sharedStrings.xml").decode("utf-8")
        strs = [html.unescape(re.sub(r"<[^>]+>", "", m)) for m in re.findall(r"<si>(.*?)</si>", ss, re.S)]
    for sheet in sorted(n for n in names if n.startswith("xl/worksheets/sheet") and n.endswith(".xml")):
        xml = z.read(sheet).decode("utf-8")
        for row in re.findall(r"<row[^>]*>(.*?)</row>", xml, re.S):
            cells = {}
            for m in re.finditer(r'<c r="([A-Z]+)\d+"([^>]*?)(?:/>|>(.*?)</c>)', row, re.S):
                col, attrs, body = m.group(1), m.group(2), m.group(3) or ""
                v = re.search(r"<v>(.*?)</v>", body, re.S)
                t = re.search(r'\bt="(\w+)"', attrs)
                if t and t.group(1) == "s" and v:
                    cells[col] = strs[int(v.group(1))]
                elif t and t.group(1) == "inlineStr":
                    cells[col] = html.unescape(re.sub(r"<[^>]+>", "", body))
                elif v:
                    cells[col] = html.unescape(v.group(1))
            yield cells


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--tlit", required=True)
    ap.add_argument("--youtube", required=True)
    ap.add_argument("--banten")
    ap.add_argument("--chaos")
    ap.add_argument("--mixtox")
    ap.add_argument("--sentiment")
    ap.add_argument("--list", required=True, help="the app's banglish-words.tsv (to spot Banglish comments)")
    ap.add_argument("--vocab", type=int, default=15000)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    listed = {l.split("\t")[0] for l in open(a.list, encoding="utf-8") if l.strip() and not l.startswith("#")}

    train, valid = defaultdict(list), defaultdict(list)

    # BanglaTLit: val + test held out.
    held = set()
    for name in ("BanglaTLiT_val.csv", "BanglaTLiT_test.csv"):
        with open(os.path.join(a.tlit, name), encoding="utf-8") as f:
            for row in csv.DictReader(f):
                t = row["text_transliterated"].strip()
                held.add(t)
                valid["tlit"] += sentences(t)
    with open(os.path.join(a.tlit, "BanglaTLit-PT.txt"), encoding="utf-8") as f:
        for line in f:
            if line.strip() not in held:
                train["tlit"] += sentences(line)

    # YouTube comments written in Banglish; every 100th held out.
    n = 0
    seen = set()
    for cells in xlsx_rows(a.youtube):
        for t in cells.values():
            if t in seen or BANGLA.search(t):
                continue
            ws = re.findall(r"[A-Za-z]+", t)
            if len(ws) < 3 or sum(w.lower() in listed for w in ws) * 2 < len(ws):
                continue
            seen.add(t)
            n += 1
            (valid if n % 100 == 0 else train)["youtube"].extend(sentences(t))

    if a.banten:
        with open(a.banten, encoding="utf-8") as f:
            for row in csv.DictReader(f):
                train["banten"] += sentences(row.get("Banglish", ""))
    if a.chaos:
        rows = list(xlsx_rows(a.chaos))
        for cells in rows[1:]:
            if cells.get("K", "").startswith("1"):
                train["chaos"] += sentences(cells.get("A", ""))
    if a.mixtox:
        with open(a.mixtox, encoding="utf-8") as f:
            for row in csv.DictReader(f):
                if row.get("Class") in ("NonToxic", "Neutral"):
                    t = row.get("Text", "")
                    ws = re.findall(r"[A-Za-z]+", t)
                    if ws and sum(w.lower() in listed for w in ws) * 2 >= len(ws):
                        train["mixtox"] += sentences(t)
    if a.sentiment:
        with open(a.sentiment, encoding="utf-8") as f:
            for row in csv.DictReader(f):
                train["sentiment"] += sentences(row.get("Comments", ""))

    # Repeated sentences once (comments are often copied).
    for d in (train, valid):
        for k in d:
            uniq, s = [], set()
            for ws in d[k]:
                key = " ".join(ws)
                if key not in s:
                    s.add(key)
                    uniq.append(ws)
            d[k] = uniq
    held_keys = {" ".join(ws) for k in valid for ws in valid[k]}
    for k in train:
        train[k] = [ws for ws in train[k] if " ".join(ws) not in held_keys]

    for k in sorted(train):
        print(f"{k}: {len(train[k]):,} sentences, {sum(map(len, train[k])):,} words")
    for k in sorted(valid):
        print(f"held out {k}: {len(valid[k]):,} sentences, {sum(map(len, valid[k])):,} words")

    # One word per sound (fold), written in its most common spelling.
    spellings = defaultdict(Counter)
    for k in train:
        for ws in train[k]:
            for w in ws:
                spellings[fold(w) or w][w] += 1
    rep = {key: c.most_common(1)[0][0] for key, c in spellings.items()}
    counts = Counter({rep[key]: sum(c.values()) for key, c in spellings.items()})
    vocab = [w for w, _ in counts.most_common(a.vocab)]
    index = {w: i + 3 for i, w in enumerate(vocab)}
    total = sum(counts.values())
    covered = sum(counts[w] for w in vocab)
    print(f"{len(spellings):,} words (by sound) in {total:,}; vocabulary {len(vocab):,} covers {100 * covered / total:.1f}%")
    with open(os.path.join(a.out, "vocab.tsv"), "w", encoding="utf-8", newline="\n") as f:
        for w in vocab:
            f.write(f"{w}\t{counts[w]}\n")

    def ids(ws):
        return [index.get(rep.get(fold(w) or w, w), 0) for w in ws]

    def write(path, sents):
        with open(path, "wb") as f:
            for ws in sents:
                t = ids(ws)
                f.write(struct.pack(">H", len(t)))
                f.write(struct.pack(f">{len(t)}H", *t))

    everything = [ws for k in sorted(train) for ws in train[k]]
    random.Random(1).shuffle(everything)
    write(os.path.join(a.out, "train.bin"), everything)
    for k in valid:
        write(os.path.join(a.out, f"valid-{k}.bin"), valid[k])
        with open(os.path.join(a.out, f"valid-{k}.txt"), "w", encoding="utf-8", newline="\n") as f:
            for ws in valid[k]:
                f.write(" ".join(ws) + "\n")
    print(f"train.bin: {len(everything):,} sentences, {sum(map(len, everything)):,} words")


if __name__ == "__main__":
    main()
