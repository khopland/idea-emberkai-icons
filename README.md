# Emberkai Icons

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="src/main/resources/META-INF/pluginIcon_dark.svg">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Emberkai Icons: four colorful symbols including an ember" width="80" height="80">
</picture>

File and folder icons for JetBrains IDEs, named to complement the Emberkai theme. Built on the Symbols icon set, with native IntelliJ type icons for Java and Kotlin.

Java and Kotlin declarations, source files, and compiled Java classes keep their native IntelliJ icons and modifier badges. Gradle Kotlin build scripts (`*.gradle.kts`) use Gradle icons; other files and folders use Symbols icons.

IntelliJ supplies visibility, static/final, runnable/test markers, read status, and registered icon layers. Nested types keep their own icons. Specialized framework providers, including live test status, take priority; methods, fields, type parameters, and package/module descriptors retain their native presentation. Java source-root integration loads only when the IDE's Java plugin is available; Kotlin's native icons come from the Kotlin plugin. Other files and folders use Symbols icons.

Includes GitHub Actions workflows and repository templates, AI assistant instructions, Azure Pipelines, Bitbucket Pipelines, Dev Containers, Renovate, and Maven icons from Symbols for `pom.xml`, `.pom`, wrapper files, and `.mvn/` configuration. Azure and Bitbucket CI YAML is recognized by its conventional filename; unrelated YAML files keep their usual icons. Azure Pipelines and Bitbucket folders use matching artwork.

Open **Settings > Appearance & Behavior > Emberkai Icons** to choose whether Java/Kotlin files use IntelliJ's native icons, color service folders containing a Gradle, Maven, or Node package manifest, select a service-folder palette, use module-aware accent colors, and enable or disable context-aware icon categories. The live project-tree preview follows unsaved settings. Accessibility options include shape-coded high-contrast markers, monochrome service folders, and reduced folder color. Known production, staging, test, and local variants of Spring profiles, Docker Compose files, Helm values, and `.env` files receive shape-and-color badges; disable **Environment variant badges** independently of their base icons. Likely secrets that are inside a VCS root but not ignored get a red stop-sign badge with an exclamation mark; disable **Unignored secret warning badges** to hide it. Spring profile files that the selected run configuration activates get a ring-and-dot live marker in the top-right corner; disable **Active Spring profile markers** to hide it. The **View > Emberkai Icons** menu also has quick toggles for folder colors, module-aware accents, accessibility modes, and JVM architecture folders. Standard filename and extension mappings remain available when a context category is disabled. Applying settings refreshes project trees and open editor tabs; other IDE surfaces may need reopening to request updated icons.

The unignored-secret badge covers `.env` and `.env.*` (except `.env.example`, `.env.sample`, `.env.template`, `.env.dist`, `.env.defaults`, and variants such as `.env.local.example`), `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.keystore`, `id_rsa`, `id_ed25519`, `id_ecdsa`, `id_dsa` (not `.pub`), `.netrc`, `.pypirc`, `credentials.json`, and `service-account*.json`. `.npmrc` and `secrets.yaml` are left out because they are often committed on purpose without credentials or encrypted. Tracked and untracked-but-not-ignored files both count. Files outside a VCS root, and files whose ignore status the IDE has not computed yet, keep their normal icons. The badge reads IntelliJ's cached change-list state, so icon lookups never run VCS commands; icons refresh after VCS status updates, such as a `.gitignore` edit. The badge is drawn in black when high-contrast or monochrome folders are enabled, and **Explain This Icon** says why it appears. It needs the IDE's VCS support; without it, no badge is shown.

Right-click a module folder and choose **Assign Module Accent Color…** to pin its accent, or pick **Automatic** to return to the module-name default. **Tools > Reset All Module Accent Colors** clears every override. Pinned colors are stored with the project in `.idea/emberkai-module-accents.xml`.

In the Project view, right-click a file or folder and choose **Explain This Icon** to see its light/dark preview, winning mapping key and source, matching context rule, and whether native icon policy or a project role took precedence. Copy the mapping key or open the artwork from the inspector.

Hovering a file or folder in the Project view shows what Emberkai recognized, such as *Kubernetes manifest*, *Flyway versioned migration*, *Helm chart values · production*, or *Test source root · module accent: payments*. Only context rules, project roles, environment variants, module accents, and a few notable path mappings are described; plain extension matches add nothing, and existing IDE tooltips are kept with the description appended. Swing exposes a tree node's tooltip as its accessible description, so screen readers announce the same text. Turn it off with **Describe recognized files and folders in Project view tooltips**. Labels live in `IconDescriptions`, keyed by mapping key.

