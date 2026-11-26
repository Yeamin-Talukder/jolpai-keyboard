"""Trains the Bangla next-word model with PyTorch (Apple GPU via MPS, or CUDA, or CPU).

Same network and file as the Kotlin trainer (Lstm.kt) and the app's reader (NextWordModel.kt):
word vectors E (V x D) shared with the output layer, one LSTM layer of H cells projected back
to D numbers, an output bias; exported as the int8 `SWKBNWP1` file.

Unlike the Kotlin trainer it uses the exact softmax over the whole vocabulary. The Kotlin
run drifted with a sampled softmax: rare words were seldom drawn as negatives, so nothing held
their scores down and they rose to the top. With every word in every step's softmax that
cannot happen. Held-out perplexity is measured during training and the best model is kept.

Input: a directory made by `lm-trainer prepare` + `encode` (vocab.tsv, train.bin, valid-*.bin).

    python train_torch.py train  DIR --dim 128 --hidden 1024 --minutes 60
    python train_torch.py eval   DIR
    python train_torch.py export DIR OUT.nwp.bin
    python train_torch.py top    DIR "আমি তোমাকে"      (next-word guesses, to compare with the app's reader)
"""
import argparse
import glob
import math
import os
import struct
import sys
import time

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

UNK, START, END, FIRST_WORD = 0, 1, 2, 3


# ------------------------------------------------------------------ data

def read_vocab(d):
    words = []
    counts = []
    with open(os.path.join(d, "vocab.tsv"), encoding="utf-8") as f:
        for line in f:
            w, n = line.rstrip("\n").split("\t")
            words.append(w)
            counts.append(int(n))
    return words, counts


def read_corpus(path, max_len=40):
    """Sentences from Prepare.encode's token file: big-endian u16 length, then u16 ids."""
    a = np.fromfile(path, dtype=">u2").astype(np.int32)
    starts, lens = [], []
    p = 0
    n = len(a)
    while p < n:
        ln = int(a[p])
        if 0 < ln <= max_len:
            starts.append(p + 1)
            lens.append(ln)
        p += 1 + ln
    return a, np.array(starts, dtype=np.int64), np.array(lens, dtype=np.int64)


# Batch lengths (steps). Every batch is padded to one of these and has a fixed number of rows:
# Apple's GPU framework compiles and keeps a graph for every new shape, and hundreds of shapes
# used up all memory.
BUCKETS = (8, 12, 16, 24, 32, 41)


