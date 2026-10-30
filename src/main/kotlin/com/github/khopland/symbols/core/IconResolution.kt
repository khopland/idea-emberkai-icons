package com.github.khopland.symbols.core

import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/** The icon chosen for a file together with the rule and mapping that produced it. */
internal data class IconResolution(
    val icon: Icon?,
    val mappingKey: String? = null,
    val mappingSource: MappingSource? = null,
    val resourcePath: String? = null,
    val contextRule: IconRuleCategory? = null,
    val projectRole: String? = null,
    /** What decorators recognized, such as an environment variant or a live run session. */
    val facts: List<IconFact> = emptyList(),
    val precedence: IconPrecedence,
    val detail: String,
)

/** The first fact of type [T] a decorator recorded, if any. */
internal inline fun <reified T : IconFact> IconResolution.fact(): T? = facts.firstNotNullOfOrNull { it as? T }

/**
 * Something an [IconDecorator] recognized about a file or folder. Project view tooltips list each
 * fact's [label] in [tooltipOrder], lowest first, after the base description.
 */
internal interface IconFact {
    /** A short tooltip phrase, such as "production" or "module accent: payments". */
    val label: String?

    val tooltipOrder: Int

    /** Describes the file itself when no mapping label does, such as "Environment file". */
    fun baseLabel(file: VirtualFile): String? = null
}

internal enum class IconPrecedence(val displayName: String) {
    NATIVE_POLICY("Native IDE icon"),
    PROJECT_ROLE("Project role"),
    CONTEXT_RULE("Context rule"),
    MAPPING("File or folder mapping"),
    FALLBACK("Generic fallback mapping"),
    IDE_DEFAULT("IDE default icon"),
}
