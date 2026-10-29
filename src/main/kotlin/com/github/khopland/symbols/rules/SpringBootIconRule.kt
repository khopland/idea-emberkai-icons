package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.IconRuleCategory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

/** Detects Spring Boot configuration when Spring Boot is the framework declared nearest to the file. */
internal class SpringBootIconRule : ContextIconRule {
    override val category = IconRuleCategory.SPRING_BOOT

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? =
        if (CONFIG_FILE.matcher(name).matches() && JvmFramework.nearest(file, project) == JvmFramework.SPRING_BOOT) {
            JvmFramework.SPRING_BOOT.mappingKey
        } else {
            null
        }

    private companion object {
        val CONFIG_FILE: Pattern = Pattern.compile("(?:application|bootstrap)(?:-.+)?\\.(?:yaml|yml|properties)")
    }
}