Run **Tools > Audit Project Icon Coverage** or right-click a directory in the Project view and choose **Audit Icon Coverage**. The cancellable audit uses the same resolver as the icon provider, groups generic and unmapped files by extension and parent folder, and lets you open a finding, copy a suggested `icons.properties` mapping, or open a prefilled missing-icon report.

Emberkai also adds read-only scopes for **Find in Files**, the Project view scope selector, inspections, and **File Colors**: **Emberkai: Database migrations**, **Infrastructure** (including Ansible and Terraform), **Kubernetes & GitOps** (including Helm, Flux, and Argo CD), **API contracts**, **CI pipelines**, **Spring configuration**, and **Production config** (files that would carry the production environment badge). Membership uses the icon resolver's context rules in the same priority order, so each file belongs to at most one tool scope. Scopes describe what a file is, so they ignore the icon category toggles. Only files are members; folders appear when they contain matching files. Spring XML DOM detection needs indexes and is not part of the Spring scope.

The **JVM architecture folders** context category is opt-in. When enabled, Java and Kotlin source packages named `controller`, `service`, `repository`, `domain`, `dto`, `mapper`, `config`, `security`, `client`, `exception`, or `util` get distinct folder icons. `config` and `client` folders are shown only when a few immediate Java/Kotlin files indicate configuration or client code; checks are bounded and cached, including unsaved edits.

Folder icons use the Symbols shape with IntelliJ-style role cues: blue corner squares for source-set/module containers, blue source folders, green test-source folders, yellow resource lines, and package cubes. With module-aware accents enabled, every IntelliJ module gets a stable palette color derived from its name: service roots use the full color, while source roots, packages, and their descendants carry a small module-color dot. Manual overrides and the reset action live in the Project and Tools menus. Java and Kotlin roots share the same source-folder style. IntelliJ's root model selects the role, including for custom directory names and Kotlin Multiplatform root types. Before import, `src/main/`, `src/test/`, and Multiplatform source sets such as `commonMain`, `commonTest`, `iosMain`, `jvmTest`, and `wasmJsMain` use the same cues for their `java/`, `kotlin/`, and `resources/` folders. Actual root markings take precedence over layout guesses. `.idea/`, library, excluded, generated, and unsupported language roots retain their specialized native icons. `.mvn/` uses the Symbols Maven folder icon, and other tool folders keep their Symbols artwork.

