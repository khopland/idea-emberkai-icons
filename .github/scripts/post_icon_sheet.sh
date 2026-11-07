#!/usr/bin/env bash
# Posts or updates one comment on the pull request whose Build run rendered SHEET, showing the before/after
# icon contact sheet. The PNG is stored on the icon-previews branch at pr-<number>/<head sha>.png, since a
# comment can only show an image that has a URL. The pull request is looked up from HEAD_OWNER:HEAD_BRANCH
# and must still point at HEAD_SHA, so a sheet from an older push never replaces a newer one. When SHEET is
# empty (no icons changed), an existing comment says so instead of showing an outdated sheet. To post a
# sheet rendered locally, run it with GH_TOKEN, RUN_URL and COMMENT_AUTHOR set to your own login.
set -euo pipefail

: "${REPO:?}" "${HEAD_SHA:?}" "${HEAD_OWNER:?}" "${HEAD_BRANCH:?}" "${RUN_URL:?}"
branch=icon-previews
marker='<!-- emberkai-icon-sheet -->'
author=${COMMENT_AUTHOR:-github-actions[bot]}
max_bytes=$((10 * 1024 * 1024))

if [[ ! "$HEAD_SHA" =~ ^[0-9a-f]{40}$ ]]; then
  echo "::error::Unexpected head SHA '$HEAD_SHA'." >&2
  exit 1
fi

pr=$(gh api -X GET "repos/$REPO/pulls" -f state=open -f head="$HEAD_OWNER:$HEAD_BRANCH" \
  --jq '.[] | select(.head.sha == $ENV.HEAD_SHA) | .number' | head -n 1)
if [[ ! "$pr" =~ ^[0-9]+$ ]]; then
  echo "No open pull request points at $HEAD_SHA anymore; a newer run will comment."
  exit 0
fi

comment=$(gh api --paginate "repos/$REPO/issues/$pr/comments" \
  --jq ".[] | select(.user.login == \"$author\" and (.body | contains(\"$marker\"))) | .id" | head -n 1)

post() {
  local body=$1
  if [[ -n "$comment" ]]; then
    gh api -X PATCH "repos/$REPO/issues/comments/$comment" -f body="$body" > /dev/null
  else
    gh api -X POST "repos/$REPO/issues/$pr/comments" -f body="$body" > /dev/null
  fi
}

short=${HEAD_SHA:0:7}
if [[ -z "${SHEET:-}" || ! -f "$SHEET" ]]; then
  if [[ -n "$comment" ]]; then
    post "$marker
### Changed icons

No icons change as of $short."
  fi
  echo "No icon contact sheet for $short."
  exit 0
fi

# The artifact comes from a run of pull request code; store it only if it is a reasonably sized PNG.
size=$(wc -c < "$SHEET" | tr -d ' ')
if [[ "$size" -gt "$max_bytes" || "$(head -c 8 "$SHEET" | od -An -tx1 | tr -d ' \n')" != 89504e470d0a1a0a ]]; then
  echo "::error::The icon contact sheet is not a PNG under $max_bytes bytes." >&2
  exit 1
fi

if ! gh api "repos/$REPO/git/ref/heads/$branch" > /dev/null 2>&1; then
  # An orphan branch, so previews never mix with the plugin's history.
  readme=$(gh api -X POST "repos/$REPO/git/blobs" -f encoding=utf-8 \
    -f content="Icon contact sheets for pull requests, posted by the Icon sheet workflow. Safe to delete." --jq .sha)
  tree=$(jq -n --arg sha "$readme" '{tree: [{path: "README.md", mode: "100644", type: "blob", sha: $sha}]}' \
    | gh api -X POST "repos/$REPO/git/trees" --input - --jq .sha)
  root=$(gh api -X POST "repos/$REPO/git/commits" -f message="Start icon previews" -f tree="$tree" --jq .sha)
  gh api -X POST "repos/$REPO/git/refs" -f ref="refs/heads/$branch" -f sha="$root" > /dev/null
fi

path="pr-$pr/$HEAD_SHA.png"
payload="${RUNNER_TEMP:-/tmp}/icon-sheet-upload.json"
base64 < "$SHEET" | tr -d '\n' | jq -Rn --arg message "Icon sheet for #$pr at $short" --arg branch "$branch" \
  '{message: $message, branch: $branch, content: input}' > "$payload"
# Runs for different pull requests commit to the same branch, so one can move it under another; GitHub
# then answers 409 and the upload is retried on the new branch head.
for attempt in 1 2 3 4 5; do
  # A rerun of the same commit replaces the image, which needs the current blob SHA.
  existing=$(gh api "repos/$REPO/contents/$path?ref=$branch" --jq .sha 2>/dev/null || true)
  upload="$payload"
  if [[ -n "$existing" ]]; then
    upload="$payload.update"
    jq --arg sha "$existing" '. + {sha: $sha}' "$payload" > "$upload"
  fi
  if gh api -X PUT "repos/$REPO/contents/$path" --input "$upload" > /dev/null; then
    break
  fi
  if [[ "$attempt" -eq 5 ]]; then
    echo "::error::Could not store the icon contact sheet on $branch." >&2
    exit 1
  fi
  sleep $((attempt * 3 + RANDOM % 3))
done

post "$marker
### Changed icons

Each changed icon before and after this pull request, at 100%, 150% and 200% on the light and dark Project view backgrounds. Rendered from $short by [Build]($RUN_URL).

![Changed icons before and after](https://raw.githubusercontent.com/$REPO/$branch/$path)"
echo "Posted the icon contact sheet for $short on #$pr."
