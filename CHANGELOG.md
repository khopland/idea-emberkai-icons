# Changelog

## [Unreleased]

- Added the **Changed on this branch** lens (**View > Emberkai Icons > Lens**): files that differ from where the branch left the default branch, including uncommitted edits and untracked files, keep full color, and the rest of the Project view is dimmed. The base is `upstream/main`, the branch `origin/HEAD` points at, `origin/main`, or local `main` (or `master`), per Git repository. Git runs in the background only while the lens is active, and the lens follows commits, checkouts, fetches, and edits. Requires the Git plugin.

## [0.5.0] - 2026-10-29

- Added Project view lenses (**View > Emberkai Icons > Lens**): files that answer one question, such as **Production blast radius**, **Infrastructure**, or **API contracts**, keep full color along with the folders that contain them, and everything else is dimmed, including native Java and Kotlin icons. Lenses use the Emberkai scopes, are computed in the background, and are not saved between sessions.
- Added a CODEOWNERS ownership layer: Project view tooltips name each file's and folder's owners, **Explain This Icon** shows the CODEOWNERS line that assigned them, every owner gets an **Emberkai: Owned by …** scope, and **Lens > Owned By** highlights one owner's files. Matching follows GitHub's rules, with a setting to hide owners from tooltips.
- Added running-service markers: while a run or debug session is live, the module folder that owns it gets a green dot-in-ring marker in the top-right corner, amber while debugging. The tooltip names the run configuration and, for Spring Boot, the `server.port` read from the arguments, environment, or active profile files. Module-based configurations and Gradle task runs are mapped to one folder, and ambiguous runs show no marker. The marker has its own setting.
- Added environment folders: children of `overlays/`, `envs/`, `environments/`, `clusters/`, and similar containers named after an environment, such as `overlays/production/` or `clusters/prod-eu/`, get the environment badge, and configuration files inside them inherit it. Inherited production files join the **Production config** scope and the **Production blast radius** lens.
- Added migration version conflict badges: Flyway versioned migrations that share a version with a sibling, such as `V3__a.sql` and `V3_0__b.sql` after a merge, and the folder that holds them get an amber warning badge, with its own setting. Migration folder tooltips list the number of versioned migrations, the latest version, and any conflicts.
- Added OpenTofu icons: `.tofu` files and `.opentofu-version` always use OpenTofu artwork, and Terraform files, lock files, and folders switch to it when the project shows OpenTofu (a version file, `.tofu` files, `registry.opentofu.org` in the lock file, or `opentofu` in `.tool-versions` or mise).
- Added Quarkus, Micronaut, and Ktor configuration icons, detected from build declarations like Spring Boot, with their own setting.
- Added icons for detekt, OpenRewrite, SonarQube, and Jib configuration; AsyncAPI contracts (by name or metadata) and Avro schemas, which join the **API contracts** scope; CircleCI, Tekton, and Bazel; Skaffold, Tilt, Dagger, and Testcontainers; and Packer, Vagrant, and Nomad, which join the **Infrastructure** scope.
- Added an IntelliJ IDEA folder icon for `.idea/`; files and folders inside it keep their native icons.
- Redrew weak or unclear icons as filled, outlined artwork that stays legible at 16 px: SonarQube is now a quality gauge, snapshots a camera, pre-commit a checked commit, Tilt and mise show their initials, JVM `client/` and `mapper/` packages have distinct badges, and Ansible, Tekton, CircleCI, Dagger, Skaffold, OpenRewrite, AsyncAPI, Avro, detekt, Quarkus, Packer, just, migrations, and several folder badges are bolder.
- Fixed Skaffold configuration and Tekton resources showing the generic Kubernetes icon.

## [0.4.0] - 2026-10-29

- Added error reporting through JetBrains Marketplace: exceptions and UI freezes attributed to Emberkai Icons can be submitted from the IDE's error-reporting dialog, or sent automatically when **Send error reports to JetBrains automatically** is enabled.
- Raised the minimum supported IDE version to 2026.1 (build 261), matching the IDE releases that ship the Emberkai theme.
- Added semantic Project view tooltips that describe what Emberkai recognized, such as a Kubernetes manifest, Flyway migration, environment variant, or module accent. Screen readers announce the same text, and a setting turns them off.
- Added **Emberkai:** scopes for database migrations, infrastructure, Kubernetes and GitOps, API contracts, CI pipelines, Spring configuration, and production config, usable in Find in Files, the Project view, inspections, and File Colors.
- Added Kubernetes kind badges for Deployments, StatefulSets, DaemonSets, Services, Ingresses, ConfigMaps, Secrets, Jobs, CronJobs, CustomResourceDefinitions, PersistentVolumeClaims, and Namespaces, plus a bundle badge for multi-document files that mix kinds.
- Added unignored-secret warning badges: `.env` files, private keys, keystores, and similar credentials inside a VCS root that are not ignored get a red stop-sign badge, with a setting to turn it off.
- Added a ring-and-dot live marker on Spring profile files activated by the selected run configuration, read from program arguments, VM options, `SPRING_PROFILES_ACTIVE`, Gradle `bootRun`, and the Spring Boot configuration's active profiles, with its own setting, an Explain This Icon note, and Project view tooltip text.
- Added Spring stereotype badges: controllers, services, repositories, configuration classes, Spring Boot applications, and JPA/MongoDB entities keep their native Java and Kotlin class icons with a small shape-coded corner glyph, read from annotations without resolving references.
- Added project-local icon overrides: files and folders can be remapped from `.idea/emberkai-icons.properties`, with artwork from bundled resources or `.idea/emberkai-icons/` (including `_dark` variants). Edits apply immediately, even before saving. **Explain This Icon** and the coverage audit can create and remove overrides, and **Tools > Browse Bundled Icons** searches every bundled mapping. Overridden files are described as a project icon override in tooltips.
- Added settings filtering, export/import, and reset-to-defaults actions.
- Fixed **Report** in the icon coverage audit, which could open an issue page that did not accept reports. It now opens a **Missing icon** issue form with the file, audit result, current mapping, and suggested mapping filled in.
- Fixed the dark preview in **Explain This Icon** for files and folders with environment, secret, live-profile, module-accent, or Spring Modulith badges, which showed the light artwork.

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

[Unreleased]: https://github.com/khopland/idea-emberkai-icons/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/khopland/idea-emberkai-icons/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/khopland/idea-emberkai-icons/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/khopland/idea-emberkai-icons/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/khopland/idea-emberkai-icons/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/khopland/idea-emberkai-icons/releases/tag/v0.1.0
