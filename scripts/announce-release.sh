#!/usr/bin/env bash
#
# Announces a release to a group chat.
#
# Reads the notes from the GitHub release itself rather than taking them as an
# argument, so the message and the release cannot drift apart: whatever is
# published is what gets announced.
#
# The webhook lives in scripts/announce.conf, which is gitignored. A chat
# webhook is a password — anyone holding it can post to the group as this app
# — so it never goes in the repository, and never in a commit message.
#
#   scripts/announce-release.sh                 # the newest release
#   scripts/announce-release.sh v0.0.3          # a specific one
#   scripts/announce-release.sh --dry-run       # print, send nothing
#   scripts/announce-release.sh --chat-id       # Telegram: find the group id
#
set -euo pipefail

REPO="Manos-G/Healthy-android-app"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF="$HERE/announce.conf"

DRY_RUN=0
CHAT_ID_ONLY=0
TAG=""
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=1 ;;
        --chat-id) CHAT_ID_ONLY=1 ;;
        -*) echo "unknown option: $arg" >&2; exit 2 ;;
        *) TAG="$arg" ;;
    esac
done

# Finding a Telegram group id is the one fiddly step of the setup, so it has a
# command of its own rather than a paragraph of instructions.
if [ "$CHAT_ID_ONLY" -eq 1 ]; then
    [ -f "$CONF" ] || { echo "Put TOKEN=... in $CONF first." >&2; exit 1; }
    # shellcheck disable=SC1090
    . "$CONF"
    [ -n "${TOKEN:-}" ] || { echo "No TOKEN in $CONF." >&2; exit 1; }
    updates=$(curl -fsS "https://api.telegram.org/bot$TOKEN/getUpdates")
    found=$(printf '%s' "$updates" | jq -r '
        [.result[]?.message.chat // .result[]?.my_chat_member.chat]
        | unique_by(.id)
        | .[]
        | "\(.id)\t\(.type)\t\(.title // .username // "—")"')
    if [ -z "$found" ]; then
        cat >&2 <<'MSG'
Telegram has nothing to report yet. Bots cannot see a group's history, and by
default they only receive messages that mention them. So:

  1. Add the bot to the group.
  2. Send "/start@YourBotName" in the group.
  3. Run this again.

A group id is negative. A supergroup id starts with -100.
MSG
        exit 1
    fi
    printf 'id\ttype\tname\n%s\n' "$found"
    echo
    echo "Put the group's id in $CONF as CHAT_ID=..."
    exit 0
fi

command -v gh >/dev/null || { echo "gh is not installed." >&2; exit 1; }

if [ -z "$TAG" ]; then
    TAG=$(gh release list --repo "$REPO" --limit 1 --json tagName -q '.[0].tagName')
    [ -n "$TAG" ] || { echo "no releases found." >&2; exit 1; }
fi

TITLE=$(gh release view "$TAG" --repo "$REPO" --json name -q '.name')
NOTES=$(gh release view "$TAG" --repo "$REPO" --json body -q '.body')
URL=$(gh release view "$TAG" --repo "$REPO" --json url -q '.url')
APK=$(gh release view "$TAG" --repo "$REPO" --json assets -q '.assets[0].name // "no APK attached"')

# Chat clients cut long messages off mid-word, so the changelog is trimmed at a
# line boundary and the link carries the rest.
LIMIT=1400
if [ "${#NOTES}" -gt "$LIMIT" ]; then
    NOTES="$(printf '%s' "$NOTES" | head -c "$LIMIT" | sed '$d')
…"
fi

MESSAGE="**$TITLE**

$NOTES

$APK · $URL"

if [ "$DRY_RUN" -eq 1 ]; then
    printf '%s\n' "--- would post ---"
    printf '%s\n' "$MESSAGE"
    printf '%s\n' "--- ${#MESSAGE} characters ---"
    exit 0
fi

if [ ! -f "$CONF" ]; then
    cat >&2 <<MSG
No $CONF.

Create it with one of these, then run this again:

  # Discord: Server Settings, Integrations, Webhooks, New Webhook, Copy URL
  PLATFORM=discord
  WEBHOOK=https://discord.com/api/webhooks/...

  # Slack: api.slack.com/apps, Incoming Webhooks, Add New Webhook
  PLATFORM=slack
  WEBHOOK=https://hooks.slack.com/services/...

  # Telegram: talk to @BotFather, add the bot to the group, then get the
  # chat id from https://api.telegram.org/bot<TOKEN>/getUpdates
  PLATFORM=telegram
  TOKEN=123456:ABC...
  CHAT_ID=-1001234567890
MSG
    exit 1
fi

# shellcheck disable=SC1090
. "$CONF"

# jq builds the JSON so a quotation mark or a backslash in the notes cannot
# break the payload, which hand-rolled escaping would eventually do.
case "${PLATFORM:-}" in
    discord)
        payload=$(jq -n --arg c "$MESSAGE" '{content: $c}')
        curl -fsS -X POST -H 'Content-Type: application/json' -d "$payload" "$WEBHOOK" >/dev/null
        ;;
    slack)
        payload=$(jq -n --arg t "$MESSAGE" '{text: $t}')
        curl -fsS -X POST -H 'Content-Type: application/json' -d "$payload" "$WEBHOOK" >/dev/null
        ;;
    telegram)
        # HTML, not Markdown. Telegram's legacy Markdown rejects the whole
        # message over a stray underscore, backtick or unbalanced asterisk,
        # and changelogs are full of all three. HTML needs only three
        # characters escaped, and they are escaped before any tag is added so
        # the tags themselves survive.
        html=$(printf '%s' "$MESSAGE" \
            | sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g' \
            | sed -e 's/\*\*\([^*]*\)\*\*/<b>\1<\/b>/g' \
            | sed -e 's/^#\{1,6\} *//')
        payload=$(jq -n --arg id "$CHAT_ID" --arg t "$html" \
            '{chat_id: $id, text: $t, parse_mode: "HTML", disable_web_page_preview: true}')
        response=$(curl -fsS -X POST -H 'Content-Type: application/json' -d "$payload" \
            "https://api.telegram.org/bot$TOKEN/sendMessage") || {
            echo "Telegram refused the message. Common causes: the bot is not in" >&2
            echo "the group, or CHAT_ID is wrong — a group id is negative, and a" >&2
            echo "supergroup id starts -100." >&2
            exit 1
        }
        printf '%s' "$response" | jq -e '.ok' >/dev/null
        ;;
    *)
        echo "Set PLATFORM to discord, slack or telegram in $CONF." >&2
        exit 1
        ;;
esac

echo "announced $TAG to $PLATFORM"
