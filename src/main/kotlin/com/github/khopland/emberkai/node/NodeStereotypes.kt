package com.github.khopland.emberkai.node

import com.github.khopland.emberkai.core.FileContentCache
import com.github.khopland.emberkai.stereotypes.ClassStereotype
import com.github.khopland.emberkai.stereotypes.StereotypeMatch
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/**
 * The JVM class stereotypes, found in TypeScript and JavaScript. Next.js routes are recognized by
 * where they sit in an app, so no file is read. NestJS, TypeORM, MikroORM, and Mongoose roles come
 * from decorators, which only count when imported from the module that defines them, as written;
 * files are read only in packages that depend on one of those. Where loading from disk is a slow
 * operation, only results already cached are used (see [NodePackages]).
 */
internal object NodeStereotypes {
    private val SOURCE_EXTENSIONS = setOf("ts", "tsx", "mts", "cts", "js", "jsx", "mjs", "cjs")
    private val ROUTE_EXTENSIONS = setOf("ts", "tsx", "js", "jsx")
    private val DECORATED_PACKAGES = setOf("@nestjs/common", "@nestjs/core", "typeorm", "@mikro-orm/core")

    /** App Router files that answer HTTP requests besides `page` and `route`. */
    private val METADATA_ROUTES = setOf("sitemap", "opengraph-image", "twitter-image", "icon", "apple-icon")

    /** Metadata routes Next.js serves only from the root of `app/`. */
    private val ROOT_METADATA_ROUTES = setOf("robots", "manifest")

    /** Files that sit beside pages without being routes; catch-alls such as `[...slug]` keep their dots. */
    private val NOT_A_PAGE = Regex("""\.(?:test|spec|stories|story)$""")

    private class Decorator(val modules: Set<String>, val stereotype: ClassStereotype)

    /** Decorators by the modules allowed to supply them, including their subpaths. */
    private val DECORATORS = mapOf(
        "Controller" to Decorator(setOf("@nestjs/common"), ClassStereotype.WEB),
        "Resolver" to Decorator(setOf("@nestjs/graphql"), ClassStereotype.WEB),
        "WebSocketGateway" to Decorator(setOf("@nestjs/websockets"), ClassStereotype.LISTENER),
        "MessagePattern" to Decorator(setOf("@nestjs/microservices"), ClassStereotype.LISTENER),
        "EventPattern" to Decorator(setOf("@nestjs/microservices"), ClassStereotype.LISTENER),
        "OnEvent" to Decorator(setOf("@nestjs/event-emitter"), ClassStereotype.LISTENER),
        "Processor" to Decorator(setOf("@nestjs/bullmq", "@nestjs/bull"), ClassStereotype.LISTENER),
        "Cron" to Decorator(setOf("@nestjs/schedule"), ClassStereotype.SCHEDULED),
        "Interval" to Decorator(setOf("@nestjs/schedule"), ClassStereotype.SCHEDULED),
        "Timeout" to Decorator(setOf("@nestjs/schedule"), ClassStereotype.SCHEDULED),
        "Injectable" to Decorator(setOf("@nestjs/common"), ClassStereotype.SERVICE),
        "Module" to Decorator(setOf("@nestjs/common"), ClassStereotype.CONFIGURATION),
        // MikroORM 7 moved its decorators to @mikro-orm/decorators/legacy and /es.
        "Entity" to Decorator(setOf("typeorm", "@mikro-orm/core", "@mikro-orm/decorators"), ClassStereotype.ENTITY),
        "Schema" to Decorator(setOf("@nestjs/mongoose"), ClassStereotype.ENTITY),
    )

    /**
     * Guards, pipes, interceptors, filters, middleware, and Passport strategies are `@Injectable`
     * too, but they wrap requests rather than serve them, so they get no service ring.
     */
    private val REQUEST_PLUMBING_SUFFIXES = listOf(".guard", ".pipe", ".interceptor", ".filter", ".middleware", ".strategy")
    private val REQUEST_PLUMBING = Regex(
        """\bimplements\s+[^{]*\b(?:CanActivate|PipeTransform|NestInterceptor|ExceptionFilter|NestMiddleware)\b""",
    )

    private val NAMED_IMPORT =
        Regex("""\bimport\s+(?:type\s+)?(?:[\w$]+\s*,\s*)?\{([^}]*)}\s*from\s*['"]([^'"]+)['"]""")
    private val NAMESPACE_IMPORT = Regex("""\bimport\s+\*\s+as\s+([\w$]+)\s+from\s*['"]([^'"]+)['"]""")
    private val REQUIRE = Regex("""\b(?:const|let|var)\s*\{([^}]*)}\s*=\s*require\(\s*['"]([^'"]+)['"]\s*\)""")
    private val ALIAS = Regex("""\s+as\s+""")
    private val REQUIRE_ALIAS = Regex("""\s*:\s*""")
    private val DECORATOR = Regex("""@([A-Za-z_$][\w$]*)(?:\.([A-Za-z_$][\w$]*))?\s*\(""")
    private val SOURCE_MATCH = Key.create<FileContentCache.Inspection<StereotypeMatch?>>("emberkai.nodeStereotype")

    fun isCandidate(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        val name = file.name.lowercase(Locale.ROOT)
        return file.extension?.lowercase(Locale.ROOT) in SOURCE_EXTENSIONS &&
            !name.endsWith(".d.ts") && !name.endsWith(".d.mts") && !name.endsWith(".d.cts")
    }

