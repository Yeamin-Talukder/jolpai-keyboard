#!/usr/bin/env bash
# Chat-style sentences from all of HPLT 2.0 cleaned, Bangla (174 parquet files, ~25 GB).
# Each file is downloaded, filtered by hplt_chat.py and deleted, 3 at a time, so only a
# few hundred MB of disk is in use. Re-running skips files already done.
#
#   hplt_chat.sh WORK_DIR BLOCKLIST.txt      → WORK_DIR/chat-NNN.txt, then cat them together
set -u
WORK=$1
BLOCK=$2
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$WORK"
one() {
  n=$(printf "%05d" "$1")
  out="$WORK/chat-$n.txt"
  [ -f "$out.done" ] && return
  f="$WORK/shard-$n.parquet"
  curl -sfL --retry 3 -o "$f" "https://huggingface.co/datasets/HPLT/HPLT2.0_cleaned/resolve/main/ben_Beng/train-$n-of-00174.parquet" || { echo "download failed: $n"; return; }
  rm -f "$out"
  (cd "$WORK" && python -E -P "$HERE/hplt_chat.py" "$out" "$BLOCK" "$f") && touch "$out.done"
  rm -f "$f"
}
export -f one
export WORK BLOCK HERE
seq 0 173 | xargs -P 3 -I{} bash -c 'one {}'
cat "$WORK"/chat-*.txt > "$WORK/hplt-chat.txt"
echo "all done: $(wc -l < "$WORK/hplt-chat.txt") sentences"