- Gradle icons cover Kotlin DSL scripts, `*.versions.toml` catalogs, and the `buildSrc/` and `build-logic/` folders. Plain `build.kts` remains an ordinary Kotlin script.
- Spring icons identify `application*` and `bootstrap*` YAML/properties files when an ancestor Maven/Gradle build file or `gradle/*.versions.toml` declares `org.springframework.boot`. Spring XML DOM descriptions also use Emberkai's Spring artwork. When the Spring Modulith plugin is installed, its package lock semantics are painted onto Emberkai folder icons, with a setting to turn the locks off. Small build files are checked using cached contents and modification stamps, including open editor changes; no dependency resolution or build commands are run. Active-profile markers read the selected run configuration's `--spring.profiles.active=` program argument, `-Dspring.profiles.active=` VM option, or `SPRING_PROFILES_ACTIVE` environment variable, in Spring's precedence order, and from `--args='...'` or the environment of Gradle `bootRun` configurations. The Ultimate Spring Boot configuration's **Active profiles** field is read when its internal accessor is available. The set is recomputed when the selection or configuration changes; other configuration types show no marker, and the marker needs the Java plugin.
- AI instruction icons cover `AGENTS.md`, `GEMINI.md`, `.windsurfrules`, `.cursor/rules/*.mdc`, `.windsurf/rules/*`, and GitHub Copilot instruction files. Claude's `CLAUDE.md` keeps its upstream Claude icon. GitHub issue and pull request templates use GitHub artwork.
- Infrastructure icons cover Azure Developer CLI's `azure.yaml`, AWS SAM `samconfig.toml`, AWS CDK `cdk.json`/`cdk.context.json`, CloudFormation/SAM templates identified by AWS template markers, and Terraform/OpenTofu files such as `.terraform-version`, `.terraform.lock.hcl`, `terraform.rc`, `*.tofu`, and `terragrunt.hcl`. Terraform and Terragrunt folders use a composed folder icon. Ansible configuration, inventories, and playbooks get Ansible artwork; playbooks are recognized by their `hosts` and task/role structure.
- Helm icons identify `Chart.yaml`, neighboring values files and `Chart.lock`, and YAML/helper templates under `templates/` or `crds/` in a chart. Chart roots and their `templates/`, `charts/`, and `crds/` folders get a Helm folder icon. A `Chart.yaml` file supplies chart context; ordinary YAML and template folders elsewhere keep their usual icons.
- Database migration icons cover Flyway versioned/repeatable SQL such as `V1__create_tables.sql` and `R__refresh_views.sql`, `flyway.conf`/`flyway.toml`, and Liquibase `db.changelog*` files in XML, YAML, JSON, or SQL plus `liquibase.properties`. They also cover `migration/` or `migrations/` folders. Other SQL files keep the database icon.
- API contract icons cover `openapi.yaml`/`.yml`/`.json`, `swagger.yaml`/`.yml`/`.json`, and YAML/JSON with top-level OpenAPI 3.x or Swagger 2.0 metadata. `api/`, `openapi/`, `swagger/`, and folders containing a canonical contract filename get an API folder icon.
- Kubernetes YAML/JSON is identified by top-level `apiVersion` and `kind`. Deployments, StatefulSets, DaemonSets, Services, Ingresses, ConfigMaps, Secrets, Jobs, CronJobs, CustomResourceDefinitions, PersistentVolumeClaims, and Namespaces from their standard API groups get kind badges; other kinds keep the generic Kubernetes icon. Multi-document files whose resources share one kind use that kind, and files that mix kinds get a bundle badge. Flux icons identify Flux API resources, `gotk-components.yaml`/`gotk-sync.yaml` under `flux-system/`, and Flux bootstrap folders. Argo CD icons identify `argoproj.io` Application/ApplicationSet/AppProject resources and common `argocd-*.yaml` config files. Kustomize has separate file and folder artwork for `kustomization.yaml`, `kustomization.yml`, or `Kustomization`, with contextual `base/`, `bases/`, `overlays/`, and `components/` folders. `k8s/` and `kubernetes/` get Kubernetes folders. GitHub workflow and Helm chart rules keep priority.
- Developer-tool icons cover `.sdkmanrc`, pre-commit configuration/hook manifests, `justfile`/`.justfile`, mise configuration variants, `.mise/config.toml`, `mise/config.toml`, `mise.lock`, and `lefthook.yml`.
- Ruby manifests (`Gemfile`, `Gemfile.lock`, `Rakefile`) use Ruby artwork; Dart/Flutter manifests (`pubspec.yaml`, `pubspec.lock`, `analysis_options.yaml`) use Dart artwork; Swift package manifests use Swift artwork; Rust toolchain and manifest files use Rust artwork; and `flake.lock` uses Nix artwork while `.nix` files use the upstream Nix mapping.
- Buf configuration files use Protocol Buffers artwork, and `proto/` and `protobuf/` folders use the API folder icon. `Jenkinsfile` uses the upstream Jenkins mapping.
- Test-asset folders include `fixtures/`, `__fixtures__/`, `testdata/`, `snapshots/`, `__snapshots__/`, and `__image_snapshots__/`. `.snap` files have snapshot artwork; other assets keep their file-format icons. Imported source/package roles take precedence over these folder names.

Content detection reads at most 256 KiB and caches results by modification stamp, including unsaved editor changes. Structured metadata checks only top-level YAML/JSON fields; bounded text signatures identify infrastructure templates and Ansible playbooks. Incomplete, unsupported, or oversized metadata keeps the normal format icon; canonical filenames still identify their tools. No builds, dependency resolution, or network calls run during icon lookup.

Custom SVGs use matching `_dark.svg` variants when neutral slate outlines or ink details need more contrast in dark themes. Brand-color artwork stays shared between themes, and IntelliJ selects the dark variant automatically.

## Install

