# Changelog

## [Unreleased]

- Added semantic Project view tooltips that describe what Emberkai recognized, such as a Kubernetes manifest, Flyway migration, environment variant, or module accent. Screen readers announce the same text, and a setting turns them off.
- Added **Emberkai:** scopes for database migrations, infrastructure, Kubernetes and GitOps, API contracts, CI pipelines, Spring configuration, and production config, usable in Find in Files, the Project view, inspections, and File Colors.
- Added Kubernetes kind badges for Deployments, StatefulSets, DaemonSets, Services, Ingresses, ConfigMaps, Secrets, Jobs, CronJobs, CustomResourceDefinitions, PersistentVolumeClaims, and Namespaces, plus a bundle badge for multi-document files that mix kinds.
- Added unignored-secret warning badges: `.env` files, private keys, keystores, and similar credentials inside a VCS root that are not ignored get a red stop-sign badge, with a setting to turn it off.
- Added a ring-and-dot live marker on Spring profile files activated by the selected run configuration, read from program arguments, VM options, `SPRING_PROFILES_ACTIVE`, Gradle `bootRun`, and the Spring Boot configuration's active profiles, with its own setting, an Explain This Icon note, and Project view tooltip text.

## [0.3.0] - 2026-10-28

- Added a **Settings > Appearance & Behavior > Emberkai Icons** page with a live project-tree preview: toggle native Java/Kotlin icons, service-folder colors and palette, and each context-aware icon category independently.
- Added accessibility folder modes: a colorblind-friendly palette, shape-coded high-contrast markers, monochrome service folders, and reduced folder color.
- Added **View > Emberkai Icons** quick toggles for folder colors, module-aware accents, accessibility modes, and JVM architecture folders.
- Added **Explain This Icon** to the Project view context menu, showing the light/dark preview, winning mapping key and source, matching context rule, and any native-icon or project-role precedence.
- Added a rule-aware icon coverage audit (**Tools > Audit Project Icon Coverage**, or **Audit Icon Coverage** on a directory) that groups generic and unmapped files and suggests `icons.properties` mappings, plus a `scripts/audit-icon-coverage.py` corpus audit.
- Added opt-in JVM architecture folder icons for `controller`, `service`, `repository`, `domain`, `dto`, `mapper`, `config`, `security`, `client`, `exception`, and `util` packages.
- Added environment variant badges for production, staging, test, and local Spring profiles, Docker Compose files, Helm values, and `.env` files, each with a distinct shape and color.
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

[Unreleased]: https://github.com/khopland/idea-emberkai-icons/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/khopland/idea-emberkai-icons/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/khopland/idea-emberkai-icons/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/khopland/idea-emberkai-icons/releases/tag/v0.1.0
