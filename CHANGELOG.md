# Changelog

## [Unreleased]

- Added opt-in module-aware folder accents: stable colors derived from IntelliJ module names, inherited accent dots on source roots, packages, and descendants, per-module overrides from the Project view, and a reset action. Overrides are stored in `.idea/emberkai-module-accents.xml`.
- Combined Spring XML configuration with Emberkai's Spring icon and layered Spring Modulith package locks onto Emberkai folder artwork, with a dedicated Spring Modulith setting.
- Added Ruby, Dart/Flutter, Swift, Rust, Nix, Buf, Lefthook, Terraform, and OpenTofu file icon mappings, plus Terraform and Terragrunt folder artwork.
- Added dark-theme variants for custom SVGs with low-contrast neutral outlines or details, and documented the theme policy.

## [0.2.0] - 2026-10-25

- Expanded icon coverage for CI and GitOps tools, Ansible and infrastructure configuration, Spring Boot and Helm, database migrations, API contracts, Kubernetes/Kustomize, Gradle, and common developer tooling.
- Restored native IntelliJ type icons and modifier badges for Java and Kotlin declarations.
- Added context-aware file and folder detection, including build and chart metadata, Kotlin Multiplatform layouts, IntelliJ roots, and structured YAML/JSON. Cached inspections account for unsaved editor changes, and native icons remain intact for IDE metadata and unsupported roots.
- Refined folder icons to combine Symbols artwork with IntelliJ source, test, resource, package, and module cues; clarified precedence for actual roots, generated/excluded folders, Maven, and Helm charts.
- Introduced original Emberkai artwork and a themed plugin logo. Separated custom mappings and assets from upstream icons, with updater and attribution support, alternate artwork, and a design preview.
- Registered file and folder icons across project views, editor tabs, and indexing, with icon resolution organized into focused, extensible rules.

## [0.1.0] - 2026-10-25

- Released the plugin as Emberkai Icons, with matching ZIP and CI artifact names.
- Established the Symbols-based Java/Kotlin icon provider, checked-in SVG assets, and a minimal Gradle build.
- Added upstream icon, license, and attribution updates with a check mode and manual CI workflow; included original icon notices under the MIT license.

[Unreleased]: https://github.com/khopland/idea-symbols/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/khopland/idea-symbols/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/khopland/idea-symbols/releases/tag/v0.1.0