1. Download the ZIP from [Releases](https://github.com/khopland/idea-emberkai-icons/releases/latest).
2. Disable the original Symbols Icons plugin if installed.
3. Open **Settings > Plugins > ⚙️ > Install Plugin from Disk...** and select the ZIP.
4. Restart if prompted.

## Build

Requires Java 21. No icon generator or submodules are needed.

```sh
./gradlew test verifyPluginStructure buildPlugin
```

The ZIP is written to `build/distributions/emberkai-icons-<version>.zip`. Run `./gradlew runIde` to try the plugin in a sandbox.

`SymbolsIconProvider` registers the PSI and virtual-file entry points. `IconResolver` defines matching precedence; `IconMappings` loads resources and matches names, extensions, and parent paths. `ProjectFolderIcons` translates IntelliJ root roles into themed folder icons, and `SourceSetFolders` handles layouts before import. `NativeIconPolicy` preserves native Java/Kotlin icons and specialized folders. Each tool has a focused `ContextIconRule`; `FileContentCache` shares bounded, modification-aware inspection, and `StructuredFileKind` reads YAML/JSON metadata without constructing tagged objects.

Upstream files stay in `src/main/resources/symbols/`. All local configuration and artwork live in `src/main/resources/emberkai/`:

- `icons.properties` contains additional mappings, using absolute classpath paths such as `/symbols/icons/files/github.svg` or `/emberkai/icons/maven.svg`.
- `native.properties` lists native file extensions, tool-script exceptions, folder names, and native folder trees.
- `icons/` contains custom SVG artwork.

Local mappings take precedence over upstream mappings. A key such as `extension..github/workflows/yml` matches YAML files directly inside `.github/workflows/`, wherever the checkout is located. For a given filename or extension, the longest matching parent suffix wins. `folder.role.*` keys choose artwork for IntelliJ root/package roles. Project root roles and native-type policies take precedence over generic filename rules.

To add another tool, implement `ContextIconRule` and register it in `IconResolver.contextRules` in priority order. Rules return keys from `emberkai/icons.properties`, keeping detection logic independent of artwork. `IconResolver.recognizedCategory` feeds the `EmberkaiScopes` scopes; override `recognizesFile` when a tool's canonical filenames are drawn by static mappings but should still join its scope. Add ordinary filename or extension mappings directly to the configuration without changing Kotlin code.

To find mapping gaps across a repository corpus, run `python3 scripts/audit-icon-coverage.py /path/to/repo [another/repo]` (Python 3.10+). It reports files with no static mapping and files mapped to generic format icons, but it does not evaluate content-aware rules. Use **Tools > Audit Project Icon Coverage** in the IDE to audit the icons selected by the real resolver.

## Release

1. Set `pluginVersion` in `gradle.properties` and move the `[Unreleased]` changelog entries under a new version heading. The Marketplace change notes are rendered from that section.
2. Merge to `main`. The draft release workflow creates a draft GitHub release tagged `v<pluginVersion>` with that changelog section as its notes, and refreshes the draft while it is unpublished.
3. Review the draft and publish it.

The release workflow fails if the tag and `pluginVersion` differ. It attaches the ZIP to the GitHub release, then publishes to JetBrains Marketplace through the `marketplace` environment. Versions with a prerelease suffix, such as `1.2.0-beta.1`, go to the matching Marketplace channel. The workflow needs a `PUBLISH_TOKEN` secret (a Marketplace personal access token). Signing is optional and uses `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, and `PRIVATE_KEY_PASSWORD`. The first upload of a new plugin must be made by hand on the Marketplace.

## Update upstream icons

Run the Bash script to fetch the latest icons and associations from [vscode-symbols](https://github.com/miguelsolorio/vscode-symbols):

```sh
./scripts/update-icons.sh
```

Requires Bash 3.2+, Git, jq 1.6+, and standard Unix tools, available on GitHub Actions Ubuntu runners. The default source is upstream `main`; use `--ref` to select a tag or commit:

```sh
./scripts/update-icons.sh --ref 2c95b15b0a8ef95ac62855e3784a6024ee6e5d2e
./scripts/update-icons.sh --ref 2c95b15b0a8ef95ac62855e3784a6024ee6e5d2e --check
```

`--check` writes nothing and exits with status 1 when an update is needed (errors also return a nonzero status). The script works from any directory, requires no credentials, and runs without prompts. It validates the downloaded files before replacing the bundled SVGs and mappings, sizes icons to 16×16 while preserving their view boxes, copies the upstream license, and records the source commit in `NOTICE` and the mapping header. Updating preserves everything under `src/main/resources/emberkai/`, but replaces local edits to the generated files under `symbols/`; review and commit the resulting diff, then run the build checks above.

For CI, run the same command before the Gradle build. The manual **Update icons** GitHub Actions workflow accepts an upstream ref, runs the updater and build checks, and uploads the refreshed sources and plugin ZIP as artifacts. Download the sources into the repository root to review and commit them.

## Credits and license

MIT licensed. Original icons are from [vscode-symbols](https://github.com/miguelsolorio/vscode-symbols) by Miguel Solorio; their original MIT notice is in [licenses](licenses/vscode-symbols-LICENSE.txt). Custom artwork in `emberkai/icons/` — including Ansible, Azure Pipelines, Bitbucket, Argo CD, Flux, and infrastructure symbols — is original Emberkai line art drawn in the Emberkai palette. Folder variants and migration artwork build on the Symbols folder outline and database icon. This project started from [Symbols Icons](https://github.com/joansgarcia/jetbrains-symbols).
