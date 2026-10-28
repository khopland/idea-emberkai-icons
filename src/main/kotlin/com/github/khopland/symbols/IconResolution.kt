package com.github.khopland.symbols

import javax.swing.Icon

/** The icon chosen for a file together with the rule and mapping that produced it. */
internal data class IconResolution(
    val icon: Icon?,
    val mappingKey: String? = null,
    val mappingSource: MappingSource? = null,
    val resourcePath: String? = null,
    val contextRule: String? = null,
    val projectRole: String? = null,
    val environmentVariant: EnvironmentVariant? = null,
    val moduleAccent: ModuleAccent? = null,
    val secretExposed: Boolean = false,
    /** Run configuration that activates this Spring profile file, when the live marker is shown. */
    val activeProfileConfiguration: String? = null,
    val precedence: IconPrecedence,
    val detail: String,
)

internal enum class IconPrecedence(val displayName: String) {
    NATIVE_POLICY("Native IDE icon"),
    PROJECT_ROLE("Project role"),
    CONTEXT_RULE("Context rule"),
    MAPPING("File or folder mapping"),
    FALLBACK("Generic fallback mapping"),
    IDE_DEFAULT("IDE default icon"),
}
