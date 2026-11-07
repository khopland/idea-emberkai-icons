package com.github.khopland.symbols.node

import com.github.khopland.symbols.IconProviderTestCase
import com.github.khopland.symbols.core.BadgeSlot
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.fact
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.EmberkaiScope
import com.github.khopland.symbols.stereotypes.ClassStereotype
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
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
            "web/src/app/api/orders/route.ts" to "Next.js route handler",
            "web/pages/index.tsx" to "Next.js page",
            "web/pages/api/orders/[id].ts" to "Next.js API route",
            "web/middleware.ts" to "Next.js middleware",
            "web/src/proxy.ts" to "Next.js proxy",
        )
        for ((path, evidence) in routes) {
            val match = match(file(path, "export default function Page() {}"))
            assertEquals(path, ClassStereotype.WEB, match?.stereotype)
            assertEquals(path, evidence, match?.evidence)
        }
        for (path in listOf(
            "web/app/layout.tsx", "web/app/_components/page.tsx", "web/pages/_app.tsx", "web/app/page.css",
            "web/components/page.tsx", "web/lib/middleware.ts", "web/app/page.d.ts",
        )) {
            assertNull(path, match(file(path, "export {}")))
        }
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
            nest("Cron", "@nestjs/schedule") to (ClassStereotype.SCHEDULED to "@Cron"),
            nest("EventPattern", "@nestjs/microservices") to (ClassStereotype.LISTENER to "@EventPattern"),
            // Aliases resolve to the imported name.
            "import { Controller as Route } from '@nestjs/common';\n@Route('orders')\nexport class Orders {}"
                to (ClassStereotype.WEB to "@Controller"),
            // Entry points outrank the roles a class plays inside a service.
            "import { Injectable } from '@nestjs/common';\nimport { Cron } from '@nestjs/schedule';\n" +
                "@Injectable()\nexport class Cleanup {\n  @Cron('0 * * * *')\n  run() {}\n}"
                to (ClassStereotype.SCHEDULED to "@Cron"),
        )
        cases.entries.forEachIndexed { index, (source, expected) ->
            val match = match(file("api/src/case$index.ts", source))
            assertEquals(source, expected, match?.stereotype to match?.evidence)
        }
        for (source in listOf(
            "@Controller('orders')\nexport class Orders {}",
            "import { Controller } from './local';\n@Controller('orders')\nexport class Orders {}",
            "import { Entity } from '@mikro-orm/core';\n@Entity()\nexport class Order {}",
        )) {
            assertNull(source, match(file("api/src/other${source.length}.ts", source)))
        }
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
