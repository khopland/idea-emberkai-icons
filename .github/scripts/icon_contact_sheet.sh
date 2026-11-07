#!/usr/bin/env bash
# Renders build/reports/icon-sheet/icons.png for the SVGs a pull request changes, with each icon's
# version from BASE_SHA as the "before" row, and outputs how many SVGs changed. Renders nothing when none did.
set -euo pipefail

: "${BASE_SHA:?set BASE_SHA to the base commit of the pull request}"
work="${RUNNER_TEMP:-$(mktemp -d)}/icon-sheet"
rm -rf "$work"
mkdir -p "$work/base"

git fetch --quiet --depth=1 --no-tags origin "$BASE_SHA"
git diff --name-only "$BASE_SHA" HEAD -- 'src/main/resources/*.svg' > "$work/changed.txt"
if [[ ! -s "$work/changed.txt" ]]; then
  echo "No icons changed."
  exit 0
fi

while IFS= read -r path; do
  # Dark and light variants are shown together, so fetch both base versions.
  icon="${path%.svg}"
  icon="${icon%_dark}"
  for candidate in "$icon.svg" "${icon}_dark.svg"; do
    if git cat-file -e "$BASE_SHA:$candidate" 2>/dev/null; then
      mkdir -p "$work/base/$(dirname -- "$candidate")"
      git show "$BASE_SHA:$candidate" > "$work/base/$candidate"
    fi
  done
done < "$work/changed.txt"

./gradlew test --tests '*IconContactSheet' -PiconSheet="$work/changed.txt" -PiconSheetBase="$work/base" --console=plain

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  count=$(wc -l < "$work/changed.txt")
  echo "changed=${count// /}" >> "$GITHUB_OUTPUT"
fi
