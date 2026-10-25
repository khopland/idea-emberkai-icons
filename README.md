# Symbols Icons (Native JVM)

![Build](https://github.com/khopland/jetbrains-symbols/actions/workflows/build.yml/badge.svg)

A fork of [Symbols Icons](https://github.com/joansgarcia/jetbrains-symbols) that keeps IntelliJ's informative Java and Kotlin icons.

<!-- Plugin description -->
<p>A minimal file and folder icon theme for JetBrains IDEs that preserves native Java and Kotlin icons.</p>
<p>Java sources (<code>.java</code>), compiled classes (<code>.class</code>), Kotlin sources (<code>.kt</code>), and Kotlin scripts (<code>.kts</code>) use the IDE's language icons, including class/interface/enum distinctions and modifier badges. Other files and folders use Symbols icons.</p>
<p>Forked from <a href="https://github.com/joansgarcia/jetbrains-symbols">Symbols Icons</a>.</p>
<p>Based on <a href="https://github.com/miguelsolorio/vscode-symbols">vscode-symbols</a> by Miguel Solorio.</p>
<!-- Plugin description end -->

## Symbols theme preview

The upstream preview below shows the general theme. This fork uses native icons for Java and Kotlin files.

<p align="center">
	<img width="1046" height="889" alt="image" src="https://github.com/user-attachments/assets/dff41558-c06d-4f6e-a0e0-a463af9fb924" />
</p>

## Installation

1. Download the plugin ZIP from [the latest release](https://github.com/khopland/jetbrains-symbols/releases/latest).
2. Disable or uninstall the original **Symbols Icons** plugin if it is installed; both themes would otherwise compete for icons.
3. In the IDE, open **Settings > Plugins > ⚙️ > Install Plugin from Disk...** and select the ZIP without extracting it.
4. Restart the IDE if prompted.

The plugin is named **Symbols Icons (Native JVM)** and has its own plugin ID, so Marketplace updates to the original plugin do not replace this fork.

## How native icons are preserved

The icon provider returns `null` for Java and Kotlin files and their declarations, allowing the IDE's own language providers to choose the icon and apply badges. It does not copy or hardcode IntelliJ icons, so they follow the installed IDE version and theme. Native Java/Kotlin icons require the corresponding language support in your IDE.

## Development

```zsh
deno task build
./gradlew --no-daemon test
./gradlew --no-daemon buildPlugin
```

## Release

```zsh
deno task build
./gradlew --no-daemon test verifyPluginStructure buildPlugin
```

The installable ZIP is written to `build/distributions/`. Publishing a GitHub release also builds, tests, and attaches the ZIP automatically. This fork does not publish to JetBrains Marketplace.

---
Based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
