package com.github.khopland.symbols

import com.github.khopland.symbols.audit.CoverageAuditFinding
import com.github.khopland.symbols.audit.CoverageFindingKind
import com.github.khopland.symbols.audit.IconCoverageAudit
import com.intellij.openapi.progress.EmptyProgressIndicator
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal class IconCoverageAuditTest : IconProviderTestCase() {
    fun testAuditGroupsGenericAndUnmappedFilesAndSkipsDependencies() {
        val unmapped = myFixture.addFileToProject("audit/notes.zzqq", "data").virtualFile
        myFixture.addFileToProject("audit/Info.plist", "<plist/>")
        myFixture.addFileToProject("audit/Dockerfile", "FROM scratch")
        myFixture.addFileToProject("audit/node_modules/dep/other.zzqq", "data")

        val result = IconCoverageAudit.run(project, unmapped.parent, EmptyProgressIndicator())

        assertEquals("node_modules is not scanned", 3, result.scannedFiles)
        assertEquals(2, result.totalFindings)
        assertEquals(0, result.omittedFindings)
        val findings = result.groups.flatMap { it.findings }.associateBy { it.file.name }
        assertEquals(setOf("Info.plist", "notes.zzqq"), findings.keys)
        assertEquals(CoverageFindingKind.GENERIC_ICON, findings.getValue("Info.plist").kind)
        assertEquals(CoverageFindingKind.NO_MAPPING, findings.getValue("notes.zzqq").kind)
        assertTrue(result.groups.first().label.startsWith("Generic .plist"))
    }

    fun testSuggestedMappingsUseParentFolderContextAndEscapeKeys() {
        val file = myFixture.addFileToProject("deploy/My Tool.cfg", "data").virtualFile
        fun finding(extension: String?, relativePath: String, parent: String) = CoverageAuditFinding(
            file = file,
            relativePath = relativePath,
            kind = CoverageFindingKind.NO_MAPPING,
            extension = extension,
            parentDirectory = parent,
            mappingKey = null,
            mappingSource = null,
            resourcePath = null,
        )

        assertEquals("extension.cfg", finding("cfg", "My Tool.cfg", ".").suggestedMappingKey)
        assertEquals("extension.deploy/cfg", finding("cfg", "deploy/My Tool.cfg", "deploy").suggestedMappingKey)
        assertEquals("file.deploy/my tool", finding(null, "deploy/My Tool", "deploy").suggestedMappingKey)
        assertEquals(
            "file.deploy/my\\ tool=/emberkai/icons/my-tool.cfg-icon.svg",
            finding(null, "deploy/My Tool", "deploy").suggestedMapping,
        )
    }

    fun testReportUrlPrefillsMissingIconIssueForm() {
        val file = myFixture.addFileToProject("deploy/Tiltfile", "data").virtualFile
        val finding = CoverageAuditFinding(
            file = file,
            relativePath = "deploy/Tiltfile",
            kind = CoverageFindingKind.NO_MAPPING,
            extension = null,
            parentDirectory = "deploy",
            mappingKey = null,
            mappingSource = null,
            resourcePath = null,
        )

        val url = finding.reportUrl
        val query = url.substringAfter('?').split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8)
        }

        assertTrue(url.startsWith("https://github.com/khopland/idea-emberkai-icons/issues/new?"))
        assertEquals("missing-icon.yml", query["template"])
        assertEquals("Missing Emberkai icon: Tiltfile", query["title"])
        assertEquals("deploy/Tiltfile", query["file"])
        assertEquals("No Emberkai mapping", query["result"])
        assertEquals("none", query["mapping"])
        assertEquals(finding.suggestedMapping, query["suggested"])
    }
}
