package com.github.khopland.symbols

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

        fun fromFile(file: VirtualFile): EnvironmentVariant? {
            if (file.isDirectory) return null
            val name = file.name.lowercase(Locale.ROOT)
            val token = PROFILE_NAMES.firstNotNullOfOrNull { pattern ->
                pattern.matcher(name).takeIf { it.matches() }?.group(1)
            } ?: return null
            return when (token) {
                "prod", "production" -> PRODUCTION
                "stage", "staging" -> STAGING
                "test", "testing" -> TEST
                "local", "dev", "development" -> LOCAL
                else -> null
            }
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
