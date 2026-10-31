package com.github.khopland.symbols

import com.github.khopland.symbols.lens.IconLens
import com.github.khopland.symbols.lens.IconLensService
import com.github.khopland.symbols.spring.ActiveSpringProfiles
import com.github.khopland.symbols.statusbar.EmberkaiStatusBarWidget
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.application.ApplicationConfigurationType
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent

internal class EmberkaiStatusBarWidgetTest : IconProviderTestCase() {
    private val addedConfigurations = mutableListOf<RunnerAndConfigurationSettings>()

    override fun tearDown() {
        try {
            addedConfigurations.forEach(RunManager.getInstance(project)::removeConfiguration)
            ActiveSpringProfiles.getInstance(project).update(null)
            IconLensService.getIfCreated(project)?.select(null)
        } finally {
            super.tearDown()
        }
    }

    fun testTextNamesTheLensAndTheActiveProfiles() {
        assertEquals("No lens", EmberkaiStatusBarWidget.text(null, null))
        assertEquals("No lens", EmberkaiStatusBarWidget.text(null, emptySet()))
        assertEquals("Lens: Hotspots", EmberkaiStatusBarWidget.text("Hotspots", null))
        assertEquals("Profile: dev, local", EmberkaiStatusBarWidget.text(null, setOf("local", "dev")))
        assertEquals(
            "Lens: Database migrations · Profile: prod",
            EmberkaiStatusBarWidget.text("Database migrations", setOf("prod")),
        )
    }

    fun testWidgetFollowsTheLensServiceAndProfiles() {
        val widget = EmberkaiStatusBarWidget(project)
        assertEquals("No lens", widget.getSelectedValue())

        IconLensService.getInstance(project).select(IconLens.MIGRATIONS)
        ActiveSpringProfiles.getInstance(project).update(ActiveSpringProfiles.Snapshot("Api", setOf("prod")))

        assertEquals("Lens: Database migrations · Profile: prod", widget.getSelectedValue())
        assertTrue(widget.getTooltipText().contains("Api"))
    }

    fun testProfileGroupListsAndSelectsRunConfigurationsWithProfiles() {
        val prod = addConfiguration("Api (prod)", "--spring.profiles.active=prod")
        val dev = addConfiguration("Api (dev)", "--spring.profiles.active=dev,local")
        addConfiguration("No profiles", null)
        RunManager.getInstance(project).selectedConfiguration = prod

        val group = ActionManager.getInstance().getAction("com.github.khopland.symbols.SpringProfileRunConfigurations") as ActionGroup
        val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
        val entries = group.getChildren(event).map { it as ToggleAction }

        assertEquals(listOf("dev, local (Api (dev))", "prod (Api (prod))"), entries.map { it.templateText })
        assertEquals(listOf(false, true), entries.map { it.isSelected(event) })

        entries.first().setSelected(event, true)
        assertSame(dev, RunManager.getInstance(project).selectedConfiguration)
    }

    private fun addConfiguration(name: String, programArguments: String?): RunnerAndConfigurationSettings {
        val runManager = RunManager.getInstance(project)
        val factory = ApplicationConfigurationType.getInstance().configurationFactories.single()
        val settings = runManager.createConfiguration(name, factory)
        (settings.configuration as ApplicationConfiguration).programParameters = programArguments
        runManager.addConfiguration(settings)
        addedConfigurations += settings
        return settings
    }
}
