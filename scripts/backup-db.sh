#!/usr/bin/env bash
# Pull the Healthy database off the phone into a timestamped, checkpointed copy.
#
# Room runs SQLite in WAL mode, so most recent writes live in healthy.db-wal
# rather than in healthy.db. Copying the main file alone loses them, and on a
# freshly created database it loses everything. This pulls the WAL too and
# replays it locally, then reports the row counts so the backup is verified
# rather than assumed.
set -euo pipefail

PKG=com.healthy.app
OUT_DIR="${1:-$HOME/healthy-backups}"
STAMP=$(date +%Y%m%d-%H%M%S)
DEST="$OUT_DIR/healthy-$STAMP.db"

command -v adb >/dev/null || { echo "adb not on PATH"; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "no device; connect the phone"; exit 1; }

mkdir -p "$OUT_DIR"

adb exec-out run-as "$PKG" cat databases/healthy.db     > "$DEST"
adb exec-out run-as "$PKG" cat databases/healthy.db-wal > "$DEST-wal" 2>/dev/null || true

python3 - "$DEST" <<'PY'
import sqlite3, sys, os
dest = sys.argv[1]
con = sqlite3.connect(dest)
# Opening with the -wal present replays it; TRUNCATE folds it into the db file
# so the backup is a single self-contained file.
con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
con.commit()
counts = {}
for t in ("night", "drink", "weight", "note", "product", "meal_entry", "recipe", "custom_drink"):
    try:
        counts[t] = con.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
    except sqlite3.Error:
        counts[t] = "-"
con.close()
for stray in (dest + "-wal", dest + "-shm"):
    if os.path.exists(stray):
        os.remove(stray)
print("rows: " + ", ".join(f"{k}={v}" for k, v in counts.items()))
total = sum(v for v in counts.values() if isinstance(v, int))
print(f"backup: {dest}")
if total == 0:
    print("WARNING: the backup is empty. Nothing was logged, or the pull failed.")
PY
