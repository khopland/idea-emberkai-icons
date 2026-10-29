package com.github.khopland.symbols.badges

import com.intellij.openapi.vfs.VirtualFile
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.util.Locale
import java.util.regex.Pattern
import javax.swing.Icon

/** Recognizes only conventional configuration names and explicit environment aliases. */
internal enum class EnvironmentVariant(
    val displayName: String,
    private val color: Color,
    private val shape: BadgeShape,
) {
    PRODUCTION("production", Color(0xDC, 0x26, 0x26), BadgeShape.CIRCLE),
    STAGING("staging", Color(0xD9, 0x77, 0x06), BadgeShape.DIAMOND),
    TEST("test", Color(0x16, 0xA3, 0x4A), BadgeShape.TRIANGLE),
    LOCAL("local", Color(0x25, 0x63, 0xEB), BadgeShape.SQUARE);

    fun badgeShape(x: Float, y: Float, size: Float): Shape = when (shape) {
        BadgeShape.CIRCLE -> Ellipse2D.Float(x, y, size, size)
        BadgeShape.DIAMOND -> Path2D.Float().apply {
            moveTo(x + size / 2, y)
            lineTo(x + size, y + size / 2)
            lineTo(x + size / 2, y + size)
            lineTo(x, y + size / 2)
            closePath()
        }
        BadgeShape.TRIANGLE -> Path2D.Float().apply {
            moveTo(x + size / 2, y)
            lineTo(x + size, y + size)
            lineTo(x, y + size)
            closePath()
        }
        BadgeShape.SQUARE -> RoundRectangle2D.Float(x, y, size, size, size * 0.16f, size * 0.16f)
    }

    fun paintColor(): Color = color

    companion object {
        private val PROFILE_NAMES = listOf(
            Pattern.compile("^(?:application|bootstrap)-([a-z0-9_-]+)\\.(?:yml|yaml)$"),
            Pattern.compile("^docker-compose\\.([a-z0-9_-]+)\\.ya?ml$"),
            Pattern.compile("^values-([a-z0-9_-]+)\\.ya?ml$"),
            Pattern.compile("^\\.env\\.([a-z0-9_-]+)$"),
        )

        /** Folders whose children are named after environments, as in Kustomize, GitOps, and Terraform layouts. */
        private val ENVIRONMENT_CONTAINERS = setOf("overlays", "envs", "env", "environments", "clusters", "stages")

        /** Only configuration formats inherit an environment folder's variant; docs and sources do not. */
        private val INHERITING_EXTENSIONS = setOf(
            "yaml", "yml", "json", "jsonnet", "libsonnet", "tf", "tfvars", "hcl", "tofu",
            "properties", "toml", "conf", "cfg", "ini", "env", "xml", "cue",
        )
        private const val INHERITING_NAME = "kustomization"
        private const val MAX_INHERITANCE_DEPTH = 6
        private val FOLDER_NAME_SEPARATORS = Regex("[-_.]")

        /** The file's own name wins; otherwise configuration files inherit from an [environmentFolderOf]. */
        fun fromFile(file: VirtualFile): EnvironmentVariant? {
            if (file.isDirectory) return null
            val name = file.name.lowercase(Locale.ROOT)
            fromFileName(name)?.let { return it }
            if (!inheritsFolderVariant(name)) return null
            return environmentFolderOf(file)?.let(::fromDirectory)
        }

        /** Whether [file]'s variant comes from a parent environment folder rather than its own name. */
        fun isInherited(file: VirtualFile): Boolean =
            !file.isDirectory && fromFileName(file.name.lowercase(Locale.ROOT)) == null && fromFile(file) != null

        /**
         * A folder such as `overlays/production/` or `clusters/prod-eu/`. Only children of an
         * environment container qualify, so `src/test/` is never a test environment, and a name that
         * mixes environments, such as `prod-to-staging`, is ignored.
         */
        fun fromDirectory(directory: VirtualFile): EnvironmentVariant? {
            if (!directory.isDirectory) return null
            val parent = directory.parent?.name?.lowercase(Locale.ROOT) ?: return null
            if (parent !in ENVIRONMENT_CONTAINERS) return null
            return directory.name.lowercase(Locale.ROOT)
                .split(FOLDER_NAME_SEPARATORS)
                .mapNotNull(::fromToken)
                .distinct()
                .singleOrNull()
        }

        /** The nearest ancestor of [file] that names an environment, at most a few levels up. */
        fun environmentFolderOf(file: VirtualFile): VirtualFile? =
            generateSequence(file.parent) { it.parent }
                .take(MAX_INHERITANCE_DEPTH)
                .firstOrNull { fromDirectory(it) != null }

        private fun fromFileName(name: String): EnvironmentVariant? =
            PROFILE_NAMES.firstNotNullOfOrNull { pattern ->
                pattern.matcher(name).takeIf { it.matches() }?.group(1)
            }?.let(::fromToken)

        private fun inheritsFolderVariant(name: String): Boolean =
            name == INHERITING_NAME || name.substringAfterLast('.', "") in INHERITING_EXTENSIONS

        private fun fromToken(token: String): EnvironmentVariant? = when (token) {
            "prod", "production" -> PRODUCTION
            "stage", "staging" -> STAGING
            "test", "testing" -> TEST
            "local", "dev", "development" -> LOCAL
            else -> null
        }
    }
}

private enum class BadgeShape {
    CIRCLE,
    DIAMOND,
    TRIANGLE,
    SQUARE,
}

/** Paints a small color-and-shape badge while preserving the resolver's base icon. */
internal class EnvironmentVariantBadgeIcon(
    baseIcon: Icon,
    internal val variant: EnvironmentVariant,
) : BadgedIcon(baseIcon) {
    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        val size = minOf(7f, iconWidth / 2f, iconHeight / 2f)
        val shape = variant.badgeShape(x + iconWidth - size - 1f, y + iconHeight - size - 1f, size)
        g.color = Color.WHITE
        g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
        g.color = OUTLINE
        g.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
        g.color = variant.paintColor()
        g.fill(shape)
        g.color = OUTLINE
        g.stroke = BasicStroke(0.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
    }

    override fun withBase(base: Icon): BadgedIcon = EnvironmentVariantBadgeIcon(base, variant)

    private companion object {
        val OUTLINE = Color(0x0F, 0x17, 0x2A)
    }
}
