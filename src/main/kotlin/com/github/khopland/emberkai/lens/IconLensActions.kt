package com.github.khopland.emberkai.lens

import com.github.khopland.emberkai.ownership.CodeOwners
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex

/**
 * View > Emberkai Icons > Lens: one radio-style entry per lens, plus No Lens, Changed on this branch
 * and Hotspots when Git is available, Failing tests when the IDE runs tests, Depends on for the
 * selected module and the one the active lens names, and an Owned by submenu with every owner named
 * in the project's CODEOWNERS file.
 */
class IconLensActionGroup : ActionGroup(), DumbAware {
    private val fixed: List<AnAction> = listOf(
        SelectIconLensAction(null),
        Separator.create(),
        *IconLens.entries.map(::SelectIconLensAction).toTypedArray(),
    )

    private val changedOnBranch = SelectIconLensAction(ChangedOnBranchLens)
    private val hotspots = SelectIconLensAction(HotspotsLens)
    private val failingTests = SelectIconLensAction(FailingTestsLens)

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val project = event?.project
        val children = fixed.toMutableList()
        // Lenses that follow Git and the test runner, offered only when the IDE has them.
        val live = if (project == null) emptyList() else listOfNotNull(
            changedOnBranch.takeIf { BranchChanges.getInstance(project) != null },
            hotspots.takeIf { Hotspots.getInstance(project) != null },
            failingTests.takeIf { TestFailures.getInstance(project) != null },
        )
        if (live.isNotEmpty()) {
            children += Separator.create()
            children += live
        }
        val moduleLenses = project?.let { moduleDependentsLenses(it, event) }.orEmpty()
        if (moduleLenses.isNotEmpty()) {
            children += Separator.create()
            children += moduleLenses.map(::SelectIconLensAction)
        }
        val owners = project?.let(::projectOwners).orEmpty()
        if (owners.isNotEmpty()) {
            val ownerActions = listOf(SelectIconLensAction(UnownedLens), Separator.create()) +
                owners.map { SelectIconLensAction(OwnerLens(it)) }
            val ownerGroup = DefaultActionGroup("Owned By", ownerActions).apply {
                isPopup = true
            }
            children += Separator.create()
            children += ownerGroup
        }
        return children.toTypedArray()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /** The lens for the module of the selection, and the active module lens if it names another module. */
    private fun moduleDependentsLenses(project: Project, event: AnActionEvent): List<ModuleDependentsLens> {
        val selected = event.getData(PlatformCoreDataKeys.MODULE)
            ?: event.getData(CommonDataKeys.VIRTUAL_FILE)?.let { ProjectFileIndex.getInstance(project).getModuleForFile(it) }
        val active = IconLensService.getIfCreated(project)?.active as? ModuleDependentsLens
        return listOfNotNull(active, selected?.let { ModuleDependentsLens(it.name) }).distinct()
    }

    private fun projectOwners(project: Project): List<String> {
        val codeOwners = project.guessProjectDir()?.let(CodeOwners::fileAt) ?: return emptyList()
        return CodeOwners.owners(codeOwners).sortedBy { it.lowercase() }
    }
}

internal class SelectIconLensAction(private val lens: Lens?) : ToggleAction(
    when (lens) {
        null -> "No Lens"
        UnownedLens -> "No Owner"
        is OwnerLens -> lens.owner
        else -> lens.displayName
    },
    when (lens) {
        null -> "Show every file in full color"
        UnownedLens -> "Keep files that CODEOWNERS gives no owner in full color and dim the rest of the Project view"
        is OwnerLens -> "Keep files owned by ${lens.owner} in full color and dim the rest of the Project view"
        is ModuleDependentsLens ->
            "Keep files in ${lens.moduleName} and in every module that depends on it in full color " +
                "and dim the rest of the Project view"
        ChangedOnBranchLens ->
            "Keep files changed since this branch left the default branch, including uncommitted ones, " +
                "in full color and dim the rest of the Project view"
        HotspotsLens -> hotspotsDescription()
        FailingTestsLens ->
            "Keep test files with a test that failed the last time it ran in full color " +
                "and dim the rest of the Project view"
        else -> "Keep ${lens.displayName.lowercase()} files in full color and dim the rest of the Project view"
    },
    null,
), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabled = event.project != null
        // The counts are settings, so the description follows them.
        if (lens == HotspotsLens) event.presentation.description = hotspotsDescription()
        if (lens == FailingTestsLens) event.presentation.text = failingTestsText(event.project)
    }

    override fun isSelected(event: AnActionEvent): Boolean {
        val project = event.project ?: return lens == null
        return IconLensService.getIfCreated(project)?.active == lens
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val project = event.project ?: return
        // Unchecking the active lens turns lenses off; unchecking No Lens does nothing.
        IconLensService.getInstance(project).select(if (state) lens else null)
    }
}

private fun hotspotsDescription(): String {
    val settings = EmberkaiLensSettings.getInstance()
    return "Keep the ${settings.hotspotLimit} files changed in the most commits over the last " +
        "${settings.hotspotDays} days in full color and dim the rest of the Project view"
}

/** "Failing tests", with how many files hold one once a run has failed. */
private fun failingTestsText(project: Project?): String {
    val count = project?.let { TestFailures.getInstance(it)?.files()?.size } ?: 0
    return if (count == 0) FailingTestsLens.displayName else "${FailingTestsLens.displayName} ($count)"
}
