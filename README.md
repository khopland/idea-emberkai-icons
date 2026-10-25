# Emberkai Icons

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="src/main/resources/META-INF/pluginIcon_dark.svg">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Emberkai Icons: four colorful symbols including an ember" width="80" height="80">
</picture>

File and folder icons for JetBrains IDEs, named to complement the Emberkai theme. Built on the Symbols icon set, with native IntelliJ icons for Java and Kotlin.

Java sources (`.java`), compiled classes (`.class`), Kotlin sources (`.kt`), and ordinary Kotlin scripts (`.kts`) keep the IDE's type icons and modifier badges. Gradle Kotlin DSL files (`*.gradle.kts`) use the Gradle icon; declarations inside those scripts still keep native type icons.

Includes GitHub Actions workflows and metadata, Dev Containers, Renovate, and a custom Maven feather for `pom.xml` and Maven wrapper files. Workflow YAML is recognized inside `.github/workflows/`; unrelated YAML files keep their usual icons.

Folder icons use the Symbols shape with IntelliJ-style role cues: blue corner squares for source-set/module containers, blue source folders, green test-source folders, yellow resource lines, and package cubes. Java and Kotlin roots share the same source-folder style. IntelliJ's root model selects the role, including for custom directory names and Kotlin Multiplatform root types. Before import, `src/main/`, `src/test/`, and Multiplatform source sets such as `commonMain`, `commonTest`, `iosMain`, `jvmTest`, and `wasmJsMain` use the same cues for their `java/`, `kotlin/`, and `resources/` folders. Actual root markings take precedence over layout guesses. `.idea/`, library, excluded, generated, and unsupported language roots retain their specialized native icons. `.mvn/` uses the Maven feather folder, and other tool folders keep their Symbols artwork.

- Gradle icons cover Kotlin DSL scripts, `*.versions.toml` catalogs, and the `buildSrc/` and `build-logic/` folders. Plain `build.kts` remains an ordinary Kotlin script.
- Spring icons identify `application.yml`, `application.yaml`, `application.properties`, and profile variants when an ancestor Maven/Gradle build file or `gradle/*.versions.toml` declares `org.springframework.boot`. Small build files are checked using cached contents and modification stamps, including open editor changes; no dependency resolution or build commands are run.
- Helm icons identify `Chart.yaml`, neighboring values files and `Chart.lock`, and YAML/helper templates under `templates/` or `crds/` in a chart. Chart roots and their `templates/`, `charts/`, and `crds/` folders get a Helm folder icon. A `Chart.yaml` file supplies chart context; ordinary YAML and template folders elsewhere keep their usual icons.
- Database migration icons cover Flyway versioned/repeatable SQL such as `V1__create_tables.sql` and `R__refresh_views.sql`, `flyway.conf`/`flyway.toml`, and `migration/` or `migrations/` folders. Other SQL files keep the database icon.
- API contract icons cover `openapi.yaml`/`.yml`/`.json`, `swagger.yaml`/`.yml`/`.json`, and YAML/JSON with top-level OpenAPI 3.x or Swagger 2.0 metadata. `api/`, `openapi/`, `swagger/`, and folders containing a canonical contract filename get an API folder icon.
- Kubernetes YAML/JSON is identified by top-level `apiVersion` and `kind`. Kustomize has separate file and folder artwork for `kustomization.yaml`, `kustomization.yml`, or `Kustomization`, with contextual `base/`, `bases/`, `overlays/`, and `components/` folders. `k8s/` and `kubernetes/` get Kubernetes folders. GitHub workflow and Helm chart rules keep priority.
- Developer-tool icons cover `.sdkmanrc`, pre-commit configuration/hook manifests, `justfile`/`.justfile`, mise configuration variants, `.mise/config.toml`, `mise/config.toml`, and `mise.lock`.
- Test-asset folders include `fixtures/`, `__fixtures__/`, `testdata/`, `snapshots/`, `__snapshots__/`, and `__image_snapshots__/`. `.snap` files have snapshot artwork; other assets keep their file-format icons. Imported source/package roles take precedence over these folder names.

Content detection reads at most 256 KiB, checks only top-level YAML/JSON metadata, and caches results by modification stamp, including unsaved editor changes. Incomplete, unsupported, or oversized metadata keeps the normal format icon; canonical filenames still identify their tools. No builds, dependency resolution, or network calls run during icon lookup.

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

`SymbolsIconProvider` registers the PSI and virtual-file entry points. `IconResolver` defines matching precedence; `IconMappings` loads resources and matches names, extensions, and parent paths. `ProjectFolderIcons` translates IntelliJ root roles into themed folder icons, and `SourceSetFolders` handles layouts before import. `NativeIconPolicy` preserves native file icons and specialized folders. Each tool has a focused `ContextIconRule`; `FileContentCache` shares bounded, modification-aware inspection, and `StructuredFileKind` reads YAML/JSON metadata without constructing tagged objects.

Upstream files stay in `src/main/resources/symbols/`. All local configuration and artwork live in `src/main/resources/emberkai/`:

- `icons.properties` contains additional mappings, using absolute classpath paths such as `/symbols/icons/files/github.svg` or `/emberkai/icons/maven.svg`.
- `native.properties` lists native file extensions, tool-script exceptions, folder names, and native folder trees.
- `icons/` contains custom SVG artwork.

Local mappings take precedence over upstream mappings. A key such as `extension..github/workflows/yml` matches YAML files directly inside `.github/workflows/`, wherever the checkout is located. For a given filename or extension, the longest matching parent suffix wins. `folder.role.*` keys choose artwork for IntelliJ root/package roles. Project root roles and native-type policies take precedence over generic filename rules.

To add another tool, implement `ContextIconRule` and register it in `IconResolver.contextRules` in priority order. Rules return keys from `emberkai/icons.properties`, keeping detection logic independent of artwork. Add ordinary filename or extension mappings directly to the configuration without changing Java code.

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

`--check` writes nothing and exits with status 1 when an update is needed (errors also return a nonzero status). The script works from any directory, requires no credentials, and runs without prompts. It validates the downloaded files before replacing the bundled SVGs and mappings, sizes icons to 16×16 while preserving their view boxes, copies the upstream license, and records the source commit in `NOTICE` and the mapping header. Java and Kotlin keep their native IDE icons. Updating preserves everything under `src/main/resources/emberkai/`, but replaces local edits to the generated files under `symbols/`; review and commit the resulting diff, then run the build checks above.

For CI, run the same command before the Gradle build. The manual **Update icons** GitHub Actions workflow accepts an upstream ref, runs the updater and build checks, and uploads the refreshed sources and plugin ZIP as artifacts. Download the sources into the repository root to review and commit them.

## Credits and license

MIT licensed. Original icons are from [vscode-symbols](https://github.com/miguelsolorio/vscode-symbols) by Miguel Solorio; their original MIT notice is in [licenses](licenses/vscode-symbols-LICENSE.txt). Custom artwork in `emberkai/icons/` is part of Emberkai Icons and covered by the project MIT license. Folder variants and migration artwork build on the Symbols folder outline and database icon. This project started from [Symbols Icons](https://github.com/joansgarcia/jetbrains-symbols).
