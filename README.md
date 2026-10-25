# Symbols Icons (Native JVM)

Symbols file and folder icons for JetBrains IDEs, with native IntelliJ icons for Java and Kotlin.

Java sources (`.java`), compiled classes (`.class`), Kotlin sources (`.kt`), and Kotlin scripts (`.kts`) keep the IDE's type icons and modifier badges. Other files and folders use Symbols icons.

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

The ZIP is written to `build/distributions/`. Run `./gradlew runIde` to try the plugin in a sandbox.

The plugin is one Java class. Icon mappings are in `src/main/resources/symbols/icons.properties`; SVGs are in `src/main/resources/symbols/icons/`. Edit these files directly to change icons.

## Credits and license

MIT licensed. Icons are from [vscode-symbols](https://github.com/miguelsolorio/vscode-symbols) by Miguel Solorio; their original MIT notice is in [licenses](licenses/vscode-symbols-LICENSE.txt). This project started from [Symbols Icons](https://github.com/joansgarcia/jetbrains-symbols).
