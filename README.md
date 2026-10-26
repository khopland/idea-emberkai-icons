# Emberkai Icons

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="src/main/resources/META-INF/pluginIcon_dark.svg">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Emberkai Icons: four colorful symbols including an ember" width="80" height="80">
</picture>

File and folder icons for JetBrains IDEs, named to complement the Emberkai theme. Built on the Symbols icon set, with native IntelliJ type icons for Java and Kotlin.

Java and Kotlin declarations, source files, and compiled Java classes keep their native IntelliJ icons and modifier badges. Gradle Kotlin build scripts (`*.gradle.kts`) use Gradle icons; other files and folders use Symbols icons.

IntelliJ supplies visibility, static/final, runnable/test markers, read status, and registered icon layers. Nested types keep their own icons. Specialized framework providers, including live test status, take priority; methods, fields, type parameters, and package/module descriptors retain their native presentation. Java source-root integration loads only when the IDE's Java plugin is available; Kotlin's native icons come from the Kotlin plugin. Other files and folders use Symbols icons.

Includes GitHub Actions workflows and repository templates, AI assistant instructions, Azure Pipelines, Bitbucket Pipelines, Dev Containers, Renovate, and Maven icons from Symbols for `pom.xml`, `.pom`, wrapper files, and `.mvn/` configuration. Azure and Bitbucket CI YAML is recognized by its conventional filename; unrelated YAML files keep their usual icons. Azure Pipelines and Bitbucket folders use matching artwork.

Open **Settings > Appearance & Behavior > Emberkai Icons** to choose whether Java/Kotlin files use IntelliJ's native icons, color service folders containing a Gradle, Maven, or Node package manifest, select a service-folder palette, and enable or disable context-aware icon categories. The live project-tree preview follows unsaved settings. Accessibility options include shape-coded high-contrast markers, monochrome service folders, and reduced folder color. Known production, staging, test, and local variants of Spring profiles, Docker Compose files, Helm values, and `.env` files receive shape-and-color badges; disable **Environment variant badges** independently of their base icons. The **View > Emberkai Icons** menu also has quick toggles for folder colors, accessibility modes, and JVM architecture folders. Standard filename and extension mappings remain available when a context category is disabled. Applying settings refreshes project trees and open editor tabs; other IDE surfaces may need reopening to request updated icons.

In the Project view, right-click a file or folder and choose **Explain This Icon** to see its light/dark preview, winning mapping key and source, matching context rule, and whether native icon policy or a project role took precedence. Copy the mapping key or open the artwork from the inspector.

Run **Tools > Audit Project Icon Coverage** or right-click a directory in the Project view and choose **Audit Icon Coverage**. The cancellable audit uses the same resolver as the icon provider, groups generic and unmapped files by extension and parent folder, and lets you open a finding, copy a suggested `icons.properties` mapping, or open a prefilled missing-icon report.

The **JVM architecture folders** context category is opt-in. When enabled, Java and Kotlin source packages named `controller`, `service`, `repository`, `domain`, `dto`, `mapper`, `config`, `security`, `client`, `exception`, or `util` get distinct folder icons. `config` and `client` folders are shown only when a few immediate Java/Kotlin files indicate configuration or client code; checks are bounded and cached, including unsaved edits.

Folder icons use the Symbols shape with IntelliJ-style role cues: blue corner squares for source-set/module containers, blue source folders, green test-source folders, yellow resource lines, and package cubes. Java and Kotlin roots share the same source-folder style. IntelliJ's root model selects the role, including for custom directory names and Kotlin Multiplatform root types. Before import, `src/main/`, `src/test/`, and Multiplatform source sets such as `commonMain`, `commonTest`, `iosMain`, `jvmTest`, and `wasmJsMain` use the same cues for their `java/`, `kotlin/`, and `resources/` folders. Actual root markings take precedence over layout guesses. `.idea/`, library, excluded, generated, and unsupported language roots retain their specialized native icons. `.mvn/` uses the Symbols Maven folder icon, and other tool folders keep their Symbols artwork.

