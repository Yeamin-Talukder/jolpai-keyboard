"""Writes DIR/web/meta.json for dashboard.html: the run's title, test-set names and its steps.

  python meta_update.py DIR STEP_INDEX STATE [NOTE]     (STATE: done | active | pending)
  python meta_update.py DIR init META_TEMPLATE.json

Times are stamped when a step becomes active or done.
"""
import json, os, sys, time

d = sys.argv[1]
path = os.path.join(d, "web", "meta.json")
os.makedirs(os.path.dirname(path), exist_ok=True)
if sys.argv[2] == "init":
    with open(sys.argv[3], encoding="utf-8") as f:
        meta = json.load(f)
else:
    with open(path, encoding="utf-8") as f:
        meta = json.load(f)
    i, state = int(sys.argv[2]), sys.argv[3]
    step = meta["steps"][i]
    step["state"] = state
    if len(sys.argv) > 4:
        step["note"] = sys.argv[4]
    if state in ("active", "done"):
        step["when"] = time.strftime("%H:%M")
    active = [s for s in meta["steps"] if s.get("state") == "active"]
    meta["status_text"] = active[0]["name"] if active else ("Finished" if all(s.get("state") == "done" for s in meta["steps"]) else "Waiting")
    if all(s.get("state") == "done" for s in meta["steps"]):
        meta["status"] = "done"
meta["updated"] = time.time()
tmp = path + ".tmp"
with open(tmp, "w", encoding="utf-8") as f:
    json.dump(meta, f, ensure_ascii=False)
os.replace(tmp, path)
