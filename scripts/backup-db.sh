#!/usr/bin/env bash
# Pull the Healthy database off the phone into a timestamped, checkpointed copy.
#
# Room runs SQLite in WAL mode, so recent writes live in healthy.db-wal rather
# than in healthy.db. Copying the main file alone loses them, and on a freshly
# created database it loses everything. This pulls the WAL too and replays it
# locally, then reports row counts so an empty backup cannot pass silently.
set -euo pipefail

PKG=com.healthy.app
OUT_DIR="${1:-$HOME/healthy-backups}"
STAMP=$(date +%Y%m%d-%H%M%S)
DEST="$OUT_DIR/healthy-$STAMP.db"

command -v adb >/dev/null || { echo "adb not on PATH"; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "no device; connect the phone"; exit 1; }

# Nothing to lose is not a failure. A test run uninstalls the app, so the very
# next backup legitimately finds no package and must not abort the caller.
if ! adb shell pm list packages 2>/dev/null | grep -q "^package:$PKG$"; then
  echo "no backup: $PKG is not installed, so there is nothing to lose"
  exit 0
fi
if ! adb exec-out run-as "$PKG" test -f databases/healthy.db 2>/dev/null; then
  echo "no backup: the app has no database yet, so there is nothing to lose"
  exit 0
fi

mkdir -p "$OUT_DIR"
adb exec-out run-as "$PKG" cat databases/healthy.db     > "$DEST"
adb exec-out run-as "$PKG" cat databases/healthy.db-wal > "$DEST-wal" 2>/dev/null || true

# A pull that produced anything other than a SQLite file is a real failure and
# must stop the caller, rather than leaving a corrupt file named as a backup.
if [ "$(head -c 15 "$DEST")" != "SQLite format 3" ]; then
  echo "BACKUP FAILED: what came off the device is not a SQLite database"
  rm -f "$DEST" "$DEST-wal"
  exit 1
fi

python3 - "$DEST" <<'PY'
import sqlite3, sys, os
dest = sys.argv[1]
con = sqlite3.connect(dest)
# Opening with the -wal present replays it; TRUNCATE folds it into the db file
# so the backup is one self-contained file.
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
print(f"backup: {dest}")
if sum(v for v in counts.values() if isinstance(v, int)) == 0:
    print("note: the database is empty. Nothing had been logged.")
PY