def batches(corpus, batch_tokens, shuffle, rng):
    """Batches of similar-length sentences: (inputs, targets) with START prepended, END appended."""
    a, starts, lens = corpus
    bucket = np.searchsorted(np.array(BUCKETS), lens + 1)
    groups = []
    for k, T in enumerate(BUCKETS):
        idx = np.nonzero(bucket == k)[0]
        if shuffle:
            rng.shuffle(idx)
        rows = max(1, batch_tokens // T)
        for i in range(0, len(idx), rows):
            groups.append((T, rows, idx[i:i + rows]))
    if shuffle:
        rng.shuffle(groups)
    for T, rows, g in groups:
        inp = np.zeros((rows, T), dtype=np.int64)
        tgt = np.full((rows, T), -100, dtype=np.int64)
        for r, s in enumerate(g):
            ln = int(lens[s])
            w = a[starts[s]:starts[s] + ln]
            inp[r, 0] = START
            inp[r, 1:ln + 1] = w
            tgt[r, :ln] = w
            tgt[r, ln] = END
        yield torch.from_numpy(inp), torch.from_numpy(tgt)


# ------------------------------------------------------------------ model

class NextWord(nn.Module):
    """The LSTM is written out step by step: PyTorch's nn.LSTM has no projection on Apple GPUs."""

    def __init__(self, vocab, dim, hidden, dropout=0.1):
        super().__init__()
        self.vocab, self.dim, self.hidden = vocab, dim, hidden
        self.emb = nn.Embedding(vocab, dim)
        # Gates in the app's order: input, forget, cell, output.
        self.wih = nn.Parameter(torch.empty(4 * hidden, dim).uniform_(-1, 1) / math.sqrt(dim))
        self.whh = nn.Parameter(torch.empty(4 * hidden, dim).uniform_(-1, 1) / math.sqrt(dim))
        self.bias = nn.Parameter(torch.zeros(4 * hidden))
        self.proj = nn.Parameter(torch.empty(dim, hidden).uniform_(-1, 1) / math.sqrt(hidden))
        self.out_bias = nn.Parameter(torch.zeros(vocab))
        self.drop = nn.Dropout(dropout)
        nn.init.uniform_(self.emb.weight, -0.1, 0.1)
        with torch.no_grad():
            self.bias[hidden:2 * hidden] = 1.0  # remember by default

    def forward(self, inp):
        B, T = inp.shape
        H, D = self.hidden, self.dim
        x = self.drop(self.emb(inp))
        gx = x @ self.wih.t() + self.bias          # B x T x 4H, all steps at once
        h = x.new_zeros(B, D)
        c = x.new_zeros(B, H)
        outs = []
        for t in range(T):
            g = gx[:, t] + h @ self.whh.t()
            i, f, u, o = g.split(H, dim=1)
            c = torch.sigmoid(f) * c + torch.sigmoid(i) * torch.tanh(u)
            h = (torch.sigmoid(o) * torch.tanh(c)) @ self.proj.t()
            outs.append(h)
        hs = self.drop(torch.stack(outs, dim=1))
        return hs @ self.emb.weight.t() + self.out_bias


def device():
    if torch.backends.mps.is_available():
        return torch.device("mps")
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


def ckpt_path(d, dim, hidden, name="best"):
    return os.path.join(d, f"torch-{dim}-{hidden}-{name}.pt")


# ------------------------------------------------------------------ measuring

@torch.no_grad()
def evaluate(model, corpus, dev, max_sentences=20000):
    """(perplexity over all next tokens, top-3 accuracy on next words) for a held-out set."""
    model.eval()
    a, starts, lens = corpus
    if len(starts) > max_sentences:
        keep = np.linspace(0, len(starts) - 1, max_sentences).astype(np.int64)
        corpus = (a, starts[keep], lens[keep])
    nll = 0.0
    n = 0
    hit3 = 0
    words = 0
    if dev.type == "mps":
        torch.mps.empty_cache()
    for inp, tgt in batches(corpus, 2048, False, None):
        inp, tgt = inp.to(dev), tgt.to(dev)
        # Whole fixed-shape tensors only (no selecting positions): see BUCKETS.
        logits = model(inp)
        nll += F.cross_entropy(logits.view(-1, logits.shape[-1]), tgt.view(-1), ignore_index=-100, reduction="sum").item()
        n += int((tgt != -100).sum())
        wm = tgt >= FIRST_WORD
        top = logits.topk(3, dim=-1).indices
        hit3 += int(((top == tgt.unsqueeze(-1)).any(dim=-1) & wm).sum())
        words += int(wm.sum())
    model.train()
    return math.exp(nll / max(n, 1)), hit3 / max(words, 1)


def valid_sets(d):
    out = {}
    for p in sorted(glob.glob(os.path.join(d, "valid-*.bin"))):
        name = os.path.basename(p)[len("valid-"):-len(".bin")]
        out[name] = read_corpus(p)
    return out


def report(model, sets, dev, results=None):
    """Prints each held-out set; returns the mean perplexity of the everyday ones (not Wikipedia).
    [results], if given, receives {set: {"ppl": .., "top3": ..}}."""
    parts = []
    everyday = []
    for name, c in sets.items():
        ppl, acc = evaluate(model, c, dev)
        parts.append(f"{name} ppl {ppl:.0f} top3 {100 * acc:.1f}%")
        if results is not None:
            results[name] = {"ppl": round(ppl, 1), "top3": round(100 * acc, 2)}
        if name != "wikipedia":
            everyday.append(math.log(ppl))
    print("   " + "; ".join(parts), flush=True)
    return math.exp(sum(everyday) / len(everyday)) if everyday else float("inf")


# Phrases whose next-word guesses the dashboard shows at each evaluation.
SAMPLES = ["আমি", "তুমি কেমন", "আমার নাম", "আজকে আমি বাসায়", "ভাই তুই কোথায়", "আমি তোমাকে", "কালকে আমরা", "এখন কি করছো"]


@torch.no_grad()
def guesses(model, index, words, dev, texts=SAMPLES, k=5):
    model.eval()
    out = []
    for text in texts:
        ids = [START] + [index.get(w, UNK) for w in text.split()]
        p = F.softmax(model(torch.tensor([ids], device=dev))[0, -1], dim=-1)
        top = p.topk(k + 2)
        names = ["<unk>", "<s>", "</s>"]
        shown = [[words[t - FIRST_WORD] if t >= FIRST_WORD else names[t], round(v, 4)]
                 for v, t in zip(top.values.tolist(), top.indices.tolist()) if t != UNK][:k]
        out.append({"text": text, "top": shown})
    model.train()
    return out


class Progress:
    """Writes DIR/web/progress.json for the dashboard (dashboard.html, served from DIR/web)."""

    def __init__(self, d, config):
        self.path = os.path.join(d, "web", "progress.json")
        os.makedirs(os.path.dirname(self.path), exist_ok=True)
        old = {}
        if os.path.exists(self.path):
            try:
                import json
                with open(self.path, encoding="utf-8") as f:
                    old = json.load(f)
            except Exception:
                old = {}
        # A resumed run keeps its history (times continue from where it stopped).
        same = old.get("config", {}).get("tokens_per_pass") == config["tokens_per_pass"] and not config.get("fresh")
        self.data = {
            "status": "training",
            "config": config,
            "offset_min": old.get("now", {}).get("elapsed_min", 0) if same else 0,
            "train": old.get("train", []) if same else [],
            "evals": old.get("evals", []) if same else [],
            "best": old.get("best") if same else None,
            "samples": old.get("samples", []) if same else [],
            "now": {},
        }

    def write(self):
        import json
        self.data["updated"] = time.time()
        tmp = self.path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.data, f, ensure_ascii=False)
        os.replace(tmp, self.path)


