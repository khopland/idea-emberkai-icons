package com.github.khopland.emberkai.audit

import com.github.khopland.emberkai.accents.ModuleAccentIcon
import com.github.khopland.emberkai.badges.ConfigDriftBadgeIcon
import com.github.khopland.emberkai.badges.EnvironmentVariant
import com.github.khopland.emberkai.badges.EnvironmentVariantBadgeIcon
import com.github.khopland.emberkai.badges.LockDriftBadgeIcon
import com.github.khopland.emberkai.badges.MigrationConflictBadgeIcon
import com.github.khopland.emberkai.badges.UnignoredSecretBadgeIcon
import com.github.khopland.emberkai.badges.UnpinnedReferenceBadgeIcon
import com.github.khopland.emberkai.core.BadgeSlot
import com.github.khopland.emberkai.core.BadgedIcon
import com.github.khopland.emberkai.core.IconMappings
import com.github.khopland.emberkai.folders.FolderIconPalette
import com.github.khopland.emberkai.folders.ServiceFolderIconStyle
import com.github.khopland.emberkai.folders.serviceFolderPalette
import com.github.khopland.emberkai.node.StereotypeBadgeIcon
import com.github.khopland.emberkai.run.RunningServiceIcon
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.spring.ActiveSpringProfileIcon
import com.github.khopland.emberkai.spring.ModulithFolderIcon
import com.github.khopland.emberkai.spring.ModulithLock
import com.github.khopland.emberkai.stereotypes.ClassStereotype
import com.intellij.icons.AllIcons
import javax.swing.Icon

/** One mark the legend shows: real badge artwork with what it means. */
internal data class LegendEntry(val icon: Icon, val name: String, val meaning: String)

/** The marks that share one corner, or another group such as folder colors. */
internal data class LegendSection(val title: String, val hint: String, val entries: List<LegendEntry>)

/** The folder and accessibility options the legend draws with: saved settings, or the settings page's unsaved ones. */
internal data class LegendMode(
    val palette: FolderIconPalette,
    val highContrast: Boolean,
    val monochrome: Boolean,
    val reducedFolderColor: Boolean,
) {
    /** Warning and secret badges turn ink in either mode, as their decorators draw them. */
    val contrast: Boolean get() = highContrast || monochrome

    companion object {
        fun current(): LegendMode = EmberkaiIconsSettings.getInstance().let {
            LegendMode(it.serviceFolderPalette, it.highContrastFolderIcons, it.monochromeFolderIcons, it.reducedFolderColor)
        }
    }
}

/**
 * Every mark Emberkai paints, drawn with the real badge classes over neutral artwork, so the legend
 * shows exactly what the Project view does in the current accessibility mode. [describe] names a
 * single badge for Explain This Icon from the same text.
 */
