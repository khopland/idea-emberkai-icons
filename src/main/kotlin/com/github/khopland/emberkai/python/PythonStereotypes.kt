package com.github.khopland.emberkai.python

import com.github.khopland.emberkai.core.FileContentCache
import com.github.khopland.emberkai.stereotypes.ClassStereotype
import com.github.khopland.emberkai.stereotypes.StereotypeMatch
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/**
 * The JVM class stereotypes, found in Python. Roles come from what a module imports and how it uses
 * it, so `@router.get` counts only when `router` is a FastAPI `APIRouter` the module creates or a
 * conventionally named app or router it imports, and `class Order(models.Model)` only when `models`
 * is Django's. Sources are read only in projects whose manifest names one of the [PythonFramework]s
 * (see [PythonProjects]); where loading from disk is a slow operation, only results already cached
 * are used.
 *
 * Test kinds count only in test modules (`test_*.py`, `*_test.py`, and `conftest.py`), so a local
 * development script that starts a container is not taken for a test.
 */
internal object PythonStereotypes {
    /** What a module defines, before its file name decides whether test kinds count. */
    internal data class Roles(val role: StereotypeMatch?, val test: StereotypeMatch?) {
        fun forModule(isTest: Boolean): StereotypeMatch? = role ?: test.takeIf { isTest }
    }

    private enum class Instance { WEB, TASKS, SCHEDULER, BROKER }

    private val WEB_FACTORIES = setOf(
        "fastapi.FastAPI", "fastapi.APIRouter", "fastapi.routing.APIRouter",
        "flask.Flask", "flask.Blueprint", "flask.blueprints.Blueprint",
        "ninja.NinjaAPI", "ninja.Router", "ninja.router.Router",
    )
    private val ROUTE_METHODS = setOf(
        "get", "post", "put", "patch", "delete", "head", "options", "trace", "route", "api_route", "websocket",
    )

    /** Names an app or router usually has when a module imports it from where it was created. */
    private val CONVENTIONAL_WEB_NAMES = setOf("app", "api", "router", "api_router", "bp", "blueprint")
    private val CONVENTIONAL_TASK_NAMES = setOf("app", "celery", "celery_app")

    private val QUALIFIED_DECORATORS = mapOf(
        "celery.shared_task" to ClassStereotype.LISTENER,
        "dramatiq.actor" to ClassStereotype.LISTENER,
        "django.dispatch.receiver" to ClassStereotype.LISTENER,
        "django.dispatch.dispatcher.receiver" to ClassStereotype.LISTENER,
        "rest_framework.decorators.api_view" to ClassStereotype.WEB,
        "django.views.decorators.http.require_http_methods" to ClassStereotype.WEB,
        "django.views.decorators.http.require_GET" to ClassStereotype.WEB,
        "django.views.decorators.http.require_POST" to ClassStereotype.WEB,
        "django.views.decorators.http.require_safe" to ClassStereotype.WEB,
    )
    private val SETTINGS_BASES = setOf("pydantic_settings.BaseSettings", "pydantic_settings.main.BaseSettings", "pydantic.BaseSettings")
    private val APPLICATION_TEST_BASES = setOf(
        "django.test.TestCase", "django.test.TransactionTestCase", "django.test.LiveServerTestCase",
        "django.contrib.staticfiles.testing.StaticLiveServerTestCase",
        "rest_framework.test.APITestCase", "rest_framework.test.APITransactionTestCase",
        "rest_framework.test.APILiveServerTestCase",
    )
    private val TEST_CLIENTS = setOf("fastapi.testclient.TestClient", "starlette.testclient.TestClient")
    private val ORM_MODULES = listOf("sqlalchemy", "flask_sqlalchemy", "sqlmodel")

    private val FROM_IMPORT = Regex("""^[ \t]*from[ \t]+(\.*[\w.]*)[ \t]+import[ \t]+(\([^)]*\)|[^\n]*)""", RegexOption.MULTILINE)
    private val IMPORT = Regex("""^[ \t]*import[ \t]+([^\n]+)""", RegexOption.MULTILINE)
    private val ALIAS = Regex("""\s+as\s+""")
    private val ASSIGNMENT = Regex("""^[ \t]*(\w+)[ \t]*(?::[^=\n]+)?=[ \t]*([\w.]+)[ \t]*\(""", RegexOption.MULTILINE)
    private val DECORATOR = Regex("""^[ \t]*@[ \t]*([\w.]+)""", RegexOption.MULTILINE)
    /** A class statement and its bases, including Python 3.12 type parameters such as `class Repo[T](Base):`. */
    private val CLASS = Regex("""^[ \t]*class[ \t]+\w+[ \t]*(?:\[[^\]]*\])?[ \t]*\(([^)]*)\)[ \t]*:""", RegexOption.MULTILINE)
    private val TABLE_NAME = Regex("""^[ \t]+__tablename__[ \t]*=""", RegexOption.MULTILINE)
    private val SOURCE_ROLES = Key.create<FileContentCache.Inspection<Roles>>("emberkai.pythonStereotype")
    private val NO_ROLES = Roles(null, null)

