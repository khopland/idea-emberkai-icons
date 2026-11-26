# Contributing

How to build Emberkai Icons, where its mappings and artwork live, and the checks that keep them consistent. Releases are covered in [Releasing](releasing.md).

## Build

Requires Java 21. No icon generator or submodules are needed.

```sh
./gradlew test verifyPluginStructure buildPlugin
```

The ZIP is written to `build/distributions/emberkai-icons-<version>.zip`. Run `./gradlew runIde` to try the plugin in a sandbox. Run `./gradlew test --tests '*MarketplaceScreenshots' -Pscreenshots` to re-render the screenshots in `design/screenshots/` from the plugin's real icons.

`EmberkaiIconProvider` registers the PSI and virtual-file entry points. `IconResolver` defines matching precedence; `IconMappings` loads resources, and `ProjectIconOverrideStore` layers project-local `.idea/emberkai-icons.properties` entries on top of them. Both match names, extensions, and parent paths through `MappingKeys`, so overrides follow the same lookup order as bundled mappings. `ProjectFolderIcons` translates IntelliJ root roles into themed folder icons, and `SourceSetFolders` handles layouts before import. `NativeIconPolicy` preserves native Java/Kotlin icons and specialized folders. `IconLensDecorator` dims Project view nodes outside the active lens from a snapshot `IconLensService` computes in the background; `GitBranchChanges` supplies the branch comparison for the **Changed on this branch** lens. `ClassStereotypeLayerProvider` adds stereotype glyphs to native Java icons through the platform's icon layers; Kotlin icons bypass those layers, so `KotlinStereotypeIconProvider` wraps the Kotlin plugin's own icon instead. Badges such as environment variants and module accents extend `BadgedIcon`, which keeps dark and light variants of the base artwork working. Integrations that need optional platform modules (Java, Kotlin, VCS, Git, XML, and Spring Modulith) are registered from their own descriptors next to `plugin.xml`. Each tool has a focused `ContextIconRule`; `FileContentCache` shares bounded, modification-aware inspection, and `StructuredFileKind` reads YAML/JSON metadata without constructing tagged objects.

All configuration and artwork live in `src/main/resources/emberkai/`:

