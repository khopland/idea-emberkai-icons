package com.github.khopland.symbols.badges

import com.intellij.openapi.project.Project
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
    /** Every versioned migration's file name, mapped to its normalized version. */
    val versions: Map<String, String> = emptyMap(),
)

/** The Flyway versioned migrations in one folder on the default branch, from Git. */
internal data class BaseBranchMigrations(
    /** The ref they were read from, such as `origin/main`. */
    val ref: String,
    val fileNames: Set<String>,
    /** The highest version on [ref], or null when the folder has no versioned migrations there. */
    val latestVersion: String?,
)

/**
 * The default branch's view of migration folders, for out-of-order warnings. Registered only when
 * the Git plugin is enabled. Lookups answer from a cache and never run Git on the calling thread.
 */
internal interface MigrationBranchBase {
    /** Null until a background lookup for [directory] finishes, or when it is not in a Git repository with a default branch. */
    fun migrationsOnBase(directory: VirtualFile): BaseBranchMigrations?

    companion object {
        fun getInstance(project: Project): MigrationBranchBase? = project.getService(MigrationBranchBase::class.java)
    }
}

/** A migration this branch added whose version is not after the latest one on the default branch. */
internal data class OutOfOrderMigration(val version: String, val baseLatest: String, val baseRef: String)

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

    /**
     * Whether [file] was added on this branch with a version at or below the latest version on the
     * default branch. Once the default branch's migrations have run, Flyway rejects it as out of
     * order (or, at the same version, as a duplicate) unless `outOfOrder` is enabled.
     */
    fun outOfOrder(file: VirtualFile, project: Project?): OutOfOrderMigration? {
        if (project == null || file.isDirectory) return null
        val version = version(file.name) ?: return null
        val base = file.parent?.let { MigrationBranchBase.getInstance(project)?.migrationsOnBase(it) } ?: return null
        return outOfOrder(file.name, version, base)
    }

    /** The migrations in [directory] that [outOfOrder] warns about, keyed by file name. */
    fun outOfOrderIn(directory: VirtualFile, project: Project?): Map<String, OutOfOrderMigration> {
        if (project == null) return emptyMap()
        val summary = summary(directory) ?: return emptyMap()
        val base = MigrationBranchBase.getInstance(project)?.migrationsOnBase(directory) ?: return emptyMap()
        return summary.versions.mapNotNull { (name, version) -> outOfOrder(name, version, base)?.let { name to it } }
            .toMap()
    }

    fun outOfOrder(fileName: String, version: String, base: BaseBranchMigrations): OutOfOrderMigration? {
        val latest = base.latestVersion ?: return null
        if (fileName in base.fileNames || compareVersions(version, latest) > 0) return null
        return OutOfOrderMigration(version, latest, base.ref)
    }

    /** The highest normalized version among [fileNames], or null when none is a versioned migration. */
    fun latestVersion(fileNames: Collection<String>): String? =
        fileNames.mapNotNull(::version).maxWithOrNull(::compareVersions)

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
            versions = byVersion.flatMap { (version, names) -> names.map { it to version } }.toMap(),
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
