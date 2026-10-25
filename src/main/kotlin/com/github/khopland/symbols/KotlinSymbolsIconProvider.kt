package com.github.khopland.symbols

import com.intellij.ide.IconProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.util.KotlinSingleClassFileAnalyzer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import javax.swing.Icon

/** Replaces Kotlin type artwork while keeping Kotlin's native badges and visibility logic. */
class KotlinSymbolsIconProvider : KotlinIconProvider() {
    override fun isMatchingExpected(declaration: KtDeclaration): Boolean = false

    override fun getIcon(psiElement: PsiElement, flags: Int): Icon? {
        if (!psiElement.isValid) return null

        if (psiElement is KtEnumEntry) return null
        if (psiElement is KtClassOrObject) {
            return springIcon(psiElement, flags)
                ?: replaceTypeIcon(super.getIcon(psiElement, flags), typeIcon(psiElement))
        }
        if (psiElement is KtFile) return fileIcon(psiElement, flags)
        if (psiElement is KtLightClass) {
            val origin = psiElement.kotlinOrigin
            if (origin !is KtClassOrObject || origin is KtEnumEntry) return null
            return springIcon(psiElement, flags)
                ?: replaceTypeIcon(super.getIcon(psiElement, flags), typeIcon(origin))
        }
        return null
    }

    /**
     * This provider must run before the bundled Kotlin provider, so extension order cannot
     * also put Spring Boot ahead of it. Yield to Spring's icons explicitly instead.
     */
    private fun springIcon(element: PsiElement, flags: Int): Icon? {
        for (provider in IconProvider.EXTENSION_POINT_NAME.extensionList) {
            if (provider === this || !provider.javaClass.name.startsWith("com.intellij.spring.")) continue
            provider.getIcon(element, flags)?.let { return it }
        }
        return null
    }

    private fun fileIcon(file: KtFile, flags: Int): Icon? {
        if (file.isScript()) {
            // Gradle Kotlin DSL files keep the Gradle script icon from the file provider.
            if (file.name.endsWith(".gradle.kts")) return null
            return replaceTypeIcon(super.getIcon(file, flags), icon("script"))
        }
        val single = KotlinSingleClassFileAnalyzer.getSingleClass(file)
        if (single == null || single is KtEnumEntry) return null
        return replaceTypeIcon(super.getIcon(file, flags), typeIcon(single))
    }

    private fun typeIcon(type: KtClassOrObject): Icon? {
        if (type is KtObjectDeclaration) return icon("object")
        if (type !is KtClass) return null
        if (type.isAnnotation()) return icon("annotation")
        if (type.isEnum()) return icon("enum")
        if (type.isInterface()) return icon("interface")
        return icon(if (type.hasModifier(KtTokens.ABSTRACT_KEYWORD)) "abstract-class" else "class")
    }

    private fun replaceTypeIcon(source: Icon?, custom: Icon?): Icon? {
        if (source == null || custom == null) return null
        if (isNativeTypeIcon(source)) return custom
        if (source is RowIcon) {
            for (i in 0 until source.iconCount) {
                val current = source.getIcon(i)
                val replaced = replaceTypeIcon(current, custom)
                if (replaced != null && replaced !== current) source.setIcon(replaced, i)
            }
        }
        return source
    }

    private fun isNativeTypeIcon(icon: Icon): Boolean = when (icon) {
        KotlinIcons.CLASS,
        KotlinIcons.ABSTRACT_CLASS,
        KotlinIcons.INTERFACE,
        KotlinIcons.ENUM,
        KotlinIcons.ANNOTATION,
        KotlinIcons.OBJECT,
        KotlinIcons.SCRIPT -> true
        else -> false
    }

    private fun icon(type: String): Icon =
        IconLoader.getIcon("/icons/kotlin/$type.svg", KotlinSymbolsIconProvider::class.java)
}
