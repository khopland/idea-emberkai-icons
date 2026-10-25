# Changelog

## [Unreleased]

- Added custom Java type icons with light/dark variants for classes, interfaces, enums, records, annotations, exceptions, and anonymous classes, with IntelliJ-style circles and broken-circle abstract variants.
- Added custom Kotlin type icons with a purple Kotlin corner badge for classes, abstract classes, interfaces, enum classes, annotation classes, objects, and scripts. Data classes use the ordinary class icon, while Gradle Kotlin DSL files, multi-declaration files, enum entries, and members retain native icons.
- Preserved IntelliJ modifier, visibility, runnable/test, extension, and Kotlin expect badges through optional Java and Kotlin support; upstream icon updates leave custom artwork intact.
- Declared Kotlin K2 compatibility and load the Kotlin provider only when the Kotlin plugin is available.
- Added matching artwork and scoped detection for database migrations, OpenAPI/Swagger contracts, Kubernetes manifests, and Kustomize folders.
- Added SDKMAN, pre-commit, just, and mise configuration icons, plus fixtures, test data, and snapshots.
- Extended source/test/resource/package folder cues to Kotlin Multiplatform layouts and imported root types, retaining generated/excluded root icons.
- Shared cached inspection across Spring and structured metadata, including unsaved editor changes and bounded YAML/JSON parsing.
- Moved local mappings, native-icon preferences, and custom artwork into a separate `emberkai/` resource folder.
- Split icon resolution into focused classes for mappings, native-icon policy, and extensible Helm/Spring context rules.
- Combined Symbols folder outlines with IntelliJ role cues for module/source-set containers, source/test roots, resources, and packages. Actual root markings take precedence over standard JVM layout fallbacks.
- Preserved native icons for IDE metadata, libraries, exclusions, generated roots, and unsupported language roots; `.mvn` uses the Maven feather folder.
- Added Gradle Kotlin DSL and version-catalog icons, plus Gradle convention-build folders.
- Added custom Spring Boot and Helm artwork with build/chart context detection and cached build-file checks.
- Added an Emberkai plugin logo with light and dark variants, pairing geometric symbols with the theme's ember mark.
- Added contextual GitHub Actions and metadata icons, Dev Containers, Renovate variants, and missing Vue, PHP, Clojure, and CoffeeScript associations.
- Added a custom Maven feather for POMs and Maven configuration, and fixed Gradle wrapper and GitLab CI filename variants.
- Registered virtual-file icons for project views, editor tabs, and indexing, while retaining native language-specific icons.
- Refreshed upstream icons and kept custom mappings and artwork separate so updates preserve them.

## [0.1.0] - 2026-10-25

- Renamed the plugin to Emberkai Icons to complement the Emberkai theme.
- Renamed the installable ZIP and CI artifact to `emberkai-icons`.
- Added a Bash updater for upstream SVGs, icon associations, license, and source attribution, with a check mode and manual CI workflow.
- Symbols file and folder icons with native IntelliJ Java and Kotlin icons and modifier badges.
- One Java icon provider, plain icon mappings, and checked-in SVGs.
- Minimal Gradle build with no icon generator or submodules.
- MIT license with original icon notices included.

[Unreleased]: https://github.com/khopland/idea-symbols/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/khopland/idea-symbols/releases/tag/v0.1.0