    fun match(file: VirtualFile, project: Project): StereotypeMatch? {
        if (!isCandidate(file)) return null
        val nodePackage = NodePackages.nearest(file, project)
        val appRoot = NodePackages.nextAppRoot(file, project) ?: nodePackage?.takeIf { "next" in it.dependencies }?.root
        appRoot?.let { root -> nextRoute(file, root)?.let { return it } }
        if (nodePackage == null || !nodePackage.dependsOnAny(DECORATED_PACKAGES)) return null
        if (!FileContentCache.mayLoad()) return FileContentCache.peek(file, SOURCE_MATCH)?.value
        return FileContentCache.inspect(file, SOURCE_MATCH, null) { decorated(it, file.nameWithoutExtension) }
    }

    /**
     * Whether [file] is a route, controller, gateway, listener, or job. Ignores the badge setting,
     * like the other scopes; safe from any thread.
     */
    fun isEntryPoint(file: VirtualFile, project: Project): Boolean {
        if (!isCandidate(file)) return false
        return runReadActionBlocking {
            !project.isDisposed && file.isValid && match(file, project)?.stereotype?.isEntryPoint == true
        }
    }

    /** App Router pages, route handlers, and metadata routes; Pages Router pages and API routes; and middleware. */
    private fun nextRoute(file: VirtualFile, appRoot: VirtualFile): StereotypeMatch? {
        if (file.extension?.lowercase(Locale.ROOT) !in ROUTE_EXTENSIONS) return null
        val relative = VfsUtilCore.getRelativePath(file, appRoot)?.split('/') ?: return null
        // Next.js ignores src/app and src/pages when app/ or pages/ sits next to src/.
        if (relative.first() == "src" && (NodePackages.childDirectory(appRoot, "app") || NodePackages.childDirectory(appRoot, "pages"))) {
            return null
        }
        val segments = if (relative.first() == "src") relative.drop(1) else relative
        val name = file.nameWithoutExtension
        val folders = segments.dropLast(1)
        if (folders.isEmpty()) {
            // Next.js 16 renamed middleware to proxy.
            return if (name == "middleware" || name == "proxy") web("Next.js $name") else null
        }
        // Folders and files starting with an underscore are private to the router.
        if (folders.drop(1).any { it.startsWith('_') } || name.startsWith('_')) return null
        return when (folders.first()) {
            "app" -> when (name) {
                "page" -> web("Next.js page")
                "route" -> web("Next.js route handler")
                in METADATA_ROUTES -> web("Next.js metadata route")
                in ROOT_METADATA_ROUTES -> if (folders.size == 1) web("Next.js metadata route") else null
                else -> null
            }
            // Tests and stories, such as index.test.tsx, sit beside pages without being routes.
            "pages" -> when {
                NOT_A_PAGE.containsMatchIn(name) -> null
                folders.getOrNull(1) == "api" -> web("Next.js API route")
                else -> web("Next.js page")
            }
            else -> null
        }
    }

    private fun web(evidence: String) = StereotypeMatch(ClassStereotype.WEB, evidence)

    /** The highest-priority decorator whose name, alias, or namespace is imported from a module that defines it. */
    internal fun decorated(source: String, baseName: String = ""): StereotypeMatch? {
        val text = withoutComments(source)
        val named = HashMap<String, Pair<String, String>>()
        for (statement in NAMED_IMPORT.findAll(text) + REQUIRE.findAll(text)) {
            val module = statement.groupValues[2]
            val separator = if (statement.value.startsWith("import")) ALIAS else REQUIRE_ALIAS
            for (specifier in statement.groupValues[1].split(',')) {
                val parts = specifier.trim().removePrefix("type ").split(separator)
                val imported = parts.first().trim()
                if (imported.isNotEmpty()) named[parts.last().trim()] = imported to module
            }
        }
        val namespaces = NAMESPACE_IMPORT.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
        if (named.isEmpty() && namespaces.isEmpty()) return null
        val plumbing = REQUEST_PLUMBING_SUFFIXES.any { baseName.endsWith(it) } || REQUEST_PLUMBING.containsMatchIn(text)
        return DECORATOR.findAll(text).mapNotNull { usage ->
            val (first, member) = usage.destructured
            val (imported, module) = if (member.isEmpty()) {
                named[first] ?: return@mapNotNull null
            } else {
                member to (namespaces[first] ?: return@mapNotNull null)
            }
            val decorator = DECORATORS[imported] ?: return@mapNotNull null
            if (decorator.modules.none { module == it || module.startsWith("$it/") }) return@mapNotNull null
            if (plumbing && imported == "Injectable") return@mapNotNull null
            StereotypeMatch(decorator.stereotype, "@$imported")
        }.minByOrNull { it.stereotype.ordinal }
    }

    /**
     * [source] with line and block comments blanked out. Strings and template literals are kept, so
     * a glob or a URL inside quotes does not start a comment.
     */
    internal fun withoutComments(source: String): String {
        val out = StringBuilder(source.length)
        var index = 0
        var quote: Char? = null
        while (index < source.length) {
            val char = source[index]
            val next = source.getOrNull(index + 1)
            when {
                quote != null -> {
                    out.append(char)
                    if (char == '\\' && next != null) {
                        out.append(next)
                        index++
                    } else if (char == quote) {
                        quote = null
                    }
                }
                char == '\'' || char == '"' || char == '`' -> {
                    quote = char
                    out.append(char)
                }
                char == '/' && next == '/' -> {
                    while (index < source.length && source[index] != '\n') index++
                    continue
                }
                char == '/' && next == '*' -> {
                    val end = source.indexOf("*/", index + 2)
                    index = if (end < 0) source.length else end + 2
                    out.append(' ')
                    continue
                }
                else -> out.append(char)
            }
            index++
        }
        return out.toString()
    }
}
