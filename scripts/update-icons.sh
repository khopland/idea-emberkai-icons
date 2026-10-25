#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: update-icons.sh [--ref REF] [--check]

Refresh bundled icons, associations, license, and NOTICE from vscode-symbols.
Local configuration and artwork in src/main/resources/emberkai/ are preserved.
Requires Bash 3.2+, Git, jq 1.6+, and standard Unix tools.

  --ref REF  Fetch this upstream branch, tag, or commit (default: main).
  --check    Write nothing; exit 1 if the bundled files need updating.
  --help     Show this help.
EOF
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

ref=main
check=false
while (($#)); do
    case "$1" in
        --ref)
            (($# >= 2)) && [[ -n "$2" && "$2" != -* ]] || fail '--ref requires a branch, tag, or commit.'
            ref=$2
            shift 2
            ;;
        --check) check=true; shift ;;
        --help|-h) usage; exit 0 ;;
        *) fail "Unknown argument: $1 (see --help)." ;;
    esac
done

for command in git jq awk find diff cp mkdir mktemp rm; do
    command -v "$command" >/dev/null 2>&1 || fail "Required command not found: $command"
done

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/emberkai-icons.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT
export LC_ALL=C
export GIT_TERMINAL_PROMPT=0

upstream="$work_dir/upstream"
staged="$work_dir/staged"
mkdir -p "$staged/symbols/icons"
printf 'Fetching vscode-symbols (%s)...\n' "$ref"
git init --quiet "$upstream"
git -C "$upstream" fetch --quiet --depth 1 --no-tags \
    https://github.com/miguelsolorio/vscode-symbols.git "$ref"
git -C "$upstream" -c advice.detachedHead=false checkout --quiet --detach FETCH_HEAD
commit=$(git -C "$upstream" rev-parse HEAD)

theme="$upstream/src/symbol-icon-theme.json"
[[ -s "$theme" && -s "$upstream/LICENSE" ]] || fail 'Upstream theme or license is missing.'

# Convert the associations understood by our provider. VS Code language IDs and
# expanded/root-folder variants have no equivalent in this provider.
{
    printf '# Icon paths are relative to symbols/icons/.\n'
    printf '# Java and Kotlin files use the IDE icons.\n'
    printf '# Source: https://github.com/miguelsolorio/vscode-symbols/tree/%s\n' "$commit"
    jq -er '
        def associations($prefix):
            to_entries | map({key: ($prefix + (.key | ascii_downcase)), value});
        def property_key:
            if test("[[:cntrl:]]") then error("Control character in association")
            else gsub("\\\\"; "\\\\") | gsub("(?<special>[ :=#!])"; "\\\(.special)") end;
        if ([.iconDefinitions, .fileNames, .fileExtensions, .folderNames]
                | all(type == "object" and length > 0)) and (.folder | type == "string")
        then . else error("Unexpected upstream theme structure") end
        | . as $theme
        | ([{key: "folder", value: .folder}]
            + (.fileNames | associations("file."))
            + (.fileExtensions | associations("extension."))
            + (.folderNames | associations("folder.")))
        | sort_by(.key) | group_by(.key)[]
        | if (map(.value) | unique | length) == 1 then .[0]
          else error("Conflicting case-insensitive association: " + .[0].key) end
        | . as $entry
        | $theme.iconDefinitions[$entry.value].iconPath as $path
        | if ($path | type) != "string" then error("Missing icon for " + $entry.key)
          elif ($path | test("^\\./icons/(files|folders)/[A-Za-z0-9._+-]+\\.svg$"))
          then ($entry.key | property_key) + "=" + ($path | ltrimstr("./icons/"))
          else error("Unexpected icon path: " + $path) end
    ' "$theme"
} > "$staged/symbols/icons.properties"