# ------------------------------------------------------------------ commands

def train(args):
    torch.manual_seed(1)
    rng = np.random.default_rng(1)
    dev = device()
    words, _ = read_vocab(args.dir)
    V = len(words) + FIRST_WORD
    corpus = read_corpus(os.path.join(args.dir, "train.bin"))
    sets = valid_sets(args.dir)
    total = int(corpus[2].sum()) + len(corpus[2])
    model = NextWord(V, args.dim, args.hidden, args.dropout).to(dev)
    params = sum(p.numel() for p in model.parameters())
    print(f"{dev}: {len(corpus[1])} sentences, {total} tokens; vocab {V}; {params} numbers", flush=True)
    opt = torch.optim.Adam(model.parameters(), lr=args.lr)
    last = ckpt_path(args.dir, args.dim, args.hidden, "last")
    best_ppl = float("inf")
    step = 0
    seen = 0
    if os.path.exists(last) and not args.fresh:
        st = torch.load(last, map_location=dev)
        model.load_state_dict(st["model"])
        opt.load_state_dict(st["opt"])
        best_ppl, step, seen = st["best"], st["step"], st["seen"]
        print(f"resumed: step {step}, best everyday perplexity {best_ppl:.1f}", flush=True)
    budget = args.minutes * 60
    t0 = time.time()
    next_eval = t0 + args.eval_minutes * 60
    planned = args.epochs * total
    loss_sum = 0.0
    loss_n = 0
    done = False
    index = {w: i + FIRST_WORD for i, w in enumerate(words)}
    prog = Progress(args.dir, {
        "device": str(dev), "dim": args.dim, "hidden": args.hidden, "vocab": V, "params": params,
        "sentences": len(corpus[1]), "tokens_per_pass": total, "planned_passes": args.epochs,
        "minutes_budget": args.minutes, "eval_minutes": args.eval_minutes, "lr": args.lr,
        "sets": list(sets.keys()), "fresh": args.fresh,
    })
    off = prog.data["offset_min"]
    next_write = t0
    rate_t, rate_seen = t0, seen
    rate = 0.0
    recent = []  # (loss) samples since the last progress write

    def update_now(now, phase="training"):
        el = (now - t0) / 60
        left = max(0.0, min(budget - (now - t0), (planned - seen) / rate if rate > 0 else budget))
        prog.data["status"] = phase
        prog.data["now"] = {
            "step": step, "seen": seen, "passes": round(seen / total, 4), "tokens_per_s": round(rate),
            "lr": opt.param_groups[0]["lr"], "elapsed_min": round(off + el, 2), "eta_min": round(left / 60, 1),
            "started": t0,
        }

    while not done:
        for inp, tgt in batches(corpus, args.batch_tokens, True, rng):
            # Learning rate: cosine from lr to lr/20 over the planned tokens.
            frac = min(seen / planned, 1.0)
            for g in opt.param_groups:
                g["lr"] = args.lr * (0.05 + 0.95 * 0.5 * (1 + math.cos(math.pi * frac)))
            ntok = int((tgt != -100).sum())  # counted on the CPU, so the GPU is not waited for
            inp, tgt = inp.to(dev), tgt.to(dev)
            logits = model(inp)
            loss = F.cross_entropy(logits.view(-1, V), tgt.view(-1), ignore_index=-100)
            opt.zero_grad(set_to_none=True)
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            step += 1
            seen += ntok
            if step % 50 == 0:
                lv = loss.item()
                loss_sum += lv
                loss_n += 1
                recent.append(lv)
            now = time.time()
            if now >= next_write:
                if now - rate_t >= 20:
                    rate = (seen - rate_seen) / (now - rate_t)
                    rate_t, rate_seen = now, seen
                update_now(now)
                if recent:
                    prog.data["train"].append([round(off + (now - t0) / 60, 2), round(math.exp(sum(recent) / len(recent)), 1)])
                    recent = []
                prog.write()
                next_write = now + 30
            if now >= next_eval or now - t0 >= budget or seen >= planned:
                el = (now - t0) / 60
                print(f"{el:.0f} min: step {step}, {seen / total:.2f} passes, train ppl {math.exp(loss_sum / max(loss_n, 1)):.0f}, lr {opt.param_groups[0]['lr']:.5f}", flush=True)
                loss_sum, loss_n = 0.0, 0
                update_now(now, "measuring")
                prog.write()
                results = {}
                ppl = report(model, sets, dev, results)
                is_best = ppl < best_ppl
                if is_best:
                    best_ppl = ppl
                    torch.save(model.state_dict(), ckpt_path(args.dir, args.dim, args.hidden))
                    print(f"   best so far (everyday perplexity {ppl:.1f}), saved", flush=True)
                torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "best": best_ppl, "step": step, "seen": seen}, last)
                at = round(off + el, 2)
                prog.data["evals"].append({"min": at, "passes": round(seen / total, 3), "everyday_ppl": round(ppl, 1), "sets": results, "best": is_best})
                if is_best:
                    prog.data["best"] = {"everyday_ppl": round(ppl, 1), "min": at, "passes": round(seen / total, 3)}
                prog.data["samples"] = guesses(model, index, words, dev, args.samples.split("|") if args.samples else SAMPLES)
                now = time.time()
                next_eval = now + args.eval_minutes * 60
                finished = now - t0 >= budget or seen >= planned
                update_now(now, "done" if finished else "training")
                prog.write()
                if finished:
                    done = True
                    break
    print(f"done: best everyday perplexity {best_ppl:.1f}", flush=True)