internal object IconLegend {
    fun sections(mode: LegendMode = LegendMode.current()): List<LegendSection> {
        val contrast = mode.contrast
        val file = IconMappings.get("extension.yaml") ?: AllIcons.FileTypes.Text
        val folder = IconMappings.get("folder") ?: AllIcons.Nodes.Folder
        val source = IconMappings.get("extension.ts") ?: file
        val badges = { slot: BadgeSlot, icons: List<BadgedIcon> -> icons.filter { it.slot == slot }.map(::entry) }
        val all = sampleBadges(file, folder, contrast, mode.highContrast, mode.monochrome, mode.palette)
        val service = mode.palette.colors.first().iconKey.let {
            ServiceFolderIconStyle.styleKey(it, mode.palette, mode.highContrast, mode.monochrome, mode.reducedFolderColor)
        }
        return listOf(
            LegendSection(
                "Corners",
                "Each corner of a 16px icon means one thing and shows at most one mark.",
                listOf(
                    LegendEntry(StereotypeBadgeIcon(source, ClassStereotype.WEB), "Top-left", BadgeSlot.ROLE.meaning),
                    LegendEntry(UnignoredSecretBadgeIcon(file, contrast), "Top-right", BadgeSlot.ATTENTION.meaning),
                    LegendEntry(EnvironmentVariantBadgeIcon(file, EnvironmentVariant.PRODUCTION), "Bottom-left", BadgeSlot.ENVIRONMENT.meaning),
                    LegendEntry(
                        IconMappings.get("context.kubernetes.deployment") ?: file,
                        "Bottom-right",
                        BadgeSlot.ARTWORK.meaning,
                    ),
                ),
            ),
            LegendSection(
                "Class icons: what a class does",
                "A Java or Kotlin class with a role shows it in place of the native class icon, under the IDE's own marks. Entry points come first.",
                ClassStereotype.entries.map { LegendEntry(it.classIcon, it.displayName, stereotypeMeaning(it)) },
            ),
            LegendSection(
                "Top-left: what a source file does",
                "The same roles as a small glyph on TypeScript and JavaScript file icons.",
                badges(BadgeSlot.ROLE, all),
            ),
            LegendSection(
                "Top-right: act on it, or live now",
                "When several apply, the most urgent wins: a secret, then a warning, then a live marker, then the accent dot. The others are still listed in the tooltip and under Recognized as in Explain This Icon.",
                badges(BadgeSlot.ATTENTION, all),
            ),
            LegendSection(
                "Bottom-left: environment",
                "On profiles, Compose files, Helm values, .env files, and environment folders. Shape and color both encode it.",
                badges(BadgeSlot.ENVIRONMENT, all),
            ),
            LegendSection(
                "Bottom-right: part of the artwork",
                "Kubernetes kinds and Symbols folder glyphs are drawn into the icon; Emberkai adds Spring Modulith locks.",
                badges(BadgeSlot.ARTWORK, all),
            ),
            LegendSection(
                "Folder colors",
                "A colored outline marks a source or test root; resources roots add stacked lines. A colored fill or dot marks a service " +
                    "or module and tells them apart. Folder color is never a status.",
                listOfNotNull(
                    folderEntry("folder.role.source", "Source root", "Outlined in the source color: production code."),
                    folderEntry("folder.role.test-source", "Test source root", "Outlined in the test color: test code."),
                    folderEntry("folder.role.resources", "Resources root", "Stacked lines in the resources color: files shipped with the code."),
                    folderEntry("folder.role.test-resources", "Test resources root", "Test-colored outline with resources lines: files for tests."),
                    folderEntry(
                        service,
                        "Service folder",
                        "Holds a Gradle, Maven, or Node package manifest." +
                            if (mode.monochrome) "" else " Each service gets its own color.",
                    ),
                ),
            ),
        )
    }

    private fun folderEntry(key: String, name: String, meaning: String): LegendEntry? =
        IconMappings.get(key)?.let { LegendEntry(it, name, meaning) }

    /** One of every badge class, in the order the legend lists them: most urgent first within a corner. */
    internal fun sampleBadges(
        file: Icon,
        folder: Icon,
        contrast: Boolean,
        highContrast: Boolean,
        monochrome: Boolean,
        palette: FolderIconPalette = FolderIconPalette.EMBERKAI,
    ): List<BadgedIcon> =
        ClassStereotype.entries.map { StereotypeBadgeIcon(IconMappings.get("extension.ts") ?: file, it) } +
            listOf(
                UnignoredSecretBadgeIcon(file, contrast),
                MigrationConflictBadgeIcon(file, contrast),
                ConfigDriftBadgeIcon(file, contrast),
                UnpinnedReferenceBadgeIcon(file, contrast),
                LockDriftBadgeIcon(file, contrast),
                ActiveSpringProfileIcon(file, highContrast, monochrome),
                RunningServiceIcon(folder, debug = false, highContrast, monochrome),
                RunningServiceIcon(folder, debug = true, highContrast, monochrome),
                ModuleAccentIcon(folder, palette.colors.first()),
            ) +
            EnvironmentVariant.entries.map { EnvironmentVariantBadgeIcon(file, it) } +
            ModulithLock.entries.map { ModulithFolderIcon(folder, it) }

    /** The legend entry for [badge], with [badge] itself as the picture. */
    fun entry(badge: BadgedIcon): LegendEntry {
        val (name, meaning) = describe(badge)
        return LegendEntry(badge, name, meaning)
    }