- Gradle icons cover Kotlin DSL scripts, `*.versions.toml` catalogs, and the `buildSrc/` and `build-logic/` folders. Plain `build.kts` remains an ordinary Kotlin script.
- Spring icons identify `application*` and `bootstrap*` YAML/properties files when an ancestor Maven/Gradle build file or `gradle/*.versions.toml` declares `org.springframework.boot`. Spring XML DOM descriptions also use Emberkai's Spring artwork. When the Spring Modulith plugin is installed, its package lock semantics are painted onto Emberkai folder icons, with a setting to turn the locks off. Small build files are checked using cached contents and modification stamps, including open editor changes; no dependency resolution or build commands are run.
- AI instruction icons cover `AGENTS.md`, `GEMINI.md`, `.windsurfrules`, `.cursor/rules/*.mdc`, `.windsurf/rules/*`, and GitHub Copilot instruction files. Claude's `CLAUDE.md` keeps its upstream Claude icon. GitHub issue and pull request templates use GitHub artwork.
- Infrastructure icons cover Azure Developer CLI's `azure.yaml`, AWS SAM `samconfig.toml`, AWS CDK `cdk.json`/`cdk.context.json`, CloudFormation/SAM templates identified by AWS template markers, and Terraform/OpenTofu files such as `.terraform-version`, `.terraform.lock.hcl`, `terraform.rc`, `*.tofu`, and `terragrunt.hcl`. Terraform and Terragrunt folders use a composed folder icon. Ansible configuration, inventories, and playbooks get Ansible artwork; playbooks are recognized by their `hosts` and task/role structure.
- Helm icons identify `Chart.yaml`, neighboring values files and `Chart.lock`, and YAML/helper templates under `templates/` or `crds/` in a chart. Chart roots and their `templates/`, `charts/`, and `crds/` folders get a Helm folder icon. A `Chart.yaml` file supplies chart context; ordinary YAML and template folders elsewhere keep their usual icons.
- Database migration icons cover Flyway versioned/repeatable SQL such as `V1__create_tables.sql` and `R__refresh_views.sql`, `flyway.conf`/`flyway.toml`, and Liquibase `db.changelog*` files in XML, YAML, JSON, or SQL plus `liquibase.properties`. They also cover `migration/` or `migrations/` folders. Other SQL files keep the database icon.
- API contract icons cover `openapi.yaml`/`.yml`/`.json`, `swagger.yaml`/`.yml`/`.json`, and YAML/JSON with top-level OpenAPI 3.x or Swagger 2.0 metadata. `api/`, `openapi/`, `swagger/`, and folders containing a canonical contract filename get an API folder icon.
- Kubernetes YAML/JSON is identified by top-level `apiVersion` and `kind`. Flux icons identify Flux API resources, `gotk-components.yaml`/`gotk-sync.yaml` under `flux-system/`, and Flux bootstrap folders. Argo CD icons identify `argoproj.io` Application/ApplicationSet/AppProject resources and common `argocd-*.yaml` config files. Kustomize has separate file and folder artwork for `kustomization.yaml`, `kustomization.yml`, or `Kustomization`, with contextual `base/`, `bases/`, `overlays/`, and `components/` folders. `k8s/` and `kubernetes/` get Kubernetes folders. GitHub workflow and Helm chart rules keep priority.
- Developer-tool icons cover `.sdkmanrc`, pre-commit configuration/hook manifests, `justfile`/`.justfile`, mise configuration variants, `.mise/config.toml`, `mise/config.toml`, `mise.lock`, and `lefthook.yml`.
- Ruby manifests (`Gemfile`, `Gemfile.lock`, `Rakefile`) use Ruby artwork; Dart/Flutter manifests (`pubspec.yaml`, `pubspec.lock`, `analysis_options.yaml`) use Dart artwork; Swift package manifests use Swift artwork; Rust toolchain and manifest files use Rust artwork; and `flake.lock` uses Nix artwork while `.nix` files use the upstream Nix mapping.
- Buf configuration files use Protocol Buffers artwork, and `proto/` and `protobuf/` folders use the API folder icon. `Jenkinsfile` uses the upstream Jenkins mapping.
- Test-asset folders include `fixtures/`, `__fixtures__/`, `testdata/`, `snapshots/`, `__snapshots__/`, and `__image_snapshots__/`. `.snap` files have snapshot artwork; other assets keep their file-format icons. Imported source/package roles take precedence over these folder names.

Content detection reads at most 256 KiB and caches results by modification stamp, including unsaved editor changes. Structured metadata checks only top-level YAML/JSON fields; bounded text signatures identify infrastructure templates and Ansible playbooks. Incomplete, unsupported, or oversized metadata keeps the normal format icon; canonical filenames still identify their tools. No builds, dependency resolution, or network calls run during icon lookup.

Custom SVGs use matching `_dark.svg` variants when neutral slate outlines or ink details need more contrast in dark themes. Brand-color artwork stays shared between themes, and IntelliJ selects the dark variant automatically.

## Install

1. Download the ZIP from [Releases](https://github.com/khopland/idea-symbols/releases/latest).
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

To add another tool, implement `ContextIconRule` and register it in `IconResolver.contextRules` in priority order. Rules return keys from `emberkai/icons.properties`, keeping detection logic independent of artwork. Add ordinary filename or extension mappings directly to the configuration without changing Kotlin code.

To find mapping gaps across a repository corpus, run `python3 scripts/audit-icon-coverage.py /path/to/repo [another/repo]` (Python 3.10+). It reports files with no static mapping and files mapped to generic format icons, but it does not evaluate content-aware rules. Use **Tools > Audit Project Icon Coverage** in the IDE to audit the icons selected by the real resolver.

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
