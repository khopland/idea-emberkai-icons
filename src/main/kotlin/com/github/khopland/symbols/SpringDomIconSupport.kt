package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlFile
import com.intellij.util.xml.DomManager
import javax.swing.Icon

/**
 * Keeps Spring's XML configuration files on the Emberkai Spring icon instead of the generic XML
 * artwork. Spring declares its config icon through a DOM file description; detecting that
 * description avoids a compile-time dependency on the Spring plugin.
 */
internal object SpringDomIconSupport {
    private const val SPRING_DESCRIPTION_PREFIX = "com.intellij.spring."

    private val springIcon: Icon by lazy {
        IconLoader.getIcon("/emberkai/icons/spring.svg", SymbolsIconProvider::class.java)
    }

    fun iconFor(element: PsiElement): Icon? {
        val file = element as? XmlFile ?: return null
        return if (isSpringConfig(file)) springIcon else null
    }

    fun iconFor(file: VirtualFile, project: Project): Icon? {
        if (!"xml".equals(file.extension, true)) return null
        val psiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
        return if (isSpringConfig(psiFile)) springIcon else null
    }

    internal fun isSpringDescription(className: String): Boolean =
        className.startsWith(SPRING_DESCRIPTION_PREFIX)

    private fun isSpringConfig(file: XmlFile): Boolean {
        val domManager = DomManager.getDomManager(file.project)
        return CachedValuesManager.getCachedValue(file) {
            val description = domManager.getDomFileDescription(file)
            CachedValueProvider.Result.create(
                description != null && isSpringDescription(description.javaClass.name),
                domManager,
                PsiModificationTracker.MODIFICATION_COUNT,
            )
        }
    }
}
