package com.github.khopland.emberkai.core

import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities

/** Adds [description] to a Project view tooltip once, inside its HTML when it has any. */
internal fun appendTooltip(existing: String?, description: String): String {
    if (existing.isNullOrBlank()) return description
    if (description in existing) return existing
    val closing = existing.lastIndexOf("</html>", ignoreCase = true)
    if (closing >= 0) {
        return existing.substring(0, closing) + "<br>" + escapeXmlEntities(description) + existing.substring(closing)
    }
    return "$existing · $description"
}
