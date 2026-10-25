<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Changelog

## [Unreleased]

## [0.3.0] - 2026-10-25

- Standalone repository with one Java icon provider and a plain icon mapping file.
- Removed Deno, generated Kotlin code, the icon submodule, and unused template tooling.
- Icons are checked in and can be edited directly.
- Preserved native Java/Kotlin icons and the existing Symbols mappings.
- Included the project MIT license and the original icon license.

## [0.2.0-native-jvm.1] - 2026-10-25

### Changed

- Preserve native IntelliJ icons for Java sources (`.java`), compiled classes (`.class`), Kotlin sources (`.kt`), and Kotlin scripts (`.kts`), including declarations inside these files.
- Keep Symbols icons for other files and folders.
- Give the fork a separate plugin identity and distribute installable ZIPs through GitHub Releases.

## [0.2.0]

### Fixed

- Folder color icons now render correctly for named folders (`src`, `test`, `tests`, `docs`, `components`, `pages`, `styles`, `scripts`, `types`, `dist`, `.github`, `.git`, `node_modules`, and 140+ more). The generator was reading icon definition keys instead of `folderNames` from the theme, so color-coded folders were silently ignored.
- Fixed `biome.svg` and `swc.svg` rendering at 48px instead of 16px due to missing `width`/`height` attributes on their root `<svg>` element.
- Icon generator (`generate.ts`) now injects `width="16" height="16"` on any SVG that lacks those attributes entirely, not only SVGs that already have them with a different value.

## [0.1.2] - 2026-04-08

For stable releases, please refer to [CHANGELOG.md](https://github.com/joangarciaa-dev/jetbrains-symbols/blob/main/CHANGELOG.md) for details.

## [0.1.1] - 2026-04-08

### Fixed

- Updated platform target to IntelliJ 2026.1 for full compatibility.
- Lowered `since-build` to 241 (IntelliJ 2024.1+) for broader IDE support.

## [0.1.0] - 2026-04-07

### Added

- Initial public release of Symbols Icons for JetBrains.
- File and folder icon provider with generated mappings from `vscode-symbols`.
- Automated icon generation pipeline using `deno task build`.
- Icon-provider integration tests covering filename, extension, folder, and fallback behavior.

### Changed

- Normalized generated SVG dimensions for JetBrains tree rendering.
- CI release guards to fail when generated icon assets are out of sync.

[Unreleased]: https://github.com/khopland/jetbrains-symbols/compare/0.3.0...HEAD
[0.3.0]: https://github.com/khopland/jetbrains-symbols/compare/0.2.0-native-jvm.1...0.3.0
[0.2.0-native-jvm.1]: https://github.com/khopland/jetbrains-symbols/releases/tag/0.2.0-native-jvm.1
[0.2.0]: https://github.com/joangarciaa-dev/jetbrains-symbols/compare/0.1.2...0.2.0
[0.1.2]: https://github.com/joangarciaa-dev/jetbrains-symbols/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/joangarciaa-dev/jetbrains-symbols/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/joangarciaa-dev/jetbrains-symbols/commits/0.1.0
