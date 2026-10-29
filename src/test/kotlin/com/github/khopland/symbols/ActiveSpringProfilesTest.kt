package com.github.khopland.symbols

import com.github.khopland.symbols.badges.EnvironmentVariant
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled
import com.github.khopland.symbols.spring.ActiveSpringProfileIcon
import com.github.khopland.symbols.spring.ActiveSpringProfiles
import com.github.khopland.symbols.spring.SpringProfileArguments
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.psi.PsiFile
import javax.swing.Icon

internal class ActiveSpringProfilesTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null
    private val addedConfigurations = mutableListOf<RunnerAndConfigurationSettings>()

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        try {
            val runManager = RunManager.getInstance(project)
            addedConfigurations.forEach(runManager::removeConfiguration)
            ActiveSpringProfiles.getInstance(project).update(null)
            savedState?.let(settings::loadState)
        } finally {
            super.tearDown()
        }
    }

    fun testProgramArgumentsVmOptionsAndEnvironmentAreParsed() {
        assertEquals(
            setOf("dev", "local"),
            SpringProfileArguments.fromProgramArguments("--debug --spring.profiles.active=dev,local"),
        )
        assertEquals(
            setOf("dev", "cloud"),
            SpringProfileArguments.fromProgramArguments("\"--spring.profiles.active=dev, cloud\""),
        )
        assertEquals(setOf("prod"), SpringProfileArguments.fromVmOptions("-Xmx1g -Dspring.profiles.active=prod"))
        assertEquals(
            "The JVM keeps the last system property value",
            setOf("qa"),
            SpringProfileArguments.fromVmOptions("-Dspring.profiles.active=dev -Dspring.profiles.active=qa"),
        )
        assertEquals(
            setOf("staging", "eu"),
            SpringProfileArguments.fromEnvironment(mapOf("SPRING_PROFILES_ACTIVE" to "Staging,eu")),
        )
        assertNull(SpringProfileArguments.fromProgramArguments("--server.port=8080"))
        assertNull(SpringProfileArguments.fromVmOptions("-Dspring.profiles.active="))
        assertNull(SpringProfileArguments.fromEnvironment(mapOf("SPRING_PROFILES_INCLUDE" to "dev")))
    }

    fun testSpringPrecedenceSelectsTheHighestSource() {
        val environment = mapOf("SPRING_PROFILES_ACTIVE" to "env")
        assertEquals(
            setOf("args"),
            SpringProfileArguments.resolve(
                "--spring.profiles.active=args", "field", "-Dspring.profiles.active=vm", environment,
            ),
        )
        assertEquals(
            setOf("field"),
            SpringProfileArguments.resolve(null, "field", "-Dspring.profiles.active=vm", environment),
        )
        assertEquals(setOf("vm"), SpringProfileArguments.resolve("", null, "-Dspring.profiles.active=vm", environment))
        assertEquals(setOf("env"), SpringProfileArguments.resolve(null, null, "-Xmx1g", environment))
        assertEquals(emptySet<String>(), SpringProfileArguments.resolve())
    }

    fun testGradleBootRunArgumentsAreParsed() {
        assertEquals(
            setOf("dev"),
            SpringProfileArguments.fromGradleArguments("bootRun --args='--spring.profiles.active=dev --debug'"),
        )
        assertEquals(
            setOf("dev", "local"),
            SpringProfileArguments.fromGradleArguments(":app:bootRun --args \"--spring.profiles.active=dev,local\""),
        )
        assertNull(SpringProfileArguments.fromGradleArguments("bootRun --info"))
    }

    fun testSelectedRunConfigurationMarksOnlyMatchingSpringProfiles() {
        springBootProject()
        val kafka = myFixture.addFileToProject("service/src/main/resources/application-kafka.yml", "server: {}")
        val cloud = myFixture.addFileToProject("service/src/main/resources/application-cloud.properties", "a=b")
        val local = myFixture.addFileToProject("service/src/main/resources/bootstrap-local.yaml", "a: b")
        val qa = myFixture.addFileToProject("service/src/main/resources/application-qa.yaml", "server: {}")
        val plain = myFixture.addFileToProject("plain/application-kafka.yml", "server: {}")

        select("Service", vmOptions = "-Dspring.profiles.active=kafka,cloud", envs = mapOf("SPRING_PROFILES_ACTIVE" to "qa"))
        assertLive("emberkai/icons/spring.svg", provider.getIcon(kafka, 0))
        assertLive("emberkai/icons/spring.svg", provider.getIcon(kafka.virtualFile, 0, project))
        assertLive("emberkai/icons/spring.svg", provider.getIcon(cloud, 0))
        assertNotLive(local)
        assertNotLive(qa)
        assertNotLive(plain)
        val explanation = IconResolver.resolveWithDetails(kafka.virtualFile, project).detail
        assertTrue(explanation, explanation.contains("active profile in run configuration 'Service'"))
        val resolution = IconResolver.resolveWithDetails(kafka.virtualFile, project)
        assertEquals(
            "Spring Boot configuration · active in 'Service'",
            IconDescriptions.describe(kafka.virtualFile, resolution),
        )

        select("Local", programArguments = "--spring.profiles.active=local")
        assertNotLive(kafka)
        val localIcon = provider.getIcon(local, 0) as? ActiveSpringProfileIcon
        assertNotNull("The local profile is live in the new selection", localIcon)
        assertEnvironmentBadge("emberkai/icons/spring.svg", EnvironmentVariant.LOCAL, localIcon!!.baseIcon)
        assertEquals(
            "Spring Boot configuration · local · active in 'Local'",
            IconDescriptions.describe(local.virtualFile, IconResolver.resolveWithDetails(local.virtualFile, project)),
        )
    }

    fun testMarkerCanBeDisabledAndUnreadableSelectionsClearIt() {
        springBootProject()
        val kafka = myFixture.addFileToProject("service/src/main/resources/application-kafka.yml", "server: {}")
        select("Service", programArguments = "--spring.profiles.active=kafka")
        assertLive("emberkai/icons/spring.svg", provider.getIcon(kafka, 0))

        settings.setEnabled(IconRuleCategory.ACTIVE_SPRING_PROFILES, false)
        assertResourceIcon("emberkai/icons/spring.svg", provider.getIcon(kafka, 0))
        settings.setEnabled(IconRuleCategory.ACTIVE_SPRING_PROFILES, true)

        settings.setEnabled(IconRuleCategory.SPRING_BOOT, false)
        assertNotLive(kafka)
        settings.setEnabled(IconRuleCategory.SPRING_BOOT, true)

        select("No profiles", programArguments = "--debug")
        assertNull(ActiveSpringProfiles.getInstance(project).snapshot)
        assertNotLive(kafka)
    }

    private fun springBootProject() {
        myFixture.addFileToProject(
            "service/build.gradle.kts",
            "plugins { id(\"org.springframework.boot\") version \"4.0.0\" }",
        )
    }

    private fun select(
        name: String,
        programArguments: String? = null,
        vmOptions: String? = null,
        envs: Map<String, String> = emptyMap(),
    ) {
        val runManager = RunManager.getInstance(project)
        val factory = ApplicationConfigurationType.getInstance().configurationFactories.single()
        val configurationSettings = runManager.createConfiguration(name, factory)
        (configurationSettings.configuration as ApplicationConfiguration).apply {
            programParameters = programArguments
            vmParameters = vmOptions
            this.envs = envs
        }
        runManager.addConfiguration(configurationSettings)
        addedConfigurations += configurationSettings
        runManager.selectedConfiguration = configurationSettings
    }

    private fun assertLive(baseIconPath: String, actual: Icon?) {
        val marker = actual as? ActiveSpringProfileIcon
        assertNotNull("Expected a live active-profile marker", marker)
        assertResourceIcon(baseIconPath, marker!!.baseIcon)
    }

    private fun assertNotLive(file: PsiFile) {
        val icon = provider.getIcon(file, 0)
        assertFalse("${file.name} should not be marked live", icon is ActiveSpringProfileIcon)
    }
}
