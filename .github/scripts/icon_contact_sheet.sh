#!/usr/bin/env bash
# Renders build/reports/icon-sheet/icons.png for the SVGs a pull request changes, with each icon's
# version from BASE_SHA as the "before" row. Prints nothing and renders nothing when no SVG changed.
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

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  count=$(wc -l < "$work/changed.txt")
  {
    echo "### Changed icons"
    echo
    echo "${count// /} SVG file(s) changed. Download the **icon-contact-sheet** artifact to compare them before and after at 100%, 150% and 200% on both themes."
  } >> "$GITHUB_STEP_SUMMARY"
fi