def load_best(args, dev):
    words, _ = read_vocab(args.dir)
    model = NextWord(len(words) + FIRST_WORD, args.dim, args.hidden).to(dev)
    model.load_state_dict(torch.load(ckpt_path(args.dir, args.dim, args.hidden), map_location=dev))
    model.eval()
    return model, words


def cmd_eval(args):
    dev = device()
    model, _ = load_best(args, dev)
    report(model, valid_sets(args.dir), dev)


def cmd_top(args):
    dev = device()
    model, words = load_best(args, dev)
    index = {w: i + FIRST_WORD for i, w in enumerate(words)}
    for text in args.texts:
        ids = [START] + [index.get(w, UNK) for w in text.split()]
        with torch.no_grad():
            logp = F.log_softmax(model(torch.tensor([ids], device=dev))[0, -1], dim=-1)
        top = logp.topk(8)
        shown = ", ".join(f"{words[k - FIRST_WORD] if k >= FIRST_WORD else ['<unk>', '<s>', '</s>'][k]} {v:.2f}"
                          for v, k in zip(top.values.tolist(), top.indices.tolist()))
        print(f"{text} → {shown}")


def export(args):
    """The app's int8 SWKBNWP1 file, byte for byte as Lstm.export writes it."""
    model, words = load_best(args, torch.device("cpu"))
    V, D, H = model.vocab, model.dim, model.hidden
    assert len(words) == V - FIRST_WORD
    emb = model.emb.weight.detach().numpy()
    wih = model.wih.detach().numpy()                           # 4H x D
    whh = model.whh.detach().numpy()                           # 4H x D (projected h)
    bias = model.bias.detach().numpy()
    proj = model.proj.detach().numpy()                         # D x H
    out_bias = model.out_bias.detach().numpy()
    buf = bytearray()

    def pad(n):
        buf.extend(b"\0" * ((n + 15) // 16 * 16 - n))

    def matrix(m):
        m = m.astype(np.float32)
        mx = np.abs(m).max(axis=1)
        scale = np.where(mx > 0, mx / 127.0, 1.0).astype(np.float32)
        q = np.clip(np.rint(m / scale[:, None]), -127, 127).astype(np.int8)
        buf.extend(q.tobytes())
        pad(q.size)
        buf.extend(scale.astype("<f4").tobytes())

    def floats(a):
        buf.extend(a.astype("<f4").tobytes())

    word_bytes = "\n".join(words).encode("utf-8")
    buf.extend(b"SWKBNWP1")
    buf.extend(struct.pack("<6i", 1, 1, V, D, H, len(word_bytes)))
    buf.extend(struct.pack("<2f", 0.7, -0.5))
    buf.extend(word_bytes)
    pad(len(word_bytes))
    matrix(emb)
    floats(out_bias)
    matrix(wih)
    matrix(whh)
    floats(bias)
    matrix(proj)
    h = 0xCBF29CE484222325  # FNV-1a 64, as Lstm.export
    for b in bytes(buf):
        h = ((h ^ b) * 0x100000001B3) & 0xFFFFFFFFFFFFFFFF
    buf.extend(struct.pack("<Q", h))
    with open(args.out, "wb") as f:
        f.write(buf)
    print(f"{args.out}: {len(buf)} bytes, vocab {V}, dim {D}, hidden {H}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("cmd", choices=["train", "eval", "export", "top"])
    p.add_argument("dir")
    p.add_argument("rest", nargs="*")
    p.add_argument("--dim", type=int, default=128)
    p.add_argument("--hidden", type=int, default=1024)
    p.add_argument("--dropout", type=float, default=0.1)
    p.add_argument("--lr", type=float, default=2e-3)
    p.add_argument("--epochs", type=float, default=3)
    p.add_argument("--minutes", type=float, default=60)
    p.add_argument("--eval-minutes", type=float, default=10)
    # ~2,000 tokens per step was fastest on an M5 (bigger batches were slower per token).
    p.add_argument("--batch-tokens", type=int, default=2048)
    p.add_argument("--fresh", action="store_true", help="ignore the last checkpoint")
    p.add_argument("--samples", default="", help="phrases for the dashboard, separated by |")
    args = p.parse_args()
    if args.cmd == "train":
        train(args)
    elif args.cmd == "eval":
        cmd_eval(args)
    elif args.cmd == "export":
        args.out = args.rest[0]
        export(args)
    else:
        args.texts = args.rest
        cmd_top(args)


if __name__ == "__main__":
    sys.exit(main())
