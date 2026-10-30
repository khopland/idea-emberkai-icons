package com.github.khopland.symbols

import com.github.khopland.symbols.badges.EnvironmentVariant
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.EmberkaiScope
import com.github.khopland.symbols.settings.setEnabled

internal class OpenTofuIconTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
        } finally {
            super.tearDown()
        }
    }

    fun testTofuFilesAndVersionFileAreAlwaysOpenTofu() {
        assertCustomPathIcon("stack/main.tofu", "infra/opentofu.svg")
        assertCustomPathIcon("stack/override.tofu.json", "infra/opentofu.svg")
        assertCustomPathIcon(".opentofu-version", "infra/opentofu.svg")
        assertTrue(EmberkaiScope.INFRASTRUCTURE.contains(myFixture.findFileInTempDir(".opentofu-version")!!, project))
    }

    fun testTerraformFilesStayTerraformWithoutOpenTofuSignals() {
        assertPathIcon("plain/main.tf", "files/terraform.svg")
        assertPathIcon("plain/prod.tfvars", "files/terraform.svg")
        myFixture.addFileToProject("plain/.terraform.lock.hcl", "provider \"registry.terraform.io/hashicorp/aws\" {}\n")
        assertIcon("files/terraform.svg", provider.getIcon(myFixture.findFileInTempDir("plain/.terraform.lock.hcl")!!, 0, project))
        assertCustomFolderIcon("plain/terraform", "folders/folder-terraform.svg")
    }

    fun testVersionFileMarksTheTreeBelowIt() {
        myFixture.addFileToProject("infra/.opentofu-version", "1.8.5\n")
        assertCustomPathIcon("infra/main.tf", "infra/opentofu.svg")
        assertCustomPathIcon("infra/modules/network/variables.tf", "infra/opentofu.svg")
        val prod = myFixture.addFileToProject("infra/envs/prod/terraform.tfvars", "region = \"eu\"")
        assertEnvironmentBadge("emberkai/icons/infra/opentofu.svg", EnvironmentVariant.PRODUCTION, provider.getIcon(prod, 0))
        assertCustomFolderIcon("infra/terraform", "folders/folder-opentofu.svg")

        val main = myFixture.findFileInTempDir("infra/main.tf")!!
        assertEquals("OpenTofu configuration", IconDescriptions.describe(main, IconResolver.resolveWithDetails(main, project)))
    }

    fun testSiblingTofuFileMarksItsFolder() {
        myFixture.addFileToProject("mixed/providers.tofu", "")
        assertCustomPathIcon("mixed/main.tf", "infra/opentofu.svg")
    }

    fun testLockFileRegistryToolVersionsAndMiseAreSignals() {
        myFixture.addFileToProject("lock/.terraform.lock.hcl", "provider \"registry.opentofu.org/hashicorp/aws\" {\n  version = \"5.0.0\"\n}\n")
        assertCustomPathIcon("lock/main.tf", "infra/opentofu.svg")
        assertResourceIcon(
            "emberkai/icons/infra/opentofu.svg",
            provider.getIcon(myFixture.findFileInTempDir("lock/.terraform.lock.hcl")!!, 0, project),
        )

        myFixture.addFileToProject("asdf/.tool-versions", "java 21.0.5\nopentofu 1.8.5\n")
        assertCustomPathIcon("asdf/main.tf", "infra/opentofu.svg")

        myFixture.addFileToProject("mise/mise.toml", "[tools]\nopentofu = \"1.8\"\n")
        assertCustomPathIcon("mise/main.tf", "infra/opentofu.svg")

        myFixture.addFileToProject("terraform-tools/.tool-versions", "terraform 1.9.0\n")
        assertPathIcon("terraform-tools/main.tf", "files/terraform.svg")
    }

    fun testAddingASignalLaterSwitchesTheIcon() {
        val main = myFixture.addFileToProject("late/main.tf", "").virtualFile
        assertIcon("files/terraform.svg", provider.getIcon(main, 0, project))

        myFixture.addFileToProject("late/.opentofu-version", "1.8.5\n")
        assertResourceIcon("emberkai/icons/infra/opentofu.svg", provider.getIcon(main, 0, project))
    }

    fun testTofuFoldersAndCategoryToggle() {
        assertCustomFolderIcon("tofu", "folders/folder-opentofu.svg")
        assertCustomFolderIcon("deploy/opentofu", "folders/folder-opentofu.svg")

        myFixture.addFileToProject("off/.opentofu-version", "1.8.5\n")
        settings.setEnabled(IconRuleCategory.INFRASTRUCTURE, false)
        assertPathIcon("off/main.tf", "files/terraform.svg")
    }
}
