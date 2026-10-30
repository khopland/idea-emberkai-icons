package com.github.khopland.symbols

import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.run.RunningServiceIcon
import com.github.khopland.symbols.run.RunningServiceReader
import com.github.khopland.symbols.run.RunningServices
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled
import com.github.khopland.symbols.spring.SpringServerPort
import com.intellij.execution.RunManager
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile

internal class RunningServicesTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        try {
            RunningServices.getInstance(project).clear()
            savedState?.let(settings::loadState)
        } finally {
            super.tearDown()
        }
    }

    fun testExplicitPortsFollowSpringPrecedence() {
        assertEquals(9000, SpringServerPort.resolve("--server.port=9000", "-Dserver.port=8000", mapOf("SERVER_PORT" to "7000")))
        assertEquals(8000, SpringServerPort.resolve("--debug", "-Dserver.port=1 -Dserver.port=8000", mapOf("SERVER_PORT" to "7000")))
        assertEquals(7000, SpringServerPort.resolve(environment = mapOf("SERVER_PORT" to "7000")))
        assertEquals(
            "Placeholders resolve against the run environment",
            6000,
            SpringServerPort.resolve("--server.port=\${PORT:8080}", environment = mapOf("PORT" to "6000")),
        )
        assertEquals(8080, SpringServerPort.resolve("--server.port=\${PORT:8080}"))
        assertNull("Port 0 picks a random port", SpringServerPort.resolve("--server.port=0"))
        assertNull(SpringServerPort.resolve("--server.port=http"))
        assertNull(SpringServerPort.resolve())
    }

    fun testYamlAndPropertiesPortsAreRead() {
        assertEquals("8081", SpringServerPort.fromYaml("server:\n  port: 8081\n", emptySet()))
        assertEquals("8082", SpringServerPort.fromYaml("server.port: 8082\n", emptySet()))
        val multiDocument = """
            server:
              port: 8080
            ---
            spring:
              config:
                activate:
                  on-profile: prod
            server:
              port: 443
        """.trimIndent()
        assertEquals("8080", SpringServerPort.fromYaml(multiDocument, emptySet()))
        assertEquals("443", SpringServerPort.fromYaml(multiDocument, setOf("prod")))
        assertNull(SpringServerPort.fromYaml("server: [", emptySet()))
        assertEquals("8083", SpringServerPort.fromProperties("server.port = 8083\n"))
        assertNull(SpringServerPort.fromProperties("server.address=0.0.0.0"))
    }

    fun testProfileFilesBeatBaseFilesAndPropertiesBeatYaml() {
        myFixture.addFileToProject("svc/src/main/resources/application.yml", "server:\n  port: 8080\n")
        myFixture.addFileToProject("svc/src/main/resources/application-dev.yml", "server:\n  port: 8090\n")
        myFixture.addFileToProject("svc/src/main/resources/application-dev.properties", "server.port=8091\n")
        myFixture.addFileToProject("svc/src/main/resources/application-local.yaml", "logging: {}\n")
        val resources = listOf(directory("svc/src/main/resources"))
        assertEquals(8080, SpringServerPort.resolve(resourceDirectories = resources))
        assertEquals(8091, SpringServerPort.resolve(profiles = listOf("dev"), resourceDirectories = resources))
        assertEquals(
            "A later profile without a port leaves the earlier profile's port",
            8091,
            SpringServerPort.resolve(profiles = listOf("dev", "local"), resourceDirectories = resources),
        )
        assertEquals(
            "Arguments still beat config files",
            9999,
            SpringServerPort.resolve("--server.port=9999", profiles = listOf("dev"), resourceDirectories = resources),
        )
    }

    fun testApplicationConfigurationMapsToItsModuleFolderAndPort() {
        myFixture.addFileToProject("src/main/resources/application.yml", "server:\n  port: 8080\n")
        myFixture.addFileToProject("src/main/resources/application-kafka.yml", "server:\n  port: 8085\n")
        val configuration = applicationConfiguration("Orders", vmOptions = "-Dspring.profiles.active=kafka")
        configuration.setModule(module)

        val root = directory("")
        val target = withSingleContentRoot(root) {
            RunningServiceReader.read(project, configuration, "Orders", debug = true)
        }
        assertNotNull(target)
        assertEquals(listOf(root), target!!.directories)
        assertEquals(RunningServices.Session("Orders", debug = true, port = 8085), target.session)

        assertNull(
            "A configuration without a module has no unambiguous folder",
            RunningServiceReader.read(project, applicationConfiguration("Loose"), "Loose", debug = false),
        )
    }

    fun testLiveSessionBadgesOnlyItsModuleFolder() {
        myFixture.addFileToProject("orders/build.gradle.kts", "plugins { java }")
        myFixture.addFileToProject("billing/build.gradle.kts", "plugins { java }")
        val orders = directory("orders")
        val billing = directory("billing")
        val handler = Any()

        val changed = RunningServices.getInstance(project)
            .start(handler, listOf(orders), RunningServices.Session("Orders", debug = false, port = 8081))
        assertEquals(listOf(orders), changed)

        val marker = provider.getIcon(orders, 0, project) as? RunningServiceIcon
        assertNotNull("The running module folder carries the marker", marker)
        assertFalse(marker!!.debug)
        assertFalse(provider.getIcon(billing, 0, project) is RunningServiceIcon)
        val resolution = IconResolver.resolveWithDetails(orders, project)
        assertTrue(resolution.detail, resolution.detail.contains("running 'Orders' on port 8081"))
        assertEquals(
            "Running 'Orders' on port 8081",
            IconDescriptions.describe(orders, resolution, project),
        )

        settings.setEnabled(IconRuleCategory.RUNNING_SERVICES, false)
        assertFalse(provider.getIcon(orders, 0, project) is RunningServiceIcon)
        settings.setEnabled(IconRuleCategory.RUNNING_SERVICES, true)

        assertEquals(listOf(orders), RunningServices.getInstance(project).stop(handler))
        assertFalse("A finished session clears the marker", provider.getIcon(orders, 0, project) is RunningServiceIcon)
        assertEquals(emptyList<VirtualFile>(), RunningServices.getInstance(project).stop(handler))
    }

    fun testDebugAndConcurrentSessionsAreDescribed() {
        myFixture.addFileToProject("orders/build.gradle.kts", "plugins { java }")
        val orders = directory("orders")
        val services = RunningServices.getInstance(project)
        services.start("debug", listOf(orders), RunningServices.Session("Orders", debug = true))
        assertTrue((provider.getIcon(orders, 0, project) as RunningServiceIcon).debug)

        services.start("tests", listOf(orders), RunningServices.Session("Orders tests", debug = false))
        assertFalse(
            "The debug color is used only when every session is a debug session",
            (provider.getIcon(orders, 0, project) as RunningServiceIcon).debug,
        )
        assertEquals(
            "Debugging 'Orders', running 'Orders tests'",
            IconDescriptions.describe(orders, IconResolver.resolveWithDetails(orders, project), project),
        )
    }

    /** Other tests leave extra content roots on the shared light module; the mapping needs exactly one. */
    private fun <T> withSingleContentRoot(root: VirtualFile, action: () -> T): T {
        val original = ModuleRootManager.getInstance(module).contentRootUrls.toList()
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.forEach(model::removeContentEntry)
            model.addContentEntry(root)
        }
        try {
            return action()
        } finally {
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.contentEntries.forEach(model::removeContentEntry)
                original.forEach(model::addContentEntry)
            }
        }
    }

    private fun applicationConfiguration(name: String, vmOptions: String? = null): ApplicationConfiguration {
        val factory = ApplicationConfigurationType.getInstance().configurationFactories.single()
        return (RunManager.getInstance(project).createConfiguration(name, factory).configuration as ApplicationConfiguration)
            .apply { vmParameters = vmOptions }
    }

    private fun directory(path: String): VirtualFile =
        myFixture.tempDirFixture.getFile(path) ?: myFixture.tempDirFixture.findOrCreateDir(path)
}
