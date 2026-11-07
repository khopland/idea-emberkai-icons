package com.github.khopland.symbols.node

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.stereotypes.ClassStereotype
import com.github.khopland.symbols.stereotypes.StereotypeMatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/**
 * The JVM class stereotypes, found in TypeScript and JavaScript. Next.js routes are recognized by
 * where they sit in a package that depends on `next`, so no file is read. NestJS and TypeORM roles
 * come from decorators, and only count when the decorator is imported from the module that defines
 * it, as written; files are read only in packages that depend on NestJS or TypeORM.
 */
internal object NodeStereotypes {
    private val SOURCE_EXTENSIONS = setOf("ts", "tsx", "mts", "cts", "js", "jsx", "mjs", "cjs")
    private val ROUTE_EXTENSIONS = setOf("ts", "tsx", "js", "jsx")
    private val DECORATED_PACKAGES = setOf("@nestjs/common", "@nestjs/core", "typeorm")

    /** Decorators by the modules allowed to supply them. */
    private val DECORATORS = mapOf(
        "Controller" to (setOf("@nestjs/common") to ClassStereotype.WEB),
        "Resolver" to (setOf("@nestjs/graphql") to ClassStereotype.WEB),
        "WebSocketGateway" to (setOf("@nestjs/websockets") to ClassStereotype.LISTENER),
        "MessagePattern" to (setOf("@nestjs/microservices") to ClassStereotype.LISTENER),
        "EventPattern" to (setOf("@nestjs/microservices") to ClassStereotype.LISTENER),
        "Processor" to (setOf("@nestjs/bullmq", "@nestjs/bull") to ClassStereotype.LISTENER),
        "Cron" to (setOf("@nestjs/schedule") to ClassStereotype.SCHEDULED),
        "Interval" to (setOf("@nestjs/schedule") to ClassStereotype.SCHEDULED),
        "Timeout" to (setOf("@nestjs/schedule") to ClassStereotype.SCHEDULED),
        "Injectable" to (setOf("@nestjs/common") to ClassStereotype.SERVICE),
        "Module" to (setOf("@nestjs/common") to ClassStereotype.CONFIGURATION),
        "Entity" to (setOf("typeorm") to ClassStereotype.ENTITY),
    )

    private val NAMED_IMPORT = Regex("""\bimport\s+(?:type\s+)?\{([^}]*)}\s*from\s*['"]([^'"]+)['"]""")
    private val DECORATOR = Regex("""@([A-Za-z_$][\w$]*)\s*\(""")
    private val SOURCE_MATCH = Key.create<FileContentCache.Inspection<StereotypeMatch?>>("emberkai.nodeStereotype")

    fun isCandidate(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        val name = file.name.lowercase(Locale.ROOT)
        return file.extension?.lowercase(Locale.ROOT) in SOURCE_EXTENSIONS &&
            !name.endsWith(".d.ts") && !name.endsWith(".d.mts") && !name.endsWith(".d.cts")
    }

    fun match(file: VirtualFile, project: Project): StereotypeMatch? {
        if (!isCandidate(file)) return null
        val nodePackage = NodePackages.nearest(file, project) ?: return null
        if ("next" in nodePackage.dependencies) nextRoute(file, nodePackage.root)?.let { return it }
        if (!nodePackage.dependsOnAny(DECORATED_PACKAGES)) return null
        return FileContentCache.inspect(file, SOURCE_MATCH, null, ::decorated)
    }

    /** App Router pages and route handlers, Pages Router pages and API routes, and middleware. */
    private fun nextRoute(file: VirtualFile, packageRoot: VirtualFile): StereotypeMatch? {
        if (file.extension?.lowercase(Locale.ROOT) !in ROUTE_EXTENSIONS) return null
        val relative = VfsUtilCore.getRelativePath(file, packageRoot) ?: return null
        val segments = relative.split('/').let { if (it.firstOrNull() == "src") it.drop(1) else it }
        val name = file.nameWithoutExtension
        val folders = segments.dropLast(1)
        if (folders.isEmpty()) {
            // Next.js 16 renamed middleware to proxy.
            return if (name == "middleware" || name == "proxy") StereotypeMatch(ClassStereotype.WEB, "Next.js $name") else null
        }
        // Folders and files starting with an underscore are private to the router.
        if (folders.drop(1).any { it.startsWith('_') } || name.startsWith('_')) return null
        return when (folders.first()) {
            "app" -> when (name) {
                "page" -> StereotypeMatch(ClassStereotype.WEB, "Next.js page")
                "route" -> StereotypeMatch(ClassStereotype.WEB, "Next.js route handler")
                else -> null
            }
            "pages" -> if (folders.getOrNull(1) == "api") {
                StereotypeMatch(ClassStereotype.WEB, "Next.js API route")
            } else {
                StereotypeMatch(ClassStereotype.WEB, "Next.js page")
            }
            else -> null
        }
    }

    /** The highest-priority decorator whose name, or alias, is imported from a module that defines it. */
    internal fun decorated(text: String): StereotypeMatch? {
        val imports = HashMap<String, Pair<String, String>>()
        for (statement in NAMED_IMPORT.findAll(text)) {
            val module = statement.groupValues[2]
            for (specifier in statement.groupValues[1].split(',')) {
                val parts = specifier.trim().removePrefix("type ").split(Regex("\\s+as\\s+"))
                val imported = parts.first().trim()
                if (imported.isNotEmpty()) imports[parts.last().trim()] = imported to module
            }
        }
        if (imports.isEmpty()) return null
        return DECORATOR.findAll(text).mapNotNull { usage ->
            val (imported, module) = imports[usage.groupValues[1]] ?: return@mapNotNull null
            val (modules, stereotype) = DECORATORS[imported] ?: return@mapNotNull null
            if (module in modules) StereotypeMatch(stereotype, "@$imported") else null
        }.minByOrNull { it.stereotype.ordinal }
    }
}