    /** A badge's name and meaning. IconLegendTest fails when a badge class is missing here or from [sampleBadges]. */
    fun describe(badge: BadgedIcon): Pair<String, String> = when (badge) {
        is StereotypeBadgeIcon -> badge.stereotype.displayName to stereotypeMeaning(badge.stereotype)
        is UnignoredSecretBadgeIcon -> "Unignored secret" to
            "A key, credential, plaintext Secret, or registry token that VCS would commit. Ignore or encrypt it."
        is MigrationConflictBadgeIcon -> "Migration conflict" to
            "A Flyway version used twice or out of order with the default branch, or a repeated Liquibase changeSet."
        is ConfigDriftBadgeIcon -> "Missing config keys" to
            "This profile, values, or .env variant lacks keys that every sibling variant defines."
        is UnpinnedReferenceBadgeIcon -> "Unpinned reference" to
            "An action or image that can change without this file changing. Pin it to a SHA, tag, or digest."
        is LockDriftBadgeIcon -> "Out-of-date Chart.lock" to
            "Chart.lock no longer matches Chart.yaml. Run helm dependency update."
        is ActiveSpringProfileIcon -> "Active Spring profile" to
            "The selected run configuration activates this profile file."
        is RunningServiceIcon -> if (badge.debug) {
            "Debugging" to "This module has a live debug session."
        } else {
            "Running" to "This module has a live run session; the tooltip names it and its port."
        }
        is ModuleAccentIcon -> "Module accent" to
            "The color of the module this folder belongs to. Only shown with module-aware accents."
        is EnvironmentVariantBadgeIcon -> environmentName(badge.variant) to environmentMeaning(badge.variant)
        is ModulithFolderIcon -> when (badge.lock) {
            ModulithLock.PUBLIC -> "Modulith public package" to "Part of a Spring Modulith module's public API."
            ModulithLock.INTERNAL -> "Modulith internal package" to "Internal to its Spring Modulith module; other modules must not use it."
            ModulithLock.NESTED -> "Modulith nested module" to "A Spring Modulith module nested inside another."
        }
        else -> badge.javaClass.simpleName to "Undocumented badge."
    }

    fun stereotypeMeaning(stereotype: ClassStereotype): String = when (stereotype) {
        ClassStereotype.APPLICATION -> "The application's entry point, such as @SpringBootApplication."
        ClassStereotype.WEB -> "Handles HTTP requests, such as @RestController, JAX-RS @Path, or a Next.js route."
        ClassStereotype.LISTENER -> "Consumes messages, such as @KafkaListener or @RabbitListener."
        ClassStereotype.SCHEDULED -> "Runs on a schedule, such as @Scheduled or NestJS @Cron."
        ClassStereotype.CLIENT -> "Calls another service over HTTP, such as @FeignClient."
        ClassStereotype.REPOSITORY -> "Reads and writes a database, such as a Spring Data repository."
        ClassStereotype.SERVICE -> "Business logic, such as @Service or @Injectable."
        ClassStereotype.CONFIGURATION -> "Configuration, such as @Configuration or @ConfigurationProperties."
        ClassStereotype.ENTITY -> "A persistent entity, such as @Entity or MongoDB @Document."
        ClassStereotype.CONTAINER_TEST -> "A test that starts containers, such as @Testcontainers."
        ClassStereotype.APPLICATION_TEST -> "A test that starts the whole application, such as @SpringBootTest."
        ClassStereotype.SLICE_TEST -> "A test that starts part of it, such as @WebMvcTest or @DataJpaTest."
    }

    private fun environmentName(variant: EnvironmentVariant): String =
        variant.displayName.replaceFirstChar(Char::titlecase)

    private fun environmentMeaning(variant: EnvironmentVariant): String = when (variant) {
        EnvironmentVariant.PRODUCTION -> "Red circle. Production files also get a red editor tab and a banner."
        EnvironmentVariant.STAGING -> "Amber diamond."
        EnvironmentVariant.TEST -> "Green triangle."
        EnvironmentVariant.LOCAL -> "Blue square."
    }
}
