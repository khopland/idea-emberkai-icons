package com.github.khopland.symbols.settings

import com.github.khopland.symbols.core.CategoryGroup
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings.State
import kotlin.reflect.KMutableProperty1

/**
 * A named choice of which features are on. A preset sets every icon category, module accents, and
 * tooltips; it leaves native Java/Kotlin icons, service-folder colors, the palette, and accessibility alone.
 */
internal enum class SettingsPreset(val displayName: String, val description: String) {
    ICONS_ONLY(
        "Icons only",
        "Tool icons for recognized files. No badges, warnings, markers, or tooltips.",
    ),
    RECOMMENDED(
        "Recommended",
        "Tool icons, warnings, markers, and tooltips. Module accents and architecture folders stay off.",
    ),
    EVERYTHING(
        "Everything",
        "Every icon, badge, marker, and tooltip, plus module accents and architecture folders.",
    ),
    ;

    /** A copy of [state] with this preset's features applied. */
    fun applyTo(state: State): State = state.copy().also { result ->
        PRESET_FLAGS.forEach { it.set(result, valueOf(it)) }
    }

    /** Whether [state] has exactly this preset's features, whatever its other settings. */
    fun matches(state: State): Boolean = PRESET_FLAGS.all { it.get(state) == valueOf(it) }

    private fun valueOf(flag: KMutableProperty1<State, Boolean>): Boolean = when (this) {
        RECOMMENDED -> flag.get(DEFAULTS)
        EVERYTHING -> true
        ICONS_ONLY -> IconRuleCategory.entries.any {
            it.group == CategoryGroup.TOOL_ICONS && EmberkaiIconsSettings.flag(it) == flag && flag.get(DEFAULTS)
        }
    }

    companion object {
        private val DEFAULTS = State()

        /** The settings a preset decides; the rest are look and accessibility choices. */
        internal val PRESET_FLAGS: List<KMutableProperty1<State, Boolean>> =
            IconRuleCategory.entries.map(EmberkaiIconsSettings::flag) + listOf(
                State::moduleAwareColors,
                State::semanticTooltips,
                State::codeOwnerTooltips,
            )

        /** The preset [state] matches, or null when its features are a custom mix. */
        fun matching(state: State): SettingsPreset? = entries.firstOrNull { it.matches(state) }
    }
}