- `associations.properties` holds the base file and folder associations, derived from the vscode-symbols theme, using absolute classpath paths such as `/emberkai/icons/originals/files/github.svg`.
- `icons.properties` contains Emberkai's own mappings, which take precedence, such as `/emberkai/icons/helm.svg`.
- `native.properties` lists native file extensions, tool-script exceptions, folder names, and native folder trees.
- `icons/` contains custom SVG artwork, generated from `design/icons/` (see [Icon sources](#icon-sources)).

`icons.properties` takes precedence over `associations.properties`. A key such as `extension..github/workflows/yml` matches YAML files directly inside `.github/workflows/`, wherever the checkout is located. For a given filename or extension, the longest matching parent suffix wins. `folder.role.*` keys choose artwork for IntelliJ root/package roles. Project root roles and native-type policies take precedence over generic filename rules.

The Kotlin packages sit in layers that can later become Gradle modules, and `PackageLayeringTest` fails when a package imports one above it: `settings` (state, the `IconRuleCategory` families, and artwork palettes); then icon resolution (`core`, `rules`, `folders`, `accents`, `overrides`, `generated`); then badges and project facts (`badges`, `spring`, `run`, `node`, `python`, `ownership`, `git`, `tooltips`, `stereotypes`, `scopes`); then `lens` and `editor`, which must not import each other; and on top `ui`, `audit`, and `statusbar`. A lower layer that needs to tell a higher one about a change publishes on a message-bus topic, such as `IconLensListener`, instead of calling it. Add a new package to a layer in that test.

To add another tool, implement `ContextIconRule` and register it in `IconResolver.contextRules` in priority order. Rules return keys from `emberkai/icons.properties`, keeping detection logic independent of artwork. `IconResolver.recognizedCategory` feeds the `EmberkaiScopes` scopes; override `recognizesFile` when a tool's canonical filenames are drawn by static mappings but should still join its scope. Add ordinary filename or extension mappings directly to the configuration without changing Kotlin code.

To add a badge:

1. Extend `BadgedIcon`, pick its `BadgeSlot` corner, and for the top-right set an `urgency` from `BadgedIcon`'s companion.
2. Return it from an `IconDecorator` with an `IconFact` whose `label` reads well in a tooltip, and give the decorator a setting in `IconRuleCategory`.
3. Add a sample to `IconLegend.sampleBadges` and a name and meaning to `IconLegend.describe`. `IconLegendTest` fails until you do, so every badge can be looked up in the legend and in Explain This Icon.
4. A new stereotype glyph needs a fill no other stereotype uses; `IconLegendTest` checks that too.

To find mapping gaps across a repository corpus, run `python3 scripts/audit-icon-coverage.py /path/to/repo [another/repo]` (Python 3.10+). It reports files with no static mapping and files mapped to generic format icons, but it does not evaluate content-aware rules. Use **Tools > Audit Project Icon Coverage** in the IDE to audit the icons selected by the real resolver.

## Icon sources

Emberkai's artwork is written once, in `design/icons/`, and `src/main/resources/emberkai/icons/` is generated from it, so an icon's light and dark versions cannot drift apart. A source is an ordinary SVG that names its colors instead of spelling them in hex:

- a palette name from `design/palette.properties`, such as `fill="green-400"`, is the same color in both themes;
- a token from `design/tokens.properties`, such as `stroke="$ink"` or `stroke="$blue"`, is one palette color in light themes and another in dark ones (`ink` is slate-800 on light and slate-200 on dark);
- `<g data-theme="light">` and `<g data-theme="dark">` keep their content in only that theme, for the few icons whose shapes differ, such as the larger dark-theme stereotype badges.

File, tool, and class icons and the Kubernetes kind badges share one look, taken from the ember in the plugin logo: a flat body, a deeper facet of the same hue toward the bottom right (on a 14px tile, `M15 4V12A3 3 0 0 1 12 15H4Z`), and no ink outline. Horizontal and vertical edges sit on whole pixels; curves and diagonals are drawn as such, not as pixel steps, so the artwork also holds up at 200%. Hues too pale to stand off a light background use a token pair such as `$green` and `$green-deep`.

Each source becomes `<name>.svg`, plus `<name>_dark.svg` when the two themes come out different. After editing a source or a token, regenerate the artwork:

```sh
./gradlew test --tests '*IconSourcesTest' -PwriteIcons
```

Each theme palette in `design/palettes/` (Catppuccin, Nord, Gruvbox) also gets a retinted copy of the whole set in `src/main/resources/emberkai/palettes/<palette>/`, chosen with **Artwork colors** in settings, plus a `colors.properties` table for the module accent dots that are painted in code. A palette gives each Tailwind family one theme color. Every shade takes that color's hue, keeps at least 60% of its own saturation so bright dark-theme shades never fade to grey, and keeps its own luminance, so contrast against both IDE backgrounds, and so the legibility lint, is unchanged. `IconSourcesTest` also checks that the eight service-folder colors stay at least 20° apart in hue in every palette. Upstream file icons, mostly brand logos, keep their colors.

`IconSourcesTest` fails when the generated artwork is stale, missing, or has no source, so a hand edit to a generated SVG is caught. Changing a token, say making `outline` slate-400 in dark themes, retints every icon that uses it.

Custom SVGs get a `_dark.svg` variant when the two themes need different colors. Neutral slate outlines and ink details get lighter in dark themes. Thin accent strokes use a deeper shade in light themes, such as blue-600 instead of blue-400, because the bright dark-theme shade washes out on a light background. Brand-color fills stay shared between themes, and IntelliJ selects the dark variant automatically. Both variants are generated from one source per icon; see [Icon sources](#icon-sources).

## Artwork checks

`IconArtworkLintTest` runs with `./gradlew test` and checks every icon SVG under `src/main/resources/emberkai/` (the Marketplace plugin icon in `META-INF/` is not an icon theme asset and is skipped). Each must parse, have a viewBox, and paint at 16×16 in IntelliJ. Emberkai artwork must also:

- be 16×16 on a `0 0 16 16` or `0 0 24 24` grid;
- paint only with colors named in `design/palette.properties` (`#RGB` or `#RRGGBB`, in attributes or `style`; `none`, `currentColor`, and `url(#…)` references are allowed, and masks are exempt). The palette holds the Tailwind families, white, black, the IDE's dark background for halos, and a few logo colors;
- pair each `_dark.svg` with light artwork on the same grid;
- stay legible: at 100% and at 200%, at least 12% of its painted pixels must reach 3:1 contrast against the New UI Project view background, light artwork on the light theme and the dark variant (or the light artwork when there is none) on the dark theme. Every icon 0.8.0 fixed by hand scored 0%; the weakest current icon scores about 14% at 100%, so the floor catches artwork that has nearly vanished rather than artwork that is merely pale.

Each rule is also run against known-bad artwork, so a rule that stops finding anything fails too.

On pull requests that change SVGs, CI renders each changed icon before and after at 100%, 150%, and 200% on both themes, labels new and deleted icons, and links the **icon-contact-sheet** artifact from the job summary. It renders after a failed build too, since a failing lint is when the picture helps most, and shows at most 100 rows. Locally, run `BASE=main bash .github/scripts/icon_contact_sheet.sh`, which compares with where the branch left `main`; the sheet is written to `build/reports/icon-sheet/icons.png`.