    fun isCandidate(file: VirtualFile): Boolean = !file.isDirectory && file.extension?.lowercase(Locale.ROOT) == "py"

    fun isTestModule(name: String): Boolean = name.startsWith("test_") || name.endsWith("_test.py") || name == "conftest.py"

    fun match(file: VirtualFile, project: Project): StereotypeMatch? {
        if (!isCandidate(file)) return null
        val pythonProject = PythonProjects.nearest(file, project) ?: return null
        if (pythonProject.frameworks.isEmpty()) return null
        val roles = if (FileContentCache.mayLoad()) {
            FileContentCache.inspect(file, SOURCE_ROLES, NO_ROLES, ::analyze)
        } else {
            FileContentCache.peek(file, SOURCE_ROLES)?.value ?: return null
        }
        return roles.forModule(isTestModule(file.name))
    }

    /** Whether [file] is a route, view, listener, or job. Ignores the badge setting, like the other scopes; safe from any thread. */
    fun isEntryPoint(file: VirtualFile, project: Project): Boolean {
        if (!isCandidate(file)) return false
        return runReadActionBlocking {
            !project.isDisposed && file.isValid && match(file, project)?.stereotype?.isEntryPoint == true
        }
    }

    /** The highest-priority role [source] defines, and the test kind it would be in a test module. */
    internal fun analyze(source: String): Roles {
        val text = withoutCommentsAndDocstrings(source)
        val imports = Imports.parse(text)
        if (imports.isEmpty()) return NO_ROLES

        val instances = HashMap<String, Instance>()
        for (assignment in ASSIGNMENT.findAll(text)) {
            val (name, callee) = assignment.destructured
            val kind = imports.qualify(callee)?.let(::instanceKind)
            if (kind != null) instances[name] = kind else instances.remove(name)
        }

        val roles = ArrayList<StereotypeMatch>()
        for (usage in DECORATOR.findAll(text)) {
            decoratorRole(usage.groupValues[1], imports, instances)?.let(roles::add)
        }

        var test: StereotypeMatch? = null
        for (declaration in CLASS.findAll(text)) {
            val arguments = declaration.groupValues[1].split(',').map { it.trim() }
            val bases = arguments.filter { it.isNotEmpty() && '=' !in it }
            val isTable = arguments.any { it.replace(" ", "") == "table=True" }
            for (base in bases) {
                if (base == "db.Model") roles += StereotypeMatch(ClassStereotype.ENTITY, base)
                val qualified = imports.qualify(base) ?: continue
                baseRole(qualified, base, isTable)?.let(roles::add)
                if (qualified in APPLICATION_TEST_BASES) test = StereotypeMatch(ClassStereotype.APPLICATION_TEST, base)
            }
        }
        if (TABLE_NAME.containsMatchIn(text) && imports.modules().any { module -> ORM_MODULES.any { module == it || module.startsWith("$it.") } }) {
            roles += StereotypeMatch(ClassStereotype.ENTITY, "__tablename__")
        }

        if (imports.modules().any { it == "testcontainers" || it.startsWith("testcontainers.") }) {
            test = StereotypeMatch(ClassStereotype.CONTAINER_TEST, "testcontainers")
        } else if (test == null && imports.importsAny(TEST_CLIENTS)) {
            test = StereotypeMatch(ClassStereotype.APPLICATION_TEST, "TestClient")
        }
        return Roles(roles.minByOrNull { it.stereotype.ordinal }, test)
    }

    private fun instanceKind(qualified: String): Instance? = when {
        qualified in WEB_FACTORIES -> Instance.WEB
        qualified == "celery.Celery" -> Instance.TASKS
        qualified.startsWith("apscheduler.") && qualified.endsWith("Scheduler") -> Instance.SCHEDULER
        qualified.startsWith("faststream.") && (qualified.endsWith("Broker") || qualified.endsWith("Router")) -> Instance.BROKER
        else -> null
    }

    private fun decoratorRole(expression: String, imports: Imports, instances: Map<String, Instance>): StereotypeMatch? {
        QUALIFIED_DECORATORS[imports.qualify(expression)]?.let { return StereotypeMatch(it, "@${expression.substringAfterLast('.')}") }
        val receiver = expression.substringBeforeLast('.', "")
        if (receiver.isEmpty() || '.' in receiver) return null
        val method = expression.substringAfterLast('.')
        val evidence = "@$expression"
        val stereotype = when (instances[receiver]) {
            Instance.WEB -> ClassStereotype.WEB.takeIf { method in ROUTE_METHODS }
            Instance.TASKS -> ClassStereotype.LISTENER.takeIf { method == "task" }
            Instance.SCHEDULER -> ClassStereotype.SCHEDULED.takeIf { method == "scheduled_job" }
            Instance.BROKER -> ClassStereotype.LISTENER.takeIf { method == "subscriber" }
            // A module that imports its app or router from where it was created, such as `from proj.celery import app`.
            null -> imports.source(receiver)?.let { module ->
                when {
                    method in ROUTE_METHODS && receiver in CONVENTIONAL_WEB_NAMES -> ClassStereotype.WEB
                    method == "task" && (receiver in CONVENTIONAL_TASK_NAMES || "celery" in module) -> ClassStereotype.LISTENER
                    method == "subscriber" -> ClassStereotype.LISTENER
                    method == "scheduled_job" -> ClassStereotype.SCHEDULED
                    else -> null
                }
            }
        }
        return stereotype?.let { StereotypeMatch(it, evidence) }
    }

