package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.IconRuleCategory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

/** Detects Spring Boot configuration using cached declarations in nearby build files. */
internal class SpringBootIconRule : ContextIconRule {
    override val category = IconRuleCategory.SPRING_BOOT

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? =
        if (CONFIG_FILE.matcher(name).matches() && BuildFiles.firstDeclared(file, project, MARKERS) != null) {
            "context.spring"
        } else {
            null
        }

    private companion object {
        val CONFIG_FILE: Pattern = Pattern.compile("(?:application|bootstrap)(?:-.+)?\\.(?:yaml|yml|properties)")
        val MARKERS = listOf(
            BuildMarker(
                "springBoot",
                Pattern.compile(
                    "[\"']org\\.springframework\\.boot(?::[^\"']*)?[\"']" +
                        "|<groupId>\\s*org\\.springframework\\.boot\\s*</groupId>",
                ),
            ),
        )
    }
}
