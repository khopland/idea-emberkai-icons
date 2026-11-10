package com.github.khopland.emberkai.node

import com.github.khopland.emberkai.IconProviderTestCase
import com.github.khopland.emberkai.core.BadgeSlot
import com.github.khopland.emberkai.core.IconDescriptions
import com.github.khopland.emberkai.core.IconResolver
import com.github.khopland.emberkai.settings.IconRuleCategory
import com.github.khopland.emberkai.core.fact
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.scopes.EmberkaiScope
import com.github.khopland.emberkai.stereotypes.ClassStereotype
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import java.nio.file.Files

internal class NodeStereotypesTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        savedState?.let(settings::loadState)
        super.tearDown()
    }

    fun testNextJsRoutesAreWebEntryPoints() {
        packageJson("web", """{ "dependencies": { "next": "16.0.0", "react": "19.0.0" } }""")
        val routes = mapOf(
            "web/app/page.tsx" to "Next.js page",
            "web/app/(shop)/cart/[id]/page.jsx" to "Next.js page",
            "web/app/@modal/(.)photo/[id]/page.tsx" to "Next.js page",
            "web/app/api/orders/route.ts" to "Next.js route handler",
            "web/app/sitemap.ts" to "Next.js metadata route",
            "web/app/blog/opengraph-image.tsx" to "Next.js metadata route",
            "web/pages/index.tsx" to "Next.js page",
            "web/pages/api/orders/[id].ts" to "Next.js API route",
            // Catch-all routes and file-like routes keep their dots.
            "web/pages/docs/[...slug].tsx" to "Next.js page",
            "web/pages/shop/[[...slug]].tsx" to "Next.js page",
            "web/pages/api/auth/[...nextauth].ts" to "Next.js API route",
            "web/pages/sitemap.xml.ts" to "Next.js page",
            "web/app/robots.ts" to "Next.js metadata route",
            "web/middleware.ts" to "Next.js middleware",
            "web/proxy.ts" to "Next.js proxy",
        )
        for ((path, evidence) in routes) {
            val match = match(file(path, "export default function Page() {}"))
            assertEquals(path, ClassStereotype.WEB to evidence, match?.stereotype to match?.evidence)
        }
        for (path in listOf(
            "web/app/layout.tsx", "web/app/_components/page.tsx", "web/pages/_app.tsx", "web/app/page.css",
            "web/components/page.tsx", "web/lib/middleware.ts", "web/app/page.d.ts", "web/pages/index.test.tsx",
            "web/pages/index.stories.tsx", "web/pages/index.spec.tsx",
            // Next.js serves robots and manifest only from the root of app/.
            "web/app/blog/robots.ts",
            // Next.js ignores src/app when app/ sits next to src/.
            "web/src/app/page.tsx",
        )) {
            assertNull(path, match(file(path, "export {}")))
        }
    }

    fun testNextJsSourceFolderAppsAreRecognized() {
        packageJson("shop", """{ "dependencies": { "next": "16.0.0" } }""")
        assertEquals("Next.js route handler", match(file("shop/src/app/api/route.ts", "export {}"))?.evidence)
        assertEquals("Next.js proxy", match(file("shop/src/proxy.ts", "export {}"))?.evidence)
    }

    fun testNextJsAppsInAMonorepoAreRootedAtTheirConfig() {
        // An Nx-style workspace: one root package.json, and each app marked by its next.config.
        packageJson("repo", """{ "dependencies": { "next": "16.0.0", "@nestjs/common": "11.0.0" } }""")
        file("repo/apps/web/next.config.ts", "export default {}")
        assertEquals("Next.js page", match(file("repo/apps/web/app/page.tsx", "export {}"))?.evidence)
        assertNull("Without a next.config, a nested folder is not an app", match(file("repo/apps/docs/app/page.tsx", "export {}")))
        assertEquals(
            "NestJS apps in the same workspace still read the root dependencies",
            ClassStereotype.WEB,
            match(file("repo/apps/api/src/orders.controller.ts", nest("Controller", "@nestjs/common")))?.stereotype,
        )
    }

    fun testNextJsLayoutNeedsTheNextDependency() {
        packageJson("site", """{ "dependencies": { "react": "19.0.0" } }""")
        assertNull(match(file("site/app/page.tsx", "export default function Page() {}")))
    }

    fun testNestJsDecoratorsNeedTheirImports() {
        packageJson("api", """{
	"dependencies": { "@nestjs/common": "11.0.0", "@nestjs/core": "11.0.0", "typeorm": "0.3.20" }
}""")
        val cases = mapOf(
            nest("Controller", "@nestjs/common") to (ClassStereotype.WEB to "@Controller"),
            nest("Injectable", "@nestjs/common") to (ClassStereotype.SERVICE to "@Injectable"),
            nest("Module", "@nestjs/common") to (ClassStereotype.CONFIGURATION to "@Module"),
            nest("Entity", "typeorm") to (ClassStereotype.ENTITY to "@Entity"),
            nest("Entity", "@mikro-orm/core") to (ClassStereotype.ENTITY to "@Entity"),
            nest("Schema", "@nestjs/mongoose") to (ClassStereotype.ENTITY to "@Schema"),
            nest("Cron", "@nestjs/schedule") to (ClassStereotype.SCHEDULED to "@Cron"),
            nest("EventPattern", "@nestjs/microservices") to (ClassStereotype.LISTENER to "@EventPattern"),
            nest("OnEvent", "@nestjs/event-emitter") to (ClassStereotype.LISTENER to "@OnEvent"),
            // Aliases resolve to the imported name.
            "import { Controller as Route } from '@nestjs/common';\n@Route('orders')\nexport class Orders {}"
                to (ClassStereotype.WEB to "@Controller"),
            // Comments inside the braces, as Prettier keeps them.
            "import {\n  // routing\n  Controller, /* web */\n} from '@nestjs/common';\n@Controller()\nexport class A {}"
                to (ClassStereotype.WEB to "@Controller"),
            // Default plus named, namespace, deep-path, and CommonJS imports.
            "import Nest, { Controller } from '@nestjs/common';\n@Controller()\nexport class B {}"
                to (ClassStereotype.WEB to "@Controller"),
            "import * as common from '@nestjs/common';\n@common.Controller()\nexport class C {}"
                to (ClassStereotype.WEB to "@Controller"),
            "import { Controller } from '@nestjs/common/decorators';\n@Controller()\nexport class D {}"
                to (ClassStereotype.WEB to "@Controller"),
            "const { Controller: Route } = require('@nestjs/common');\n@Route()\nclass E {}"
                to (ClassStereotype.WEB to "@Controller"),
            // Globs and URLs in strings do not open comments.
            "import { Module } from '@nestjs/common';\nconst ENTITIES = 'dist/*.entity.js';\nconst API = 'https://x.dev';\n" +
                "@Module({})\nexport class AppModule {} /** end */"
                to (ClassStereotype.CONFIGURATION to "@Module"),
            nest("Entity", "@mikro-orm/decorators/legacy") to (ClassStereotype.ENTITY to "@Entity"),
            // Entry points outrank the roles a class plays inside a service.
            "import { Injectable } from '@nestjs/common';\nimport { Cron } from '@nestjs/schedule';\n" +
                "@Injectable()\nexport class Cleanup {\n  @Cron('0 * * * *')\n  run() {}\n}"
                to (ClassStereotype.SCHEDULED to "@Cron"),
        )
        cases.entries.forEachIndexed { index, (source, expected) ->
            val match = match(file("api/src/case$index.ts", source))
            assertEquals(source, expected, match?.stereotype to match?.evidence)
        }
        val unmatched = listOf(
            "@Controller('orders')\nexport class Orders {}",
            "import { Controller } from './local';\n@Controller('orders')\nexport class Orders {}",
            "import { Entity } from '@acme/orm';\n@Entity()\nexport class Order {}",
            // Commented-out code and documentation are not decorators.
            "// import { Controller } from '@nestjs/common';\n// @Controller()\nexport class F {}",
            "import { Controller } from '@nestjs/common';\n/** Use @Controller() on handlers. */\nexport const G = 1",
            "import { Controller } from '@nestjs/common';\n// TODO: @Controller('later')\nexport class H {}",
        )
        unmatched.forEachIndexed { index, source -> assertNull(source, match(file("api/src/other$index.ts", source))) }
    }

    fun testRequestPlumbingIsNotAService() {
        packageJson("api", """{ "dependencies": { "@nestjs/common": "11.0.0" } }""")
        val guard = "import { Injectable, CanActivate } from '@nestjs/common';\n" +
            "@Injectable()\nexport class Auth implements CanActivate {\n  canActivate() { return true }\n}"
        assertNull(match(file("api/src/auth.ts", guard)))
        assertNull(match(file("api/src/roles.guard.ts", nest("Injectable", "@nestjs/common"))))
        assertNull(match(file("api/src/jwt.strategy.ts", nest("Injectable", "@nestjs/common"))))
        assertEquals(ClassStereotype.SERVICE, match(file("api/src/orders.service.ts", nest("Injectable", "@nestjs/common")))?.stereotype)
    }

    fun testPackageAndConfigChangesRepaintIcons() {
        val listener = NodePackageListener()
        val events = listOf("api/package.json", "web/next.config.mjs", "web/app/page.tsx", "api/node_modules/x/package.json").map { path ->
            VFileContentChangeEvent(null, file(path, "{}"), 0, 0)
        }
        assertTrue(listener.isRelevant(events.subList(0, 1)))
        assertTrue(listener.isRelevant(events.subList(1, 2)))
        assertFalse(listener.isRelevant(events.subList(2, 3)))
        assertFalse("Installs do not repaint the project", listener.isRelevant(events.subList(3, 4)))
    }

    fun testNestJsSourcesAreReadOnlyInNestPackages() {
        packageJson("tool", """{ "devDependencies": { "typescript": "5.9.0" } }""")
        assertNull(match(file("tool/src/orders.ts", nest("Controller", "@nestjs/common"))))
    }

    fun testInstalledPackagesAreIgnored() {
        packageJson("api", """{ "dependencies": { "@nestjs/common": "11.0.0" } }""")
        packageJson("api/node_modules/lib", """{ "dependencies": { "@nestjs/common": "11.0.0" } }""")
        assertNull(match(file("api/node_modules/lib/orders.ts", nest("Controller", "@nestjs/common"))))
    }

    fun testPackagesOutsideTheProjectAreIgnored() {
        val outside = Files.createTempDirectory("emberkai-outside")
        try {
            Files.writeString(outside.resolve("package.json"), """{ "dependencies": { "next": "16.0.0" } }""")
            Files.createDirectories(outside.resolve("app"))
            Files.writeString(outside.resolve("app/page.tsx"), "export default function Page() {}")
            val page = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(outside.resolve("app/page.tsx"))!!
            assertNull("A package.json outside every content root never counts", match(page))
        } finally {
            outside.toFile().deleteRecursively()
        }
    }

    fun testBadgeIsDrawnTopLeftAndFollowsTheSetting() {
        packageJson("api", """{ "dependencies": { "@nestjs/common": "11.0.0" } }""")
        val controller = file("api/src/orders.controller.ts", nest("Controller", "@nestjs/common"))

        val resolution = IconResolver.resolveWithDetails(controller, project)
        val badge = resolution.icon as? StereotypeBadgeIcon
        assertNotNull("A NestJS controller gets the stereotype glyph", badge)
        assertEquals(ClassStereotype.WEB, badge!!.stereotype)
        assertEquals(BadgeSlot.ROLE, badge.slot)
        assertEquals("web controller (@Controller)", resolution.fact<SourceStereotypeFact>()?.label)
        assertEquals("Web controller (@Controller)", IconDescriptions.describe(controller, resolution))

        val dark = IconLoader.getDarkIcon(badge, true) as? StereotypeBadgeIcon
        assertNotNull("Theme previews keep the badge", dark)
        assertNotSame("and swap the glyph for its dark variant too", badge.glyph, dark!!.glyph)
        assertEquals(ClassStereotype.WEB, dark.stereotype)

        settings.setEnabled(IconRuleCategory.STEREOTYPE_BADGES, false)
        assertFalse(IconResolver.resolve(controller, project) is StereotypeBadgeIcon)
    }

    fun testEntryPointsScopeIncludesRoutesAndControllers() {
        packageJson("web", """{ "dependencies": { "next": "16.0.0" } }""")
        packageJson("api", """{ "dependencies": { "@nestjs/common": "11.0.0" } }""")
        val page = file("web/app/page.tsx", "export default function Page() {}")
        val controller = file("api/src/orders.controller.ts", nest("Controller", "@nestjs/common"))
        val service = file("api/src/orders.service.ts", nest("Injectable", "@nestjs/common"))

        assertTrue(EmberkaiScope.ENTRY_POINTS.contains(page, project))
        assertTrue(EmberkaiScope.ENTRY_POINTS.contains(controller, project))
        assertFalse("Services are not entry points", EmberkaiScope.ENTRY_POINTS.contains(service, project))
    }

    private fun match(file: VirtualFile) = NodeStereotypes.match(file, project)

    private fun packageJson(directory: String, content: String) = file("$directory/package.json", content)

    private fun file(path: String, content: String) = myFixture.addFileToProject(path, content).virtualFile

    private fun nest(decorator: String, module: String) =
        "import { $decorator } from '$module';\n\n@$decorator()\nexport class Subject {}\n"
}
