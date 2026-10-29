package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.util.Locale
import javax.swing.Icon

/** Conventional names for files that usually hold credentials or private keys. */
internal object SecretFileNames {
    private val EXACT_NAMES = setOf(
        ".env", ".netrc", ".pypirc", "credentials.json",
        "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519",
    )
    private val EXTENSIONS = setOf("pem", "key", "p12", "pfx", "jks", "keystore")
    private val ENV_TEMPLATES = setOf("example", "sample", "template", "dist", "defaults")

    fun isCandidate(file: VirtualFile): Boolean = !file.isDirectory && isCandidate(file.name)

    fun isCandidate(fileName: String): Boolean {
        val name = fileName.lowercase(Locale.ROOT)
        if (name in EXACT_NAMES) return true
        if (name.startsWith(".env.")) {
            // .env.example and friends are committed on purpose; .env.local.example is too.
            return name.removePrefix(".env.").split('.').none { it.isEmpty() || it in ENV_TEMPLATES }
        }
        if (name.startsWith("service-account") && name.endsWith(".json")) return true
        val dot = name.lastIndexOf('.')
        return dot > 0 && name.substring(dot + 1) in EXTENSIONS
    }
}

/**
 * Reports whether a secret-looking file sits in a VCS root without being ignored. The VCS-backed
 * implementation is registered only when the IDE has VCS support; without it, no badge is shown.
 */
internal interface SecretFileExposure {
    /** Must answer from cached VCS state; returns false while ignore status is still unknown. */
    fun isExposed(file: VirtualFile): Boolean

    companion object {
        fun getInstance(project: Project): SecretFileExposure? =
            project.getService(SecretFileExposure::class.java)
    }
}

/** Paints a stop-sign badge with an exclamation mark while preserving the resolver's base icon. */
internal class UnignoredSecretBadgeIcon(
    baseIcon: Icon,
    internal val highContrast: Boolean,
) : BadgedIcon(baseIcon) {
    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        // Top-right keeps the badge clear of environment badges in the bottom-right corner.
        val size = minOf(8f, iconWidth / 2f, iconHeight / 2f)
        val badgeX = x + iconWidth - size - 0.5f
        val badgeY = y + 0.5f
        val shape = octagon(badgeX, badgeY, size)
        g.color = Color.WHITE
        g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
        g.color = OUTLINE
        g.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(shape)
        g.color = if (highContrast) OUTLINE else WARNING
        g.fill(shape)
        g.color = Color.WHITE
        val centerX = badgeX + size / 2
        g.stroke = BasicStroke(size * 0.18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Line2D.Float(centerX, badgeY + size * 0.24f, centerX, badgeY + size * 0.56f))
        val dot = size * 0.18f
        g.fill(Ellipse2D.Float(centerX - dot / 2, badgeY + size * 0.72f, dot, dot))
    }

    override fun withBase(base: Icon): BadgedIcon = UnignoredSecretBadgeIcon(base, highContrast)

    private fun octagon(x: Float, y: Float, size: Float): Path2D.Float {
        val inset = size * 0.29f
        return Path2D.Float().apply {
            moveTo(x + inset, y)
            lineTo(x + size - inset, y)
            lineTo(x + size, y + inset)
            lineTo(x + size, y + size - inset)
            lineTo(x + size - inset, y + size)
            lineTo(x + inset, y + size)
            lineTo(x, y + size - inset)
            lineTo(x, y + inset)
            closePath()
        }
    }

    private companion object {
        val WARNING = Color(0xB9, 0x1C, 0x1C)
        val OUTLINE = Color(0x0F, 0x17, 0x2A)
    }
}
