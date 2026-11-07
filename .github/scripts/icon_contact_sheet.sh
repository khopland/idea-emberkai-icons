#!/usr/bin/env bash
# Renders build/reports/icon-sheet/icons.png for the SVGs a pull request changes, with each icon's
# version from BASE (the merge commit's base parent by default) as the "before" row, and outputs how
# many SVGs changed. Renders nothing when none did. Locally, BASE=main compares with where the branch
# left main, so later commits on main do not show up as changes.
set -euo pipefail

if [[ -n "${BASE:-}" ]]; then
  base=$(git merge-base "$BASE" HEAD)
else
  base="HEAD^1"
fi
work="${RUNNER_TEMP:-$(mktemp -d)}/icon-sheet"
rm -rf "$work"
mkdir -p "$work/base"

# Renames count as a removal and an addition, so both versions are drawn. -z keeps non-ASCII paths unquoted.
git diff -z --no-renames --name-only "$base" HEAD -- 'src/main/resources/*.svg' | tr '\0' '\n' > "$work/changed.txt"
if [[ ! -s "$work/changed.txt" ]]; then
  echo "No icons changed."
  exit 0
fi

while IFS= read -r path; do
  # Dark and light variants are shown together, so fetch both base versions.
  icon="${path%.svg}"
  icon="${icon%_dark}"
  for candidate in "$icon.svg" "${icon}_dark.svg"; do
    if git cat-file -e "$base:$candidate" 2>/dev/null; then
      mkdir -p "$work/base/$(dirname -- "$candidate")"
      git show "$base:$candidate" > "$work/base/$candidate"
    fi
  done
done < "$work/changed.txt"

./gradlew test --tests '*IconContactSheet' -PiconSheet="$work/changed.txt" -PiconSheetBase="$work/base" --console=plain

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  count=$(wc -l < "$work/changed.txt")
  echo "changed=${count// /}" >> "$GITHUB_OUTPUT"
fi