# Change only the outer SVG size, retaining viewBox and all drawing coordinates.
# Read the whole file so multiline root elements work too.
while IFS= read -r -d '' svg; do
    relative=${svg#"$upstream/src/icons/"}
    [[ "$relative" =~ ^(files|folders)/[A-Za-z0-9._+-]+\.svg$ ]] || fail "Unexpected SVG path: $relative"
    mkdir -p "$staged/symbols/icons/$(dirname -- "$relative")"
    awk '
        { content = content $0 ORS }
        END {
            if (!match(content, /<svg([[:space:]][^>]*)?>/)) exit 1
            before = substr(content, 1, RSTART - 1)
            root = substr(content, RSTART, RLENGTH)
            after = substr(content, RSTART + RLENGTH)
            if (root !~ /[[:space:]]viewBox[[:space:]]*=/) {
                # Some upstream icons rely on their original pixel dimensions.
                # Supply the equivalent viewBox before changing those dimensions.
                if (!match(root, /[[:space:]]width="[0-9.]+(px)?"/)) exit 1
                width = substr(root, RSTART, RLENGTH)
                gsub(/[^0-9.]/, "", width)
                if (!match(root, /[[:space:]]height="[0-9.]+(px)?"/)) exit 1
                height = substr(root, RSTART, RLENGTH)
                gsub(/[^0-9.]/, "", height)
                if (width + 0 <= 0 || height + 0 <= 0) exit 1
                sub(/<svg/, "<svg viewBox=\"0 0 " width " " height "\"", root)
            }
            gsub(/[[:space:]](width|height)[[:space:]]*=[[:space:]]*("[^"]*"|\047[^\047]*\047)/, "", root)
            sub(/<svg/, "<svg width=\"16\" height=\"16\"", root)
            printf "%s%s%s", before, root, after
        }
    ' "$svg" > "$staged/symbols/icons/$relative" || fail "Cannot size SVG: $relative"
done < <(find "$upstream/src/icons" -type f -name '*.svg' -print0)

while IFS= read -r mapping; do
    [[ "$mapping" == \#* ]] && continue
    icon=${mapping##*=}
    [[ -s "$staged/symbols/icons/$icon" ]] || fail "Missing SVG for mapping: $mapping"
done < "$staged/symbols/icons.properties"

cp "$upstream/LICENSE" "$staged/LICENSE"
awk -v commit="$commit" '
    /^Icons: vscode-symbols / {
        $0 = "Icons: vscode-symbols by Miguel Solorio (commit " commit ")"
        updated++
    }
    { print }
    END { if (updated != 1) exit 1 }
' "$root/NOTICE" > "$staged/NOTICE" || fail 'Expected one vscode-symbols attribution in NOTICE.'

target="$root/src/main/resources/symbols"
if diff -qr "$staged/symbols/icons" "$target/icons" >/dev/null \
        && diff -q "$staged/symbols/icons.properties" "$target/icons.properties" >/dev/null \
        && diff -q "$staged/LICENSE" "$root/licenses/vscode-symbols-LICENSE.txt" >/dev/null \
        && diff -q "$staged/NOTICE" "$root/NOTICE" >/dev/null; then
    printf 'Icons are up to date at %s.\n' "$commit"
    exit 0
fi

if "$check"; then
    printf 'Bundled icons differ from vscode-symbols at %s.\n' "$commit" >&2
    printf 'Run scripts/update-icons.sh --ref %s to update them.\n' "$commit" >&2
    exit 1
fi

# Network, JSON, SVG, and mapping validation have all completed before writes.
# Replace the icon directory so deleted upstream icons do not linger.
# Local configuration and artwork live separately in resources/emberkai/.
rm -rf -- "$target/icons"
cp -R "$staged/symbols/." "$target/"
cp "$staged/LICENSE" "$root/licenses/vscode-symbols-LICENSE.txt"
cp "$staged/NOTICE" "$root/NOTICE"
printf 'Updated icons and associations to %s.\n' "$commit"