    private fun baseRole(qualified: String, base: String, isTable: Boolean): StereotypeMatch? {
        val last = qualified.substringAfterLast('.')
        val stereotype = when {
            qualified.startsWith("django.") && qualified.endsWith("models.Model") -> ClassStereotype.ENTITY
            qualified == "sqlmodel.SQLModel" && isTable -> ClassStereotype.ENTITY
            (qualified.startsWith("django.views.") || qualified.startsWith("rest_framework.")) &&
                (last.endsWith("View") || last.endsWith("ViewSet")) -> ClassStereotype.WEB
            qualified in SETTINGS_BASES -> ClassStereotype.CONFIGURATION
            qualified == "django.apps.AppConfig" -> ClassStereotype.CONFIGURATION
            else -> return null
        }
        return StereotypeMatch(stereotype, if (stereotype == ClassStereotype.ENTITY && isTable) "$base, table=True" else base)
    }

    /** A module's imports: names bound by `from … import`, and modules bound by `import`. */
    private class Imports(
        /** Local name to the imported name and the module it came from. */
        private val named: Map<String, Pair<String, String>>,
        /** Local name to the module it is bound to. */
        private val modules: Map<String, String>,
    ) {
        fun isEmpty(): Boolean = named.isEmpty() && modules.isEmpty()

        /** The module [name] was imported from, or null when [name] is not imported. */
        fun source(name: String): String? = named[name]?.second ?: modules[name]

        fun modules(): Sequence<String> = named.values.asSequence().map { it.second } + modules.values.asSequence()

        fun importsAny(qualified: Set<String>): Boolean = named.values.any { (name, module) -> "$module.$name" in qualified }

        /** The dotted name [reference] refers to through the imports, such as `django.db.models.Model` for `models.Model`. */
        fun qualify(reference: String): String? {
            val head = reference.substringBefore('.')
            val rest = reference.substringAfter('.', "").let { if (it.isEmpty()) "" else ".$it" }
            named[head]?.let { (imported, module) -> return "$module.$imported$rest" }
            modules[head]?.let { return "$it$rest" }
            return null
        }

        companion object {
            fun parse(text: String): Imports {
                val named = HashMap<String, Pair<String, String>>()
                val modules = HashMap<String, String>()
                for (statement in FROM_IMPORT.findAll(text)) {
                    val module = statement.groupValues[1]
                    val names = statement.groupValues[2].trim().removePrefix("(").removeSuffix(")")
                    for (specifier in names.split(',')) {
                        val parts = specifier.trim().split(ALIAS)
                        val imported = parts.first().trim()
                        if (imported.isEmpty() || imported == "*") continue
                        named[parts.last().trim()] = imported to module
                    }
                }
                for (statement in IMPORT.findAll(text)) {
                    for (specifier in statement.groupValues[1].split(',')) {
                        val parts = specifier.trim().split(ALIAS)
                        val module = parts.first().trim()
                        if (module.isEmpty()) continue
                        // `import a.b` binds `a`; `import a.b as c` binds `c` to `a.b`.
                        if (parts.size > 1) modules[parts.last().trim()] = module else module.substringBefore('.').let { modules[it] = it }
                    }
                }
                return Imports(named, modules)
            }
        }
    }

    /**
     * [source] with comments removed and triple-quoted strings, such as docstrings, blanked out with
     * their line breaks kept, so an example decorator in a docstring counts for nothing. Other
     * strings are kept, so a `#` inside quotes does not start a comment.
     */
    internal fun withoutCommentsAndDocstrings(source: String): String {
        val out = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (char == '#') {
                while (index < source.length && source[index] != '\n') index++
                continue
            }
            if (char != '"' && char != '\'') {
                out.append(char)
                index++
                continue
            }
            val quote = if (source.startsWith("$char$char$char", index)) "$char$char$char" else char.toString()
            val start = index
            index += quote.length
            while (index < source.length) {
                when {
                    source[index] == '\\' -> index += 2
                    source.startsWith(quote, index) -> { index += quote.length; break }
                    quote.length == 1 && source[index] == '\n' -> break
                    else -> index++
                }
            }
            val end = minOf(index, source.length)
            if (quote.length == 3) {
                for (position in start until end) out.append(if (source[position] == '\n') '\n' else ' ')
            } else {
                out.append(source, start, end)
            }
        }
        return out.toString()
    }
}
