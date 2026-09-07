#!/usr/bin/env bash
# Back up first, then run the on-device tests.
#
# ./gradlew connectedDebugAndroidTest UNINSTALLS the app when it finishes,
# which deletes the database and every day the user has logged. Always take a
# backup first. Never run the gradle task directly once there is real data.
set -euo pipefail

cd "$(dirname "$0")/.."
./scripts/backup-db.sh
echo
echo "Running device tests. This will uninstall the app and wipe its data."
./gradlew "${@:-connectedDebugAndroidTest}"
