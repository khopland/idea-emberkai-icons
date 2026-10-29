package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.IconRuleCategory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

/**
 * Quarkus, Micronaut, and Ktor configuration: an `application` or `bootstrap` file whose nearest
 * framework declaration (see [JvmFramework.nearest]) is one of them.
 */
internal class JvmFrameworkIconRule : ContextIconRule {
    override val category = IconRuleCategory.JVM_FRAMEWORKS

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (!CONFIG_FILE.matcher(name).matches()) return null
        return JvmFramework.nearest(file, project)?.takeIf { it != JvmFramework.SPRING_BOOT }?.mappingKey
    }

    private companion object {
        val CONFIG_FILE: Pattern = Pattern.compile("(?:application|bootstrap)(?:-.+)?\\.(?:properties|yaml|yml|conf|toml)")
    }
}
