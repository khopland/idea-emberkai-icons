package com.github.khopland.symbols

import com.intellij.ui.icons.CompositeIcon
import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.ReplaceableIcon
import com.intellij.util.IconUtil
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.Icon

internal fun iconsIn(root: Icon?): Set<Icon> {
    if (root == null) return emptySet()
    val seen = Collections.newSetFromMap(IdentityHashMap<Icon, Boolean>())
    object : IconReplacer {
        override fun replaceIcon(icon: Icon?): Icon? {
            if (icon != null && seen.add(icon)) {
                if (icon is CompositeIcon) {
                    for (i in 0 until icon.iconCount) replaceIcon(icon.getIcon(i))
                } else if (icon is ReplaceableIcon) {
                    icon.replaceBy(this)
                }
            }
            return icon
        }
    }.replaceIcon(IconUtil.deepRetrieveIconNow(root))
    return seen
}
