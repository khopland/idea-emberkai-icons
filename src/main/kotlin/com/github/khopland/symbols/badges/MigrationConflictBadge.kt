package com.github.khopland.symbols.badges

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.math.BigInteger
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.swing.Icon

/** The Flyway versioned migrations directly inside one folder. */
internal class MigrationFolderSummary(
    val versionedCount: Int,
    /** The highest version, such as `1.2`, or null when the folder has no versioned migrations. */
    val latestVersion: String?,
    /** Versions used by more than one migration, mapped to the file names that share them. */
    val conflicts: Map<String, List<String>>,
)

/**
 * Finds duplicate Flyway versions among sibling SQL migrations, the usual result of two branches
 * adding the same next version. Only file names are read. Summaries are cached per folder and
 * dropped whenever a file is created, deleted, moved, copied, or renamed anywhere.
 */
internal object FlywayMigrations {
    private val VERSIONED: Pattern = Pattern.compile("^v(\\d+(?:[._]\\d+)*)__.+\\.sql$")
    private val EMPTY = MigrationFolderSummary(0, null, emptyMap())
    private val summaries = ConcurrentHashMap<VirtualFile, MigrationFolderSummary>()

    /**
     * Flyway's version for a file name, normalized the way Flyway compares versions: `_` and `.`
     * separate parts, leading zeros are ignored, and trailing zero parts do not count, so
     * `V1_0__init.sql` and `V01__init.sql` are both version `1`.
     */
    fun version(fileName: String): String? {
        val matcher = VERSIONED.matcher(fileName.lowercase(Locale.ROOT))
        if (!matcher.matches()) return null
        val parts = matcher.group(1).split('.', '_').map { BigInteger(it) }.toMutableList()
        while (parts.size > 1 && parts.last() == BigInteger.ZERO) parts.removeAt(parts.lastIndex)
        return parts.joinToString(".")
    }

    /** The migration folder summary, or null when [directory] holds no versioned migrations. */
    fun summary(directory: VirtualFile): MigrationFolderSummary? {
        if (!directory.isDirectory || !directory.isValid) return null
        return summaries.computeIfAbsent(directory, ::summarize).takeIf { it !== EMPTY }
    }

    /** The duplicated version [file] uses, if any, and the other files that use it. */
    fun conflict(file: VirtualFile): Pair<String, List<String>>? {
        if (file.isDirectory) return null
        val version = version(file.name) ?: return null
        val others = file.parent?.let(::summary)?.conflicts?.get(version) ?: return null
        return version to others.filter { it != file.name }
    }

    fun invalidate() = summaries.clear()

    private fun summarize(directory: VirtualFile): MigrationFolderSummary {
        val byVersion = directory.children.orEmpty()
            .filter { !it.isDirectory }
            .mapNotNull { child -> version(child.name)?.let { it to child.name } }
            .groupBy({ it.first }, { it.second })
        if (byVersion.isEmpty()) return EMPTY
        return MigrationFolderSummary(
            versionedCount = byVersion.values.sumOf { it.size },
            latestVersion = byVersion.keys.maxWithOrNull(::compareVersions),
            conflicts = byVersion.filterValues { it.size > 1 }.mapValues { (_, names) -> names.sorted() },
        )
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map(::BigInteger)
        val b = right.split('.').map(::BigInteger)
        for (index in 0 until maxOf(a.size, b.size)) {
            val compared = a.getOrElse(index) { BigInteger.ZERO }.compareTo(b.getOrElse(index) { BigInteger.ZERO })
            if (compared != 0) return compared
        }
        return 0
    }
}

/** Drops cached migration summaries when files appear, disappear, or are renamed. */
internal class FlywayMigrationCacheListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any(::changesNames)) FlywayMigrations.invalidate()
    }

    private fun changesNames(event: VFileEvent): Boolean = when (event) {
        is VFileCreateEvent, is VFileDeleteEvent, is VFileMoveEvent, is VFileCopyEvent -> true
        is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME
        else -> false
    }
}

/**
 * Paints an amber warning triangle in the bottom-left corner, the one corner no other Emberkai
 * badge uses, so it stacks with environment, secret, and live-profile badges.
 */
internal class MigrationConflictBadgeIcon(
    baseIcon: Icon,
    internal val highContrast: Boolean,
) : BadgedIcon(baseIcon) {
    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        val size = minOf(8f, iconWidth / 2f, iconHeight / 2f)
        val left = x + 0.5f
        val top = y + iconHeight - size - 0.5f
        val triangle = Path2D.Float().apply {
            moveTo(left + size / 2, top)
            lineTo(left + size, top + size)
            lineTo(left, top + size)
            closePath()
        }
        g.color = Color.WHITE
        g.stroke = BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(triangle)
        g.color = OUTLINE
        g.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(triangle)
        g.color = if (highContrast) OUTLINE else WARNING
        g.fill(triangle)
        g.color = Color.WHITE
        val centerX = left + size / 2
        g.stroke = BasicStroke(size * 0.16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Line2D.Float(centerX, top + size * 0.36f, centerX, top + size * 0.64f))
        val dot = size * 0.16f
        g.fill(Ellipse2D.Float(centerX - dot / 2, top + size * 0.76f, dot, dot))
    }

    override fun withBase(base: Icon): BadgedIcon = MigrationConflictBadgeIcon(base, highContrast)

    private companion object {
        val WARNING = Color(0xB4, 0x53, 0x09)
        val OUTLINE = Color(0x0F, 0x17, 0x2A)
    }
}
