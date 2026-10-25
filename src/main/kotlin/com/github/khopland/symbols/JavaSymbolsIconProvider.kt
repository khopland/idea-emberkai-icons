package com.github.khopland.symbols

import com.intellij.ide.IconProvider
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.ui.IconManager
import javax.swing.Icon

/** Replaces Java type artwork while using the IDE's own badge and visibility logic. */
class JavaSymbolsIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (!element.isValid || !element.language.isKindOf(JavaLanguage.INSTANCE)) return null

        if (element is PsiClass) {
            // Type parameters have their own native icon and are not class declarations.
            return if (element is PsiTypeParameter) null else classIcon(element, flags)
        }
        if (element is PsiClassOwner) {
            // A Java file can declare several types; prefer the one matching its name.
            val name = FileUtilRt.getNameWithoutExtension(element.name)
            val classes = element.classes
            classes.firstOrNull { it.name == name }?.let { return classIcon(it, flags) }
            if (classes.size == 1) return classIcon(classes[0], flags)
            // Package and module descriptors have special native presentations.
            if (name != "package-info" && name != "module-info") {
                return IconLoader.getIcon("/icons/java/source.svg", JavaSymbolsIconProvider::class.java)
            }
        }
        return null
    }

    private fun classIcon(psiClass: PsiClass, flags: Int): Icon {
        // These are the same layers used for native Java classes: final, static,
        // runnable, test, read status, and any registered IconLayerProvider.
        val locked = flags and Iconable.ICON_FLAG_READ_STATUS != 0 && !psiClass.isWritable
        val layered = IconManager.getInstance().createLayeredIcon(
            psiClass,
            typeIcon(psiClass),
            ElementPresentationUtil.getFlags(psiClass, locked),
        )
        return ElementPresentationUtil.addVisibilityIcon(psiClass, flags, layered)
    }

    private fun typeIcon(psiClass: PsiClass): Icon {
        val type = when {
            psiClass.isAnnotationType -> "annotation"
            psiClass.isEnum -> "enum"
            psiClass.isRecord -> "record"
            psiClass.isInterface -> "interface"
            psiClass is PsiAnonymousClass -> "anonymous"
            else -> {
                val isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
                val isException =
                    ElementPresentationUtil.getClassKind(psiClass) == ElementPresentationUtil.CLASS_KIND_EXCEPTION
                when {
                    isException && isAbstract -> "abstract-exception"
                    isException -> "exception"
                    isAbstract -> "abstract-class"
                    else -> "class"
                }
            }
        }
        return IconLoader.getIcon("/icons/java/$type.svg", JavaSymbolsIconProvider::class.java)
    }
}
